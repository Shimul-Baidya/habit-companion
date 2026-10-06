package com.example.habit.ui.screens.coach

import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.coach.*
import com.example.habit.data.*
import com.example.habit.data.controls.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.components.*
import com.example.habit.ui.navigation.*
import com.example.habit.ui.screens.home.*
import com.example.habit.ui.screens.detail.*
import com.example.habit.ui.screens.newhabit.*
import com.example.habit.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.math.BigDecimal
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class CoachWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val day = LocalDate.of(2026, 10, 7)
    private var now = day.atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = Clock.fixed(now, zone)
    }
    private val dates = object : DateProvider {
        override val dates = MutableStateFlow(day)
        override fun today() = dates.value
        override fun refresh() {}
    }
    private val enabled = MutableStateFlow(true)
    private val prefs = MutableStateFlow(SettingsRepository.Configuration(coachEnabled = true))
    private lateinit var db: HabitDatabase
    private lateinit var history: HabitHistoryRepository
    private lateinit var actions: CoachActionRepository
    private lateinit var strategies: StrategyRepository
    private lateinit var catalog: StrategyCatalog
    private val store = ViewModelStore()
    private val calls = AtomicInteger()
    private val requests = mutableListOf<CoachRequest>()
    private var failure: CoachFailure? = null
    private var invalid = false
    private var suspended = false
    private var cancelled = false
    private var connected = true
    private val service = object : CoachService {
        override suspend fun request(request: CoachRequest): CoachServiceResult {
            calls.incrementAndGet(); requests.add(request)
            if (suspended) {
                try { awaitCancellation() } finally { cancelled = true }
            }
            return failure?.let { CoachServiceResult.Failure(it) }
                ?: CoachServiceResult.RawResponse(if (invalid) "{}" else response(request))
        }
    }
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build()
        val gate = DataGate()
        history = HabitHistoryRepository(db, clock, gate)
        strategies = StrategyRepository { context.assets.open("coach_cards.json").use { it.readBytes() } }
        catalog = runBlocking { (strategies.load() as CatalogResult.Ready).catalog }
        actions = CoachActionRepository(db, history, gate, clock, { catalog }, { enabled.value })
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun response(request: CoachRequest): String {
        val summary = (request.context as? CoachContext.Existing)?.summary
        val fact = when {
            summary == null -> "PLANNING_DRAFT"
            summary.missed > 0 -> "RECENT_MISSES"
            summary.completed > 0 -> "RECENT_COMPLETIONS"
            else -> "OPEN_EXPECTATIONS"
        }
        return json("reading" to json("facts" to JSONArray(listOf(fact)), "possibleBarrier" to "UNCLEAR_CUE"),
            "suggestions" to JSONArray(request.strategies.mapIndexed { index, strategy ->
                json("strategyId" to strategy.card.id, "title" to "Try a clear plan ${index + 1}", "advice" to "If it fits, prepare a small step before starting.",
                    "action" to json("type" to "PLAN", "note" to "Applied plan ${index + 1}"))
            })).toString()
    }
    private fun habit(name: String = "Read 20 pages", schedule: HabitSchedule = HabitSchedule.Daily, mode: TrackingMode = TrackingMode.Binary): Long = runBlocking {
        val old = HabitHistoryRepository(db, Clock.fixed(day.minusDays(10).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
        val id = old.create(HabitDraft(name, HabitSettings(schedule, mode), "book", "purple"))
        for (offset in 0..5) {
            val date = day.minusDays(10).plusDays(offset.toLong())
            if (schedule !is HabitSchedule.Custom || date.dayOfWeek in schedule.weekdays) history.correct(id, date,
                if (mode is TrackingMode.Quantity) CompletionValue.Quantity(mode.target, mode.unit) else CompletionValue.Binary(true))
        }
        id
    }
    private fun model(id: Long?, saved: SavedStateHandle = SavedStateHandle(), form: NewHabitViewModel? = null, timeout: Long = 30_000, records: Flow<List<HabitRecord>> = history.records): CoachViewModel {
        lateinit var vm: CoachViewModel
        compose.runOnIdle { vm = CoachViewModel(id, records, enabled, dates, strategies, service, actions, saved, form, clock, { connected }, timeout)
            store.put("coach-${System.nanoTime()}", vm) }
        return vm
    }
    private fun ready(vm: CoachViewModel) { compose.waitUntil(8000) { vm.state.value.ready && !vm.state.value.loading && (vm.state.value.response != null || vm.state.value.failure != null) } }
    private fun screen(vm: CoachViewModel, dark: Boolean = false, small: Boolean = false) {
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) { HabitTheme(darkTheme = dark) { CoachScreen(vm, {}) } }
        } }; ready(vm)
    }
    private fun scroll(tag: String) { compose.onNodeWithTag("coach-list").performScrollToNode(hasTestTag(tag)) }
    private fun back() { compose.waitForIdle(); Espresso.pressBack(); compose.waitForIdle() }
    private fun pageBack() {
        val ime = WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())
        if (ime) { back(); compose.waitUntil(5000) { !WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime()) } }
        back()
    }
    private fun capture(name: String, dialog: Boolean = false) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk11-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun parcel(saved: SavedStateHandle): SavedStateHandle {
        val bundle = Bundle()
        saved.keys().forEach { key -> when (val value = saved.get<Any?>(key)) {
            is String -> bundle.putString(key, value)
            is Long -> bundle.putLong(key, value)
            is Int -> bundle.putInt(key, value)
            is Boolean -> bundle.putBoolean(key, value)
            is ArrayList<*> -> @Suppress("UNCHECKED_CAST") bundle.putStringArrayList(key, value as ArrayList<String>)
        } }
        val bytes = Parcel.obtain().let { p -> p.writeBundle(bundle); val value = p.marshall(); p.recycle(); value }
        val restored = Parcel.obtain().let { p -> p.unmarshall(bytes, 0, bytes.size); p.setDataPosition(0); val value = p.readBundle(javaClass.classLoader)!!; p.recycle(); value }
        @Suppress("DEPRECATION") return SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
    }
    @Test fun threeCardsHaveActualTagsAndSourceBackDoesNotApply() {
        val id = habit(); val vm = model(id); screen(vm)
        assertEquals(1, calls.get()); assertEquals(3, vm.state.value.response!!.value.suggestions.size)
        for (index in 0..2) { scroll("coach-card-$index"); compose.onNodeWithTag("coach-card-$index").assertIsDisplayed() }
        scroll("coach-card-0"); compose.onNodeWithContentDescription("Coach privacy and strategy sources").performClick()
        compose.onAllNodesWithText("Supplied attribution:", substring = true).assertCountEquals(3); capture("strategy-source", true)
        back(); assertEquals("", runBlocking { history.record(id)!!.habit.planNote }); assertNull(vm.state.value.receipt)
        compose.activityRule.scenario.onActivity { WindowInsetsControllerCompat(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
        compose.waitUntil(5000) { !WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime()) }
        compose.onNodeWithTag("coach-list").performScrollToIndex(0)
        capture("suggestions-light-reference")
    }
    @Test fun wholeCardAndPillApplyOnceAndUndoPreservesCompletions() {
        val id = habit(mode = TrackingMode.Quantity(BigDecimal("20"), "pages")); val before = runBlocking { history.record(id)!!.completions }
        val vm = model(id); screen(vm)
        scroll("coach-card-0"); compose.onNodeWithTag("coach-card-0").performClick()
        compose.runOnIdle { vm.apply(0) }
        compose.waitUntil(5000) { vm.state.value.receipt != null && !vm.state.value.busy }
        assertEquals("Applied plan 1", runBlocking { history.record(id)!!.habit.planNote })
        scroll("coach-confirmation"); compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { vm.state.value.receipt?.status == "UNDONE" }
        assertEquals("", runBlocking { history.record(id)!!.habit.planNote }); assertEquals(before, runBlocking { history.record(id)!!.completions })
        compose.onNodeWithTag("coach-list").performScrollToIndex(0)
        compose.onNodeWithText("Change undone ·", substring = true).assertIsDisplayed()
        assertEquals(1, runBlocking { db.coachDao().allActions().size })
    }
    @Test fun restoredScreenUsesOriginalCacheAndDeadlineWithoutAnotherDispatch() {
        val id = habit(); val saved = SavedStateHandle(); val vm = model(id, saved); screen(vm)
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        val original = vm.state.value.receipt!!
        now = now.plusMillis(9000)
        val restored = model(id, parcel(saved)); ready(restored)
        assertEquals(1, calls.get()); assertEquals(original.appliedAt, restored.state.value.receipt?.appliedAt)
        compose.waitUntil(5000) { restored.state.value.cachedAt != null }
        now = now.plusMillis(1000); compose.waitUntil(5000) { !restored.state.value.receipt!!.canUndo(restored.state.value.now) }
        assertFalse(restored.state.value.receipt!!.canUndo(clock.millis()))
    }
    @Test fun offlinePrecheckSkipsSpinnerAndFailureQuestionRetryUsesCooldown() {
        connected = false; val vm = model(habit()); screen(vm)
        assertEquals(0, calls.get()); assertEquals(CoachFailure.Offline, vm.state.value.failure)
        compose.runOnIdle { vm.retry() }; assertEquals(0, calls.get())
        now = now.plusMillis(3000); connected = true
        compose.waitUntil(5000) { vm.state.value.canRequest }
        compose.runOnIdle { vm.question("How can I make the cue clear?"); vm.send(); vm.send() }
        compose.waitUntil(5000) { vm.state.value.response != null && !vm.state.value.loading }
        assertEquals(1, calls.get()); assertEquals("How can I make the cue clear?", requests.single().question)
        assertEquals("", vm.state.value.question)
    }
    @Test fun cachedFailureIsHonestAndFailureCopySurvivesParcelRestore() {
        val id = habit(); val saved = SavedStateHandle(); val vm = model(id, saved); screen(vm)
        failure = CoachFailure.RateLimited(60)
        compose.runOnIdle { vm.question("Try a simpler plan"); vm.send() }
        compose.waitUntil(5000) { vm.state.value.failure is CoachFailure.RateLimited }
        assertEquals("Try a simpler plan", vm.state.value.question)
        val restored = model(id, parcel(saved)); ready(restored)
        compose.waitUntil(5000) { restored.state.value.cachedAt != null }
        assertEquals(CoachFailure.RateLimited(60), restored.state.value.failure)
        assertEquals(2, calls.get()); assertFalse(restored.state.value.canRequest)
        compose.runOnIdle { restored.cached() }; assertNull(restored.state.value.failure)
    }
    @Test fun disableDuringRequestCancelsItAndDoesNotSaveOrApply() {
        suspended = true; val id = habit(); val vm = model(id)
        compose.setContent { HabitTheme { CoachScreen(vm, {}) } }
        compose.waitUntil(5000) { calls.get() == 1 }
        enabled.value = false
        compose.waitUntil(5000) { cancelled && !vm.state.value.loading }
        assertEquals(CoachFailure.Disabled, vm.state.value.failure)
        assertEquals(0, runBlocking { db.coachDao().allMessages().size })
        compose.runOnIdle { vm.apply(0) }; assertEquals("", runBlocking { history.record(id)!!.habit.planNote })
        enabled.value = true; compose.waitUntil(5000) { vm.state.value.enabled }; assertEquals(1, calls.get())
    }
    @Test fun timeoutMalformedAndServerStatesHaveNoSuggestionsOrWrites() {
        suspended = true; val id = habit(); val vm = model(id, timeout = 100)
        screen(vm); assertEquals(CoachFailure.Timeout, vm.state.value.failure)
        suspended = false; invalid = true; now = now.plusMillis(3000)
        compose.waitUntil(5000) { vm.state.value.canRequest }; compose.runOnIdle { vm.retry() }
        compose.waitUntil(5000) { vm.state.value.failure == CoachFailure.MalformedResponse }
        assertNull(vm.state.value.response); assertEquals(0, runBlocking { db.coachDao().allMessages().size })
        invalid = false; failure = CoachFailure.ServerError; now = now.plusMillis(6000)
        compose.waitUntil(5000) { vm.state.value.canRequest }; compose.runOnIdle { vm.retry() }
        compose.waitUntil(5000) { calls.get() == 3 && vm.state.value.failure == CoachFailure.ServerError }
    }
    @Test fun clearingHistoryInvalidatesCachedSuggestionsAndReceipt() {
        val id = habit(); val vm = model(id); screen(vm)
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        runBlocking { actions.clearHistory() }
        compose.waitUntil(5000) { vm.state.value.response == null && vm.state.value.receipt == null && vm.state.value.messages.isEmpty() }
        assertEquals("Applied plan 1", runBlocking { history.record(id)!!.habit.planNote })
        compose.runOnIdle { vm.undo(); vm.apply(0) }; assertEquals(1, calls.get())
    }
    @Test fun weeklyCustomAndQuantityContextsUseOnlySelectedMeasuredSummary() {
        val ids = listOf(habit("Weekly private", HabitSchedule.Weekly(3)),
            habit("Custom private", HabitSchedule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))),
            habit("Quantity private", mode = TrackingMode.Quantity(BigDecimal("20"), "pages")))
        ids.forEach { id -> val vm = model(id); ready(vm); assertTrue(vm.state.value.response != null) }
        assertEquals(3, calls.get())
        requests.forEach { request -> val payload = CoachJson.payload(request)
            assertFalse(payload.contains("private")); assertFalse(payload.contains("habitId")); assertFalse(payload.contains("conversation")) }
        assertEquals(HabitSchedule.Weekly(3), (requests[0].context as CoachContext.Existing).summary.schedule)
        assertTrue((requests[2].context as CoachContext.Existing).summary.tracking is TrackingMode.Quantity)
    }
    @Test fun freshNoHistoryShowsInsufficientContextWithoutPaddingStrategies() {
        val id = runBlocking { history.create(HabitDraft("Fresh", HabitSettings(HabitSchedule.Daily, TrackingMode.Binary))) }
        val vm = model(id); screen(vm)
        assertEquals(CoachFailure.InsufficientContext, vm.state.value.failure); assertEquals(0, calls.get())
        compose.onNodeWithText("Not enough context yet").assertIsDisplayed()
    }
    @Test fun strictStoredContextRejectsTamperingAndPreservesDatedReading() {
        val id = habit(); val request = (CoachRequestBuilder.existing(catalog, runBlocking { history.record(id)!!.toHistory() }, day, enabled = true) as RequestResult.Ready).request
        val payload = CoachJson.payload(request)
        val restored = CoachJson.storedRequest(payload, catalog.sha256, catalog)!!
        assertEquals(payload, CoachJson.payload(restored))
        assertNull(CoachJson.storedRequest(payload, "wrong", catalog))
        assertNull(CoachJson.storedRequest(JSONObject(payload).put("id", id).toString(), catalog.sha256, catalog))
        val changedCard = JSONObject(payload).apply { getJSONArray("strategies").getJSONObject(0).put("title", "fabricated") }
        assertNull(CoachJson.storedRequest(changedCard.toString(), catalog.sha256, catalog))
    }
    @Test fun nativeKeyboardSmallDarkKeepsInputSendAndCardsScrollable() {
        val vm = model(habit()); screen(vm, dark = true, small = true)
        capture("suggestions-dark-small")
        compose.onNodeWithTag("coach-question").performClick(); compose.onNodeWithTag("coach-question").performTextInput("How can I prepare the cue?")
        compose.waitUntil(5000) { WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime()) }
        compose.onNodeWithContentDescription("Send question").assertIsDisplayed().assertIsEnabled()
        capture("question-dark-small-keyboard")
        compose.onNodeWithContentDescription("Send question").performClick()
        compose.waitUntil(5000) { calls.get() == 2 && !vm.state.value.loading && vm.state.value.messages.any { it.user } }
        assertTrue(vm.state.value.messages.any { it.user })
        assertEquals(1, requests.count { it.question.isNotBlank() })
    }
    @Test fun appliedAndOfflineVariantsRetainContextAndReferenceVisuals() {
        val vm = model(habit()); screen(vm)
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        scroll("coach-confirmation"); capture("applied-light-reference")
        failure = CoachFailure.Offline; compose.runOnIdle { vm.question("Can I simplify this?"); vm.send() }
        compose.waitUntil(5000) { vm.state.value.failure == CoachFailure.Offline }
        compose.onNodeWithTag("coach-list").performScrollToIndex(0); capture("offline-light-reference")
        assertEquals("Read 20 pages", vm.state.value.name); assertNotNull(vm.state.value.response)
    }

    @Test fun localReadFailureRetainsSuggestionsAndQuestionAndRetryDispatchesNothing() {
        val fault = MutableStateFlow(false)
        val records = combine(history.records, fault) { list, fail -> if (fail) throw java.io.IOException("isolated read fault") else list }
        val vm = model(habit(), records = records); screen(vm)
        compose.runOnIdle { vm.question("Keep this question") }; fault.value = true
        compose.waitUntil(5000) { vm.state.value.localError }
        assertNotNull(vm.state.value.response); assertFalse(vm.state.value.canApply)
        compose.runOnIdle { vm.send() }; assertEquals(1, calls.get())
        fault.value = false; compose.runOnIdle { vm.retry() }
        compose.waitUntil(5000) { !vm.state.value.localError }
        assertEquals("Keep this question", vm.state.value.question); assertEquals(1, calls.get())
    }
    @Test fun failedApplyAndUndoRetainReceiptForRetryAndNeverClaimSuccess() {
        val id = habit(); val vm = model(id); screen(vm)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_coach_write BEFORE UPDATE ON habits BEGIN SELECT RAISE(ABORT, 'isolated failure'); END")
        compose.runOnIdle { vm.apply(0) }
        compose.waitUntil(5000) { vm.state.value.operationError != null && !vm.state.value.busy }
        assertNull(vm.state.value.receipt); assertEquals("", runBlocking { history.record(id)!!.habit.planNote })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_coach_write")
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_coach_write BEFORE UPDATE ON habits BEGIN SELECT RAISE(ABORT, 'isolated failure'); END")
        compose.runOnIdle { vm.undo() }
        compose.waitUntil(5000) { vm.state.value.operationError?.startsWith("Could not undo") == true && !vm.state.value.busy }
        assertEquals("APPLIED", vm.state.value.receipt!!.status)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_coach_write")
        compose.runOnIdle { vm.undo() }; compose.waitUntil(5000) { vm.state.value.receipt?.status == "UNDONE" }
        assertEquals(1, calls.get())
    }
    @Test fun disablingSuggestionsLeavesLocalUndoAvailable() {
        val id = habit(); val vm = model(id); screen(vm)
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        enabled.value = false; compose.waitUntil(5000) { !vm.state.value.enabled }
        assertFalse(vm.state.value.canApply)
        compose.runOnIdle { vm.undo() }; compose.waitUntil(5000) { vm.state.value.receipt?.status == "UNDONE" }
        assertEquals("", runBlocking { history.record(id)!!.habit.planNote })
    }
    @Test fun disabledEntryAndArchiveDuringPendingRequestNeverSendOrSaveLateData() {
        enabled.value = false
        val id = habit(); val blocked = model(id)
        compose.waitUntil(5000) { blocked.state.value.ready }
        compose.runOnIdle { blocked.send(); blocked.retry(); blocked.apply(0) }; assertEquals(0, calls.get())
        enabled.value = true; suspended = true
        compose.waitUntil(5000) { calls.get() == 1 }
        runBlocking { history.archive(id) }
        compose.waitUntil(5000) { blocked.state.value.missing && cancelled }
        assertEquals(0, runBlocking { db.coachDao().allMessages().size })
        assertNull(blocked.state.value.response)
    }
    @Test fun deletionWhileOpenRemovesContextAndCascadesLocalCoachData() {
        val id = habit(); val vm = model(id); screen(vm)
        runBlocking { history.delete(id) }
        compose.waitUntil(5000) { vm.state.value.missing }
        compose.runOnIdle { vm.apply(0); vm.fresh() }
        assertEquals(1, calls.get()); assertEquals(0, runBlocking { db.coachDao().allCaches().size })
    }
    @Test fun restoredThreadWaitsForCatalogAndShowsAtMostFiftyLocalMessages() {
        val id = habit(); val vm = model(id); ready(vm)
        runBlocking {
            val exchange = db.coachDao().allCaches().single { it.response.isNotEmpty() }
            repeat(60) { db.coachDao().message(CoachMessageEntity(habitId = id, role = "USER", text = "Saved question $it", createdAt = clock.millis(), exchangeId = exchange.id)) }
            db.coachDao().trim(id)
        }
        val restored = model(id); ready(restored)
        compose.waitUntil(5000) { restored.state.value.messages.size == 50 }
        assertEquals("Saved question 10", restored.state.value.messages.first().text)
        assertEquals(1, calls.get())
    }
    @Test fun planningQuantityPreviewPreservesDraftAndOriginalReceiptThroughParcel() {
        val saved = SavedStateHandle()
        lateinit var form: NewHabitViewModel
        compose.runOnIdle { form = NewHabitViewModel(history, saved, enabled, clock).also { store.put("planning-form", it) } }
        compose.waitUntil(5000) { !form.state.value.loading && form.state.value.coachEnabled }
        compose.runOnIdle { form.change(form.state.value.draft.copy(name = "Read 20 pages", tracking = "QUANTITY", target = "20", unit = "pages", frequency = "WEEKLY", quota = "3")) }
        val vm = model(null, form = form); screen(vm)
        capture("planning-light-reference")
        compose.runOnIdle { vm.apply(0) }; compose.waitUntil(5000) { vm.state.value.receipt != null }
        assertEquals(0, runBlocking { history.records.first().size })
        now = now.plusMillis(9000)
        lateinit var restored: NewHabitViewModel
        compose.runOnIdle { restored = NewHabitViewModel(history, parcel(saved), enabled, clock).also { store.put("restored-form", it) } }
        compose.waitUntil(5000) { !restored.state.value.loading && restored.state.value.coachEnabled }
        val receipt = restored.coachReceipt(restored.coachActionId.value!!)!!
        assertEquals(vm.state.value.receipt!!.appliedAt, receipt.appliedAt)
        assertTrue(receipt.canUndo(clock.millis()))
        compose.runOnIdle { assertEquals(CoachUndoResult.UNDONE, restored.undoCoach(receipt.id)) }
        assertEquals("20", restored.state.value.draft.target); assertEquals("3", restored.state.value.draft.quota)
        assertEquals("", restored.state.value.draft.planNote); assertEquals(0, runBlocking { history.records.first().size })
    }

    @Test fun removingRootSelectionRequiresDeliberateChoiceAndNeverRequestsAnotherHabit() {
        val first = habit("Selected private"); val other = habit("Remaining private")
        lateinit var selection: CoachSelectionViewModel
        val saved = SavedStateHandle()
        compose.runOnIdle { selection = CoachSelectionViewModel(history.records, enabled, saved).also { store.put("selection", it) } }
        compose.waitUntil(5000) { selection.state.value.ready && selection.state.value.habits.size == 2 }
        compose.runOnIdle { selection.choose(first) }
        runBlocking { history.archive(first) }
        compose.waitUntil(5000) { selection.state.value.habits.size == 1 && selection.state.value.selected == 0L }
        lateinit var restored: CoachSelectionViewModel
        compose.runOnIdle { restored = CoachSelectionViewModel(history.records, enabled, parcel(saved)).also { store.put("restored-selection", it) } }
        compose.waitUntil(5000) { restored.state.value.ready }
        assertEquals(0L, restored.state.value.selected); assertEquals(other, restored.state.value.habits.single().habit.id)
        assertEquals(0, calls.get())
    }
    @Test fun applyPillHasOwnFortyEightDpTargetAndCommitsSameValidatedAction() {
        val id = habit(); val vm = model(id); screen(vm)
        scroll("coach-card-1")
        val pill = compose.onNode(hasText("Apply") and hasAnyAncestor(hasTestTag("coach-card-1")))
        pill.assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil(5000) { vm.state.value.receipt != null && !vm.state.value.busy }
        assertEquals("Applied plan 2", runBlocking { history.record(id)!!.habit.planNote })
        compose.onNodeWithContentDescription("Coach privacy and strategy sources").performClick()
        compose.onNodeWithText("Habit names, local IDs", substring = true).assertExists()
        back(); assertEquals(1, runBlocking { db.coachDao().allActions().size })
    }

    @Test fun interruptedSavedInteractionRequiresExplicitRetryAndRetainsQuestion() {
        val saved = SavedStateHandle(mapOf("entered" to true, "inFlight" to true,
            "question" to "How can I make a simple cue?", "lastQuestion" to "How can I make a simple cue?"))
        val vm = model(habit(), saved); screen(vm)
        assertEquals(CoachFailure.ServerError, vm.state.value.failure)
        assertEquals(0, calls.get()); assertEquals("How can I make a simple cue?", vm.state.value.question)
        compose.runOnIdle { vm.retry() }; compose.waitUntil(5000) { vm.state.value.response != null }
        assertEquals(1, calls.get()); assertEquals("How can I make a simple cue?", requests.single().question)
    }

    @Test fun planningWaitsForOwningFormCoachSettingBeforeItsSingleEntryRequest() {
        lateinit var form: NewHabitViewModel
        val formEnabled = MutableStateFlow(false)
        compose.runOnIdle { form = NewHabitViewModel(history, SavedStateHandle(), formEnabled, clock).also { store.put("waiting-form", it) } }
        compose.waitUntil(5000) { !form.state.value.loading && form.state.value.coachReady }
        val vm = model(null, form = form)
        compose.waitUntil(5000) { vm.state.value.enabled }
        assertEquals(0, calls.get())
        formEnabled.value = true; ready(vm)
        assertEquals(1, calls.get()); assertNull(vm.state.value.failure)
    }

    @Test fun planningApplyDarkSmallFormShowsOriginalUndoAndExpiresWithoutSaving() {
        lateinit var form: NewHabitViewModel
        compose.runOnIdle { form = NewHabitViewModel(history, SavedStateHandle(), enabled, clock).also { store.put("dark-form", it) } }
        compose.waitUntil(5000) { !form.state.value.loading && form.state.value.coachEnabled }
        compose.runOnIdle { form.change(form.state.value.draft.copy(name = "Read 20 pages")) }
        val vm = model(null, form = form)
        var returned by mutableStateOf(false)
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) { HabitTheme(darkTheme = true) {
                if (returned) NewHabitScreen({}, {}, viewModel = form) else CoachScreen(vm, {}, onAppliedDraft = { returned = true })
            } }
        } }; ready(vm)
        capture("planning-dark-small")
        scroll("coach-card-0"); compose.onNodeWithTag("coach-card-0").performClick()
        compose.waitUntil(5000) { returned }
        compose.onNodeWithText("Undo").assertIsDisplayed()
        capture("planning-applied-form-dark-small")
        assertEquals(0, runBlocking { history.records.first().size })
        now = now.plusMillis(10_000)
        compose.waitUntil(5000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
        assertEquals("Applied plan 1", form.state.value.draft.planNote)
        assertEquals(0, runBlocking { history.records.first().size })
    }

    private fun graph(): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            val home = remember { HomeViewModel(HabitRepository(history), prefs, dates, SavedStateHandle()).also { store.put("home", it) } }
            HabitTheme {
                HabitNavigationGraph(nav, splash = { _, ready -> LaunchedEffect(Unit) { ready(false) } }, onboarding = {},
                    home = { add, coach, detail, tab -> HomeScreen(add, coach, detail, tab, home) },
                    form = { entry, back ->
                        val form: NewHabitViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory { initializer { NewHabitViewModel(history, createSavedStateHandle(), enabled, clock) } })
                        NewHabitScreen(back, back, onOpenCoach = { e -> nav.navigate(if (e is FormCoachEntry.Planning) Routes.COACH_PLANNING else Routes.coach((e as FormCoachEntry.Existing).habitId)) }, viewModel = form)
                    }, root = { tab, select ->
                        if (tab == HomeTab.COACH) {
                            val selection: CoachSelectionViewModel = viewModel(factory = viewModelFactory { initializer { CoachSelectionViewModel(history.records, enabled, createSavedStateHandle()) } })
                            CoachRoot(select, selection, modelFactory = { id -> viewModelFactory { initializer {
                                CoachViewModel(id, history.records, enabled, dates, strategies, service, actions, createSavedStateHandle(), clock = clock)
                            } } })
                        } else Column { Text(tab.name, Modifier.testTag("root-${tab.name}")); Button(onClick = { select(HomeTab.COACH) }) { Text("Open Coach tab") }; Button(onClick = { select(HomeTab.HOME) }) { Text("Open Home tab") }
                            runBlocking { history.records.first() }.firstOrNull()?.let { h -> Button(onClick = { nav.navigate(Routes.detail(h.habit.id)) }) { Text("Open detail") } } }
                    }, detail = { id, back ->
                        val detail = remember(id) { HabitDetailViewModel(id, HabitRepository(history), history, prefs, dates, SavedStateHandle()).also { store.put("detail-$id", it) } }
                        HabitDetailScreen(back, {}, detail, onOpenCoach = { nav.navigate(Routes.coach(id)) })
                    }, coach = { entry, back ->
                        val owner = if (entry.destination.route == Routes.COACH_PLANNING) remember(entry) { nav.getBackStackEntry(Routes.NEW_HABIT) } else null
                        val form: NewHabitViewModel? = owner?.let { viewModel(viewModelStoreOwner = it, factory = viewModelFactory { initializer { NewHabitViewModel(history, createSavedStateHandle(), enabled, clock) } }) }
                        val id = entry.arguments?.getLong(Routes.ARG_HABIT_ID)?.takeIf { it > 0 }
                        val vm: CoachViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory { initializer {
                            CoachViewModel(id, history.records, enabled, dates, strategies, service, actions, createSavedStateHandle(), form, clock)
                        } })
                        CoachScreen(vm, back)
                    })
            }
        }
        compose.waitUntil(5000) { nav.currentDestination?.route == Routes.HOME }
        return nav
    }
    @Test fun emptyHomePlanningApplyReturnsSameFormWithoutInsertionAndUndoWorks() {
        val nav = graph()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Not sure where to start?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Not sure where to start?").performClick()
        compose.waitUntil(8000) { nav.currentDestination?.route == Routes.COACH_PLANNING && calls.get() == 1 }
        scroll("coach-card-0"); compose.onNodeWithTag("coach-card-0").performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Routes.NEW_HABIT }
        assertEquals(0, runBlocking { history.records.first().size })
        compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
        compose.onNodeWithText("Draft change undone.").assertExists()
        pageBack(); compose.waitUntil(5000) { nav.currentDestination?.route == Routes.HOME }
        assertEquals(0, runBlocking { history.records.first().size })
    }
    @Test fun formPlanningBackPreservesDraftAndDirtyDiscardStillGuards() {
        val nav = graph(); compose.onNodeWithText("Add your first habit").performClick()
        compose.onNodeWithContentDescription("Habit name").performTextInput("Unsaved reading")
        back(); compose.waitUntil(5000) { !WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime()) }
        compose.onNodeWithText("Not sure how to make this stick?").performScrollTo().performClick()
        compose.waitUntil(8000) { nav.currentDestination?.route == Routes.COACH_PLANNING }
        pageBack(); compose.waitUntil(5000) { nav.currentDestination?.route == Routes.NEW_HABIT }; compose.onNodeWithContentDescription("Habit name").assertTextContains("Unsaved reading")
        pageBack(); compose.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, runBlocking { history.records.first().size })
    }
    @Test fun progressDetailCoachAndErrorBackReturnToActualCaller() {
        val id = habit(); failure = CoachFailure.ServerError
        val nav = graph(); compose.runOnIdle { nav.navigate(Routes.PROGRESS) }
        compose.onNodeWithText("Open detail").performClick()
        compose.onNodeWithText("Get a plan").performClick()
        compose.waitUntil(8000) { nav.currentDestination?.route == Routes.COACH_HABIT && calls.get() == 1 }
        compose.onNodeWithText("The Coach is unavailable").assertIsDisplayed()
        pageBack(); compose.waitUntil(5000) { nav.currentDestination?.route == Routes.HABIT_DETAIL }; assertEquals(Routes.HABIT_DETAIL, nav.currentDestination?.route)
        assertEquals(id, nav.currentBackStackEntry?.arguments?.getLong(Routes.ARG_HABIT_ID))
        pageBack(); compose.waitUntil(5000) { nav.currentDestination?.route == Routes.PROGRESS }; assertEquals(Routes.PROGRESS, nav.currentDestination?.route)
    }
    @Test fun multiHabitRootSheetBackAndSelectionSendOnlySelectedContextAndTabRetainsIt() {
        habit("First private", HabitSchedule.Weekly(3)); habit("Second private", mode = TrackingMode.Quantity(BigDecimal("20"), "pages"))
        val nav = graph(); compose.runOnIdle { nav.navigate(Routes.COACH) }
        compose.onNodeWithText("Choose a habit").assertExists(); assertEquals(0, calls.get())
        back(); assertEquals(Routes.COACH, nav.currentDestination?.route)
        compose.onNodeWithText("Choose habit").performClick(); compose.onNodeWithText("Second private").performClick()
        compose.waitUntil(8000) { calls.get() == 1 }
        assertTrue((requests.single().context as CoachContext.Existing).summary.tracking is TrackingMode.Quantity)
        assertFalse(CoachJson.payload(requests.single()).contains("private"))
        compose.onNodeWithText("Profile").performClick(); compose.onNodeWithText("Open Coach tab").performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Routes.COACH }
        compose.onNodeWithText("Second private").assertExists(); assertEquals(1, calls.get())
        compose.onNodeWithText("Change habit").performClick(); back(); assertEquals(Routes.COACH, nav.currentDestination?.route)
    }
}
