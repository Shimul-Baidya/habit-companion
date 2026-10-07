package com.example.habit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.coach.*
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.HabitFormDraft
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.math.BigDecimal
import java.time.LocalDate

/** Real packaged bytes and Android strict JSON; no activity, real stores or transport needed. */
@RunWith(AndroidJUnit4::class)
class CoachBoundaryRuntimeTest {
    private fun bytes() = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("coach_cards.json").use { it.readBytes() }
    private fun catalog() = CoachJson.catalog(bytes())
    private val today = LocalDate.of(2026, 10, 6)
    private fun history(schedule: HabitSchedule = HabitSchedule.Daily, tracking: TrackingMode = TrackingMode.Binary) =
        HabitHistory(today.minusDays(5), listOf(EffectiveSettings(today.minusDays(5), HabitSettings(schedule, tracking))))
    private fun planning(c: StrategyCatalog, draft: HabitFormDraft = HabitFormDraft()) =
        (CoachRequestBuilder.planning(c, draft, 4, enabled = true) as RequestResult.Ready).request
    private fun existing(c: StrategyCatalog, h: HabitHistory = history()) =
        (CoachRequestBuilder.existing(c, h, today, enabled = true) as RequestResult.Ready).request
    private fun keys(o: JSONObject) = o.keys().asSequence().toSet()
    private fun response(request: CoachRequest): JSONObject = JSONObject().put("reading", JSONObject()
        .put("facts", JSONArray(listOf(if (request.context is CoachContext.Planning) "PLANNING_DRAFT" else "RECENT_MISSES")))
        .put("possibleBarrier", "STARTING_SIZE"))
        .put("suggestions", JSONArray(request.strategies.map { JSONObject().put("strategyId", it.card.id)
            .put("title", "Try a smaller starting step").put("advice", "If starting is difficult, prepare one small step.")
            .put("action", JSONObject().put("type", "PLAN").put("note", "Prepare a small starting step")) }))
    private fun invalid(json: String, request: CoachRequest, c: StrategyCatalog) {
        assertEquals(json.take(80), ResponseResult.Invalid, CoachJson.response(json, request, c))
    }

