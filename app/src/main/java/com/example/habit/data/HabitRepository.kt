package com.example.habit.data

import com.example.habit.data.local.CompletionDao
import com.example.habit.data.local.CompletionEntity
import com.example.habit.data.local.HabitDao
import com.example.habit.data.local.HabitEntity
import com.example.habit.domain.HabitStatus
import com.example.habit.domain.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Joins the two tables Home needs. The DAOs stay dumb; the streak maths stays pure in
 * [StreakCalculator]; this is the only place the two are put together.
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val completionDao: CompletionDao,
    private val history: HabitHistoryRepository,
) {

    /**
     * Every habit with its status for [today]. The completion window is wide enough to
     * settle the longest streak the UI will show without loading the whole table.
     */
    fun observeStatuses(today: LocalDate): Flow<List<HabitStatus>> {
        val from = today.minusDays(STATUS_WINDOW_DAYS).toEpochDay()
        return combine(
            habitDao.observeAll(),
            completionDao.observeRange(from, today.toEpochDay()),
        ) { habits, completions ->
            val byHabit = completions.groupBy(CompletionEntity::habitId)
            habits.map { habit ->
                val done = byHabit[habit.id].orEmpty()
                    .map { LocalDate.ofEpochDay(it.epochDay) }
                    .toSet()
                HabitStatus(
                    habit = habit,
                    scheduledToday = StreakCalculator.isScheduled(habit, today),
                    doneToday = today in done,
                    currentStreak = StreakCalculator.currentStreak(habit, done, today),
                    atRisk = StreakCalculator.isAtRisk(habit, done, today),
                )
            }
        }
    }

    /** SCR-04 element 8: one tap toggles today. */
    suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean) {
        // Until the new Home state is wired in chunk 03, ignore legacy rest-day controls.
        if (!StreakCalculator.isScheduled(habit, today) || habit.archivedAt != null) return
        history.correct(habit.id, today, com.example.habit.domain.CompletionValue.Binary(done))
    }

    suspend fun create(habit: HabitEntity): Long = history.createLegacy(habit)

    private companion object {
        /** Longer than any streak Home renders, short of loading the entire history. */
        const val STATUS_WINDOW_DAYS = 400L
    }
}
