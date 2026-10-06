package com.example.habit.data

import com.example.habit.data.local.HabitRecord
import com.example.habit.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class EvaluatedRecord(val record: HabitRecord, val evaluation: HabitEvaluation)
data class HabitSnapshot(
    val today: LocalDate,
    val records: List<EvaluatedRecord>,
    val active: List<HabitStatus>,
    val lifetime: CompletionTotals,
    val allTimeBest: Int,
    val week: List<CalendarBucket>,
    val month: List<CalendarBucket>,
) {
    val topStreaks: List<HabitStatus> get() = active.filter { it.currentStreak > 0 }.sortedByDescending { it.currentStreak }
    fun totals(range: DateRange): CompletionTotals = StatsAggregator.aggregate(records.map { it.evaluation }, range)
    fun month(month: YearMonth, weekStart: DayOfWeek): List<CalendarBucket> =
        StatsAggregator.monthBuckets(records.map { it.evaluation }, month, weekStart)

    companion object {
        fun from(records: List<HabitRecord>, today: LocalDate, weekStart: DayOfWeek): HabitSnapshot {
            val evaluated = records.map { EvaluatedRecord(it, StatsAggregator.evaluate(it.toHistory(), today)) }
            val active = evaluated.filter { it.record.habit.archivedAt == null && it.evaluation.history.createdOn <= today }.map { (record, value) ->
                HabitStatus(record.habit, value.dueToday, value.doneToday, value.metrics.currentStreak,
                    value.metrics.attention == HabitAttention.AT_RISK, value.metrics.bestStreak, value.metrics.attention,
                    requireNotNull(value.settingsToday), value.amountProgress, value.valueToday, value.canLogToday,
                    value.history.settings.any { it.from <= today && it.settings.schedule is HabitSchedule.Weekly })
            }
            val values = evaluated.map { it.evaluation }
            return HabitSnapshot(today, evaluated, active, StatsAggregator.aggregate(values),
                values.maxOfOrNull { it.metrics.bestStreak } ?: 0,
                StatsAggregator.dailyBuckets(values, StatsAggregator.week(today, weekStart)),
                StatsAggregator.monthBuckets(values, YearMonth.from(today), weekStart))
        }
    }
}
