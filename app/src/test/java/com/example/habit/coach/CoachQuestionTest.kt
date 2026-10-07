package com.example.habit.coach

import com.example.habit.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** Exercises real supplied vocabulary on the JVM; production JSON parsing remains Android-tested. */
class CoachQuestionTest {
    private val day = LocalDate.of(2026, 10, 7)
    private val catalog: StrategyCatalog by lazy {
        val file = listOf(File("../resources/coach_cards.json"), File("resources/coach_cards.json")).first { it.isFile }
        val bytes = file.readBytes()
        // This trusted flat source currently contains no escaped strings or nested objects.
        // Fail explicitly if that changes; this is not a replacement production JSON parser.
        val text = bytes.toString(Charsets.UTF_8)
        require('\\' !in text)
        val cards = Regex("\\{[^{}]*}").findAll(text).map { match ->
            fun field(key: String) = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(match.value)!!.groupValues[1]
            val tags = Regex("\"tags\"\\s*:\\s*\\[([^]]*)]").find(match.value)!!.groupValues[1]
            StrategyCard(field("id"), field("title"), field("principle"), field("action"), field("use_when"),
                Regex("\"([^\"]*)\"").findAll(tags).map { it.groupValues[1] }.toList(), field("source"))
        }.toList()
        assertEquals(60, cards.size)
        StrategyCatalog.validated(cards, bytes)
    }
    private fun history(schedule: HabitSchedule = HabitSchedule.Daily, tracking: TrackingMode = TrackingMode.Binary,
        days: Long = 0) = HabitHistory(day.minusDays(days), listOf(EffectiveSettings(day.minusDays(days), HabitSettings(schedule, tracking))))
    private fun request(history: HabitHistory, question: String) =
        (CoachRequestBuilder.existing(catalog, history, day, question, true) as RequestResult.Ready).request

