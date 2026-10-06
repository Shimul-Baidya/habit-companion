package com.example.habit.data.local

import androidx.room.*
import com.example.habit.domain.*
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** Separate dated fields prevent a pending schedule edit from overwriting a target edit. */
@Entity(tableName = "schedule_history", primaryKeys = ["habit_id", "effective_day"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)])
data class ScheduleHistoryEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "effective_day") val effectiveDay: Long,
    val kind: String,
    @ColumnInfo(name = "weekday_mask") val weekdayMask: Int,
    val quota: Int?,
) {
    fun toDomain(): HabitSchedule = when (kind) {
        "DAILY" -> HabitSchedule.Daily
        "WEEKLY" -> HabitSchedule.Weekly(requireNotNull(quota))
        "CUSTOM" -> {
            require(weekdayMask in 1..127) { "Invalid historical weekday mask" }
            HabitSchedule.Custom(DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet())
        }
        else -> error("Unknown schedule type: $kind")
    }
    companion object {
        fun from(id: Long, date: LocalDate, schedule: HabitSchedule) = when (schedule) {
            HabitSchedule.Daily -> ScheduleHistoryEntity(id, date.toEpochDay(), "DAILY", 127, null)
            is HabitSchedule.Weekly -> ScheduleHistoryEntity(id, date.toEpochDay(), "WEEKLY", 127, schedule.completions)
            is HabitSchedule.Custom -> ScheduleHistoryEntity(id, date.toEpochDay(), "CUSTOM",
                schedule.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }, null)
        }
    }
}

@Entity(tableName = "tracking_history", primaryKeys = ["habit_id", "effective_day"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)])
data class TrackingHistoryEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "effective_day") val effectiveDay: Long,
    val mode: String,
    val target: String?,
    val unit: String?,
) {
    fun toDomain(): TrackingMode = when (mode) {
        "BINARY" -> TrackingMode.Binary
        "QUANTITY" -> TrackingMode.Quantity(BigDecimal(requireNotNull(target)), requireNotNull(unit))
        else -> error("Unknown tracking mode: $mode")
    }
    companion object {
        fun from(id: Long, date: LocalDate, tracking: TrackingMode) = when (tracking) {
            TrackingMode.Binary -> TrackingHistoryEntity(id, date.toEpochDay(), "BINARY", null, null)
            is TrackingMode.Quantity -> TrackingHistoryEntity(id, date.toEpochDay(), "QUANTITY", tracking.target.toPlainString(), tracking.unit)
        }
    }
}

data class HabitRecord(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val schedules: List<ScheduleHistoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val tracking: List<TrackingHistoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val completions: List<CompletionEntity>,
) {
    fun toHistory(): HabitHistory {
        val schedulesByDate = schedules.sortedBy { it.effectiveDay }
        val trackingByDate = tracking.sortedBy { it.effectiveDay }
        val effective = (schedules.map { it.effectiveDay } + tracking.map { it.effectiveDay }).distinct().sorted().map { day ->
            EffectiveSettings(LocalDate.ofEpochDay(day), HabitSettings(
                requireNotNull(schedulesByDate.lastOrNull { it.effectiveDay <= day }).toDomain(),
                requireNotNull(trackingByDate.lastOrNull { it.effectiveDay <= day }).toDomain()))
        }
        return HabitHistory(LocalDate.ofEpochDay(habit.createdEpochDay), effective,
            completions.sortedBy { it.epochDay }.map { row -> HabitLog(LocalDate.ofEpochDay(row.epochDay), when (row.trackingMode) {
                "BINARY" -> CompletionValue.Binary(true) // Legacy count is metadata, not a quantity.
                "QUANTITY" -> CompletionValue.Quantity(BigDecimal(requireNotNull(row.quantityAmount)), requireNotNull(row.quantityUnit))
                else -> error("Unknown completion mode: ${row.trackingMode}")
            }) }, habit.archivedEpochDay?.let(LocalDate::ofEpochDay))
    }
}
