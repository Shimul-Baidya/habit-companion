package com.example.habit.reminders

import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository.Configuration
import com.example.habit.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.*

class ReminderPlannerTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val date = monday.plusDays(1)
    private val zone = ZoneOffset.UTC
    private val now = date.atTime(12, 0).toInstant(zone)
    private val config = Configuration(reminderEnabled = true, reminderMinute = 1200)
    private fun record(schedule: HabitSchedule = HabitSchedule.Daily, mode: TrackingMode = TrackingMode.Binary,
        logs: List<CompletionEntity> = emptyList()) = HabitRecord(HabitEntity(id = 9, name = "Private name", createdEpochDay = monday.toEpochDay()),
        listOf(ScheduleHistoryEntity.from(9, monday, schedule)), listOf(TrackingHistoryEntity.from(9, monday, mode)), logs)
    @Test fun inheritedOverrideOffAndGlobalMaster() {
        val r = record()
        assertEquals(1200, ReminderPlanner.minute(r, config))
        assertEquals(900, ReminderPlanner.minute(r.copy(habit = r.habit.copy(reminderEnabled = true, reminderMinute = 900)), config))
        assertNull(ReminderPlanner.minute(r.copy(habit = r.habit.copy(reminderEnabled = false)), config))
        assertNull(ReminderPlanner.minute(r, config.copy(reminderEnabled = false)))
        assertNull(ReminderPlanner.minute(r.copy(habit = r.habit.copy(archivedAt = 1)), config))
    }
    @Test fun binaryAndHistoricalQuantityThresholdRemainDistinct() {
        assertTrue(ReminderPlanner.due(record(), date))
        assertFalse(ReminderPlanner.due(record(logs = listOf(CompletionEntity(9, date.toEpochDay()))), date))
        val r = record(mode = TrackingMode.Quantity(BigDecimal("5"), "pages"), logs = listOf(CompletionEntity(9, date.toEpochDay(), trackingMode = "QUANTITY", quantityAmount = "2", quantityUnit = "pages")))
        assertTrue(ReminderPlanner.due(r, date))
        assertFalse(ReminderPlanner.due(r.copy(completions = r.completions.map { it.copy(quantityAmount = "5") }), date))
        val changed = r.copy(tracking = r.tracking + TrackingHistoryEntity.from(9, date.plusDays(1), TrackingMode.Quantity(BigDecimal.ONE, "pages")))
        assertTrue(ReminderPlanner.due(changed, date))
        assertFalse(ReminderPlanner.due(r, monday.minusDays(1)))
    }
    @Test fun customRestAndPendingWeeklyQuota() {
        assertFalse(ReminderPlanner.due(record(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), date))
        val r = record(HabitSchedule.Weekly(2), logs = listOf(CompletionEntity(9, monday.toEpochDay())))
        assertTrue(ReminderPlanner.due(r, date))
        val met = r.copy(completions = r.completions + CompletionEntity(9, date.toEpochDay()))
        assertFalse(ReminderPlanner.due(met, date.plusDays(1)))
        assertTrue(ReminderPlanner.due(met, monday.plusWeeks(1)))
    }
    @Test fun creationProrationAndScheduleChangesUseHistoricalExpectation() {
        val friday = monday.plusDays(4)
        val r = record(HabitSchedule.Weekly(1), logs = listOf(CompletionEntity(9, friday.toEpochDay())))
            .let { it.copy(habit = it.habit.copy(createdEpochDay = friday.toEpochDay()), schedules = listOf(ScheduleHistoryEntity.from(9, friday, HabitSchedule.Weekly(1))), tracking = listOf(TrackingHistoryEntity.from(9, friday, TrackingMode.Binary))) }
        assertFalse(ReminderPlanner.due(r, friday.plusDays(1)))
        val changed = record().let { it.copy(schedules = it.schedules + ScheduleHistoryEntity.from(9, date.plusDays(1), HabitSchedule.Custom(setOf(DayOfWeek.SUNDAY)))) }
        assertTrue(ReminderPlanner.due(changed, date))
        assertFalse(ReminderPlanner.due(changed, date.plusDays(1)))
    }
    @Test fun nearestSlotSkipsDeliveredDeletedAndRestDaysAndCatchesUpToday() {
        val r = record()
        assertEquals(date.atTime(20, 0).toInstant(zone), ReminderPlanner.next(listOf(r), config, now, zone, emptySet())!!.at)
        assertEquals(date.plusDays(1), ReminderPlanner.next(listOf(r), config, now, zone, setOf(ReminderPlanner.key(9, date)))!!.date)
        assertNull(ReminderPlanner.next(emptyList(), config, now, zone, emptySet()))
        val after = date.atTime(21, 0).toInstant(zone)
        assertEquals(date, ReminderPlanner.next(listOf(r), config, after, zone, emptySet())!!.date)
        assertEquals(DayOfWeek.MONDAY, ReminderPlanner.next(listOf(record(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)))), config, now, zone, emptySet())!!.date.dayOfWeek)
    }
    @Test fun localWallTimeFollowsZoneAndDaylightSavingGap() {
        val berlin = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 3, 29)
        val r = record().let { it.copy(habit = it.habit.copy(createdEpochDay = day.toEpochDay()), schedules = listOf(ScheduleHistoryEntity.from(9, day, HabitSchedule.Daily)), tracking = listOf(TrackingHistoryEntity.from(9, day, TrackingMode.Binary))) }
        val slot = ReminderPlanner.next(listOf(r), config.copy(reminderMinute = 150), day.atStartOfDay(berlin).toInstant(), berlin, emptySet())!!
        assertEquals(day.atTime(3, 30).atZone(berlin).toInstant(), slot.at)
    }
}
