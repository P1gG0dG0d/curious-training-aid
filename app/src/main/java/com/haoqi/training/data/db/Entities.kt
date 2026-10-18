package com.haoqi.training.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 训练计划（v1 只有一份，id 固定 1；预留多计划升级空间） */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: Long = 1,
    val dayCount: Int = 1,
)

/** 循环里的某一天。dayIndex 只是编号，不代表日期/时间顺序。 */
@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 1,
    val dayIndex: Int,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val name: String = "",
)

/** 动作：重量与次数分开设置、分开保存，统一应用于该动作所有组。 */
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val orderIndex: Int,
    val name: String,
    val setCount: Int,
    val restSeconds: Int,
    val weightKg: Float,
    val reps: Int,
)

/** 每一组的完成状态（点一下变黑 = done）。 */
@Entity(
    tableName = "set_states",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exerciseId", "setIndex"], unique = true),
        Index("exerciseId"),
    ],
)
data class SetStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setIndex: Int,
    val done: Boolean = false,
)

/** 一条食物记录（date 为 yyyy-MM-dd 本地日期）。 */
@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val meal: String,
    val name: String,
    val portion: String,
    val kcal: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val estimated: Boolean = false,
)

/** 每个本地日期独立保存活动消耗；缺记录时视为 0，不沿用旧资料常量。 */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val date: String,
    val kcal: Float,
)

/** 个人资料（只有一份）。configured=false 表示用户还没保存过，界面不应把默认值当真实数据。 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val sex: String = "MALE",
    val age: Int = 30,
    val heightCm: Float = 175f,
    val weightKg: Float = 70f,
    val activityKcal: Float = 300f,
    val configured: Boolean = false,
)
