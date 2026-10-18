package com.haoqi.training.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlanEntity::class,
        PlanDayEntity::class,
        ExerciseEntity::class,
        SetStateEntity::class,
        FoodEntryEntity::class,
        ProfileEntity::class,
        DailyActivityEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun dietDao(): DietDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /** v2：个人资料区分「用户已保存」和默认占位值。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE profile ADD COLUMN configured INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v3：只增列，保留训练日 ID、动作、组状态和明确完成状态。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE plan_days ADD COLUMN name TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v4: no synthetic daily data; retain the legacy profile column unchanged. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `daily_activity` (`date` TEXT NOT NULL, `kcal` REAL NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "curious_training.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