    @Test fun exactOriginalSixtyCardsAndEveryFieldReachPackagedAsset() = runBlocking {
        val raw = bytes(); val c = catalog()
        assertEquals("060c4c76706da0c77c2bce45222b98730055c82107928c8bd7873237a53d13ff", c.sha256)
        assertEquals(60, c.cards.size); assertEquals(60, c.byId.size)
        val source = JSONArray(raw.toString(Charsets.UTF_8))
        c.cards.forEachIndexed { i, card ->
            val original = source.getJSONObject(i)
            assertEquals(original.getString("id"), card.id); assertEquals(original.getString("title"), card.title)
            assertEquals(original.getString("principle"), card.principle); assertEquals(original.getString("action"), card.action)
            assertEquals(original.getString("use_when"), card.useWhen); assertEquals(original.getString("source"), card.source)
            val tags = original.getJSONArray("tags")
            assertEquals((0 until tags.length()).map { tags.getString(it) }, card.tags)
        }
        val loaded = StrategyRepository { raw }.load() as CatalogResult.Ready
        assertEquals(c.sha256, loaded.catalog.sha256)
    }
    @Test fun missingCorruptOversizedAndDuplicateAssetNeverBecomeAFakeLibrary() = runBlocking {
        assertEquals(CatalogResult.Unavailable(CoachFailure.CatalogMissing), StrategyRepository { throw FileNotFoundException() }.load())
        val raw = bytes().toString(Charsets.UTF_8)
        val duplicate = JSONArray(raw).let { it.put(it.get(0)); it.toString() }
        listOf("{}", "[]", "[{\"id\":\"x\"}]", "[null]", "/*comment*/$raw", "$raw trailing", duplicate,
            raw.replaceFirst("\"title\":", "\"id\":\"duplicate\",\"title\":"),
            raw.replaceFirst("\"source\":", "\"unknown\":"), raw.replaceFirst("\"card_01\"", "null"),
            " ".repeat(262_145)).forEach { corrupt ->
            assertEquals(CatalogResult.Unavailable(CoachFailure.CatalogInvalid), StrategyRepository { corrupt.toByteArray() }.load())
        }
        assertEquals(CatalogResult.Unavailable(CoachFailure.CatalogInvalid), StrategyRepository { byteArrayOf(0xc3.toByte(), 0x28) }.load())
    }
    @Test fun actualCatalogRankingIsStableRelevantAndDoesNotFillWeakContext() {
        val c = catalog()
        repeat(4) { assertEquals(listOf("card_01", "card_11", "card_39"), planning(c).strategies.map { it.card.id }) }
        assertEquals(listOf("card_11", "card_18", "card_39"), existing(c).strategies.map { it.card.id })
        val fresh = HabitHistory(today, listOf(EffectiveSettings(today, HabitSettings(HabitSchedule.Weekly(3)))))
        assertEquals(RequestResult.Unavailable(CoachFailure.InsufficientContext), CoachRequestBuilder.existing(c, fresh, today, enabled = true))
        val email = CoachRequestBuilder.existing(c, fresh, today, "email inbox", true) as RequestResult.Ready
        assertTrue(email.request.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
        assertEquals(setOf("card_22", "card_26", "card_51"), email.request.strategies.map { it.card.id }.toSet())
    }
    @Test fun legacyQuestionCachesStillValidateButAlteredCardsDoNot() {
        val c = catalog(); val context = existing(c).context; val question = "How can I remember to start?"
        val old = (StrategyRetriever.legacyRetrieve(c, context, question) as RetrievalResult.Ready).strategies
        val current = (StrategyRetriever.retrieve(c, context, question) as RetrievalResult.Ready).strategies
        assertNotEquals(old, current)
        for (cards in listOf(old, current)) {
            val request = CoachRequest(context, question, cards, c.sha256)
            val payload = CoachJson.payload(request)
            val restored = CoachJson.storedRequest(payload, c.sha256, c)!!
            assertEquals(payload, CoachJson.payload(restored))
            assertTrue(CoachJson.response(response(request).toString(), restored, c) is ResponseResult.Valid)
            val altered = JSONObject(payload).apply { getJSONArray("strategies").getJSONObject(0).put("title", "invented") }
            assertNull(CoachJson.storedRequest(altered.toString(), c.sha256, c))
        }
    }
    @Test fun existingWirePayloadContainsOnlyOneMeasuredSummaryAndQuestionNotRecordFields() {
        val c = catalog(); val q = TrackingMode.Quantity(BigDecimal("5.00"), "pages")
        val h = history(tracking = q).let { it.copy(logs = listOf(HabitLog(today.minusDays(1), CompletionValue.Quantity(BigDecimal("2"), "pages")))) }
        val request = existing(c, h)
        val payload = JSONObject(CoachJson.payload(request))
        assertEquals(setOf("contractVersion", "mode", "context", "question", "strategies"), keys(payload))
        assertEquals("EXISTING", payload.getString("mode"))
        val context = payload.getJSONObject("context")
        assertEquals(setOf("windowDays", "observedDays", "schedule", "tracking", "metricUnit", "completed", "missed", "pending",
            "partialQuantityDays", "recentSettledOutcomes", "attention"), keys(context))
        assertEquals(30, context.getInt("windowDays")); assertEquals(6, context.getInt("observedDays"))
        assertEquals("5.00", context.getJSONObject("tracking").getString("target"))
        assertEquals(1, context.getInt("partialQuantityDays")); assertEquals(0, context.getInt("completed"))
        assertEquals("required_occurrences", context.getString("metricUnit"))
        assertEquals(3, payload.getJSONArray("strategies").length())
        val contextText = context.toString()
        listOf("name", "habitId", "created", "logs", "date", "cue", "token", "bestStreak", "messages", "count").forEach {
            assertFalse("Unexpected automatic field $it", contextText.contains("\"$it\""))
        }
    }
    @Test fun planningWireWhitelistsPermittedDraftAndNecessaryTrackingQuotaFields() {
        val c = catalog(); val draft = HabitFormDraft(name = "My private draft", frequency = "WEEKLY", quota = "3",
            tracking = "QUANTITY", target = "0.30", unit = "km", cue = "Private cue", anchor = "Private anchor", planNote = "Private note",
            iconKey = "private-icon", colorKey = "private-colour")
        val payload = JSONObject(CoachJson.payload(planning(c, draft)))
        val context = payload.getJSONObject("context")
        assertEquals(setOf("draftName", "schedule", "tracking", "activeHabitCount"), keys(context))
        assertEquals(draft.name, context.getString("draftName")); assertEquals(4, context.getInt("activeHabitCount"))
        assertEquals(3, context.getJSONObject("schedule").getInt("quota"))
        assertEquals("0.3", context.getJSONObject("tracking").getString("target"))
        listOf("Private cue", "Private anchor", "Private note", "private-icon", "private-colour", "token").forEach {
            assertFalse(payload.toString().contains(it))
        }
        val binary = JSONObject(CoachJson.payload(planning(c))).getJSONObject("context").getJSONObject("tracking")
        assertEquals(setOf("mode"), keys(binary))
    }
    @Test fun structuredResponseIsValidatedAndReadingIsConditionalLocalCopy() {
        val c = catalog(); val request = existing(c)
        val valid = CoachJson.response(response(request).toString(), request, c) as ResponseResult.Valid
        assertEquals(3, valid.response.value.suggestions.size)
        assertTrue(valid.response.readingText().contains("5 required occurrences missed"))
        assertTrue(valid.response.readingText().contains("If starting feels"))
        invalid(response(request).put("reading", JSONObject().put("text", "You are anxious and lazy")).toString(), request, c)
        invalid(response(request).put("reading", JSONObject().put("facts", JSONArray(listOf("RECENT_COMPLETIONS"))).put("possibleBarrier", JSONObject.NULL)).toString(), request, c)
    }
    @Test fun strictResponseRejectsUnknownFieldsIdsCountsTypesDuplicatesAndTrailingData() {
        val c = catalog(); val request = planning(c); val valid = response(request).toString()
        listOf("{}", "null", "[]", "$valid trailing", "/*comment*/$valid", valid.replaceFirst("\"reading\":", "\"extra\":1,\"reading\":"),
            valid.replaceFirst("\"reading\":", "\"reading\":null,\"reading\":"),
            valid.replace("\"possibleBarrier\":\"STARTING_SIZE\"", "\"possibleBarrier\":\"YOU_ARE_ANXIOUS\""),
            valid.replaceFirst("\"${request.strategies.first().card.id}\"", "\"unknown\""),
            valid.replaceFirst("\"${request.strategies.first().card.id}\"", "\"card_60\""),
            valid.replaceFirst("\"${request.strategies.first().card.id}\"", "\"${request.strategies.last().card.id}\""),
            valid.replaceFirst("\"type\":\"PLAN\"", "\"type\":\"DELETE_ALL\""),
            valid.replaceFirst("\"note\":", "\"unknown\":1,\"note\":"), " ".repeat(32_769)).forEach { invalid(it, request, c) }
        val fewer = response(request); fewer.getJSONArray("suggestions").remove(2); invalid(fewer.toString(), request, c)
        val more = response(request); more.getJSONArray("suggestions").put(more.getJSONArray("suggestions").get(0)); invalid(more.toString(), request, c)
    }
    @Test fun closedActionParserRejectsInvalidQuotasDaysAmountsUnitsReminderAndMode() {
        val c = catalog(); val request = planning(c, HabitFormDraft(tracking = "QUANTITY", target = "5", unit = "pages"))
        fun withAction(action: JSONObject, id: String = "card_01"): String {
            val response = response(request)
            val suggestions = response.getJSONArray("suggestions")
            (0 until 3).map { suggestions.getJSONObject(it) }.single { it.getString("strategyId") == id }.put("action", action)
            return response.toString()
        }
        fun schedule(kind: String, key: String, value: Any) = JSONObject().put("type", "SCHEDULE")
            .put("schedule", JSONObject().put("kind", kind).put(key, value))
        listOf(0, 8, "3", 3.5).forEach { invalid(withAction(schedule("WEEKLY", "quota", it)), request, c) }
        listOf(emptyList<Int>(), listOf(0), listOf(8), listOf(1, 1), listOf("1")).forEach {
            invalid(withAction(schedule("CUSTOM", "weekdays", JSONArray(it))), request, c)
        }
        listOf("0", "-1", "1e3", "NaN").forEach {
            invalid(withAction(JSONObject().put("type", "TARGET").put("amount", it).put("unit", "pages"), "card_11"), request, c)
        }
        invalid(withAction(JSONObject().put("type", "TARGET").put("amount", "2").put("unit", "km"), "card_11"), request, c)
        invalid(withAction(JSONObject().put("type", "TARGET").put("amount", 2).put("unit", "pages"), "card_11"), request, c)
        invalid(withAction(JSONObject().put("type", "REMINDER").put("setting", JSONObject().put("mode", "AT").put("minuteOfDay", 600))), request, c)
        assertTrue(CoachJson.response(withAction(JSONObject().put("type", "TARGET").put("amount", "2.5").put("unit", "pages"), "card_11"), request, c) is ResponseResult.Valid)
        assertTrue(CoachJson.response(withAction(schedule("WEEKLY", "quota", 3)), request, c) is ResponseResult.Valid)
    }
}
