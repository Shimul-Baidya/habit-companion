package com.example.habit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId

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
     * Legacy projection, bit 0 = Monday. Authoritative dated schedules live in
     * schedule_history; v1 Weekly masks retain Custom historical semantics.
     */
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int = ALL_DAYS,
    /** Preserved v1 metadata; targets now live in dated tracking_history. */
    val goal: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    @ColumnInfo(name = "created_epoch_day", defaultValue = "0")
    val createdEpochDay: Long = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
    @ColumnInfo(name = "archived_epoch_day") val archivedEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val cue: String = "",
    @ColumnInfo(defaultValue = "''") val anchor: String = "",
    @ColumnInfo(name = "plan_note", defaultValue = "''") val planNote: String = "",
    /** Null inherits global configuration; false explicitly disables it. */
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean? = null,
    @ColumnInfo(name = "reminder_minute") val reminderMinute: Int? = null,
) {
    companion object {
        const val ALL_DAYS = 0b111_1111
    }
}

enum class Frequency { DAILY, WEEKLY, CUSTOM }
