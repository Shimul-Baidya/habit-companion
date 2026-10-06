package com.example.habit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.coach.*
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.HabitFormDraft
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic inputs/fake credentials only. Every case consumes zero Gemini requests. */
@RunWith(AndroidJUnit4::class)
class GeminiTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun catalog() = CoachJson.catalog(context.assets.open("coach_cards.json").use { it.readBytes() })
    private fun planning(draft: HabitFormDraft = HabitFormDraft()) =
        (CoachRequestBuilder.planning(catalog(), draft, 2, "How can I start small?", true) as RequestResult.Ready).request
    private fun existing(): CoachRequest {
        val day = LocalDate.of(2026, 10, 6)
        val settings = HabitSettings(HabitSchedule.Weekly(3), TrackingMode.Quantity(java.math.BigDecimal("5"), "pages"))
        val history = HabitHistory(day.minusDays(14), listOf(EffectiveSettings(day.minusDays(14), settings)),
            logs = listOf(HabitLog(day.minusDays(1), CompletionValue.Quantity(java.math.BigDecimal("2"), "pages"))))
        return (CoachRequestBuilder.existing(catalog(), history, day, "Help with a smaller starting step", true) as RequestResult.Ready).request
    }
    private fun valid(request: CoachRequest): String = JSONObject().put("reading", JSONObject()
        .put("facts", JSONArray(listOf(CoachResponseValidator.allowedFacts(request.context).first().name)))
        .put("possibleBarrier", JSONObject.NULL)).put("suggestions", JSONArray(request.strategies.map {
            JSONObject().put("strategyId", it.card.id).put("title", "Prepare a small step")
                .put("advice", "If starting feels difficult, prepare one small step.")
                .put("action", JSONObject().put("type", "PLAN").put("note", "Prepare one small step"))
        })).toString()
    private fun envelope(text: String, finish: String = "STOP") = JSONObject().put("candidates", JSONArray(listOf(
        JSONObject().put("finishReason", finish).put("content", JSONObject().put("parts", JSONArray(listOf(JSONObject().put("text", text)))))))).toString()
    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()
    private fun failure(reason: CoachFailure) = CoachServiceResult.Failure(reason)
    private val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")

    @Test fun planningBodyContainsExactlyTheApprovedPayloadAndSingleCandidateSchema() {
        val request = planning(HabitFormDraft(name = "Synthetic plan", frequency = "CUSTOM", weekdayMask = 5,
            tracking = "QUANTITY", target = "5", unit = "pages", cue = "excluded-cue", planNote = "excluded-note"))
        val body = JSONObject(GeminiWire.body(request))
        assertEquals(setOf("systemInstruction", "contents", "generationConfig"), keys(body))
        val contents = body.getJSONArray("contents"); assertEquals(1, contents.length())
        val parts = contents.getJSONObject(0).getJSONArray("parts"); assertEquals(1, parts.length())
        assertEquals(CoachJson.payload(request), parts.getJSONObject(0).getString("text"))
        assertFalse(body.toString().contains("excluded-cue")); assertFalse(body.toString().contains("excluded-note"))
        val config = body.getJSONObject("generationConfig")
        assertEquals(1, config.getInt("candidateCount")); assertEquals(4096, config.getInt("maxOutputTokens"))
        assertEquals("application/json", config.getString("responseMimeType"))
        val schema = config.getJSONObject("responseJsonSchema")
        assertFalse(schema.getBoolean("additionalProperties"))
        val variants = schema.getJSONObject("properties").getJSONObject("suggestions").getJSONObject("items").getJSONArray("anyOf")
        assertEquals(3, variants.length())
        request.strategies.forEachIndexed { index, strategy ->
            val suggestion = variants.getJSONObject(index).getJSONObject("properties")
            assertEquals(strategy.card.id, suggestion.getJSONObject("strategyId").getJSONArray("enum").getString(0))
            val allowed = suggestion.getJSONObject("action").toString()
            assertFalse(allowed.contains("REMINDER")); assertTrue(allowed.contains("PLAN"))
        }
        assertTrue(schema.toString().contains("pages")); assertTrue(schema.toString().contains("TARGET"))
    }
    @Test fun existingBodyHasOnlyMeasuredContextNoAutomaticNamesIdsConversationOrCollection() {
        val request = existing()
        val body = JSONObject(GeminiWire.body(request))
        val wire = JSONObject(body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals(CoachJson.payload(request), wire.toString())
        assertEquals("EXISTING", wire.getString("mode")); assertEquals(3, wire.getJSONArray("strategies").length())
        val data = wire.getJSONObject("context")
        assertEquals(3, data.getJSONObject("schedule").getInt("quota"))
        assertEquals("QUANTITY", data.getJSONObject("tracking").getString("mode"))
        listOf("name", "habitId", "logs", "date", "messages", "activeHabitCount", "catalogSha256", "token").forEach { assertFalse(data.has(it)) }
        val schema = body.getJSONObject("generationConfig").getJSONObject("responseJsonSchema").toString()
        assertFalse(schema.contains("NEW_HABIT_COUNT")); assertFalse(schema.contains("PLANNING_DRAFT"))
        assertFalse(schema.contains("TOO_MANY_NEW_HABITS"))
    }
    @Test fun successfulProviderResponseStillPassesTheOriginalStrictContractAndDispatchesOnce() = runBlocking {
        val request = planning(); val calls = AtomicInteger()
        val service = GeminiCoachService("synthetic-key", "gemini-3.5-flash-lite", GeminiTransport { url, key, body ->
            calls.incrementAndGet(); assertEquals(endpoint, url); assertEquals("synthetic-key", key)
            assertFalse(body.contains(key)); GeminiHttpResponse(200, envelope(valid(request)))
        })
        val result = service.request(request) as CoachServiceResult.RawResponse
        assertTrue(CoachJson.response(result.json, request, catalog()) is ResponseResult.Valid)
        assertEquals(1, calls.get())
    }
    @Test fun transportSuccessDoesNotAdmitUnknownStrategyOrAction() = runBlocking {
        val request = planning()
        for (invalid in listOf(valid(request).replace(request.strategies[0].card.id, "unknown"),
            valid(request).replace("PLAN", "DELETE_ALL"))) {
            val service = GeminiCoachService("synthetic", "gemini-3.5-flash-lite", GeminiTransport { _, _, _ -> GeminiHttpResponse(200, envelope(invalid)) })
            assertEquals(ResponseResult.Invalid, CoachJson.response((service.request(request) as CoachServiceResult.RawResponse).json, request, catalog()))
        }
    }
    @Test fun malformedBlockedTruncatedDuplicateAndMultipleCandidatesNeverBecomeAdvice() = runBlocking {
        val request = planning(); val valid = envelope(valid(request))
        val samples = listOf("{}", "null", "$valid trailing", "/*comment*/$valid", valid.replaceFirst("\"candidates\":", "\"candidates\":[],\"candidates\":"),
            envelope("{}", "MAX_TOKENS"), envelope("{}", "SAFETY"),
            JSONObject(valid).put("promptFeedback", JSONObject().put("blockReason", "SAFETY")).toString(),
            JSONObject(valid).apply { getJSONArray("candidates").put(getJSONArray("candidates").get(0)) }.toString(),
            JSONObject(valid).apply { getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).put("functionCall", JSONObject()) }.toString(),
            envelope(" "), envelope("x".repeat(32_769)))
        for (sample in samples) {
            val service = GeminiCoachService("synthetic", "gemini-3.5-flash-lite", GeminiTransport { _, _, _ -> GeminiHttpResponse(200, sample) })
            assertEquals(failure(CoachFailure.MalformedResponse), service.request(request))
        }
    }
    @Test fun thoughtTextIsDiscardedAndOnlyFinalTextReachesValidation() {
        val request = planning(); val body = JSONObject(envelope(valid(request)))
        val parts = body.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        parts.put(JSONObject().put("thought", true).put("text", "untrusted internal thought"))
        assertEquals(valid(request), GeminiWire.extract(body.toString()))
    }
    @Test fun providerHttpFailuresNeverRetryOrLeakErrorText() = runBlocking {
        val calls = AtomicInteger()
        for (status in listOf(400, 401, 403, 404, 408, 429, 500, 503, 504, 302)) {
            val service = GeminiCoachService("synthetic", "gemini-3.5-flash-lite", GeminiTransport { _, _, _ ->
                calls.incrementAndGet(); GeminiHttpResponse(status, "sensitive-error-not-for-display", "12")
            })
            val expected = when (status) { 401, 403, 404 -> CoachFailure.Unconfigured; 408, 504 -> CoachFailure.Timeout; 429 -> CoachFailure.RateLimited(12); else -> CoachFailure.ServerError }
            assertEquals(failure(expected), service.request(planning()))
        }
        assertEquals(10, calls.get())
    }
    @Test fun retryAfterHonoursGoogleDurationHttpDateAndBoundedFallback() {
        val now = Instant.parse("2026-10-06T00:00:00Z")
        fun delay(header: String?, body: String = "") = GeminiCoachService.retrySeconds(header, body, now)
        assertEquals(3, delay(null)); assertEquals(3, delay("nonsense")); assertEquals(3600, delay("999999"))
        assertEquals(3, delay("-3")); assertEquals(3, delay("0"))
        assertEquals(60, delay("Tue, 06 Oct 2026 00:01:00 GMT"))
        val retry = """{"error":{"details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"61.2s"}]}}"""
        assertEquals(62, delay("12", retry)); assertEquals(120, delay("120", retry))
        assertEquals(3, delay(null, retry.replace("61.2s", "NaNs")))
    }
    @Test fun socketTimeoutOfflineAndEnvelopeFailuresHaveDistinctLocalStates() = runBlocking {
        val cases = listOf(SocketTimeoutException() to CoachFailure.Timeout, IOException() to CoachFailure.Offline,
            GeminiEnvelopeTooLarge() to CoachFailure.MalformedResponse, java.nio.charset.CharacterCodingException() to CoachFailure.MalformedResponse)
        for ((exception, reason) in cases) {
            val service = GeminiCoachService("synthetic", "gemini-3.5-flash-lite", GeminiTransport { _, _, _ -> throw exception })
            assertEquals(failure(reason), service.request(planning()))
        }
    }
    @Test fun missingCredentialsOrDifferentModelSendNothingAndContainerUsesTheConfiguredProvider() = runBlocking {
        val transport = GeminiTransport { _, _, _ -> error("must not dispatch") }
        assertEquals(failure(CoachFailure.Unconfigured), GeminiCoachService("", "gemini-3.5-flash-lite", transport).request(planning()))
        assertEquals(failure(CoachFailure.Unconfigured), GeminiCoachService("synthetic", "different-model", transport).request(planning()))
        assertEquals("gemini-3.5-flash-lite", BuildConfig.GEMINI_MODEL)
        assertEquals(BuildConfig.GEMINI_API_KEY.isNotBlank(), (context.applicationContext as HabitApplication).container.coachService is GeminiCoachService)
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions?.contains("android.permission.INTERNET") == true)
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }
    private open class Connection(private val code: Int = 200, private val bytes: ByteArray = "{}".toByteArray()) : HttpURLConnection(URL("https://generativelanguage.googleapis.com")) {
        val written = ByteArrayOutputStream()
        val disconnected = CountDownLatch(1)
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
        override fun getOutputStream(): OutputStream = written
        override fun getResponseCode() = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun getErrorStream(): InputStream = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?) = if (name == "Retry-After") "9" else null
    }
    @Test fun platformTransportUsesHeaderAuthBoundedTimeoutsNoCacheNoRedirectAndCloses() = runBlocking {
        val connection = Connection(429, "quota error".toByteArray()); val calls = AtomicInteger()
        val transport = AndroidGeminiTransport { calls.incrementAndGet(); connection }
        val result = transport.post(endpoint, "synthetic", "{\"only\":\"reviewed payload\"}")
        assertEquals(429, result.status); assertEquals("quota error", result.body); assertEquals("9", result.retryAfter)
        assertEquals("POST", connection.requestMethod); assertEquals("synthetic", connection.getRequestProperty("x-goog-api-key"))
        assertFalse(connection.instanceFollowRedirects); assertFalse(connection.useCaches)
        assertEquals(10000, connection.connectTimeout); assertEquals(20000, connection.readTimeout)
        assertEquals("{\"only\":\"reviewed payload\"}", connection.written.toString("UTF-8"))
        assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS)); assertEquals(1, calls.get())
    }
    @Test fun platformRejectsNonGoogleCleartextAndQueryCredentialsBeforeOpening() = runBlocking {
        val transport = AndroidGeminiTransport { error("must not open") }
        for (url in listOf("http://generativelanguage.googleapis.com/", "https://example.com/", "$endpoint?key=synthetic")) {
            try { transport.post(URL(url), "synthetic", "{}"); fail("Unexpected dispatch") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun platformCapsEnvelopeAndRejectsMalformedUtf8() = runBlocking {
        for (bytes in listOf(ByteArray(65_537) { 65 }, byteArrayOf(0xc3.toByte(), 0x28))) {
            val connection = Connection(bytes = bytes)
            try { AndroidGeminiTransport { connection }.post(endpoint, "synthetic", "{}"); fail("Unexpected text") }
            catch (exception: IOException) { assertTrue(exception is GeminiEnvelopeTooLarge || exception is java.nio.charset.CharacterCodingException) }
            assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
        }
    }
    @Test fun cancellationDisconnectsActiveTransportAndProducesNoResultOrRetry() = runBlocking {
        val entered = CountDownLatch(1); val calls = AtomicInteger()
        val connection = object : Connection() {
            override fun getInputStream(): InputStream {
                entered.countDown(); disconnected.await(5, TimeUnit.SECONDS); throw IOException("cancelled")
            }
        }
        val job = launch(Dispatchers.Default) { AndroidGeminiTransport { calls.incrementAndGet(); connection }.post(endpoint, "synthetic", "{}"); fail("Cancelled call returned") }
        assertTrue(entered.await(3, TimeUnit.SECONDS)); job.cancelAndJoin()
        assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS)); assertEquals(1, calls.get())
    }
    @Test fun providerCancellationPropagatesWithoutOfflineOrServerFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val calls = AtomicInteger()
        val service = GeminiCoachService("synthetic", "gemini-3.5-flash-lite", GeminiTransport { _, _, _ ->
            calls.incrementAndGet(); entered.complete(Unit); awaitCancellation()
        })
        val job = launch { service.request(planning()); fail("Cancelled service returned") }
        entered.await(); job.cancelAndJoin(); assertEquals(1, calls.get())
    }
}
