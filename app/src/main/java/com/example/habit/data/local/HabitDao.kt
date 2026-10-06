package com.example.habit.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    /** SCR-03 / SCR-04 decide between the empty and populated Home from this. */
    @Query("SELECT * FROM habits WHERE archived_at IS NULL ORDER BY created_at ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    /** SCR-01 reads this while the splash holds the first frame. */
    @Query("SELECT COUNT(*) FROM habits WHERE archived_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    /** SCR-14 archive: the row leaves Home, the history stays. */
    @Query("UPDATE habits SET archived_at = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Long = System.currentTimeMillis())

    /** SCR-14 delete. Cascades to completions — irreversible, as the dialog says. */
    @Delete
    suspend fun delete(habit: HabitEntity)
}
