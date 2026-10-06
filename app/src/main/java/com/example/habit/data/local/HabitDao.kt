package com.example.habit.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
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
    suspend fun updateRow(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id") suspend fun row(id: Long): HabitEntity?
    @Query("INSERT OR IGNORE INTO habit_field_versions(habit_id, field, revision) VALUES (:id, :field, 0)") suspend fun ensureVersion(id: Long, field: String)
    @Query("UPDATE habit_field_versions SET revision = revision + 1 WHERE habit_id = :id AND field = :field") suspend fun incrementVersion(id: Long, field: String)
    @Transaction suspend fun update(habit: HabitEntity) {
        val before = row(habit.id)
        updateRow(habit)
        if (before != null) {
            val fields = buildList {
                if (before.cue != habit.cue) add("cue")
                if (before.anchor != habit.anchor) add("anchor")
                if (before.planNote != habit.planNote) add("plan")
                if (before.reminderEnabled != habit.reminderEnabled || before.reminderMinute != habit.reminderMinute) add("reminder")
            }
            fields.forEach { ensureVersion(habit.id, it); incrementVersion(habit.id, it) }
        }
    }

    /** SCR-14 delete. Cascades to completions — irreversible, as the dialog says. */
    @Delete
    suspend fun delete(habit: HabitEntity)
}
