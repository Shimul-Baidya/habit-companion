package com.example.habit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HabitEntity::class, CompletionEntity::class, ScheduleHistoryEntity::class, TrackingHistoryEntity::class, CoachMessageEntity::class, CoachCacheEntity::class, CoachActionEntity::class, HabitFieldVersion::class],
    version = 3,
    exportSchema = true,
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun coachDao(): CoachDao
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun historyDao(): HabitHistoryDao

    companion object {
        private const val NAME = "habitflow.db"

        fun build(context: Context): HabitDatabase =
            Room.databaseBuilder(context.applicationContext, HabitDatabase::class.java, NAME)
                .addMigrations(HabitMigrations.MIGRATION_1_2, HabitMigrations.MIGRATION_2_3)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
