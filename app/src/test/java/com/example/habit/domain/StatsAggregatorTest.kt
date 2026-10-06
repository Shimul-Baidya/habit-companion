package com.example.habit.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class StatsAggregatorTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private fun history(schedule: HabitSchedule = HabitSchedule.Daily, successes: List<Int> = emptyList()) =
        HabitHistory(monday, listOf(EffectiveSettings(monday, HabitSettings(schedule))),
            successes.map { HabitLog(monday.plusDays(it.toLong()), CompletionValue.Binary(true)) })

    @Test fun consistencyWeightsEligibleOccurrencesRatherThanHabitPercentages() {
        val values = listOf(StatsAggregator.evaluate(history(successes = (0..5).toList()), monday.plusDays(6)),
            StatsAggregator.evaluate(history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), monday.plusDays(6)))
        val total = StatsAggregator.aggregate(values)
        assertEquals(CompletionTotals(6, 1, 1), total)
        assertEquals(0.75, total.consistency!!, 0.0)
        assertNotEquals((6.0 / 7 + 0) / 2, total.consistency!!, 0.0001)
    }

    @Test fun zeroAndFutureRangesHaveNoDenominatorAndTodayIsNotMissed() {
        val value = StatsAggregator.evaluate(history(), monday)
        assertEquals(CompletionTotals(pending = 1), value.totals())
        assertEquals(0, value.metrics.missed)
        assertNull(value.totals(DateRange(monday.plusDays(1), monday.plusDays(7))).consistency)
        assertNull(StatsAggregator.aggregate(emptyList()).consistency)
        assertEquals(HabitAttention.NEUTRAL, value.metrics.attention)
    }

    @Test fun quantityPartialProgressAndDatedTargetsRemainDistinct() {
        val small = TrackingMode.Quantity(BigDecimal("5"), "pages")
        val large = TrackingMode.Quantity(BigDecimal("10"), "pages")
        val h = HabitHistory(monday, listOf(EffectiveSettings(monday, HabitSettings(HabitSchedule.Daily, small)),
            EffectiveSettings(monday.plusDays(1), HabitSettings(HabitSchedule.Daily, large))),
            listOf(HabitLog(monday, CompletionValue.Quantity(BigDecimal("5"), "pages")),
                HabitLog(monday.plusDays(1), CompletionValue.Quantity(BigDecimal("5"), "pages"))))
        val value = StatsAggregator.evaluate(h, monday.plusDays(1))
        assertFalse(value.doneToday)
        assertEquals(0.5f, value.amountProgress, 0.0f)
        assertEquals(CompletionTotals(1, 0, 1), value.totals())
        assertEquals(1, value.metrics.currentStreak)
        val corrected = CompletionRules.correct(h, HabitLog(monday.plusDays(1), CompletionValue.Quantity(BigDecimal("10"), "pages")), monday.plusDays(1))
        val updated = StatsAggregator.evaluate(corrected, monday.plusDays(1))
        assertTrue(updated.doneToday)
        assertEquals(CompletionTotals(2), updated.totals())
        assertEquals(2, updated.metrics.bestStreak)
    }

    @Test fun archiveCancelsPendingButKeepsHistoricalContribution() {
        val active = history(successes = listOf(0, 1))
        val archived = active.copy(archivedOn = monday.plusDays(2))
        val value = StatsAggregator.evaluate(archived, monday.plusDays(20))
        assertEquals(CompletionTotals(2), value.totals())
        assertFalse(value.dueToday)
        assertFalse(value.canLogToday)
        assertEquals(2, value.metrics.bestStreak)
    }

    @Test fun selectedMonthDoesNotRestrictAllTimeBest() {
        val h = history(successes = (0..699).toList() + listOf(1500))
        val value = StatsAggregator.evaluate(h, monday.plusDays(1500))
        val range = DateRange(monday.plusDays(1490), monday.plusDays(1500))
        assertEquals(700, value.metrics.bestStreak)
        assertEquals(1, value.metrics.currentStreak)
        assertEquals(CompletionTotals(1, 10), value.totals(range))
    }

    @Test fun quotaIsWeightedOnceAndDisplayWeekStartDoesNotChangeLifetime() {
        val value = StatsAggregator.evaluate(history(HabitSchedule.Weekly(3), listOf(0, 2, 4, 5)), monday.plusDays(6))
        assertEquals(CompletionTotals(3), value.totals())
        assertFalse(value.dueToday)
        assertTrue(value.canLogToday) // historical corrections still permit extra actual dates
        val first = StatsAggregator.monthBuckets(listOf(value), YearMonth.from(monday), DayOfWeek.MONDAY)
        val second = StatsAggregator.monthBuckets(listOf(value), YearMonth.from(monday), DayOfWeek.SUNDAY)
        assertEquals(first.fold(CompletionTotals()) { a, b -> a + b.totals }, second.fold(CompletionTotals()) { a, b -> a + b.totals })
    }

    @Test fun weeklyShortfallAppearsOnDeadlineAndPartialCurrentWeekStaysPending() {
        val value = StatsAggregator.evaluate(history(HabitSchedule.Weekly(3), listOf(0)), monday.plusDays(7))
        assertEquals(CompletionTotals(missed = 2), value.totals(DateRange(monday.plusDays(6), monday.plusDays(6))))
        assertEquals(CompletionTotals(pending = 3), value.totals(DateRange(monday.plusDays(7), monday.plusDays(7))))
        assertEquals(0, value.metrics.currentStreak)
    }

    @Test fun customRestDateIsNeitherDueNorEditable() {
        val value = StatsAggregator.evaluate(history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), listOf(0)), monday.plusDays(1))
        assertFalse(value.dueToday)
        assertFalse(value.canLogToday)
        assertEquals(CompletionTotals(1), value.totals())
    }

    @Test fun allCalendarBucketsCoverExactlyTheMonthForEveryWeekStart() {
        for (year in 2020..2030) for (month in 1..12) for (start in DayOfWeek.entries) {
            val ym = YearMonth.of(year, month)
            val buckets = StatsAggregator.monthBuckets(emptyList(), ym, start)
            assertTrue(buckets.size in 4..6)
            assertEquals(ym.atDay(1), buckets.first().range.first)
            assertEquals(ym.atEndOfMonth(), buckets.last().range.last)
            assertTrue(buckets.zipWithNext().all { (a, b) -> a.range.last.plusDays(1) == b.range.first })
            assertEquals(ym.lengthOfMonth().toLong(), buckets.sumOf { it.range.last.toEpochDay() - it.range.first.toEpochDay() + 1 })
        }
        assertEquals(4, StatsAggregator.monthBuckets(emptyList(), YearMonth.of(2021, 2), DayOfWeek.MONDAY).size)
        assertEquals(6, StatsAggregator.monthBuckets(emptyList(), YearMonth.of(2021, 5), DayOfWeek.MONDAY).size)
    }

    @Test fun clockRollbackHidesLaterFactsFromCurrentStatsWithoutRemovingHistory() {
        val h = history(successes = listOf(0, 1, 2))
        val rolledBack = StatsAggregator.evaluate(h, monday.plusDays(1))
        assertEquals(CompletionTotals(2), rolledBack.totals())
        assertEquals(2, rolledBack.metrics.bestStreak)
        assertEquals(3, rolledBack.history.logs.size)
        assertEquals(CompletionTotals(3), StatsAggregator.evaluate(rolledBack.history, monday.plusDays(2)).totals())
    }

    @Test fun earlierThanCreationHasNoExpectationOrCompletionControl() {
        val value = StatsAggregator.evaluate(history(successes = listOf(0)), monday.minusDays(1))
        assertEquals(CompletionTotals(), value.totals())
        assertNull(value.settingsToday)
        assertFalse(value.dueToday)
        assertFalse(value.canLogToday)
    }

    @Test fun dailyBucketsExcludeUnelapsedFutureDaysAndPreserveWeeklyTotal() {
        val values = listOf(StatsAggregator.evaluate(history(successes = listOf(0)), monday.plusDays(2)))
        val range = StatsAggregator.week(monday.plusDays(2), DayOfWeek.MONDAY)
        val buckets = StatsAggregator.dailyBuckets(values, range)
        assertEquals(7, buckets.size)
        assertEquals(CompletionTotals(1, 1, 1), buckets.fold(CompletionTotals()) { a, b -> a + b.totals })
        assertTrue(buckets.drop(3).all { it.totals.eligible == 0L })
    }
}
