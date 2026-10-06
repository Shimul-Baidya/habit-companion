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
    @Upsert suspend fun putSchedule(value: ScheduleHistoryEntity)
    @Upsert suspend fun putTracking(value: TrackingHistoryEntity)
    @Query("DELETE FROM habits") suspend fun deleteAll()
    @Query("DELETE FROM habits WHERE id = :id") suspend fun delete(id: Long)
}
