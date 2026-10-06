package com.example.habit.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class DateRange(val first: LocalDate, val last: LocalDate) {
    init { require(first <= last) }
    operator fun contains(date: LocalDate): Boolean = date >= first && date <= last
}

data class CompletionTotals(val completed: Long = 0, val missed: Long = 0, val pending: Long = 0) {
    val eligible: Long get() = completed + missed + pending
    val consistency: Double? get() = if (eligible == 0L) null else completed.toDouble() / eligible
    operator fun plus(other: CompletionTotals) = CompletionTotals(completed + other.completed, missed + other.missed, pending + other.pending)
}
data class CalendarBucket(val range: DateRange, val totals: CompletionTotals)

data class HabitEvaluation(
    val history: HabitHistory,
    val today: LocalDate,
    val occurrences: List<ScheduledOccurrence>,
    val metrics: HistoryMetrics,
    val settingsToday: HabitSettings?,
    val valueToday: CompletionValue?,
    val doneToday: Boolean,
    val dueToday: Boolean,
    val canLogToday: Boolean,
    val amountProgress: Float,
) {
    fun totals(range: DateRange? = null): CompletionTotals = StatsAggregator.totals(occurrences, range)
}

/** Range statistics and all-time facts derive from the same dated occurrence replay. */
object StatsAggregator {
    fun evaluate(history: HabitHistory, today: LocalDate): HabitEvaluation {
        // A zone/clock rollback can make already-stored facts later than the displayed date.
        // Keep those records, but replay only facts available as of today. Write guards still
        // forbid creating future completions; the strict calculator rejects future inputs.
        val asOfToday = if (history.logs.any { it.date > today }) history.copy(logs = history.logs.filter { it.date <= today }) else history
        val occurrences = HistoryCalculator.occurrences(asOfToday, today)
        val metrics = HistoryCalculator.summarize(occurrences)
        val settings = history.settingsOn(today)
        val value = history.logs.singleOrNull { it.date == today }?.value
        val complete = settings != null && value != null && CompletionRules.isComplete(settings.tracking, value)
        val eligible = CompletionRules.canCorrect(history, today, today)
        val due = eligible && when (settings?.schedule) {
            is HabitSchedule.Weekly -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                complete || occurrences.any { it.periodStart == monday && it.outcome == OccurrenceOutcome.PENDING }
            }
            else -> true
        }
        val amountProgress = when {
            complete -> 1f
            settings?.tracking is TrackingMode.Quantity && value is CompletionValue.Quantity ->
                value.amount.divide(settings.tracking.target, 6, RoundingMode.HALF_UP).coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toFloat()
            else -> 0f
        }
        return HabitEvaluation(history, today, occurrences, metrics, settings, value, complete, due, eligible, amountProgress)
    }

    fun totals(occurrences: List<ScheduledOccurrence>, range: DateRange? = null): CompletionTotals {
        var result = CompletionTotals()
        for (item in occurrences) {
            if (range != null && item.date !in range) continue
            result += when (item.outcome) {
                OccurrenceOutcome.COMPLETED -> CompletionTotals(completed = 1)
                OccurrenceOutcome.MISSED -> CompletionTotals(missed = 1)
                OccurrenceOutcome.PENDING -> CompletionTotals(pending = 1)
            }
        }
        return result
    }

    fun aggregate(evaluations: List<HabitEvaluation>, range: DateRange? = null): CompletionTotals =
        evaluations.fold(CompletionTotals()) { total, habit -> total + habit.totals(range) }

    fun week(date: LocalDate, weekStart: DayOfWeek): DateRange {
        val first = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return DateRange(first, first.plusDays(6))
    }
    fun dailyBuckets(evaluations: List<HabitEvaluation>, week: DateRange): List<CalendarBucket> =
        (0L..6L).map { offset ->
            val date = week.first.plusDays(offset)
            val range = DateRange(date, date)
            CalendarBucket(range, aggregate(evaluations, range))
        }

    /** Display buckets clip to the month; they never redefine Monday–Sunday quota periods. */
    fun monthBuckets(evaluations: List<HabitEvaluation>, month: YearMonth, weekStart: DayOfWeek): List<CalendarBucket> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val result = mutableListOf<CalendarBucket>()
        var bucket = week(first, weekStart).first
        while (bucket <= last) {
            val range = DateRange(maxOf(bucket, first), minOf(bucket.plusDays(6), last))
            result += CalendarBucket(range, aggregate(evaluations, range))
            bucket = bucket.plusDays(7)
        }
        return result
    }
}
