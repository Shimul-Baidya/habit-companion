package com.example.habit.domain

import com.example.habit.data.local.HabitEntity

/** One habit as SCR-04 needs it: the row, plus everything the row has to render. */
data class HabitStatus(
    val habit: HabitEntity,
    val scheduledToday: Boolean,
    val doneToday: Boolean,
    val currentStreak: Int,
    val atRisk: Boolean,
    val bestStreak: Int = currentStreak,
    val attention: HabitAttention = if (atRisk) HabitAttention.AT_RISK else HabitAttention.HEALTHY,
    val settings: HabitSettings = HabitSettings(HabitSchedule.Daily),
    val progressToday: Float = if (doneToday) 1f else 0f,
    val valueToday: CompletionValue? = null,
    val canLogToday: Boolean = scheduledToday,
    /** Daily/Custom streaks count scheduled days; Weekly/mixed histories count occurrences. */
    val usesOccurrenceStreak: Boolean = false,
)
