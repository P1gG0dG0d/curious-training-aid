package com.haoqi.training.data.repo

import androidx.room.withTransaction
import com.haoqi.training.data.db.AppDatabase
import com.haoqi.training.data.db.ExerciseEntity
import com.haoqi.training.data.db.FoodEntryEntity
import com.haoqi.training.data.db.PlanDayEntity
import com.haoqi.training.data.db.PlanEntity
import com.haoqi.training.data.db.ProfileEntity
import com.haoqi.training.data.db.SetStateEntity
import kotlinx.coroutines.flow.Flow

class TrainingRepository(private val db: AppDatabase) {

    private val dao = db.trainingDao()

    val planFlow: Flow<PlanEntity?> = dao.observePlan()
    val daysFlow: Flow<List<PlanDayEntity>> = dao.observeDays()
    val exercisesFlow: Flow<List<ExerciseEntity>> = dao.observeExercises()
    val setStatesFlow: Flow<List<SetStateEntity>> = dao.observeSetStates()

    /** 首次启动时建一份默认计划（1 天、无动作），用户自己加。 */
    suspend fun ensureSeeded() = db.withTransaction {
        if (dao.getPlan() == null) {
            dao.insertPlan(PlanEntity(id = 1, dayCount = 1))
            dao.insertDay(PlanDayEntity(planId = 1, dayIndex = 1))
        }
    }

    /** 在末尾新增一天。 */
    suspend fun addDay() = db.withTransaction {
        val dayCount = dao.getPlan()?.dayCount ?: 1
        dao.insertDay(PlanDayEntity(planId = 1, dayIndex = dayCount + 1))
        renumberDaysLocked()
    }

    suspend fun removeDay(dayId: Long) = db.withTransaction {
        val remaining = dao.getDays().filterNot { it.id == dayId }
        dao.deleteDay(dayId)
        if (remaining.isEmpty()) {
            // 至少保留一天
            dao.insertDay(PlanDayEntity(planId = 1, dayIndex = 1))
        }
        renumberDaysLocked()
    }

    /** 新增或更新动作（id = 0 视为新增）。组数变化时同步创建/清理组状态。 */
    suspend fun saveExercise(exercise: ExerciseEntity): Long = db.withTransaction {
        if (exercise.id == 0L) {
            val orderIndex = (dao.getExercisesOfDay(exercise.dayId).maxOfOrNull { it.orderIndex } ?: -1) + 1
            val id = dao.insertExercise(exercise.copy(orderIndex = orderIndex))
            dao.insertSets(
                (0 until exercise.setCount).map { SetStateEntity(exerciseId = id, setIndex = it) }
            )
            id
        } else {
            dao.updateExercise(exercise)
            syncSetCountLocked(exercise)
            exercise.id
        }
    }

    suspend fun deleteExercise(exercise: ExerciseEntity) = db.withTransaction {
        dao.deleteExercise(exercise)
    }

    /** 保存某一天的完整动作顺序（长按拖动结束后调用）。 */
    suspend fun reorderExercises(dayId: Long, orderedIds: List<Long>) = db.withTransaction {
        // 只接受属于这一天的动作，顺序号从 0 重排，避免和别天冲突
        val ownIds = dao.getExercisesOfDay(dayId).map { it.id }.toSet()
        orderedIds.filter { it in ownIds }.forEachIndexed { index, id ->
            dao.updateExerciseOrder(id, index)
        }
    }

    /** 点一下组块：切换该组完成状态。 */
    suspend fun setSetDone(exerciseId: Long, setIndex: Int, done: Boolean) = db.withTransaction {
        dao.findSet(exerciseId, setIndex)?.let { dao.updateSet(it.copy(done = done)) }
    }

    /** 只改名称，避免覆盖重命名期间变化的完成状态；空名称恢复默认编号。 */
    suspend fun renameDay(dayId: Long, name: String) {
        dao.renameDay(dayId, name.trim())
    }

    suspend fun setDayCompleted(dayId: Long, completed: Boolean) = db.withTransaction {
        dao.setDayCompleted(dayId, completed)
        // 点「撤销」时，把这一天所有已经变黑的组一起恢复成未完成，
        // 否则再点「完成」前那些组还是黑的，用户得一组一组手动取消。
        if (!completed) dao.clearSetsOfDay(dayId)
    }

    private suspend fun syncSetCountLocked(exercise: ExerciseEntity) {
        val existing = dao.getSets(exercise.id)
        when {
            existing.size < exercise.setCount -> dao.insertSets(
                (existing.size until exercise.setCount).map {
                    SetStateEntity(exerciseId = exercise.id, setIndex = it)
                }
            )

            existing.size > exercise.setCount -> dao.deleteSetsFrom(exercise.id, exercise.setCount)
        }
    }

    /** 删除/新增天后，把 dayIndex 重排成连续的 1..N，并同步计划天数。 */
    private suspend fun renumberDaysLocked() {
        val days = dao.getDays()
        days.forEachIndexed { index, day ->
            if (day.dayIndex != index + 1) dao.updateDay(day.copy(dayIndex = index + 1))
        }
        dao.updateDayCount(days.size.coerceAtLeast(1))
    }
}

class DietRepository(db: AppDatabase) {

    private val dao = db.dietDao()

    fun observeEntries(date: String): Flow<List<FoodEntryEntity>> = dao.observeEntries(date)

    fun observeActivity(date: String) = dao.observeActivity(date)

    suspend fun saveActivity(date: String, kcal: Float) {
        require(kcal.isFinite() && kcal >= 0f)
        java.time.LocalDate.parse(date)
        dao.saveActivity(com.haoqi.training.data.db.DailyActivityEntity(date, kcal))
    }

    suspend fun addEntry(entry: FoodEntryEntity): Long = dao.insertEntry(entry)

    suspend fun updateEntry(entry: FoodEntryEntity) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: FoodEntryEntity) = dao.deleteEntry(entry)
}

class ProfileRepository(db: AppDatabase) {

    private val dao = db.profileDao()

    val profileFlow: Flow<ProfileEntity?> = dao.observeProfile()

    suspend fun ensureDefault() {
        if (dao.getProfile() == null) dao.upsertProfile(ProfileEntity())
    }

    suspend fun saveProfile(profile: ProfileEntity) = dao.upsertProfile(profile)
}
