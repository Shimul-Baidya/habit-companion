package com.example.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import android.os.Bundle
import android.os.Parcel
import com.example.habit.ui.screens.newhabit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.coach.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.*

@RunWith(AndroidJUnit4::class)
class FormPersistenceTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private lateinit var db: HabitDatabase
    private fun repo(day: Long = 0) = HabitHistoryRepository(db, Clock.fixed(monday.plusDays(day).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun createsBinaryAndQuantityForEveryScheduleWithAppearanceAndPlan() = runBlocking {
        val schedules = listOf(HabitSchedule.Daily, HabitSchedule.Weekly(3), HabitSchedule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val modes = listOf(TrackingMode.Binary, TrackingMode.Quantity(BigDecimal("5"), "pages"))
        val repository = repo()
        for ((index, schedule) in schedules.withIndex()) for ((modeIndex, mode) in modes.withIndex()) {
            val id = repository.saveForm(null, HabitDraft("  Isolated $index $modeIndex  ", HabitSettings(schedule, mode), "book", "purple", "one page", "after coffee", "start small"), null)
            val record = repository.record(id)!!
            assertEquals("Isolated $index $modeIndex", record.habit.name)
            assertEquals(HabitSettings(schedule, mode), record.toHistory().settingsOn(monday))
            assertEquals("book", record.habit.iconKey); assertEquals("purple", record.habit.colorKey)
            assertEquals("one page", record.habit.cue); assertEquals("after coffee", record.habit.anchor)
            assertEquals("start small", record.habit.planNote)
        }
        assertEquals(6, db.historyDao().records().size)
    }
    @Test fun combinedEditKeepsIdAndHistoryAndUsesIndependentEffectiveDates() = runBlocking {
        val repository = repo()
        val id = repository.saveForm(null, HabitDraft("Isolated edit"), null)
        repository.logToday(id, monday, CompletionValue.Binary(true))
        val before = repository.record(id)!!.editableDraft()
        repository.saveForm(id, before.copy(name = "Edited", iconKey = "book", colorKey = "purple",
            settings = HabitSettings(HabitSchedule.Weekly(2), TrackingMode.Quantity(BigDecimal("5"), "pages"))), before)
        val record = repository.record(id)!!
        assertEquals(1, db.historyDao().records().size); assertEquals(id, record.habit.id)
        assertEquals("Edited", record.habit.name); assertEquals(1, record.completions.size)
        val history = record.toHistory()
        assertEquals(HabitSettings(HabitSchedule.Daily), history.settingsOn(monday))
        assertEquals(HabitSchedule.Daily, history.settingsOn(monday.plusDays(1))!!.schedule)
        assertEquals(TrackingMode.Quantity(BigDecimal("5"), "pages"), history.settingsOn(monday.plusDays(1))!!.tracking)
        assertEquals(HabitSchedule.Weekly(2), history.settingsOn(monday.plusDays(7))!!.schedule)
        assertEquals(1, HistoryCalculator.calculate(history, monday).bestStreak)
    }
    @Test fun customScheduleEditDoesNotReinterpretEarlierCompletion() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Isolated custom"))
        repository.logToday(id, monday, CompletionValue.Binary(true))
        val before = repository.record(id)!!.editableDraft()
        repository.saveForm(id, before.copy(settings = HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.WEDNESDAY)))), before)
        val history = repository.record(id)!!.toHistory()
        assertEquals(HabitSchedule.Daily, history.settingsOn(monday)!!.schedule)
        assertFalse(CompletionRules.isScheduled(history.settingsOn(monday.plusDays(1))!!.schedule, monday.plusDays(1)))
        assertEquals(1, HistoryCalculator.calculate(history, monday.plusDays(1)).currentStreak)
    }
    @Test fun duplicateCheckIsAtomicWithConcurrentSubmitsAndAllowsExplicitOverride() = runBlocking {
        val repository = repo()
        val results = listOf("Read", " read ").map { name -> async(Dispatchers.IO) {
            runCatching { repository.saveForm(null, HabitDraft(name), null) }
        } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess }); assertEquals(1, results.count { it.exceptionOrNull() is DuplicateHabitName })
        repository.saveForm(null, HabitDraft("READ"), null, allowDuplicate = true)
        val existing = db.historyDao().records()
        assertEquals(2, existing.size); assertNotEquals(existing[0].habit.id, existing[1].habit.id)
        existing.forEach { repository.archive(it.habit.id) }
        repository.saveForm(null, HabitDraft("Read"), null)
        assertEquals(1, db.habitDao().count()) // archived names do not block creation
    }
    @Test fun expectationFailureRollsBackMetadataAndAllOtherFields() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Before"))
        val before = repository.record(id)!!.editableDraft()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_form_tracking BEFORE INSERT ON tracking_history BEGIN SELECT RAISE(ABORT, 'isolated test'); END")
        try {
            repository.saveForm(id, before.copy(name = "After", colorKey = "purple", settings = HabitSettings(HabitSchedule.Weekly(2), TrackingMode.Quantity(BigDecimal("5"), "pages"))), before)
            fail("All writes must roll back")
        } catch (_: android.database.SQLException) { }
        assertEquals(before, repository.record(id)!!.editableDraft())
        assertEquals(1, repository.record(id)!!.schedules.size)
    }
    @Test fun laterUnrelatedEditsAndCompletionsSurviveFormSave() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Before"))
        val before = repository.record(id)!!.editableDraft()
        repository.updateMetadata(id, HabitMetadata("Before", "default", "primary", "new cue", "new anchor", "new plan", true, 900))
        repository.logToday(id, monday, CompletionValue.Binary(true))
        repository.saveForm(id, before.copy(name = "After"), before)
        val stored = repository.record(id)!!
        assertEquals("After", stored.habit.name); assertEquals("new cue", stored.habit.cue)
        assertEquals("new anchor", stored.habit.anchor); assertEquals("new plan", stored.habit.planNote)
        assertEquals(true, stored.habit.reminderEnabled); assertEquals(900, stored.habit.reminderMinute)
        assertEquals(1, stored.completions.size)
    }
    @Test fun concurrentChangesToSameFieldAreRejectedWithoutPartialWrite() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Before"))
        val before = repository.record(id)!!.editableDraft()
        repository.changeSettings(id, listOf(HabitSettingChange.Tracking(TrackingMode.Quantity(BigDecimal("5"), "pages"))))
        try {
            repository.saveForm(id, before.copy(name = "Wrong partial rename", settings = HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("10"), "pages"))), before)
            fail("Must reject conflicting edits")
        } catch (_: HabitFormConflict) { }
        assertEquals("Before", repository.record(id)!!.habit.name)
        assertEquals(BigDecimal("5"), (repository.record(id)!!.editableDraft().settings.tracking as TrackingMode.Quantity).target)
    }
    @Test fun reopeningPendingSettingsAndChangingModeKeepsEarlierQuantityFacts() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Read", HabitSettings(HabitSchedule.Weekly(2), TrackingMode.Quantity(BigDecimal("5"), "pages"))))
        repository.logToday(id, monday, CompletionValue.Quantity(BigDecimal("5"), "pages"))
        val before = repository.record(id)!!.editableDraft()
        repository.saveForm(id, before.copy(settings = HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), TrackingMode.Binary)), before)
        val pending = repository.record(id)!!
        assertEquals(HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)), TrackingMode.Binary), pending.editableDraft().settings)
        assertEquals(TrackingMode.Quantity(BigDecimal("5"), "pages"), pending.toHistory().settingsOn(monday)!!.tracking)
        assertEquals("5", pending.completions.single().quantityAmount)
        val next = repo(1)
        val pendingBefore = next.record(id)!!.editableDraft()
        next.saveForm(id, pendingBefore.copy(settings = HabitSettings(HabitSchedule.Weekly(1), TrackingMode.Binary)), pendingBefore)
        assertEquals(HabitSchedule.Weekly(2), next.record(id)!!.toHistory().settingsOn(monday.plusDays(6))!!.schedule)
        assertEquals(HabitSchedule.Weekly(1), next.record(id)!!.toHistory().settingsOn(monday.plusDays(7))!!.schedule)
    }
    @Test fun numericallyUnchangedTargetDoesNotAddAnExpectationRevision() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Read", HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("5.0"), "pages"))))
        val before = repository.record(id)!!.editableDraft()
        repository.saveForm(id, before.copy(settings = HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("5"), "pages"))), before)
        assertEquals(1, repository.record(id)!!.tracking.size)
    }
    @Test fun parcelRestorationKeepsTypedDraftAndThenPersistsThroughRealViewModel() = runBlocking {
        val stores = listOf(ViewModelStore(), ViewModelStore())
        try {
            val handle = SavedStateHandle()
            val first = withContext(Dispatchers.Main) { NewHabitViewModel(repo(), handle, MutableStateFlow(true)).also { stores[0].put("form", it) } }
            withTimeout(5000) { first.state.first { !it.loading } }
            withContext(Dispatchers.Main) { first.change(HabitFormDraft(name = "Restored", iconKey = "book", colorKey = "purple", frequency = "WEEKLY", quota = "2",
                tracking = "QUANTITY", target = "5", unit = "pages", cue = "one page", anchor = "coffee", planNote = "start small")) }
            val bundle = Bundle()
            handle.keys().forEach { key -> when (val value = handle.get<Any>(key)) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> fail("Draft must use saved-state-compatible primitives")
            } }
            val parcel = Parcel.obtain()
            val restored = try { parcel.writeBundle(bundle); parcel.setDataPosition(0); requireNotNull(parcel.readBundle(javaClass.classLoader)) } finally { parcel.recycle() }
            @Suppress("DEPRECATION")
            val restoredHandle = SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
            val second = withContext(Dispatchers.Main) { NewHabitViewModel(repo(), restoredHandle, MutableStateFlow(true)).also { stores[1].put("form", it) } }
            withTimeout(5000) { second.state.first { !it.loading } }
            assertEquals(first.state.value.draft, second.state.value.draft)
            assertEquals((first.coachEntry() as FormCoachEntry.Planning).request.token, (second.coachEntry() as FormCoachEntry.Planning).request.token)
            assertTrue(db.historyDao().records().isEmpty())
            withContext(Dispatchers.Main) { second.save(); second.save() }
            val saved = withTimeout(5000) { second.state.first { it.savedHabitId != null } }.savedHabitId!!
            assertEquals(1, db.historyDao().records().size)
            val record = repo().record(saved)!!
            assertEquals(HabitSchedule.Weekly(2), record.toHistory().settingsOn(monday)!!.schedule)
            assertEquals(TrackingMode.Quantity(BigDecimal("5"), "pages"), record.toHistory().settingsOn(monday)!!.tracking)
            assertEquals("coffee", record.habit.anchor)
        } finally { withContext(Dispatchers.Main) { stores.forEach { it.clear() } } }
    }

    @Test fun coachDraftReceiptParcelRestorationPreservesDeadlineAndNeverInsertsOnApplyOrUndo() = runBlocking {
        val catalog = CoachJson.catalog(InstrumentationRegistry.getInstrumentation().targetContext.assets.open("coach_cards.json").use { it.readBytes() })
        val stores = listOf(ViewModelStore(), ViewModelStore()); val enabled = MutableStateFlow(true)
        val fixed = Clock.fixed(monday.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        try {
            val handle = SavedStateHandle()
            val first = withContext(Dispatchers.Main) { NewHabitViewModel(repo(), handle, enabled, fixed).also { stores[0].put("form", it) } }
            withTimeout(5000) { first.state.first { !it.loading && it.coachEnabled } }
            withContext(Dispatchers.Main) { first.change(HabitFormDraft(name = "Draft", planNote = "Prior plan")) }
            val entry = first.coachEntry() as FormCoachEntry.Planning
            val request = (CoachRequestBuilder.planning(catalog, entry.request.draft, 0, enabled = true) as RequestResult.Ready).request
            val response = (CoachResponseValidator.validate(CoachResponse(CoachReading(listOf(ReadingFact.PLANNING_DRAFT), null), request.strategies.map {
                CoachSuggestion(it.card.id, "Small start", "If useful, plan a small start", CoachAction.NewHabitCount(1)) }), request, catalog) as ResponseResult.Valid).response
            val receipt = withContext(Dispatchers.Main) {
                assertTrue(first.beginCoach(entry.request.token, "form_exchange", request))
                requireNotNull(first.applyCoach(entry.request.token, "form_exchange", 0, request, response, catalog))
            }
            assertEquals("Start with 1 new habit.", first.state.value.draft.planNote); assertTrue(db.historyDao().records().isEmpty())
            val bundle = Bundle()
            handle.keys().forEach { key -> when (val value = handle.get<Any>(key)) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is ArrayList<*> -> { @Suppress("UNCHECKED_CAST") bundle.putStringArrayList(key, value as ArrayList<String>) }
                else -> fail("Only primitive saved values are allowed")
            } }
            val parcel = Parcel.obtain()
            val restored = try { parcel.writeBundle(bundle); parcel.setDataPosition(0); requireNotNull(parcel.readBundle(javaClass.classLoader)) } finally { parcel.recycle() }
            @Suppress("DEPRECATION") val secondHandle = SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
            val second = withContext(Dispatchers.Main) { NewHabitViewModel(repo(), secondHandle, enabled, Clock.offset(fixed, Duration.ofMillis(9999))).also { stores[1].put("form", it) } }
            withTimeout(5000) { second.state.first { !it.loading && it.coachEnabled } }
            withContext(Dispatchers.Main) {
                second.change(second.state.value.draft.copy(name = "Later name"))
                assertEquals(receipt, second.coachReceipt(receipt.id))
                assertEquals(CoachUndoResult.UNDONE, second.undoCoach(receipt.id))
            }
            assertEquals("Later name", second.state.value.draft.name); assertEquals("Prior plan", second.state.value.draft.planNote)
            assertTrue(db.historyDao().records().isEmpty())
            enabled.value = false; withTimeout(5000) { second.state.first { !it.coachEnabled } }
            withContext(Dispatchers.Main) { assertNull(second.applyCoach(entry.request.token, "form_exchange", 0, request, response, catalog)); second.save(); second.save() }
            val saved = withTimeout(5000) { second.state.first { it.savedHabitId != null } }.savedHabitId!!
            assertEquals(1, db.historyDao().records().size); assertEquals("Prior plan", repo().record(saved)!!.habit.planNote)
            withContext(Dispatchers.Main) { assertEquals(CoachUndoResult.UNAVAILABLE, second.undoCoach(receipt.id)) }
        } finally { withContext(Dispatchers.Main) { stores.forEach { it.clear() } } }
    }
    @Test fun deletedAndArchivedRecordsCannotBeResurrectedByEdit() = runBlocking {
        val repository = repo()
        val id = repository.create(HabitDraft("Read")); val before = repository.record(id)!!.editableDraft()
        repository.archive(id)
        try { repository.saveForm(id, before.copy(name = "After"), before); fail("Archived") } catch (_: IllegalArgumentException) { }
        repository.delete(id)
        try { repository.saveForm(id, before.copy(name = "After"), before); fail("Deleted") } catch (_: IllegalArgumentException) { }
        assertTrue(db.historyDao().records().isEmpty())
    }
}
