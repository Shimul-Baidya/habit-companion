package com.example.habit.ui.screens.newhabit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class NewHabitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var count = 0
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private class Forms : HabitFormDataSource {
        val data = MutableStateFlow<List<HabitRecord>>(emptyList())
        val fail = MutableStateFlow(false)
        override val records = combine(data, fail) { values, failed -> if (failed) throw IOException(); values }
        var writes = 0
        var sent: HabitDraft? = null
        var original: HabitDraft? = null
        var savedId: Long? = null
        var failure: Exception? = null
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun saveForm(id: Long?, draft: HabitDraft, original: HabitDraft?, allowDuplicate: Boolean): Long {
            writes++; hold?.await(); failure?.let { throw it }; sent = draft; this.original = original; savedId = id
            return id ?: 99L
        }
    }
    private fun model(forms: Forms, handle: SavedStateHandle = SavedStateHandle(), coach: Flow<Boolean> = MutableStateFlow(true)) =
        NewHabitViewModel(forms, handle, coach).also { store.put("form-${count++}", it) }
    private fun record(name: String = "Read", id: Long = 1): HabitRecord {
        val date = LocalDate.of(2026, 10, 5)
        return HabitRecord(HabitEntity(id = id, name = name, iconKey = "book", colorKey = "purple", createdEpochDay = date.toEpochDay()),
            listOf(ScheduleHistoryEntity.from(id, date, HabitSchedule.Daily)), listOf(TrackingHistoryEntity.from(id, date, TrackingMode.Binary)), emptyList())
    }
    private fun copy(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun enteringAndLeavingCleanFormNeverCreatesHabit() = runTest(dispatcher) {
        val forms = Forms(); val vm = model(forms); runCurrent()
        assertTrue(vm.requestBack())
        assertEquals(0, forms.writes)
        assertFalse(vm.state.value.canSave)
    }
    @Test fun saveTrimsNameAndRepeatedSubmitStartsOneWrite() = runTest(dispatcher) {
        val forms = Forms().apply { hold = CompletableDeferred() }; val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "  Read  "))
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, forms.writes); assertTrue(vm.state.value.saving)
        vm.change(vm.state.value.draft.copy(name = "Wrong later input"))
        assertEquals("  Read  ", vm.state.value.draft.name)
        forms.hold!!.complete(Unit); runCurrent()
        assertEquals("Read", forms.sent!!.name)
        assertEquals(99L, vm.state.value.savedHabitId)
        vm.save(); runCurrent(); assertEquals(1, forms.writes)
    }
    @Test fun saveFailureKeepsDraftAndRetrySucceeds() = runTest(dispatcher) {
        val forms = Forms().apply { failure = IOException() }; val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Read", tracking = "QUANTITY", target = "5", unit = "pages"))
        vm.save(); runCurrent()
        assertEquals(FormSaveError.WRITE_FAILED, vm.state.value.saveError)
        assertFalse(vm.state.value.saving); assertTrue(vm.state.value.dirty)
        assertEquals("5", vm.state.value.draft.target)
        forms.failure = null; vm.save(); runCurrent()
        assertEquals(TrackingMode.Quantity(BigDecimal("5"), "pages"), forms.sent!!.settings.tracking)
    }
    @Test fun invalidKeyboardSubmitShowsValidationAndDoesNotWrite() = runTest(dispatcher) {
        val forms = Forms(); val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Read", frequency = "CUSTOM", weekdayMask = 0))
        vm.save(); runCurrent()
        assertTrue(vm.state.value.showValidation); assertTrue(vm.state.value.validation.schedule)
        assertEquals(0, forms.writes)
    }
    @Test fun duplicateNameWarnsCaseInsensitivelyAndNeedsExplicitConfirmation() = runTest(dispatcher) {
        val forms = Forms().apply { data.value = listOf(record()) }; val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = " read ")); vm.save(); runCurrent()
        assertTrue(vm.state.value.confirmDuplicate); assertEquals(0, forms.writes)
        vm.dismissDuplicate(); assertFalse(vm.state.value.confirmDuplicate)
        vm.save(allowDuplicate = true); runCurrent(); assertEquals(1, forms.writes)
    }
    @Test fun concurrentDuplicateDetectedByRepositoryAlsoShowsConfirmation() = runTest(dispatcher) {
        val forms = Forms().apply { failure = DuplicateHabitName() }; val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Read")); vm.save(); runCurrent()
        assertTrue(vm.state.value.confirmDuplicate); assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.savedHabitId)
    }
    @Test fun dirtyBackConfirmsAndCancelKeepsDraftWithoutWriting() = runTest(dispatcher) {
        val forms = Forms(); val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Read"))
        assertFalse(vm.requestBack()); assertTrue(vm.state.value.confirmDiscard)
        vm.dismissDiscard(); assertEquals("Read", vm.state.value.draft.name)
        assertEquals(0, forms.writes)
    }
    @Test fun recreatedSavedStateRestoresEveryDraftFieldAndPlanningIdentity() = runTest(dispatcher) {
        val handle = SavedStateHandle(); val forms = Forms(); val vm = model(forms, handle); runCurrent()
        vm.change(HabitFormDraft(name = "Read", iconKey = "book", colorKey = "purple", frequency = "WEEKLY", quota = "2", weekdayMask = 5,
            tracking = "QUANTITY", target = "5", unit = "pages", cue = "one page", anchor = "coffee", planNote = "start small"))
        val entry = vm.coachEntry() as FormCoachEntry.Planning
        val recreated = model(forms, copy(handle)); runCurrent()
        assertEquals(vm.state.value.draft, recreated.state.value.draft)
        assertTrue(recreated.state.value.dirty)
        assertEquals(entry.request.token, (recreated.coachEntry() as FormCoachEntry.Planning).request.token)
        assertTrue(recreated.applyPlanning(DraftPlanningResult(entry.request.token, DraftPlanningChange.Schedule(HabitSchedule.Daily))))
        assertEquals("DAILY", recreated.state.value.draft.frequency)
        assertEquals("Read", recreated.state.value.draft.name)
        assertEquals(0, forms.writes)
    }
    @Test fun wrongPlanningIdentityAndDisabledCoachCannotApplyChanges() = runTest(dispatcher) {
        val enabled = MutableStateFlow(true); val forms = Forms(); val vm = model(forms, coach = enabled); runCurrent()
        val token = (vm.coachEntry() as FormCoachEntry.Planning).request.token
        assertFalse(vm.applyPlanning(DraftPlanningResult("other draft", DraftPlanningChange.Schedule(HabitSchedule.Weekly(2)))))
        enabled.value = false; runCurrent()
        assertNull(vm.coachEntry())
        assertFalse(vm.applyPlanning(DraftPlanningResult(token, DraftPlanningChange.Schedule(HabitSchedule.Weekly(2)))))
        assertEquals("DAILY", vm.state.value.draft.frequency)
    }
    @Test fun editLoadsPendingValuesAndSavesSameIdWithoutInserting() = runTest(dispatcher) {
        val item = record().let { it.copy(tracking = it.tracking + TrackingHistoryEntity.from(1, LocalDate.of(2026, 10, 6), TrackingMode.Quantity(BigDecimal("5"), "pages"))) }
        val forms = Forms().apply { data.value = listOf(item) }
        val vm = model(forms, SavedStateHandle(mapOf(NewHabitViewModel.ARG_HABIT_ID to 1L))); runCurrent()
        assertEquals("QUANTITY", vm.state.value.draft.tracking); assertEquals("5", vm.state.value.draft.target)
        assertFalse(vm.state.value.dirty); assertFalse(vm.state.value.duplicateName)
        vm.change(vm.state.value.draft.copy(name = "Read more")); vm.save(); runCurrent()
        assertEquals(1L, forms.savedId); assertEquals(1L, vm.state.value.savedHabitId)
        assertEquals("Read", forms.original!!.name)
        assertTrue(vm.coachEntry() is FormCoachEntry.Existing)
    }
    @Test fun restoredEditKeepsUnsavedInputWhenObservationRestarts() = runTest(dispatcher) {
        val forms = Forms().apply { data.value = listOf(record()) }
        val handle = SavedStateHandle(mapOf(NewHabitViewModel.ARG_HABIT_ID to 1L))
        val vm = model(forms, handle); runCurrent(); vm.change(vm.state.value.draft.copy(name = "Unsaved change"))
        val recreated = model(forms, copy(handle)); runCurrent()
        assertEquals("Unsaved change", recreated.state.value.draft.name)
        assertTrue(recreated.state.value.dirty)
        recreated.save(); runCurrent(); assertEquals("Read", forms.original!!.name)
    }
    @Test fun missingOrArchivedHabitCannotSave() = runTest(dispatcher) {
        val forms = Forms(); val vm = model(forms, SavedStateHandle(mapOf(NewHabitViewModel.ARG_HABIT_ID to 1L))); runCurrent()
        assertEquals(FormLoadError.UNAVAILABLE, vm.state.value.loadError)
        vm.save(); runCurrent(); assertEquals(0, forms.writes)
    }
    @Test fun readFailureRetainsNewDraftAndRetryRecovers() = runTest(dispatcher) {
        val forms = Forms().apply { fail.value = true }; val vm = model(forms); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Unsaved"))
        assertEquals(FormLoadError.READ_FAILED, vm.state.value.loadError); assertFalse(vm.state.value.canSave)
        forms.fail.value = false; vm.retryLoad(); runCurrent()
        assertEquals("Unsaved", vm.state.value.draft.name); assertTrue(vm.state.value.canSave)
    }
    @Test fun conflictRetainsInputUntilConfirmedReload() = runTest(dispatcher) {
        val forms = Forms().apply { data.value = listOf(record()); failure = HabitFormConflict() }
        val vm = model(forms, SavedStateHandle(mapOf(NewHabitViewModel.ARG_HABIT_ID to 1L))); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "My edit")); vm.save(); runCurrent()
        assertEquals(FormSaveError.CONFLICT, vm.state.value.saveError); assertEquals("My edit", vm.state.value.draft.name)
        forms.data.value = listOf(record("Latest")); runCurrent()
        vm.requestReload(); assertTrue(vm.state.value.confirmReload)
        vm.reload(); runCurrent(); assertEquals("Latest", vm.state.value.draft.name); assertFalse(vm.state.value.dirty)
    }
    @Test fun successfulSaveSurvivesRestoredStateAndCannotRepeatInsert() = runTest(dispatcher) {
        val forms = Forms(); val handle = SavedStateHandle(); val vm = model(forms, handle); runCurrent()
        vm.change(vm.state.value.draft.copy(name = "Read")); vm.save(); runCurrent()
        val recreated = model(forms, copy(handle)); runCurrent(); recreated.save(); runCurrent()
        assertEquals(99L, recreated.state.value.savedHabitId); assertEquals(1, forms.writes)
    }
}
