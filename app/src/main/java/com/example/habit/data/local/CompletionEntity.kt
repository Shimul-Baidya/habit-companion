package com.example.habit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One habit completed on one day. The date is an epoch day rather than a timestamp so
 * "did I do it today" is an equality check, not a range query across a timezone.
 *
 * CASCADE is the delete path SCR-14 warns about: removing a habit removes its history.
 */
@Entity(
    tableName = "completions",
    primaryKeys = ["habit_id", "epoch_day"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epoch_day")],
)
data class CompletionEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    /** How many times it was logged that day; compared against the habit's goal. */
    val count: Int = 1,
    @ColumnInfo(name = "completed_at") val completedAt: Long = System.currentTimeMillis(),
)
