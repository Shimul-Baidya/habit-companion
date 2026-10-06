package com.example.habit.reminders

import com.example.habit.data.local.HabitRecord
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.StatsAggregator
import java.time.*

data class ReminderSlot(val at: Instant, val date: LocalDate, val minute: Int)
object ReminderPlanner {
    fun minute(record: HabitRecord, config: SettingsRepository.Configuration): Int? {
        if (!config.reminderEnabled || record.habit.reminderEnabled == false || record.habit.archivedAt != null) return null
        return record.habit.reminderMinute ?: config.reminderMinute
    }
    fun due(record: HabitRecord, date: LocalDate): Boolean {
        if (record.habit.archivedAt != null || date.toEpochDay() < record.habit.createdEpochDay) return false
        val facts = StatsAggregator.evaluate(record.toHistory(), date)
        return facts.dueToday && !facts.doneToday
    }
    fun key(id: Long, date: LocalDate) = "$id@${date.toEpochDay()}"
    fun next(records: List<HabitRecord>, config: SettingsRepository.Configuration, now: Instant,
        zone: ZoneId, delivered: Set<String>): ReminderSlot? {
        val today = now.atZone(zone).toLocalDate()
        // Every approved schedule has an eligible day within the next seven days.
        return (0L..7L).flatMap { offset ->
            val date = today.plusDays(offset)
            records.mapNotNull { record ->
                val minute = minute(record, config) ?: return@mapNotNull null
                val at = date.atTime(minute / 60, minute % 60).atZone(zone).toInstant()
                if (key(record.habit.id, date) !in delivered && due(record, date)) ReminderSlot(at, date, minute) else null
            }
        }.minByOrNull { it.at }
    }
}
