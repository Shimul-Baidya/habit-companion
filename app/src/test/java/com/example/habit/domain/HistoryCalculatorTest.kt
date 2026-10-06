package com.example.habit.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class HistoryCalculatorTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private fun history(schedule: HabitSchedule = HabitSchedule.Daily, days: List<Int> = emptyList(),
        created: LocalDate = monday, archived: LocalDate? = null) = HabitHistory(created,
        listOf(EffectiveSettings(created, HabitSettings(schedule))),
        days.map { HabitLog(created.plusDays(it.toLong()), CompletionValue.Binary(true)) }, archived)
    private fun metrics(history: HabitHistory, day: Int) = HistoryCalculator.calculate(history, monday.plusDays(day.toLong()))

    @Test fun newHabitIsNeutralAndTodayIsPending() {
        val result = metrics(history(), 0)
        assertEquals(HabitAttention.NEUTRAL, result.attention)
        assertEquals(1, result.pending)
        assertEquals(0, result.missed)
        assertEquals(0.0, result.consistency!!, 0.0)
        assertEquals(0, result.currentStreak)
        assertEquals(HistoryMetrics(0, 0, HabitAttention.NEUTRAL, 0, 0, 0), metrics(history(), -1))
        assertNull(metrics(history(), -1).consistency)
    }

    @Test fun todayPendingDoesNotBreakCurrentOrBestStreak() {
        val result = metrics(history(days = listOf(0, 1, 2)), 3)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
        assertEquals(0, result.missed)
    }

    @Test fun restDaysNeitherBreakStreakNorCountAsMisses() {
        val h = history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), listOf(0, 2))
        val result = metrics(h, 6)
        assertEquals(2, result.currentStreak)
        assertEquals(0, result.missed)
        assertEquals(0, result.pending)
    }

    @Test fun riskCanCoexistWithPositiveStreakAndClearsAfterTwoSuccesses() {
        val h = history(days = listOf(2, 3))
        assertEquals(HabitAttention.AT_RISK, metrics(h.copy(logs = h.logs.take(1)), 2).attention)
        assertEquals(1, metrics(h.copy(logs = h.logs.take(1)), 2).currentStreak)
        assertEquals(HabitAttention.HEALTHY, metrics(h, 3).attention)
        assertEquals(2, metrics(h, 3).currentStreak)
        assertEquals(HabitAttention.AT_RISK, metrics(h, 5).attention)
    }

    @Test fun staleMissesDoNotRetriggerRiskOnAnotherSuccess() {
        val h = history(days = listOf(2, 3, 4))
        assertEquals(HabitAttention.HEALTHY, metrics(h, 4).attention)
    }

    @Test fun onlyLastSevenSettledOccurrencesTriggerRisk() {
        val h = history(days = (1..7).toList())
        // Miss day 0 is outside the window when day 8 closes.
        assertEquals(HabitAttention.HEALTHY, metrics(h, 9).attention)
        assertEquals(HabitAttention.AT_RISK, metrics(h, 10).attention)
    }

    @Test fun riskRecoveryIgnoresRestDays() {
        val h = history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), listOf(14, 21))
        assertEquals(HabitAttention.AT_RISK, metrics(h.copy(logs = h.logs.take(1)), 20).attention)
        assertEquals(HabitAttention.HEALTHY, metrics(h, 27).attention)
    }

    @Test fun historyCorrectionReplaysStreakBestAndRiskTogether() {
        val h = history(days = listOf(2))
        assertEquals(HabitAttention.AT_RISK, metrics(h, 2).attention)
        val corrected = CompletionRules.correct(h, HabitLog(monday.plusDays(1), CompletionValue.Binary(true)), monday.plusDays(2))
        assertEquals(HabitAttention.HEALTHY, metrics(corrected, 2).attention)
        assertEquals(2, metrics(corrected, 2).bestStreak)
        assertEquals(1, metrics(corrected, 2).missed)
    }

    @Test fun allTimeStreaksExceedFourHundredDays() {
        val h = history(days = (0..999).toList())
        val result = metrics(h, 1000)
        assertEquals(1000, result.currentStreak)
        assertEquals(1000, result.bestStreak)
        assertEquals(1000, metrics(h, 1002).bestStreak)
        assertEquals(0, metrics(h, 1002).currentStreak)
    }

    @Test fun oldBestStreakIsRetainedAfterLongGap() {
        val result = metrics(history(days = (0..599).toList() + listOf(1200)), 1200)
        assertEquals(600, result.bestStreak)
        assertEquals(1, result.currentStreak)
        assertEquals(HabitAttention.AT_RISK, result.attention)
    }

    @Test fun weeklyNonconsecutiveAchievementsFillQuota() {
        val result = metrics(history(HabitSchedule.Weekly(3), listOf(1, 3, 5)), 7)
        assertEquals(3, result.completed)
        assertEquals(0, result.missed)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.pending) // new week
    }

    @Test fun weeklyShortfallWaitsUntilWeekCloses() {
        val h = history(HabitSchedule.Weekly(3), listOf(1, 3))
        assertEquals(0, metrics(h, 6).missed)
        assertEquals(1, metrics(h, 6).pending)
        val result = metrics(h, 7)
        assertEquals(1, result.missed)
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.bestStreak)
        assertEquals(2.0 / 6.0, result.consistency!!, 0.000001) // closed quota plus open new week
        assertEquals(2.0 / 3.0, metrics(h, 6).consistency!!, 0.000001)
        val settled = HistoryCalculator.occurrences(h, monday.plusDays(7)).filter { it.outcome != OccurrenceOutcome.PENDING }
        assertEquals(listOf(OccurrenceOutcome.COMPLETED, OccurrenceOutcome.COMPLETED, OccurrenceOutcome.MISSED), settled.map { it.outcome })
        assertEquals(monday.plusDays(6), settled.last().date)
    }

    @Test fun weeklyShortfallsEnterRiskAndTwoNextWeekSuccessesRecoverBeforeClose() {
        val h = history(HabitSchedule.Weekly(3), listOf(7, 9))
        assertEquals(HabitAttention.AT_RISK, metrics(h.copy(logs = h.logs.take(1)), 7).attention)
        assertEquals(1, metrics(h.copy(logs = h.logs.take(1)), 7).currentStreak)
        assertEquals(HabitAttention.HEALTHY, metrics(h, 9).attention)
        assertEquals(2, metrics(h, 9).currentStreak)
        assertEquals(1, metrics(h, 9).pending)
        assertEquals(HabitAttention.AT_RISK, metrics(h, 14).attention)
    }

    @Test fun correctingWeeklySuccessAllowsRetainedExtraLogToFillTheQuota() {
        val h = history(HabitSchedule.Weekly(2), listOf(0, 1, 2))
        val corrected = CompletionRules.correct(h, HabitLog(monday, CompletionValue.Binary(false)), monday.plusDays(6))
        val outcomes = HistoryCalculator.occurrences(corrected, monday.plusDays(6))
        assertEquals(listOf(monday.plusDays(1), monday.plusDays(2)), outcomes.map { it.date })
        assertEquals(2, metrics(corrected, 6).completed)
        assertEquals(0, metrics(corrected, 6).missed)
        assertEquals(3, corrected.logs.size)
    }

    @Test fun extraWeeklyLogsAreRetainedButNeverInflateMetrics() {
        val h = history(HabitSchedule.Weekly(3), (0..6).toList())
        assertEquals(7, h.logs.size)
        assertEquals(3, metrics(h, 7).completed)
        assertEquals(3, metrics(h, 7).bestStreak)
        assertEquals(1.0, metrics(h, 6).consistency!!, 0.0)
    }

    @Test fun creationPartialWeekRoundsQuotaUp() {
        // Friday creation: ceil(3 * 3/7) = 2.
        val h = history(HabitSchedule.Weekly(3), listOf(0), monday.plusDays(4))
        val result = metrics(h, 7)
        assertEquals(1, result.completed)
        assertEquals(1, result.missed)
        val sunday = history(HabitSchedule.Weekly(7), created = monday.plusDays(6))
        assertEquals(1, metrics(sunday, 7).missed)
    }

    @Test fun weeklyArchiveCancelsPendingSlotsButPreservesClosedMissesAndSuccesses() {
        val h = history(HabitSchedule.Weekly(3), listOf(1, 8), archived = monday.plusDays(10))
        val result = metrics(h, 20)
        assertEquals(2, result.completed)
        assertEquals(2, result.missed) // only first week's shortfall
        assertEquals(0, result.pending)
        assertEquals(1, result.currentStreak)
        assertEquals(HabitAttention.AT_RISK, result.attention)
    }

    @Test fun archivingAtMondayPreservesPreviousSundayShortfall() {
        assertEquals(3, metrics(history(HabitSchedule.Weekly(3), archived = monday.plusDays(7)), 10).missed)
        assertEquals(0, metrics(history(HabitSchedule.Weekly(3), archived = monday.plusDays(6)), 10).missed)
    }

    @Test fun dailyArchiveStopsExpectationsWithoutRemovingHistory() {
        val h = history(days = listOf(0, 1), archived = monday.plusDays(3))
        val result = metrics(h, 20)
        assertEquals(2, result.completed)
        assertEquals(1, result.missed)
        assertEquals(0, result.pending)
        assertEquals(2, result.bestStreak)
    }

    @Test fun historicalScheduleChangesPreserveEarlierRestDaysAndMisses() {
        val h = HabitHistory(monday, listOf(
            EffectiveSettings(monday, HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)))),
            EffectiveSettings(monday.plusDays(3), HabitSettings(HabitSchedule.Daily))),
            listOf(HabitLog(monday, CompletionValue.Binary(true)), HabitLog(monday.plusDays(3), CompletionValue.Binary(true))))
        val result = metrics(h, 5)
        assertEquals(2, result.completed)
        assertEquals(1, result.missed)
        assertEquals(1, result.pending)
        assertEquals(2, result.bestStreak)
    }

    @Test fun weeklyQuotaChangesPreservePreviousPeriods() {
        val h = HabitHistory(monday, listOf(
            EffectiveSettings(monday, HabitSettings(HabitSchedule.Weekly(3))),
            EffectiveSettings(monday.plusDays(7), HabitSettings(HabitSchedule.Weekly(1)))),
            listOf(1, 3, 8).map { HabitLog(monday.plusDays(it.toLong()), CompletionValue.Binary(true)) })
        val result = metrics(h, 14)
        assertEquals(3, result.completed)
        assertEquals(1, result.missed)
        assertEquals(1, result.pending)
    }

    @Test fun quantityPartialLogsAndTargetChangesUseDatedThresholds() {
        val h = HabitHistory(monday, listOf(
            EffectiveSettings(monday, HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("10"), "minutes"))),
            EffectiveSettings(monday.plusDays(2), HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("20"), "minutes")))),
            listOf(0, 1, 2, 3).map { HabitLog(monday.plusDays(it.toLong()), CompletionValue.Quantity(BigDecimal("10"), "minutes")) })
        val result = metrics(h, 3)
        assertEquals(2, result.completed)
        assertEquals(1, result.missed)
        assertEquals(1, result.pending)
        assertEquals(2, result.bestStreak)
    }

    @Test fun weeklyQuantityAndUnitChangeEvaluateEachDateSeparately() {
        val h = HabitHistory(monday, listOf(
            EffectiveSettings(monday, HabitSettings(HabitSchedule.Weekly(2), TrackingMode.Quantity(BigDecimal("30"), "minutes"))),
            EffectiveSettings(monday.plusDays(2), HabitSettings(HabitSchedule.Weekly(2), TrackingMode.Quantity(BigDecimal("1"), "hours")))),
            listOf(HabitLog(monday, CompletionValue.Quantity(BigDecimal("30"), "minutes")),
                HabitLog(monday.plusDays(2), CompletionValue.Quantity(BigDecimal("0.5"), "hours"))))
        assertEquals(1, metrics(h, 7).completed)
        assertEquals(1, metrics(h, 7).missed)
    }

    @Test fun invalidLogsAreNeverCountedAsCompletion() {
        assertThrows(IllegalArgumentException::class.java) { metrics(history(days = listOf(1)), 0) }
        assertThrows(IllegalArgumentException::class.java) { history(days = listOf(-1)) }
        assertThrows(IllegalArgumentException::class.java) { history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), listOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { history(days = listOf(2), archived = monday.plusDays(2)) }
    }

    @Test fun binaryFalseIsPendingTodayAndMissedAfterClose() {
        val h = history().copy(logs = listOf(HabitLog(monday, CompletionValue.Binary(false))))
        assertEquals(1, metrics(h, 0).pending)
        assertEquals(1, metrics(h, 1).missed)
    }

    @Test fun settingsChangesHaveSafeEffectiveDatesAndKeepUnrelatedFields() {
        val daily = HabitSettings(HabitSchedule.Daily)
        val weekly = HabitSettings(HabitSchedule.Weekly(3))
        val schedule = HabitSettingChange.Schedule(HabitSchedule.Weekly(2))
        assertEquals(monday.plusDays(7), schedule.effectiveOn(daily, monday))
        assertEquals(monday.plusDays(7), schedule.effectiveOn(weekly, monday.plusDays(6)))
        val custom = HabitSettingChange.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.FRIDAY)))
        assertEquals(monday.plusDays(2), custom.effectiveOn(daily, monday.plusDays(1)))
        assertEquals(monday.plusDays(7), custom.effectiveOn(weekly, monday.plusDays(1)))
        val tracking = HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("2"), "pages"))
        assertEquals(monday.plusDays(2), tracking.effectiveOn(weekly, monday.plusDays(1)))
        assertEquals(weekly.schedule, tracking.applyTo(weekly).schedule)
        assertEquals(daily.tracking, schedule.applyTo(daily).tracking)
        assertThrows(IllegalArgumentException::class.java) {
            HabitHistory(monday, listOf(EffectiveSettings(monday, weekly), EffectiveSettings(monday.plusDays(1), daily)))
        }
    }
}
