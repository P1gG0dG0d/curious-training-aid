package com.haoqi.training.ui.diet

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haoqi.training.ai.DeepSeekClient
import com.haoqi.training.ai.FoodDraft
import com.haoqi.training.data.db.FoodEntryEntity
import com.haoqi.training.data.db.ProfileEntity
import com.haoqi.training.data.prefs.ApiConfigStore
import com.haoqi.training.data.repo.DietRepository
import com.haoqi.training.data.repo.ProfileRepository
import com.haoqi.training.domain.BmrCalculator
import com.haoqi.training.domain.Sex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.haoqi.training.domain.DailyDiet
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐"),
}

sealed interface AiState {
    data object Idle : AiState
    data object Analyzing : AiState
    data class Result(val drafts: List<FoodDraft>, val date: String) : AiState
    data class Failed(val message: String) : AiState
}

data class DietUiState(
    val date: String = LocalDate.now().toString(),
    val entries: List<FoodEntryEntity> = emptyList(),
    val profile: ProfileEntity? = null,
    val activityKcal: Float = 0f,
    val message: String? = null,
    val aiState: AiState = AiState.Idle,
) {
    val totalKcal: Float get() = entries.sumOf { it.kcal.toDouble() }.toFloat()
    val totalProtein: Float get() = entries.sumOf { it.protein.toDouble() }.toFloat()
    val totalFat: Float get() = entries.sumOf { it.fat.toDouble() }.toFloat()
    val totalCarbs: Float get() = entries.sumOf { it.carbs.toDouble() }.toFloat()

    val bmrKcal: Float?
        get() = profile?.takeIf { it.configured }?.let {
            BmrCalculator.bmrKcal(runCatching { Sex.valueOf(it.sex) }.getOrDefault(Sex.MALE),
                it.weightKg, it.heightCm, it.age).takeIf { value -> value.isFinite() && value >= 0f }
        }
    val expenditureKcal: Float get() = DailyDiet.expenditure(bmrKcal, activityKcal)

    fun entriesOf(meal: MealType): List<FoodEntryEntity> = entries.filter { it.meal == meal.name }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModel(
    private val dietRepo: DietRepository,
    private val profileRepo: ProfileRepository,
    private val apiConfigStore: ApiConfigStore,
) : ViewModel() {

    /** 当前记录日期（本地时区）。跨过午夜回到前台时由 refreshDate() 切成新的一天。 */
    private val selectedDate = MutableStateFlow(LocalDate.now().toString())
    val date: String get() = selectedDate.value

    private val aiState = MutableStateFlow<AiState>(AiState.Idle)
    private var analyzeJob: Job? = null
    private val client = DeepSeekClient()

    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<DietUiState> = selectedDate.flatMapLatest { day ->
        combine(dietRepo.observeEntries(day), dietRepo.observeActivity(day),
            profileRepo.profileFlow, aiState, message) { entries, activity, profile, ai, notice ->
            DietUiState(day, entries, profile, activity?.kcal ?: 0f, notice, ai)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState(date = selectedDate.value))

    init {
        viewModelScope.launch { profileRepo.ensureDefault() }
        viewModelScope.launch {
            while (isActive) { refreshDate(); delay(1_000) }
        }
    }

    private fun currentDay(editorDate: String): Boolean {
        refreshDate()
        return DailyDiet.canSave(editorDate, LocalDate.now()).also {
            if (!it) message.value = "日期已切换，未保存旧日期操作，请重新填写今天的数据"
        }
    }

    fun saveActivity(editorDate: String, kcal: Float) {
        if (!currentDay(editorDate)) return
        if (!DailyDiet.validActivity(kcal)) { message.value = "活动消耗必须是非负有限数字"; return }
        viewModelScope.launch {
            dietRepo.saveActivity(editorDate, kcal)
            message.value = "已保存 $editorDate 的活动消耗"
        }
    }

    /** 日期变了就切到新的「今天」。 */
    fun refreshDate() {
        val today = LocalDate.now().toString()
        if (today != selectedDate.value) {
            selectedDate.value = today
            clearAiState()
            message.value = "日期已切换为 $today，未填写的活动消耗默认为 0"
        }
    }

    fun addEntry(
        editorDate: String,
        meal: MealType,
        name: String,
        portion: String,
        kcal: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        estimated: Boolean,
    ) {
        if (!currentDay(editorDate) || name.isBlank()) return
        viewModelScope.launch {
            dietRepo.addEntry(
                FoodEntryEntity(
                    date = editorDate,
                    meal = meal.name,
                    name = name.trim(),
                    portion = portion.trim().ifBlank { "1 份" },
                    kcal = kcal.coerceAtLeast(0f),
                    protein = protein.coerceAtLeast(0f),
                    fat = fat.coerceAtLeast(0f),
                    carbs = carbs.coerceAtLeast(0f),
                    estimated = estimated,
                )
            )
        }
    }

    fun updateEntry(entry: FoodEntryEntity) {
        if (!currentDay(entry.date)) return
        viewModelScope.launch { dietRepo.updateEntry(entry) }
    }

    fun deleteEntry(entry: FoodEntryEntity) {
        if (!currentDay(entry.date)) return
        viewModelScope.launch { dietRepo.deleteEntry(entry) }
    }

    fun analyzePhoto(editorDate: String, imageBytes: ByteArray, mime: String) {
        if (!currentDay(editorDate)) return
        val config = apiConfigStore.config.value
        if (!config.isConfigured) {
            aiState.value = AiState.Failed("请先在设置里填写 DeepSeek API Key")
            return
        }
        analyzeJob?.cancel()
        aiState.value = AiState.Analyzing
        analyzeJob = viewModelScope.launch {
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            client.analyzeFood(config, base64, mime)
                .onSuccess { if (currentDay(editorDate)) aiState.value = AiState.Result(it, editorDate) }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    aiState.value = AiState.Failed(error.message ?: "识别失败")
                }
        }
    }

    /** 识别结果确认后入账（全部标记为估算）。 */
    fun confirmDrafts(editorDate: String, meal: MealType, drafts: List<FoodDraft>) {
        if (!currentDay(editorDate)) return
        drafts.forEach { draft ->
            addEntry(
                editorDate = editorDate,
                meal = meal,
                name = draft.name,
                portion = draft.portion,
                kcal = draft.kcal,
                protein = draft.protein,
                fat = draft.fat,
                carbs = draft.carbs,
                estimated = true,
            )
        }
        clearAiState()
    }

    fun clearAiState() {
        analyzeJob?.cancel()
        aiState.value = AiState.Idle
    }
}
