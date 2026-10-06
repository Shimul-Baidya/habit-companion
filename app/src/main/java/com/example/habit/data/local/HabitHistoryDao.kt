package com.example.habit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitHistoryDao {
    @Transaction @Query("SELECT * FROM habits ORDER BY created_at, id")
    fun observeRecords(): Flow<List<HabitRecord>>
    @Transaction @Query("SELECT * FROM habits ORDER BY created_at, id")
    suspend fun records(): List<HabitRecord>
    @Transaction @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun record(id: Long): HabitRecord?
    @Upsert suspend fun putScheduleRow(value: ScheduleHistoryEntity)
    @Query("SELECT * FROM schedule_history WHERE habit_id = :id AND effective_day = :day") suspend fun schedule(id: Long, day: Long): ScheduleHistoryEntity?
    @Query("DELETE FROM schedule_history WHERE habit_id = :id AND effective_day = :day") suspend fun deleteScheduleRow(id: Long, day: Long)
    @Transaction suspend fun putSchedule(value: ScheduleHistoryEntity) {
        if (schedule(value.habitId, value.effectiveDay) != value) {
            putScheduleRow(value); bump(value.habitId, "schedule")
        }
    }
    @Transaction suspend fun deleteSchedule(id: Long, day: Long) { deleteScheduleRow(id, day); bump(id, "schedule") }
    @Upsert suspend fun putTrackingRow(value: TrackingHistoryEntity)
    @Query("SELECT * FROM tracking_history WHERE habit_id = :id AND effective_day = :day") suspend fun tracking(id: Long, day: Long): TrackingHistoryEntity?
    @Query("DELETE FROM tracking_history WHERE habit_id = :id AND effective_day = :day") suspend fun deleteTrackingRow(id: Long, day: Long)
    @Transaction suspend fun putTracking(value: TrackingHistoryEntity) {
        if (tracking(value.habitId, value.effectiveDay) != value) {
            putTrackingRow(value); bump(value.habitId, "tracking")
        }
    }
    @Transaction suspend fun deleteTracking(id: Long, day: Long) { deleteTrackingRow(id, day); bump(id, "tracking") }
    @Query("SELECT revision FROM habit_field_versions WHERE habit_id = :id AND field = :field") suspend fun version(id: Long, field: String): Long?
    @Query("INSERT OR IGNORE INTO habit_field_versions(habit_id, field, revision) VALUES (:id, :field, 0)") suspend fun ensureVersion(id: Long, field: String)
    @Query("UPDATE habit_field_versions SET revision = revision + 1 WHERE habit_id = :id AND field = :field") suspend fun incrementVersion(id: Long, field: String)
    @Transaction suspend fun bump(id: Long, field: String) { ensureVersion(id, field); incrementVersion(id, field) }
    @Query("DELETE FROM habits") suspend fun deleteAll()
    @Query("DELETE FROM habits WHERE id = :id") suspend fun delete(id: Long)
}
