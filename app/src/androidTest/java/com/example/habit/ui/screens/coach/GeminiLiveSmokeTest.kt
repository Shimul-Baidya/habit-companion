package com.example.habit.ui.screens.coach

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.BuildConfig
import com.example.habit.coach.*
import com.example.habit.data.HabitHistoryRepository
import com.example.habit.data.HabitDraft
import com.example.habit.data.controls.DataGate
import com.example.habit.data.local.HabitDatabase
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.HabitFormDraft
import com.example.habit.ui.screens.newhabit.NewHabitScreen
import com.example.habit.ui.screens.newhabit.NewHabitViewModel
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in only: exactly one real Gemini call per test, synthetic in-memory local data. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GeminiLiveSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.of(2026, 10, 7)
    private val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val enabled = MutableStateFlow(true)
    private val dates = object : DateProvider {
        override val dates = MutableStateFlow(day)
        override fun today() = day
        override fun refresh() {}
    }
    private var db: HabitDatabase? = null
    private lateinit var history: HabitHistoryRepository
    private lateinit var strategies: StrategyRepository
    private lateinit var actions: CoachActionRepository
    private lateinit var catalog: StrategyCatalog
    private val store = ViewModelStore()
    private val calls = AtomicInteger()
    private var submitted: CoachRequest? = null
    private val service = object : CoachService {
        override suspend fun request(request: CoachRequest): CoachServiceResult {
            check(calls.incrementAndGet() == 1) { "Live smoke must never redispatch" }
            submitted = request
            return GeminiCoachService(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL, GeminiTransport { url, key, body ->
                val mode = if (request.context is CoachContext.Planning) "planning" else "existing"
                // These are synthetic test inputs; the credential is a header, never in this capture.
                File(context.cacheDir, "chunk12-qa").apply { mkdirs() }.resolve("live-$mode-request.json").writeText(body)
                AndroidGeminiTransport().post(url, key, body)
            }).request(request)
        }
    }
    @Before fun setup() {
        Assume.assumeTrue("Live requests require explicit opt-in", InstrumentationRegistry.getArguments().getString("coachLive") == "true")
        check(BuildConfig.GEMINI_API_KEY.isNotBlank()) { "Local Gemini configuration is missing" }
        val database = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build(); db = database
        val gate = DataGate()
        history = HabitHistoryRepository(database, clock, gate)
        strategies = StrategyRepository { context.assets.open("coach_cards.json").use { it.readBytes() } }
        catalog = runBlocking { (strategies.load() as CatalogResult.Ready).catalog }
        actions = CoachActionRepository(database, history, gate, clock, { catalog }, { enabled.value })
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db?.close() }
    private fun model(id: Long?, saved: SavedStateHandle = SavedStateHandle(), form: NewHabitViewModel? = null): CoachViewModel {
        lateinit var vm: CoachViewModel
        compose.runOnIdle {
            vm = CoachViewModel(id, history.records, enabled, dates, strategies, service, actions, saved, form, clock)
            store.put("live-${System.nanoTime()}", vm)
        }
        return vm
    }
    private fun waitForResponse(vm: CoachViewModel) {
        compose.waitUntil(35_000) { !vm.state.value.loading && (vm.state.value.response != null || vm.state.value.failure != null) }
        assertEquals("Live provider failed: ${vm.state.value.failure}", null, vm.state.value.failure)
        assertNotNull(vm.state.value.response); assertEquals(3, vm.state.value.response!!.value.suggestions.size)
        assertEquals(1, calls.get())
        assertEquals(submitted!!.strategies.map { it.card.id }.toSet(), vm.state.value.response!!.value.suggestions.map { it.strategyId }.toSet())
    }
    private fun capture(name: String) {
        File(context.cacheDir, "chunk12-qa").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun frame(dark: Boolean = false, small: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) { HabitTheme(darkTheme = dark) { Box(Modifier) { content() } } }
        } }
    }
    @Test fun liveBinaryPlanningValidatesAppliesToTheSameUnsavedFormAndSendsOnce() {
        lateinit var form: NewHabitViewModel
        compose.runOnIdle { form = NewHabitViewModel(history, SavedStateHandle(), enabled, clock).also { store.put("form", it) } }
        compose.waitUntil(5000) { !form.state.value.loading && form.state.value.coachReady }
        compose.runOnIdle { form.change(HabitFormDraft(name = "Demo reading")) }
        val before = form.state.value.draft
        val vm = model(null, form = form)
        var returned by mutableStateOf(false)
        frame { if (returned) NewHabitScreen({}, {}, viewModel = form) else CoachScreen(vm, {}, { returned = true }) }
        waitForResponse(vm)
        compose.onNodeWithTag("coach-transmission").assertIsDisplayed(); capture("live-planning-light")
        val index = vm.state.value.response!!.value.suggestions.indexOfFirst { it.action != CoachAction.AdviceOnly }.coerceAtLeast(0)
        compose.runOnIdle { vm.apply(index) { returned = true } }
        compose.waitUntil(5000) { returned && vm.state.value.receipt != null && !vm.state.value.busy }
        assertNull(form.state.value.savedHabitId); assertTrue(runBlocking { history.records.first() }.isEmpty())
        val receipt = vm.state.value.receipt!!
        if (receipt.canUndo(clock.millis())) {
            compose.runOnIdle { assertEquals(CoachUndoResult.UNDONE, form.undoCoach(receipt.id)) }
            assertEquals(before, form.state.value.draft)
        }
        assertEquals(1, calls.get()); capture("live-returned-form")
    }
    @Test fun liveFreshKarateStarterHasHonestRestDayFactsAndNoImplicitApply() {
        val id = runBlocking { history.create(HabitDraft("Synthetic practice", HabitSettings(HabitSchedule.Custom(setOf(java.time.DayOfWeek.MONDAY))))) }
        val vm = model(id)
        frame(dark = true, small = true) { CoachScreen(vm, {}) }
        compose.waitUntil(5000) { vm.state.value.failure == CoachFailure.InsufficientContext }
        assertEquals(0, calls.get())
        compose.onNodeWithTag("coach-list").performScrollToNode(hasText("Help me get started"))
        compose.onNodeWithText("Help me get started").performClick()
        waitForResponse(vm)
        assertTrue(submitted!!.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
        assertEquals(listOf(ReadingFact.NO_SETTLED_HISTORY), vm.state.value.response!!.value.reading.facts)
        assertNull(vm.state.value.receipt)
        assertTrue(runBlocking { history.record(id)!!.completions }.isEmpty())
        capture("live-fresh-starter-dark-small")
    }
    @Test fun liveSpecificReadingQuestionShowsGeneratedAdviceInConversationAndSendsOnce() {
        val id = runBlocking { history.create(HabitDraft("Synthetic reading", HabitSettings(HabitSchedule.Daily))) }
        val vm = model(id, SavedStateHandle(mapOf("entered" to true)))
        frame { CoachScreen(vm, {}) }
        compose.waitUntil(5000) { vm.state.value.canRequest }
        val question = "My phone distracts me while reading. How can I concentrate without reducing my reading goal?"
        compose.runOnIdle { vm.question(question); vm.send(); vm.send() }
        waitForResponse(vm)
        compose.waitUntil(5000) { vm.state.value.messages.any { !it.user } }
        val reply = vm.state.value.messages.last { !it.user }.text
        assertEquals(vm.state.value.response!!.conversationText(question), reply)
        assertFalse(reply.contains("required occurrences are still pending"))
        assertTrue(submitted!!.strategies.all { it.applicability == Applicability.QUESTION_OPTION })
        assertNull(vm.state.value.receipt)
        capture("live-specific-reading-conversation")
        // Semantics/quality of the captured advice is reviewed by a human, not a keyword oracle.
    }
    @Test fun liveWeeklyQuantityPersistsValidatedCacheAppliesUndoesAndReentrySendsNothing() {
        val database = db!!
        val oldClock = Clock.fixed(day.minusDays(14).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val id = runBlocking {
            val key = HabitHistoryRepository(database, oldClock).create(HabitDraft("Local synthetic name excluded from request",
                HabitSettings(HabitSchedule.Weekly(3), TrackingMode.Quantity(BigDecimal("5"), "pages")), "book", "purple"))
            history.correct(key, day.minusDays(1), CompletionValue.Quantity(BigDecimal("2"), "pages")); key
        }
        val original = runBlocking { history.record(id)!! }
        val vm = model(id)
        frame(dark = true, small = true) { CoachScreen(vm, {}) }
        waitForResponse(vm); capture("live-weekly-quantity-dark-small")
        val index = vm.state.value.response!!.value.suggestions.indexOfFirst { it.action != CoachAction.AdviceOnly }.coerceAtLeast(0)
        compose.runOnIdle { vm.apply(index) }
        compose.waitUntil(5000) { vm.state.value.receipt != null && !vm.state.value.busy }
        assertEquals(original.completions, runBlocking { history.record(id)!!.completions })
        if (vm.state.value.receipt!!.canUndo(clock.millis())) {
            compose.runOnIdle { vm.undo() }
            compose.waitUntil(5000) { vm.state.value.receipt?.status == "UNDONE" && !vm.state.value.busy }
        }
        val restored = model(id)
        compose.waitUntil(5000) { restored.state.value.response != null }
        assertNotNull(restored.state.value.cachedAt); assertEquals(1, calls.get())
        assertFalse(CoachJson.payload(submitted!!).contains(original.habit.name))
        assertEquals(1, runBlocking { database.coachDao().allMessages().count { it.role == "COACH" } })
        assertTrue(runBlocking { database.coachDao().allCaches().single().response.isNotEmpty() })
    }
}
