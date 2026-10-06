package com.example.habit.ui.screens.detail

import com.example.habit.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.math.BigDecimal

class DetailFactsTest {
    private val start = LocalDate.of(2026, 9, 28)
    private val today = start.plusDays(8)
    private fun value(schedule: HabitSchedule = HabitSchedule.Daily, logs: List<HabitLog> = emptyList(),
        mode: TrackingMode = TrackingMode.Binary) = StatsAggregator.evaluate(HabitHistory(start, listOf(EffectiveSettings(start, HabitSettings(schedule, mode))), logs), today)
    @Test fun calendarDoesNotEnableFuturePreCreationOrRestDates() {
        val days = calendarDays(value(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), YearMonth.of(2026, 9))
        assertFalse(days.first().editable)
        assertTrue(days.single { it.date == start }.editable)
        assertEquals(DayMark.REST, days.last().mark)
        assertFalse(days.last().editable)
        assertFalse(calendarDays(value(), YearMonth.of(2026, 11)).any { it.editable })
    }
    @Test fun weeklyFlexibleDatesAreNotMissesAndShortfallsRemainInMetrics() {
        val evaluated = value(HabitSchedule.Weekly(3))
        assertEquals(3, evaluated.metrics.missed)
        val days = calendarDays(evaluated, YearMonth.of(2026, 10))
        assertEquals(DayMark.FLEXIBLE, days.single { it.date == today.minusDays(1) }.mark)
        assertFalse(days.any { it.mark == DayMark.MISSED })
    }
    @Test fun historicalQuantityTargetMarksPartialAndAchievedSeparately() {
        val mode = TrackingMode.Quantity(BigDecimal.TEN, "pages")
        val settings = listOf(EffectiveSettings(start, HabitSettings(HabitSchedule.Daily, mode)),
            EffectiveSettings(today, HabitSettings(HabitSchedule.Daily, mode.copy(target = BigDecimal.ONE))))
        val evaluated = StatsAggregator.evaluate(HabitHistory(start, settings, listOf(HabitLog(start, CompletionValue.Quantity(BigDecimal("2"), "pages")),
            HabitLog(today, CompletionValue.Quantity(BigDecimal("2"), "pages")))), today)
        assertEquals(DayMark.PARTIAL, calendarDays(evaluated, YearMonth.from(start)).last { it.date == start }.mark)
        assertEquals(DayMark.DONE, calendarDays(evaluated, YearMonth.from(today)).single { it.date == today }.mark)
    }
    @Test fun positiveThresholdAndRealRiskStreakAreIndependent() {
        val full = (0L..8L).map { HabitLog(start.plusDays(it), CompletionValue.Binary(true)) }
        assertTrue(positiveCallout(value(logs = full)))
        assertFalse(positiveCallout(value(logs = full.take(6))))
        val risk = value(logs = listOf(HabitLog(today.minusDays(1), CompletionValue.Binary(true))))
        assertEquals(1, risk.metrics.currentStreak)
        assertNotNull(patternReading(risk))
        assertTrue(patternReading(risk)!!.contains("One completed occurrence"))
        assertNull(patternReading(value(logs = full)))
    }
    @Test fun monthHasExactlyAllItsDatesIncludingLeapDay() {
        for (month in 1..12) assertEquals(YearMonth.of(2024, month).lengthOfMonth(), calendarDays(value(), YearMonth.of(2024, month)).size)
    }
}
