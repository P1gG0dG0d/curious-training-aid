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

enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐"),
}

sealed interface AiState {
    data object Idle : AiState
    data object Analyzing : AiState
    data class Result(val drafts: List<FoodDraft>) : AiState
    data class Failed(val message: String) : AiState
}

data class DietUiState(
    val date: String = LocalDate.now().toString(),
    val entries: List<FoodEntryEntity> = emptyList(),
    val profile: ProfileEntity? = null,
    val aiState: AiState = AiState.Idle,
) {
    val totalKcal: Float get() = entries.sumOf { it.kcal.toDouble() }.toFloat()
    val totalProtein: Float get() = entries.sumOf { it.protein.toDouble() }.toFloat()
    val totalFat: Float get() = entries.sumOf { it.fat.toDouble() }.toFloat()
    val totalCarbs: Float get() = entries.sumOf { it.carbs.toDouble() }.toFloat()

    /** 用户还没保存过资料时返回 0，界面会提示先去设置，避免拿默认值当真实消耗。 */
    val expenditureKcal: Float
        get() {
            val profile = profile ?: return 0f
            if (!profile.configured) return 0f
            return BmrCalculator.totalKcal(
                sex = runCatching { Sex.valueOf(profile.sex) }.getOrDefault(Sex.MALE),
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                ageYears = profile.age,
                activityKcal = profile.activityKcal,
            )
        }

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

    private val entriesFlow = selectedDate.flatMapLatest { dietRepo.observeEntries(it) }

    val uiState: StateFlow<DietUiState> = combine(
        entriesFlow,
        profileRepo.profileFlow,
        aiState,
    ) { entries, profile, ai ->
        DietUiState(date = selectedDate.value, entries = entries, profile = profile, aiState = ai)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState(date = selectedDate.value))

    init {
        viewModelScope.launch { profileRepo.ensureDefault() }
    }

    /** 日期变了就切到新的「今天」。 */
    fun refreshDate() {
        val today = LocalDate.now().toString()
        if (today != selectedDate.value) selectedDate.value = today
    }

    fun addEntry(
        meal: MealType,
        name: String,
        portion: String,
        kcal: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        estimated: Boolean,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dietRepo.addEntry(
                FoodEntryEntity(
                    date = date,
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
        viewModelScope.launch { dietRepo.updateEntry(entry) }
    }

    fun deleteEntry(entry: FoodEntryEntity) {
        viewModelScope.launch { dietRepo.deleteEntry(entry) }
    }

    fun analyzePhoto(imageBytes: ByteArray, mime: String) {
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
                .onSuccess { aiState.value = AiState.Result(it) }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    aiState.value = AiState.Failed(error.message ?: "识别失败")
                }
        }
    }

    /** 识别结果确认后入账（全部标记为估算）。 */
    fun confirmDrafts(meal: MealType, drafts: List<FoodDraft>) {
        drafts.forEach { draft ->
            addEntry(
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
