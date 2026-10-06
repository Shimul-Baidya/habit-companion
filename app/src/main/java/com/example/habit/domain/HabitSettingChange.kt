package com.example.habit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Typed proposals; persistence and Coach validation will use these without prose parsing. */
sealed interface HabitSettingChange {
    fun applyTo(settings: HabitSettings): HabitSettings

    data class Schedule(val value: HabitSchedule) : HabitSettingChange {
        override fun applyTo(settings: HabitSettings) = settings.copy(schedule = value)
    }
    data class Tracking(val value: TrackingMode) : HabitSettingChange {
        override fun applyTo(settings: HabitSettings) = settings.copy(tracking = value)
    }

    /** Today retains its meaning. Weekly schedule edits retain the entire open period. */
    fun effectiveOn(settings: HabitSettings, today: LocalDate): LocalDate =
        if (this is Schedule && (settings.schedule is HabitSchedule.Weekly || value is HabitSchedule.Weekly)) {
            today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        } else today.plusDays(1)
}
