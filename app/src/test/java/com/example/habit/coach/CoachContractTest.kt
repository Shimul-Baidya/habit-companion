package com.example.habit.coach

import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** Small controlled fixtures exercise policy independently of Android's asset/parser boundary. */
class CoachContractTest {
    private val cards = listOf(
        card("card_01", listOf("planning", "cues", "scheduling")),
        card("card_02", listOf("cues", "routines")),
        card("card_08", listOf("friction", "starting")),
        card("card_11", listOf("simplicity", "starting", "two-minute-rule")),
        card("card_13", listOf("habit-shaping", "progression", "consistency")),
        card("card_17", listOf("tracking", "cues")),
        card("card_18", listOf("recovery", "consistency")),
        StrategyCard("card_email", "Email", "Prose", "Prose", "When quick emails pile up in your inbox", listOf("email", "processing"), "Supplied attribution"),
    )
    private fun card(id: String, tags: List<String>) = StrategyCard(id, id, "Principle", "Advice prose",
        "When starting a routine seems difficult", tags, "Source attribution")
    private val catalog = StrategyCatalog.validated(cards, byteArrayOf(1))
    private val today = LocalDate.of(2026, 10, 6)
    private fun history(schedule: HabitSchedule = HabitSchedule.Daily, mode: TrackingMode = TrackingMode.Binary,
        days: Int = 5, logs: List<HabitLog> = emptyList()) = HabitHistory(today.minusDays(days.toLong()),
        listOf(EffectiveSettings(today.minusDays(days.toLong()), HabitSettings(schedule, mode))), logs)
    private fun planning(draft: HabitFormDraft = HabitFormDraft(), question: String = "") =
        (CoachRequestBuilder.planning(catalog, draft, 2, question, true) as RequestResult.Ready).request
    private fun existing(h: HabitHistory = history()) =
        (CoachRequestBuilder.existing(catalog, h, today, enabled = true) as RequestResult.Ready).request
    private fun response(request: CoachRequest, fact: ReadingFact = ReadingFact.PLANNING_DRAFT) =
        CoachResponse(CoachReading(listOf(fact), PossibleBarrier.STARTING_SIZE), request.strategies.map {
            CoachSuggestion(it.card.id, "Try a small step", "If this fits, try a small starting step.", CoachAction.Plan("Start small"))
        }, if (request.contractVersion == 2) "If this fits, try a small starting step." else null)
    private fun validation(request: CoachRequest, action: CoachAction, id: String? = null): ResponseResult {
        val r = response(request, if (request.context is CoachContext.Planning) ReadingFact.PLANNING_DRAFT else ReadingFact.RECENT_MISSES)
        val index = id?.let { name -> r.suggestions.indexOfFirst { it.strategyId == name } } ?: 0
        require(index >= 0)
        return CoachResponseValidator.validate(r.copy(suggestions = r.suggestions.mapIndexed { i, s -> if (i == index) s.copy(action = action) else s }), request, catalog)
    }

