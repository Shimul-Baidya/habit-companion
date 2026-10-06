package com.example.habit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {

    /** SCR-04 — everything logged today, for the ring and the row states. */
    @Query("SELECT * FROM completions WHERE epoch_day = :epochDay")
    fun observeForDate(epochDay: Long): Flow<List<CompletionEntity>>

    /** SCR-07 / SCR-08 month grid. */
    @Query(
        "SELECT * FROM completions WHERE habit_id = :habitId " +
            "AND epoch_day BETWEEN :fromDay AND :toDay ORDER BY epoch_day ASC"
    )
    fun observeRange(habitId: Long, fromDay: Long, toDay: Long): Flow<List<CompletionEntity>>

    /** SCR-12 / SCR-13 — every habit's completions across a window. */
    @Query("SELECT * FROM completions WHERE epoch_day BETWEEN :fromDay AND :toDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<CompletionEntity>>

    @Upsert
    suspend fun upsert(completion: CompletionEntity)

    /** Untoggling today, and correcting a past day from the month grid. */
    @Query("DELETE FROM completions WHERE habit_id = :habitId AND epoch_day = :epochDay")
    suspend fun clear(habitId: Long, epochDay: Long)
}