    @Test fun freshHabitDoesNotInventMeasuredOptionsButStartingQuestionWorksAcrossSchedulesAndModes() {
        val schedules = listOf(HabitSchedule.Daily, HabitSchedule.Weekly(3), HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)))
        val modes = listOf(TrackingMode.Binary, TrackingMode.Quantity(BigDecimal("15"), "minutes"))
        for (schedule in schedules) for (mode in modes) {
            val h = history(schedule, mode)
            assertEquals(RequestResult.Unavailable(CoachFailure.InsufficientContext), CoachRequestBuilder.existing(catalog, h, day, enabled = true))
            val r = request(h, "How can I make starting this habit easier?")
            assertEquals(3, r.strategies.size)
            assertTrue(r.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
            assertTrue(r.strategies.all { it.card.tags.any { tag -> tag in setOf("starting", "simplicity") } })
            assertEquals(0, (r.context as CoachContext.Existing).summary.missed)
        }
    }
    @Test fun rememberingKarateAndReadingDistractionsOutrankOldMisses() {
        val h = history(days = 12)
        val remembering = request(h, "I forget to pack my karate kit. How do I remember before class?")
        assertTrue(remembering.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
        assertTrue(remembering.strategies.all { it.card.tags.any { tag -> tag in setOf("cues", "triggers") } })
        val focus = request(h, "I get distracted by my phone while reading. How can I concentrate?")
        assertTrue(focus.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
        assertTrue(focus.strategies.all { it.card.tags.any { tag -> tag in setOf("focus", "distraction-control") } })
        assertNotEquals(remembering.strategies.map { it.card.id }, focus.strategies.map { it.card.id })
    }
    @Test fun typedMessagesNeverNeedThreeKeywordMatchesAndDoNotPadCards() {
        assertEquals(RequestResult.Unavailable(CoachFailure.InsufficientContext), CoachRequestBuilder.existing(catalog, history(), day, enabled = true))
        for (q in listOf("help", "karate", "astronomy telescope", "Hello", "I keep failing at this", "এটা করতে কষ্ট হয়")) {
            val r = request(history(), q)
            assertEquals(2, r.contractVersion)
            assertEquals(q, r.question)
            assertTrue(r.strategies.size <= 3)
        }
        assertTrue(request(history(), "Hello").strategies.isEmpty())
        assertTrue(request(history(), "astronomy telescope").strategies.isEmpty())
    }
    @Test fun consistencyAndMissedDaysUseRelevantCardsWithoutInventingMeasuredMisses() {
        for (q in listOf("How can I stay consistent?", "Can you strategize this so I don't miss it?", "I struggle to keep doing this regularly")) {
            val r = request(history(), q)
            assertTrue(r.strategies.isNotEmpty())
            assertTrue(r.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
            assertEquals(0, (r.context as CoachContext.Existing).summary.missed)
        }
    }
    @Test fun conversationReplyWorksWithNoCardsAndCannotSmuggleAnyApplyAction() {
        val r = request(history(), "Hello")
        val response = CoachResponse(CoachReading(listOf(ReadingFact.NO_SETTLED_HISTORY), null), emptyList(), "Hello! What feels difficult about this habit?")
        val valid = CoachResponseValidator.validate(response, r, catalog) as ResponseResult.Valid
        assertEquals(response.reply, valid.response.conversationText(r.question))
        for (bad in listOf(response.copy(reply = ""), response.copy(reply = "x".repeat(CoachLimits.REPLY + 1)),
            response.copy(suggestions = listOf(CoachSuggestion("card_11", "Change target", "Change it", CoachAction.Target("1", "pages")))))) {
            assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(bad, r, catalog))
        }
    }
    @Test fun noSettledFactSupportsRestDayWithoutClaimingMissesOrPendingDays() {
        val r = request(history(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY))), "How can I start?")
        assertEquals(setOf(ReadingFact.NO_SETTLED_HISTORY), CoachResponseValidator.allowedFacts(r.context))
        val response = CoachResponse(CoachReading(listOf(ReadingFact.NO_SETTLED_HISTORY), null), r.strategies.map {
            CoachSuggestion(it.card.id, "A starting step", "Choose an easy first step if starting is difficult.", CoachAction.AdviceOnly)
        }, "Choose an easy first step if starting is difficult.")
        assertTrue(CoachResponseValidator.validate(response, r, catalog) is ResponseResult.Valid)
        assertEquals(ResponseResult.Invalid, CoachResponseValidator.validate(response.copy(reading = CoachReading(listOf(ReadingFact.RECENT_MISSES), null)), r, catalog))
        assertFalse(ReadingFact.NO_SETTLED_HISTORY in CoachResponseValidator.allowedFacts(request(history(days = 3), "starting").context))
    }
    @Test fun conversationShowsSpecificAdviceAndInitialPatternKeepsFixedFacts() {
        val r = request(history(days = 8), "I forget my karate kit")
        val suggestions = r.strategies.mapIndexed { index, it -> CoachSuggestion(it.card.id, "Packing option ${index + 1}",
            "If packing slips your mind, leave your kit beside the door before class.", CoachAction.AdviceOnly) }
        val validated = (CoachResponseValidator.validate(CoachResponse(CoachReading(listOf(ReadingFact.RECENT_MISSES), null), suggestions, "If packing slips your mind, leave your kit beside the door before class."), r, catalog) as ResponseResult.Valid).response
        val bubble = validated.conversationText(r.question)
        assertTrue(bubble.contains("kit beside the door"))
        assertFalse(bubble.contains("required occurrences missed"))
        assertEquals(validated.readingText(), validated.conversationText(""))
        assertTrue(validated.readingText().contains("8 required occurrences missed"))
    }
    @Test fun questionRankingIsDeterministicAndLegacyOrderRemainsReconstructible() {
        val context = request(history(days = 8), "I forget my karate kit").context
        val q = "How can I remember to start?"
        val old = StrategyRetriever.legacyRetrieve(catalog, context, q) as RetrievalResult.Ready
        val new = StrategyRetriever.retrieve(catalog, context, q) as RetrievalResult.Ready
        assertNotEquals(old.strategies, new.strategies)
        repeat(3) { assertEquals(new, StrategyRetriever.retrieve(StrategyCatalog.validated(catalog.cards.reversed(), byteArrayOf(1)), context, q)) }
        assertEquals(old, StrategyRetriever.legacyRetrieve(catalog, context, q))
    }
}