    @Test fun catalogRejectsMissingDuplicateAndBlankFields() {
        listOf(emptyList(), cards + cards.first(), cards.mapIndexed { i, c -> if (i == 0) c.copy(source = "") else c },
            cards.mapIndexed { i, c -> if (i == 0) c.copy(tags = listOf("cues", "cues")) else c }).forEach {
            assertTrue(runCatching { StrategyCatalog.validated(it, byteArrayOf(1)) }.isFailure)
        }
    }
    @Test fun rankIsStableForEqualScoresAndReorderedCatalog() {
        val request = planning()
        val reordered = StrategyCatalog.validated(cards.reversed(), byteArrayOf(1))
        val other = CoachRequestBuilder.planning(reordered, HabitFormDraft(), 2, enabled = true) as RequestResult.Ready
        assertEquals(request.strategies, other.request.strategies)
        assertEquals(3, request.strategies.size)
        assertFalse(request.strategies.any { it.card.id == "card_email" })
    }
    @Test fun initialSuggestionsNeedContextButTypedMessagesDoNotNeedThreeCards() {
        assertEquals(RequestResult.Unavailable(CoachFailure.InsufficientContext),
            CoachRequestBuilder.existing(catalog, history(days = 0), today, enabled = true))
        for (question in listOf("help", "astronomy telescope", "email")) {
            val result = CoachRequestBuilder.existing(catalog, history(days = 0), today, question, true) as RequestResult.Ready
            assertEquals(2, result.request.contractVersion)
            assertTrue(result.request.strategies.size < 3)
        }
    }
    @Test fun questionAndPlanningMatchesRemainConditional() {
        val request = planning(question = "I do not struggle with starting routines")
        assertTrue(request.strategies.any { it.applicability == Applicability.QUESTION_OPTION })
        assertTrue(planning().strategies.all { it.applicability == Applicability.PLANNING_OPTION })
    }
    @Test fun measuredMissesRankRecoveryWithoutInferringEmotions() {
        val request = existing()
        assertTrue(request.strategies.any { it.card.id == "card_18" })
        assertTrue(request.strategies.all { it.applicability == Applicability.MEASURED_PATTERN_OPTION })
        val result = CoachResponseValidator.validate(response(request, ReadingFact.RECENT_MISSES), request, catalog) as ResponseResult.Valid
        assertTrue(result.response.readingText().contains("5 required occurrences missed"))
        assertTrue(result.response.readingText().contains("If starting feels"))
    }
    @Test fun longHistorySummaryUsesOnlyThirtyDaysWithoutLimitingLocalStatistics() {
        val start = today.minusDays(900)
        val h = HabitHistory(start, listOf(EffectiveSettings(start, HabitSettings(HabitSchedule.Daily))),
            (0L..900).map { HabitLog(start.plusDays(it), CompletionValue.Binary(true)) })
        val summary = MeasuredSummary.from(h, today)
        assertEquals(30, summary.observedDays); assertEquals(30, summary.completed)
        assertEquals(7, summary.recentSettled.size)
        assertEquals(901, HistoryCalculator.calculate(h, today).bestStreak)
    }
    @Test fun historicalTargetsCountPartialAmountsAndAchievementsSeparately() {
        val start = today.minusDays(4)
        val h = HabitHistory(start, listOf(EffectiveSettings(start, HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("5"), "pages"))),
            EffectiveSettings(today.minusDays(1), HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("2"), "pages")))),
            listOf(HabitLog(start, CompletionValue.Quantity(BigDecimal("3"), "pages")),
                HabitLog(today.minusDays(1), CompletionValue.Quantity(BigDecimal("3"), "pages"))))
        val summary = MeasuredSummary.from(h, today)
        assertEquals(1, summary.partialQuantityDays); assertEquals(1, summary.completed)
        assertEquals(3, summary.missed); assertEquals(1, summary.pending)
    }
    @Test fun openQuotaExtraSuccessesAndDisplayWeekStartDoNotInventDailyMisses() {
        val start = today.minusDays(1)
        val h = HabitHistory(start, listOf(EffectiveSettings(start, HabitSettings(HabitSchedule.Weekly(1)))),
            listOf(HabitLog(start, CompletionValue.Binary(true)), HabitLog(today, CompletionValue.Binary(true))))
        val s = MeasuredSummary.from(h, today)
        assertEquals(1, s.completed); assertEquals(0, s.missed); assertEquals(0, s.pending)
        val pending = MeasuredSummary.from(h.copy(logs = emptyList()), today)
        assertEquals(1, pending.pending); assertEquals(0, pending.missed)
    }
    @Test fun customRestDaysCreationAndFutureStoredFactsRemainHonest() {
        val h = history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), days = 1)
        val s = MeasuredSummary.from(h, today)
        assertEquals(1, s.missed); assertEquals(0, s.pending)
        val future = h.copy(logs = listOf(HabitLog(today.plusDays(6), CompletionValue.Binary(true))))
        assertEquals(s, MeasuredSummary.from(future, today))
        assertTrue(runCatching { MeasuredSummary.from(h.copy(archivedOn = today), today) }.isFailure)
        assertTrue(runCatching { MeasuredSummary.from(history(days = -1), today) }.isFailure)
    }
    @Test fun boundedQuestionsDisabledSettingsAndInvalidDraftsAreRejectedWithoutChangingDraft() {
        val draft = HabitFormDraft(name = "Saved", frequency = "WEEKLY", quota = "0")
        assertEquals(RequestResult.Unavailable(CoachFailure.InvalidInput), CoachRequestBuilder.planning(catalog, draft, 1, enabled = true))
        assertEquals(RequestResult.Unavailable(CoachFailure.Disabled), CoachRequestBuilder.planning(catalog, draft, -1, enabled = false))
        assertEquals(RequestResult.Unavailable(CoachFailure.InvalidInput), CoachRequestBuilder.planning(catalog, HabitFormDraft(), 1, "x".repeat(1001), true))
        assertEquals(RequestResult.Unavailable(CoachFailure.InvalidInput), CoachRequestBuilder.planning(catalog, HabitFormDraft(), 1, "bad\u0000text", true))
        assertEquals("0", draft.quota)
        assertEquals("", (planning().context as CoachContext.Planning).name)
    }
    @Test fun onlyThreeDistinctAdmittedCatalogIdsAreAccepted() {
        val request = planning(); val r = response(request)
        assertTrue(CoachResponseValidator.validate(r, request, catalog) is ResponseResult.Valid)
        listOf(r.copy(suggestions = r.suggestions.take(2)), r.copy(suggestions = r.suggestions + r.suggestions.first()),
            r.copy(suggestions = listOf(r.suggestions.first(), r.suggestions.first(), r.suggestions.last())),
            r.copy(suggestions = r.suggestions.mapIndexed { i, s -> if (i == 0) s.copy(strategyId = "unknown") else s }),
            r.copy(suggestions = r.suggestions.mapIndexed { i, s -> if (i == 0) s.copy(strategyId = "card_email") else s })).forEach {
            assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(it, request, catalog))
        }
        assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(r, request, StrategyCatalog.validated(cards, byteArrayOf(2))))
    }
    @Test fun readingCannotClaimUnmeasuredFactsOrUnconditionalCause() {
        val request = planning(); val r = response(request)
        listOf(emptyList(), listOf(ReadingFact.RECENT_MISSES), listOf(ReadingFact.PLANNING_DRAFT, ReadingFact.PLANNING_DRAFT)).forEach {
            assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(r.copy(reading = CoachReading(it, null)), request, catalog))
        }
        val existing = existing()
        assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(response(existing, ReadingFact.RECENT_MISSES)
            .copy(reading = CoachReading(listOf(ReadingFact.RECENT_MISSES, ReadingFact.OPEN_EXPECTATIONS, ReadingFact.ATTENTION), null)), existing, catalog))
        assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(response(existing, ReadingFact.RECENT_MISSES)
            .copy(reading = CoachReading(listOf(ReadingFact.RECENT_MISSES), PossibleBarrier.TOO_MANY_NEW_HABITS)), existing, catalog))
        PossibleBarrier.entries.forEach { assertTrue(it.conditionalText.startsWith("If ")) }
    }
    @Test fun targetActionsRequireQuantitySameUnitPositivePlainBoundedDecimalAndRelevantStrategy() {
        val request = planning(HabitFormDraft(tracking = "QUANTITY", target = "5", unit = "pages"))
        assertTrue(validation(request, CoachAction.Target("2.50", "pages"), "card_11") is ResponseResult.Valid)
        listOf("0", "-1", "NaN", "1e3", "1,5", "9".repeat(65)).forEach {
            assertEquals(ResponseResult.Invalid, validation(request, CoachAction.Target(it, "pages"), "card_11"))
        }
        assertEquals(ResponseResult.Invalid, validation(request, CoachAction.Target("2", "km"), "card_11"))
        assertEquals(ResponseResult.Invalid, validation(planning(), CoachAction.Target("2", "pages"), "card_11"))
        assertEquals(ResponseResult.Invalid, validation(request, CoachAction.Target("2", "pages"), "card_01"))
    }
    @Test fun supportedScheduleCuePlanAndInformationalActionsRemainTyped() {
        val r = planning()
        listOf(CoachAction.Schedule(HabitSchedule.Daily), CoachAction.Schedule(HabitSchedule.Weekly(3)),
            CoachAction.Schedule(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), CoachAction.CueAnchor("After breakfast", ""),
            CoachAction.Plan("Prepare a small step"), CoachAction.AdviceOnly).forEach {
            assertTrue(validation(r, it, "card_01") is ResponseResult.Valid)
        }
        assertEquals(ResponseResult.Invalid, validation(r, CoachAction.CueAnchor("", "")))
        assertEquals(ResponseResult.Invalid, validation(r, CoachAction.Plan("x".repeat(241))))
    }
    @Test fun planningCountNeverBecomesHabitMutationAndReminderCannotEnterUnsavedDraft() {
        val r = planning()
        assertTrue(validation(r, CoachAction.NewHabitCount(1), "card_01") is ResponseResult.Valid)
        listOf(0, 8, -1).forEach { assertEquals(ResponseResult.Invalid, validation(r, CoachAction.NewHabitCount(it), "card_01")) }
        assertEquals(ResponseResult.Invalid, validation(r, CoachAction.Reminder(ReminderSetting.At(600)), "card_01"))
        assertEquals(ResponseResult.Invalid, validation(existing(), CoachAction.NewHabitCount(1)))
    }
    @Test fun reminderBoundsAndWrongStrategyAreRejected() {
        // A cue question deliberately admits cue/reminder-capable cards for the existing habit.
        val request = (CoachRequestBuilder.existing(catalog, history(), today, "cues scheduling planning", true) as RequestResult.Ready).request
        assertTrue(validation(request, CoachAction.Reminder(ReminderSetting.At(600)), "card_01") is ResponseResult.Valid)
        listOf(-1, 1440).forEach { assertEquals(ResponseResult.Invalid, validation(request, CoachAction.Reminder(ReminderSetting.At(it)), "card_01")) }
    }
    @Test fun unconfiguredServiceReturnsAnExplicitFailureAndNeverASimulatedResponse() = runTest {
        assertEquals(CoachServiceResult.Failure(CoachFailure.Unconfigured), UnconfiguredCoachService().request(planning()))
    }
    @Test fun existingPlanningEnvelopeRemainsPrimitiveAndDoesNotGainOutboundFields() {
        val result = DraftPlanningResult("local-private-token", DraftPlanningChange.Schedule(HabitSchedule.Weekly(2)))
        assertEquals(result, PlanningDraftContract.decode(PlanningDraftContract.encode(result)))
        assertFalse(CoachRequest::class.java.declaredFields.any { it.name.contains("token", true) || it.name.contains("habitId", true) })
    }
}
