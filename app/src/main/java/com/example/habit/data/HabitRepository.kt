package com.example.habit.data

import com.example.habit.data.local.HabitEntity
import com.example.habit.domain.CompletionValue
import com.example.habit.domain.HabitStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.time.DayOfWeek
import java.time.LocalDate

interface HabitDataSource {
    fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek): Flow<HabitSnapshot>
    suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean)
}

/** Atomic Room records feed one complete historical calculation path. Failures propagate. */
class HabitRepository(private val history: HabitHistoryRepository) : HabitDataSource {

    override fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek): Flow<HabitSnapshot> =
        history.records.map { HabitSnapshot.from(it, today, weekStart) }.flowOn(Dispatchers.Default)

    fun observeStatuses(today: LocalDate): Flow<List<HabitStatus>> =
        observeSnapshot(today, DayOfWeek.MONDAY).map { it.active }

    /** SCR-04 element 8: one tap toggles today. */
    override suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean) {
        history.logToday(habit.id, today, CompletionValue.Binary(done))
    }

    suspend fun create(habit: HabitEntity): Long = history.createLegacy(habit)

}
