package com.example.habit.domain

import com.example.habit.data.local.HabitEntity

/** One habit as SCR-04 needs it: the row, plus everything the row has to render. */
data class HabitStatus(
    val habit: HabitEntity,
    val scheduledToday: Boolean,
    val doneToday: Boolean,
    val currentStreak: Int,
    val atRisk: Boolean,
)
