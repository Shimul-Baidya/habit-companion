package com.example.habit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class OccurrenceOutcome { COMPLETED, MISSED, PENDING }
enum class HabitAttention { NEUTRAL, HEALTHY, AT_RISK }

/** Weekly successes carry their actual date; shortfalls carry the closed period's Sunday. */
data class ScheduledOccurrence(
    val date: LocalDate,
    val outcome: OccurrenceOutcome,
    val periodStart: LocalDate = date,
    val slot: Int = 0,
)

data class HistoryMetrics(
    val currentStreak: Int,
    val bestStreak: Int,
    val attention: HabitAttention,
    val completed: Int,
    val missed: Int,
    val pending: Int,
) {
    val eligible: Int get() = completed + missed + pending
    /** Open expectations affect progress/consistency, but never become premature misses. */
    val consistency: Double? get() = if (eligible == 0) null else completed.toDouble() / eligible
}

/** Pure replay with no lookback cap. All consumers use the same ordered occurrence history. */
object HistoryCalculator {
    fun occurrences(history: HabitHistory, today: LocalDate): List<ScheduledOccurrence> {
        require(history.logs.all { it.date <= today }) { "Future completion is not allowed" }
        val logs = history.logs.associateBy { it.date }
        val result = mutableListOf<ScheduledOccurrence>()
        var date = history.createdOn
        val end = minOf(today, history.archivedOn?.minusDays(1) ?: today)
        while (date <= end) {
            val settings = requireNotNull(history.settingsOn(date))
            when (val schedule = settings.schedule) {
                is HabitSchedule.Weekly -> {
                    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val sunday = monday.plusDays(6)
                    val segmentEnd = minOf(sunday, end)
                    val availableDays = ChronoUnit.DAYS.between(date, sunday).toInt() + 1
                    val quota = (schedule.completions * availableDays + 6) / 7
                    var achieved = 0
                    var logDate = date
                    while (logDate <= segmentEnd) {
                        val value = logs[logDate]?.value
                        if (value != null && CompletionRules.isComplete(requireNotNull(history.settingsOn(logDate)).tracking, value)) {
                            if (achieved < quota) {
                                result += ScheduledOccurrence(logDate, OccurrenceOutcome.COMPLETED, monday, achieved)
                                achieved++
                            }
                        }
                        logDate = logDate.plusDays(1)
                    }
                    val closed = sunday < today && (history.archivedOn == null || history.archivedOn > sunday)
                    // Archive cancels unfilled slots in its interrupted period, without losing successes.
                    val cancelled = history.archivedOn != null && history.archivedOn <= sunday && history.archivedOn <= today
                    if (!cancelled) {
                        repeat(quota - achieved) { offset ->
                            result += ScheduledOccurrence(segmentEnd,
                                if (closed) OccurrenceOutcome.MISSED else OccurrenceOutcome.PENDING,
                                monday, achieved + offset)
                        }
                    }
                    date = sunday.plusDays(1)
                }
                else -> {
                    if (CompletionRules.isScheduled(schedule, date)) {
                        val value = logs[date]?.value
                        val outcome = when {
                            value != null && CompletionRules.isComplete(settings.tracking, value) -> OccurrenceOutcome.COMPLETED
                            date == today -> OccurrenceOutcome.PENDING
                            else -> OccurrenceOutcome.MISSED
                        }
                        result += ScheduledOccurrence(date, outcome)
                    }
                    date = date.plusDays(1)
                }
            }
        }
        return result
    }

    fun calculate(history: HabitHistory, today: LocalDate): HistoryMetrics = summarize(occurrences(history, today))

    fun summarize(occurrences: List<ScheduledOccurrence>): HistoryMetrics {
        var current = 0
        var best = 0
        var atRisk = false
        var recovery = 0
        var completed = 0
        var missed = 0
        var pending = 0
        val recent = ArrayDeque<OccurrenceOutcome>()
        for (occurrence in occurrences) {
            val outcome = occurrence.outcome
            if (outcome == OccurrenceOutcome.PENDING) { pending++; continue }
            recent.addLast(outcome)
            if (recent.size > 7) recent.removeFirst()
            when (outcome) {
                OccurrenceOutcome.COMPLETED -> {
                    completed++
                    current++
                    best = maxOf(best, current)
                    recovery++
                    if (atRisk && recovery >= 2) atRisk = false
                }
                OccurrenceOutcome.MISSED -> {
                    missed++
                    current = 0
                    recovery = 0
                    if (recent.count { it == OccurrenceOutcome.MISSED } >= 2) atRisk = true
                }
                OccurrenceOutcome.PENDING -> error("Handled above")
            }
        }
        return HistoryMetrics(current, best,
            when { atRisk -> HabitAttention.AT_RISK; completed + missed == 0 -> HabitAttention.NEUTRAL; else -> HabitAttention.HEALTHY },
            completed, missed, pending)
    }
}
