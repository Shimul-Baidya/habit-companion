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
    /** Preserved v1 metadata; never used as a measured quantity. */
    val count: Int = 1,
    @ColumnInfo(name = "completed_at") val completedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "tracking_mode", defaultValue = "'BINARY'") val trackingMode: String = "BINARY",
    /** Exact decimal text, never a SQLite REAL or a guessed conversion from legacy count. */
    @ColumnInfo(name = "quantity_amount") val quantityAmount: String? = null,
    @ColumnInfo(name = "quantity_unit") val quantityUnit: String? = null,
)
