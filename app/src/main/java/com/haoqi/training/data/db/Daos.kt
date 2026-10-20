package com.haoqi.training.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {

    @Query("SELECT * FROM plans WHERE id = 1")
    fun observePlan(): Flow<PlanEntity?>

    @Query("SELECT * FROM plan_days WHERE planId = 1 ORDER BY dayIndex ASC")
    fun observeDays(): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM exercises ORDER BY orderIndex ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM set_states")
    fun observeSetStates(): Flow<List<SetStateEntity>>

    @Query("SELECT * FROM plans WHERE id = 1 LIMIT 1")
    suspend fun getPlan(): PlanEntity?

    @Query("SELECT * FROM plan_days WHERE planId = 1 ORDER BY dayIndex ASC")
    suspend fun getDays(): List<PlanDayEntity>

    @Query("SELECT * FROM exercises WHERE dayId = :dayId ORDER BY orderIndex ASC")
    suspend fun getExercisesOfDay(dayId: Long): List<ExerciseEntity>

    @Query("SELECT * FROM set_states WHERE exerciseId = :exerciseId")
    suspend fun getSets(exerciseId: Long): List<SetStateEntity>

    @Query("SELECT * FROM set_states WHERE exerciseId = :exerciseId AND setIndex = :setIndex LIMIT 1")
    suspend fun findSet(exerciseId: Long, setIndex: Int): SetStateEntity?

    @Insert
    suspend fun insertPlan(plan: PlanEntity)

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(states: List<SetStateEntity>)

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    /** 长按拖动排序：只改这一条的顺序号，不碰组状态。 */
    @Query("UPDATE exercises SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateExerciseOrder(id: Long, orderIndex: Int)

    @Update
    suspend fun updateSet(state: SetStateEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM plan_days WHERE id = :dayId")
    suspend fun deleteDay(dayId: Long)

    @Query("DELETE FROM set_states WHERE exerciseId = :exerciseId AND setIndex >= :fromIndex")
    suspend fun deleteSetsFrom(exerciseId: Long, fromIndex: Int)

    @Query("UPDATE plans SET dayCount = :count WHERE id = 1")
    suspend fun updateDayCount(count: Int)

    @Query("UPDATE plan_days SET name = :name WHERE id = :dayId")
    suspend fun renameDay(dayId: Long, name: String)

    @Query("UPDATE plan_days SET completed = :completed WHERE id = :dayId")
    suspend fun setDayCompleted(dayId: Long, completed: Boolean)
}

@Dao
interface DietDao {

    @Query("SELECT * FROM daily_activity WHERE date = :date")
    fun observeActivity(date: String): Flow<DailyActivityEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActivity(activity: DailyActivityEntity)

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY id ASC")
    fun observeEntries(date: String): Flow<List<FoodEntryEntity>>

    @Insert
    suspend fun insertEntry(entry: FoodEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: FoodEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: FoodEntryEntity)
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)
}
