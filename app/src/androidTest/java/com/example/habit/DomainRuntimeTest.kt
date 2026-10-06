package com.example.habit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habit.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Checks the Android runtime/desugaring boundary, independently of UI and Room. */
@RunWith(AndroidJUnit4::class)
class DomainRuntimeTest {
    @Test fun datedDecimalQuantityQuotaRunsOnAndroid() {
        val friday = LocalDate.of(2026, 10, 2)
        val today = HabitDateSource(Clock.fixed(Instant.parse("2026-10-04T18:00:00Z"), ZoneId.of("Asia/Dhaka"))).today()
        val history = HabitHistory(friday,
            listOf(EffectiveSettings(friday, HabitSettings(HabitSchedule.Weekly(3),
                TrackingMode.Quantity(BigDecimal("0.3"), "km")))),
            listOf(HabitLog(friday, CompletionValue.Quantity(BigDecimal("0.1") + BigDecimal("0.2"), "km")),
                HabitLog(friday.plusDays(2), CompletionValue.Quantity(BigDecimal("0.30"), "km"))))
        val result = HistoryCalculator.calculate(history, today)
        assertEquals(LocalDate.of(2026, 10, 5), today)
        assertEquals(2, result.completed)
        assertEquals(0, result.missed)
        assertEquals(2, result.currentStreak)
        assertEquals(3, result.pending)
    }
}
