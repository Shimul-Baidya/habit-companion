package com.example.habit.ui.screens.detail

import com.example.habit.domain.*
import java.time.*

/** Calendar presentation uses dated facts, not a second streak/consistency calculator. */
enum class DayMark { DONE, PARTIAL, MISSED, PENDING, FLEXIBLE, REST, UNAVAILABLE }
data class DetailDay(val date: LocalDate, val mark: DayMark, val editable: Boolean)
fun calendarDays(value: HabitEvaluation, month: YearMonth): List<DetailDay> = (1..month.lengthOfMonth()).map { day ->
    val date = month.atDay(day)
    val history = value.history
    val settings = history.settingsOn(date)
    val eligible = CompletionRules.canCorrect(history, date, value.today)
    val log = history.logs.singleOrNull { it.date == date }?.value
    val mark = when {
        date > value.today || date < history.createdOn -> DayMark.UNAVAILABLE
        settings == null || !CompletionRules.isScheduled(settings.schedule, date) -> DayMark.REST
        !history.isActiveOn(date) && log == null -> DayMark.UNAVAILABLE
        log != null && CompletionRules.isComplete(settings.tracking, log) -> DayMark.DONE
        log is CompletionValue.Quantity && log.amount.signum() > 0 -> DayMark.PARTIAL
        settings.schedule is HabitSchedule.Weekly -> DayMark.FLEXIBLE
        date == value.today -> DayMark.PENDING
        else -> DayMark.MISSED
    }
    DetailDay(date, mark, eligible && history.archivedOn == null)
}
fun positiveCallout(value: HabitEvaluation): Boolean = value.metrics.attention != HabitAttention.AT_RISK &&
    value.metrics.currentStreak == value.metrics.bestStreak && value.metrics.bestStreak >= 7
fun patternReading(value: HabitEvaluation): String? {
    if (value.metrics.attention != HabitAttention.AT_RISK) return null
    val recent = value.occurrences.filter { it.outcome != OccurrenceOutcome.PENDING }.takeLast(7)
    val misses = recent.count { it.outcome == OccurrenceOutcome.MISSED }
    val recovery = recent.takeLastWhile { it.outcome == OccurrenceOutcome.COMPLETED }.size
    return "$misses misses in the last ${recent.size} scheduled occurrences." +
        if (recovery == 1) " One completed occurrence since then; one more clears attention." else " Two consecutive completions clear attention."
}
fun scheduleText(schedule: HabitSchedule): String = when (schedule) {
    HabitSchedule.Daily -> "Every day"
    is HabitSchedule.Weekly -> "${schedule.completions} times per week · Monday–Sunday"
    is HabitSchedule.Custom -> schedule.weekdays.sortedBy { it.value }.joinToString(" · ") { it.name.take(3).lowercase().replaceFirstChar(Char::titlecase) }
}
fun occurrenceUnits(value: HabitEvaluation): Boolean = value.history.settings.any {
    it.from <= value.today && it.settings.schedule is HabitSchedule.Weekly
}
