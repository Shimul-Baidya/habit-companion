package com.example.habit.domain

import com.example.habit.data.local.HabitEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every streak number in the app comes from here, and none of it comes from the model —
 * SCR-08's pattern callout is a sentence built from these values, not generated text.
 *
 * Pure functions over a set of completed dates, so all of it is unit-testable on the JVM.
 */
object StreakCalculator {

    /** Nothing looks further back than this; it bounds the walk on a corrupt schedule. */
    private const val MAX_LOOKBACK_DAYS = 400

    /** SCR-08 entry rule: at risk at two or more misses in the last seven scheduled days. */
    const val AT_RISK_MISSES = 2
    const val AT_RISK_WINDOW = 7

    /** Bit 0 is Monday, matching [HabitEntity.scheduledDays]. */
    fun isScheduled(habit: HabitEntity, date: LocalDate): Boolean {
        val bit = date.dayOfWeek.value - 1
        return habit.scheduledDays and (1 shl bit) != 0
    }

    /**
     * Scheduled days completed in an unbroken run ending now.
     *
     * A scheduled today that has not been ticked yet does not break the streak — the day
     * is not over. That is the difference between "you are at 6" and "you lost your 6"
     * every morning before the user has had a chance to act.
     */
    fun currentStreak(habit: HabitEntity, completed: Set<LocalDate>, today: LocalDate): Int {
        var streak = 0
        var date = today
        repeat(MAX_LOOKBACK_DAYS) {
            if (isScheduled(habit, date)) {
                when {
                    date in completed -> streak++
                    date == today -> Unit // still open
                    else -> return streak
                }
            }
            date = date.minusDays(1)
        }
        return streak
    }

    /**
     * The longest run ever achieved. SCR-08 keeps showing this through a break: best is
     * preserved, never reset, because it is the evidence the habit is achievable.
     */
    fun bestStreak(habit: HabitEntity, completed: Set<LocalDate>, today: LocalDate): Int {
        val earliest = completed.minOrNull() ?: return 0
        var best = 0
        var run = 0
        var date = earliest
        while (!date.isAfter(today)) {
            if (isScheduled(habit, date)) {
                if (date in completed) {
                    run++
                    if (run > best) best = run
                } else {
                    run = 0
                }
            }
            date = date.plusDays(1)
        }
        return best
    }

    /**
     * Drives which of SCR-07 and SCR-08 renders.
     *
     * The window stops short of today: an untouched today is not yet a miss, for the same
     * reason it does not break a streak.
     */
    fun isAtRisk(habit: HabitEntity, completed: Set<LocalDate>, today: LocalDate): Boolean =
        recentScheduledDays(habit, today).count { it !in completed } >= AT_RISK_MISSES

    /**
     * The [AT_RISK_WINDOW] most recent scheduled days strictly before [today], oldest
     * first. Returns fewer than that for a habit too new to have them.
     */
    fun recentScheduledDays(
        habit: HabitEntity,
        today: LocalDate,
        count: Int = AT_RISK_WINDOW,
    ): List<LocalDate> {
        val created = habit.createdAtDate()
        val days = ArrayList<LocalDate>(count)
        var date = today.minusDays(1)
        repeat(MAX_LOOKBACK_DAYS) {
            if (days.size == count || date.isBefore(created)) return days.asReversed()
            if (isScheduled(habit, date)) days += date
            date = date.minusDays(1)
        }
        return days.asReversed()
    }

    /** Created-at is a wall-clock instant, so it resolves in the device's own zone. */
    private fun HabitEntity.createdAtDate(): LocalDate =
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
}
