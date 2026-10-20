package com.haoqi.training.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haoqi.training.data.db.ExerciseEntity
import com.haoqi.training.data.db.PlanDayEntity
import com.haoqi.training.data.repo.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainingUiState(
    val loaded: Boolean = false,
    val dayCount: Int = 1,
    val days: List<PlanDayEntity> = emptyList(),
    val exercisesByDay: Map<Long, List<ExerciseEntity>> = emptyMap(),
    val doneSetsByExercise: Map<Long, Set<Int>> = emptyMap(),
    val selectedDayIndex: Int = 0,
) {
    val selectedDay: PlanDayEntity? get() = days.getOrNull(selectedDayIndex)
    val selectedExercises: List<ExerciseEntity>
        get() = selectedDay?.let { exercisesByDay[it.id].orEmpty() }.orEmpty()
}

class TrainingViewModel(private val repo: TrainingRepository) : ViewModel() {

    private val selectedDayIndex = MutableStateFlow(0)

    val uiState: StateFlow<TrainingUiState> = combine(
        repo.planFlow,
        repo.daysFlow,
        repo.exercisesFlow,
        repo.setStatesFlow,
        selectedDayIndex,
    ) { plan, days, exercises, setStates, selected ->
        TrainingUiState(
            loaded = plan != null,
            dayCount = plan?.dayCount ?: days.size.coerceAtLeast(1),
            days = days,
            exercisesByDay = exercises.groupBy { it.dayId }.mapValues { (_, list) -> list.sortedBy { it.orderIndex } },
            doneSetsByExercise = setStates
                .filter { it.done }
                .groupBy { it.exerciseId }
                .mapValues { (_, list) -> list.map { it.setIndex }.toSet() },
            selectedDayIndex = selected.coerceIn(0, (days.size - 1).coerceAtLeast(0)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUiState())

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    fun selectDay(index: Int) {
        selectedDayIndex.value = index
    }

    fun addDay() {
        viewModelScope.launch {
            repo.addDay()
            selectedDayIndex.value = (repo.daysFlow.first().size - 1).coerceAtLeast(0)
        }
    }

    fun removeSelectedDay() {
        viewModelScope.launch {
            val state = uiState.value
            val day = state.days.getOrNull(state.selectedDayIndex) ?: return@launch
            repo.removeDay(day.id)
            selectedDayIndex.value = selectedDayIndex.value.coerceAtMost((state.days.size - 2).coerceAtLeast(0))
        }
    }

    fun saveExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { repo.saveExercise(exercise) }
    }

    fun deleteExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { repo.deleteExercise(exercise) }
    }

    fun setSetDone(exerciseId: Long, setIndex: Int, done: Boolean) {
        viewModelScope.launch { repo.setSetDone(exerciseId, setIndex, done) }
    }

    fun renameDay(dayId: Long, name: String) {
        viewModelScope.launch { repo.renameDay(dayId, name) }
    }

    /** 长按拖动后保存新的动作顺序。 */
    fun reorderExercises(dayId: Long, orderedIds: List<Long>) {
        viewModelScope.launch { repo.reorderExercises(dayId, orderedIds) }
    }

    fun setDayCompleted(dayId: Long, completed: Boolean) {
        viewModelScope.launch { repo.setDayCompleted(dayId, completed) }
    }

    /** 点组块：切换完成状态；变黑（完成）且该动作有休息时间时，触发休息倒计时。 */
    fun toggleSet(
        exercise: ExerciseEntity,
        setIndex: Int,
        currentlyDone: Boolean,
        onRestStart: (seconds: Int, label: String) -> Unit,
    ) {
        val newDone = !currentlyDone
        setSetDone(exercise.id, setIndex, newDone)
        if (newDone && exercise.restSeconds > 0) {
            onRestStart(exercise.restSeconds, exercise.name)
        }
    }
}
