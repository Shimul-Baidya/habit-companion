package com.example.habit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HabitEntity::class, CompletionEntity::class, ScheduleHistoryEntity::class, TrackingHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun historyDao(): HabitHistoryDao

    companion object {
        private const val NAME = "habitflow.db"

        fun build(context: Context): HabitDatabase =
            Room.databaseBuilder(context.applicationContext, HabitDatabase::class.java, NAME)
                .addMigrations(HabitMigrations.MIGRATION_1_2)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
