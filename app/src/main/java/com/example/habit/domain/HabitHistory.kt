package com.example.habit.domain

import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/** Independent of Room: dated settings preserve the meaning of each historical log. */
sealed interface HabitSchedule {
    data object Daily : HabitSchedule
    data class Weekly(val completions: Int) : HabitSchedule {
        init { require(completions in 1..7) { "Weekly quota must be between 1 and 7" } }
    }
    data class Custom(val weekdays: Set<DayOfWeek>) : HabitSchedule {
        init { require(weekdays.isNotEmpty()) { "Select at least one weekday" } }
    }
}

sealed interface TrackingMode {
    data object Binary : TrackingMode
    data class Quantity(val target: BigDecimal, val unit: String) : TrackingMode {
        init {
            require(target.signum() > 0) { "Target must be positive" }
            require(unit.isNotBlank() && unit == unit.trim() && unit.length <= 40) {
                "Provide a trimmed unit of at most 40 characters"
            }
        }
    }
}

sealed interface CompletionValue {
    data class Binary(val done: Boolean) : CompletionValue
    data class Quantity(val amount: BigDecimal, val unit: String) : CompletionValue {
        init {
            require(amount.signum() >= 0) { "Amount cannot be negative" }
            require(unit.isNotBlank() && unit == unit.trim() && unit.length <= 40) {
                "Provide a trimmed unit of at most 40 characters"
            }
        }
    }
}

data class HabitSettings(val schedule: HabitSchedule, val tracking: TrackingMode = TrackingMode.Binary)
data class EffectiveSettings(val from: LocalDate, val settings: HabitSettings)
data class HabitLog(val date: LocalDate, val value: CompletionValue)

/** Archive stops new expectations; logs already made on the archive date remain facts. */
data class HabitHistory(
    val createdOn: LocalDate,
    val settings: List<EffectiveSettings>,
    val logs: List<HabitLog> = emptyList(),
    val archivedOn: LocalDate? = null,
) {
    init {
        require(settings.isNotEmpty() && settings.first().from == createdOn)
        require(settings.zipWithNext().all { (a, b) -> a.from < b.from }) {
            "Settings must have unique, ascending effective dates"
        }
        require(settings.zipWithNext().all { (a, b) ->
            a.settings.schedule == b.settings.schedule ||
                (a.settings.schedule !is HabitSchedule.Weekly && b.settings.schedule !is HabitSchedule.Weekly) ||
                b.from.dayOfWeek == DayOfWeek.MONDAY
        }) { "Changes to or from Weekly start on Monday" }
        require(archivedOn == null || archivedOn >= createdOn)
        require(logs.map { it.date }.distinct().size == logs.size) { "One log per date" }
        logs.forEach { log ->
            require(isActiveOn(log.date) || (log.date == archivedOn && log.date >= createdOn)) {
                "Log outside the habit's active dates"
            }
            val historical = requireNotNull(settingsOn(log.date))
            require(CompletionRules.isScheduled(historical.schedule, log.date)) { "Log on a rest date" }
            CompletionRules.isComplete(historical.tracking, log.value)
        }
    }

    fun settingsOn(date: LocalDate): HabitSettings? =
        settings.lastOrNull { it.from <= date }?.settings

    fun isActiveOn(date: LocalDate): Boolean = date >= createdOn &&
        (archivedOn == null || date < archivedOn)
}

/** Caller supplies a Clock with the device zone; tests can advance it deterministically. */
class HabitDateSource(private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock)
}

object CompletionRules {
    fun isScheduled(schedule: HabitSchedule, date: LocalDate): Boolean = when (schedule) {
        HabitSchedule.Daily, is HabitSchedule.Weekly -> true
        is HabitSchedule.Custom -> date.dayOfWeek in schedule.weekdays
    }

    fun canCorrect(history: HabitHistory, date: LocalDate, today: LocalDate): Boolean =
        date <= today && history.isActiveOn(date) &&
            history.settingsOn(date)?.let { isScheduled(it.schedule, date) } == true

    /** Reject mode/unit mismatches instead of guessing conversions. */
    fun isComplete(mode: TrackingMode, value: CompletionValue): Boolean = when (mode) {
        TrackingMode.Binary -> {
            require(value is CompletionValue.Binary) { "Binary log required" }
            value.done
        }
        is TrackingMode.Quantity -> {
            require(value is CompletionValue.Quantity && value.unit == mode.unit) {
                "Quantity log with the historical unit required"
            }
            value.amount >= mode.target
        }
    }

    /** Replacing a past log evaluates its historical target, never today's target. */
    fun correct(history: HabitHistory, log: HabitLog, today: LocalDate): HabitHistory {
        require(canCorrect(history, log.date, today)) { "Date is not eligible for correction" }
        isComplete(requireNotNull(history.settingsOn(log.date)).tracking, log.value)
        return history.copy(logs = (history.logs.filterNot { it.date == log.date } + log).sortedBy { it.date })
    }
}
