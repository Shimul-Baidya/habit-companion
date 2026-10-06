package com.example.habit.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HabitHistoryTest {
    private val start = LocalDate.of(2026, 10, 5)
    private fun quantity(target: String, unit: String = "minutes") =
        TrackingMode.Quantity(BigDecimal(target), unit)

    @Test fun decimalTargetsAreComparedWithoutFloatingPointRounding() {
        val mode = quantity("0.3")
        assertTrue(CompletionRules.isComplete(mode, CompletionValue.Quantity(BigDecimal("0.1") + BigDecimal("0.2"), "minutes")))
        assertFalse(CompletionRules.isComplete(mode, CompletionValue.Quantity(BigDecimal("0.299"), "minutes")))
        assertTrue(CompletionRules.isComplete(mode, CompletionValue.Quantity(BigDecimal("0.30"), "minutes")))
    }

    @Test fun historicalCorrectionsUseHistoricalTargetsAndModes() {
        val history = HabitHistory(start, listOf(
            EffectiveSettings(start, HabitSettings(HabitSchedule.Daily, quantity("10"))),
            EffectiveSettings(start.plusDays(2), HabitSettings(HabitSchedule.Daily, quantity("20"))),
            EffectiveSettings(start.plusDays(3), HabitSettings(HabitSchedule.Daily)),
        ))
        val old = CompletionRules.correct(history, HabitLog(start, CompletionValue.Quantity(BigDecimal("10"), "minutes")), start.plusDays(4))
        assertTrue(CompletionRules.isComplete(old.settingsOn(start)!!.tracking, old.logs.single().value))
        assertFalse(CompletionRules.isComplete(old.settingsOn(start.plusDays(2))!!.tracking, old.logs.single().value))
        assertEquals(TrackingMode.Binary, old.settingsOn(start.plusDays(3))!!.tracking)
    }

    @Test fun correctionRejectsFuturePrecreationRestAndArchiveDates() {
        val history = HabitHistory(start, listOf(EffectiveSettings(start,
            HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))))),
            archivedOn = start.plusDays(4))
        assertTrue(CompletionRules.canCorrect(history, start.plusDays(2), start.plusDays(8)))
        assertFalse(CompletionRules.canCorrect(history, start.minusDays(7), start.plusDays(8)))
        assertFalse(CompletionRules.canCorrect(history, start.plusDays(1), start.plusDays(8)))
        assertFalse(CompletionRules.canCorrect(history, start.plusDays(7), start.plusDays(8)))
        assertFalse(CompletionRules.canCorrect(history, start.plusDays(2), start))
    }

    @Test fun correctionReplacesOnlyOneDateAndRetainsOtherRecords() {
        val history = HabitHistory(start, listOf(EffectiveSettings(start, HabitSettings(HabitSchedule.Daily))),
            listOf(HabitLog(start, CompletionValue.Binary(true)), HabitLog(start.plusDays(1), CompletionValue.Binary(true))))
        val corrected = CompletionRules.correct(history, HabitLog(start, CompletionValue.Binary(false)), start.plusDays(1))
        assertEquals(2, corrected.logs.size)
        assertEquals(CompletionValue.Binary(false), corrected.logs.first().value)
        assertEquals(history.logs.last(), corrected.logs.last())
        assertEquals(CompletionValue.Binary(true), history.logs.first().value)
    }

    @Test fun invalidTargetsModesAndUnitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { quantity("0") }
        assertThrows(IllegalArgumentException::class.java) { quantity("1", " ") }
        assertThrows(IllegalArgumentException::class.java) { HabitSchedule.Weekly(8) }
        assertThrows(IllegalArgumentException::class.java) { HabitSchedule.Custom(emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { CompletionValue.Quantity(BigDecimal("-1"), "minutes") }
        assertThrows(IllegalArgumentException::class.java) { CompletionValue.Quantity(BigDecimal.ONE, " ") }
        assertThrows(IllegalArgumentException::class.java) { CompletionRules.isComplete(quantity("1"), CompletionValue.Binary(true)) }
        assertThrows(IllegalArgumentException::class.java) { CompletionRules.isComplete(quantity("1"), CompletionValue.Quantity(BigDecimal.ONE, "hours")) }
    }

    @Test fun invalidHistoryOrderAndDuplicateDatesAreRejected() {
        val settings = EffectiveSettings(start, HabitSettings(HabitSchedule.Daily))
        assertThrows(IllegalArgumentException::class.java) { HabitHistory(start, listOf(settings, settings)) }
        assertThrows(IllegalArgumentException::class.java) { HabitHistory(start, listOf(settings.copy(from = start.plusDays(1)))) }
        val log = HabitLog(start, CompletionValue.Binary(true))
        assertThrows(IllegalArgumentException::class.java) { HabitHistory(start, listOf(settings), listOf(log, log)) }
    }

    @Test fun injectedClockResolvesMidnightInItsZone() {
        val instant = Instant.parse("2026-10-05T18:00:00Z")
        assertEquals(start.plusDays(1), HabitDateSource(Clock.fixed(instant, ZoneId.of("Asia/Dhaka"))).today())
        assertEquals(start, HabitDateSource(Clock.fixed(instant.minusSeconds(1), ZoneId.of("Asia/Dhaka"))).today())
        assertEquals(start, HabitDateSource(Clock.fixed(instant, ZoneId.of("UTC"))).today())
    }
}
