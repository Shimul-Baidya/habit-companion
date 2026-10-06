package com.example.habit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One habit. `archivedAt` is what SCR-14 sets when a habit is archived: the row leaves
 * Home but its completion history is kept. Deleting is the only irreversible path.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Key into the icon set offered by SCR-05, not a drawable resource id. */
    @ColumnInfo(name = "icon_key") val iconKey: String = "default",
    /** Key into the swatch set offered by SCR-05. */
    @ColumnInfo(name = "color_key") val colorKey: String = "primary",
    val frequency: Frequency = Frequency.DAILY,
    /**
     * Bitmask of scheduled weekdays, bit 0 = Monday. Only meaningful for
     * [Frequency.WEEKLY] and [Frequency.CUSTOM]; DAILY schedules all seven.
     */
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int = ALL_DAYS,
    /** Times per scheduled day. The Coach shrinks this rather than resetting a streak. */
    val goal: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
) {
    companion object {
        const val ALL_DAYS = 0b111_1111
    }
}

enum class Frequency { DAILY, WEEKLY, CUSTOM }
