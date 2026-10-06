package com.example.habit.ui.screens.newhabit

import com.example.habit.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek

class HabitFormDraftTest {
    @Test fun binaryDefaultIsRecommendedAndNeverTreatsTargetAsCompletionCount() {
        val draft = HabitFormDraft(name = "  Read  ", target = "10", unit = "pages").toHabitDraft()
        assertEquals("Read", draft.name)
        assertEquals(TrackingMode.Binary, draft.settings.tracking)
        assertEquals(HabitSchedule.Daily, draft.settings.schedule)
        assertFalse(HabitFormDraft().validate().valid)
    }
    @Test fun weeklyQuotaAndCustomDaysAreDistinctAndValidated() {
        val base = HabitFormDraft(name = "Walk")
        assertEquals(HabitSchedule.Weekly(3), base.copy(frequency = "WEEKLY").toHabitDraft().settings.schedule)
        assertEquals(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), base.copy(frequency = "CUSTOM", weekdayMask = 5).toHabitDraft().settings.schedule)
        for (quota in listOf("", "0", "8", "1.5", "-1", "many")) assertFalse(base.copy(frequency = "WEEKLY", quota = quota).validate().valid)
        for (mask in listOf(0, -1, 128)) assertFalse(base.copy(frequency = "CUSTOM", weekdayMask = mask).validate().valid)
    }
    @Test fun decimalQuantityIsPositiveAndUnitIsRequiredAndTrimmed() {
        val base = HabitFormDraft(name = "Read", tracking = "QUANTITY", target = "0,5", unit = " pages ")
        assertEquals(TrackingMode.Quantity(BigDecimal("0.5"), "pages"), base.toHabitDraft().settings.tracking)
        assertEquals(BigDecimal("0.5"), HabitFormDraft.decimal(".5"))
        for (target in listOf("", "0", "-1", "NaN", "Infinity", "1e3", "1..5")) assertFalse(base.copy(target = target).validate().valid)
        assertFalse(base.copy(unit = " ").validate().valid)
        assertFalse(base.copy(unit = "a".repeat(41)).validate().valid)
        assertTrue(base.copy(unit = "a".repeat(40)).validate().valid)
    }
    @Test fun navigationResultRoundTripRejectsMalformedOrUnvalidatedValues() {
        val changes = listOf(DraftPlanningChange.Schedule(HabitSchedule.Daily), DraftPlanningChange.Schedule(HabitSchedule.Weekly(2)),
            DraftPlanningChange.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), DraftPlanningChange.Tracking(TrackingMode.Binary),
            DraftPlanningChange.Tracking(TrackingMode.Quantity(BigDecimal("5"), "pages")), DraftPlanningChange.CueAnchor("one page", "coffee"),
            DraftPlanningChange.PlanNote("start small"))
        changes.forEach { change ->
            val result = DraftPlanningResult("draft-token", change)
            assertEquals(result, PlanningDraftContract.decode(PlanningDraftContract.encode(result)))
        }
        for (invalid in listOf(emptyList(), listOf("x", "unknown"), listOf("x", "WEEKLY", "8"), listOf("x", "CUSTOM", "0"),
            listOf("x", "QUANTITY", "0", "pages"), listOf("x", "QUANTITY", "5", ""), listOf("x", "DAILY", "extra")))
            assertNull(PlanningDraftContract.decode(invalid))
    }

    @Test fun typedPlanningChangesPreserveOtherDraftFields() {
        val base = HabitFormDraft(name = "Read", iconKey = "book", colorKey = "purple")
        val changed = base.apply(DraftPlanningChange.Schedule(HabitSchedule.Weekly(2)))
            .apply(DraftPlanningChange.Tracking(TrackingMode.Quantity(BigDecimal("5"), "pages")))
            .apply(DraftPlanningChange.CueAnchor("read one page", "after coffee"))
        assertEquals("Read", changed.name)
        assertEquals("book", changed.iconKey)
        assertEquals(HabitSchedule.Weekly(2), changed.toHabitDraft().settings.schedule)
        assertEquals("after coffee", changed.toHabitDraft().anchor)
        assertEquals("read one page", changed.toHabitDraft().cue)
    }
}
