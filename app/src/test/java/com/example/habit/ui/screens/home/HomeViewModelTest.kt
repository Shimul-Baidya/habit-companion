package com.example.habit.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val monday = LocalDate.of(2026, 10, 5)
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Dates(date: LocalDate) : DateProvider {
        override val dates = MutableStateFlow(date)
        var actual = date
        var refreshes = 0
        override fun today() = actual
        override fun refresh() { refreshes++; dates.value = actual }
    }
    private class Source : HabitDataSource {
        val records = MutableStateFlow<List<HabitRecord>>(emptyList())
        val fail = MutableStateFlow(false)
        var writes = 0
        var writeFailure = false
        var hold: CompletableDeferred<Unit>? = null
        override fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek) = combine(records, fail) { data, failed ->
            if (failed) throw IOException("isolated test failure")
            HabitSnapshot.from(data, today, weekStart)
        }
        var lastQuantity: CompletionValue.Quantity? = null
        override suspend fun quantityToday(habit: HabitEntity, today: LocalDate, value: CompletionValue.Quantity?) {
            toggleToday(habit, today, value != null)
            lastQuantity = value
            records.value = records.value.map { record -> record.copy(completions =
                if (value == null) emptyList() else listOf(CompletionEntity(habit.id, today.toEpochDay(), completedAt = 1,
                    trackingMode = "QUANTITY", quantityAmount = value.amount.toPlainString(), quantityUnit = value.unit))) }
        }
        override suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean) {
            writes++
            hold?.await()
            if (writeFailure) throw IOException("isolated test write failure")
        }
    }
    private fun record(schedule: HabitSchedule = HabitSchedule.Daily): HabitRecord = HabitRecord(
        HabitEntity(id = 1, name = "Test", createdEpochDay = monday.toEpochDay()),
        listOf(ScheduleHistoryEntity.from(1, monday, schedule)),
        listOf(TrackingHistoryEntity.from(1, monday, TrackingMode.Binary)), emptyList())
    private fun model(source: Source, dates: Dates = Dates(monday), prefs: Flow<SettingsRepository.Configuration> =
        MutableStateFlow(SettingsRepository.Configuration())) = HomeViewModel(source, prefs, dates, SavedStateHandle()).also { store.put("home", it) }

    @Test fun loadingIsNotEmptyUntilSuccessfulRead() = runTest(dispatcher) {
        val vm = model(Source())
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.isEmpty)
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.readError)
    }

    @Test fun failureIsNotEmptyAndRetryRecoversInSameViewModel() = runTest(dispatcher) {
        val source = Source().apply { fail.value = true }
        val vm = model(source)
        runCurrent()
        assertTrue(vm.state.value.readError)
        assertFalse(vm.state.value.isEmpty)
        assertFalse(vm.state.value.canWrite)
        source.fail.value = false
        vm.retry(); runCurrent()
        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.readError)
    }

    @Test fun laterObservationFailureRetainsSnapshotAndDisablesWrites() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(record()) }
        val vm = model(source)
        runCurrent()
        val old = vm.state.value.snapshot
        source.fail.value = true; runCurrent()
        assertEquals(old, vm.state.value.snapshot)
        assertEquals(1, vm.state.value.allHabits.size)
        assertTrue(vm.state.value.readError)
        vm.toggle(vm.state.value.allHabits.single()); runCurrent()
        assertEquals(0, source.writes)
        source.fail.value = false; vm.retry(); runCurrent()
        assertTrue(vm.state.value.canWrite)
    }

    @Test fun preferenceReadFailureAlsoRequiresRetryRatherThanInventingEmptyData() = runTest(dispatcher) {
        var fail = true
        val preferences = flow { if (fail) throw IOException("preferences"); emit(SettingsRepository.Configuration(userName = "Owner")) }
        val vm = model(Source(), prefs = preferences)
        runCurrent()
        assertTrue(vm.state.value.readError)
        fail = false; vm.retry(); runCurrent()
        assertEquals("Owner", vm.state.value.userName)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test fun repeatedTapWhileWritePendingStartsOnlyOneWrite() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(record()); hold = CompletableDeferred() }
        val vm = model(source)
        runCurrent()
        val row = vm.state.value.allHabits.single()
        vm.toggle(row); vm.toggle(row); runCurrent()
        assertEquals(1, source.writes)
        assertEquals(setOf(1L), vm.state.value.writingIds)
        source.hold!!.complete(Unit); runCurrent()
        assertTrue(vm.state.value.writingIds.isEmpty())
    }

    @Test fun writeFailureDoesNotPretendCompletionOrEraseData() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(record()); writeFailure = true }
        val vm = model(source)
        runCurrent()
        vm.toggle(vm.state.value.allHabits.single()); runCurrent()
        assertTrue(vm.state.value.actionError)
        assertFalse(vm.state.value.allHabits.single().doneToday)
        assertEquals(1, vm.state.value.allHabits.size)
        assertTrue(vm.state.value.writingIds.isEmpty())
        vm.clearActionError()
        assertFalse(vm.state.value.actionError)
    }

    @Test fun midnightRaceRejectsStaleTapThenObservesNewDate() = runTest(dispatcher) {
        val dates = Dates(monday)
        val source = Source().apply { records.value = listOf(record()) }
        val vm = model(source, dates)
        runCurrent()
        dates.actual = monday.plusDays(1) // clock changed before state observation caught up
        vm.toggle(vm.state.value.allHabits.single()); runCurrent()
        assertEquals(0, source.writes)
        assertEquals(monday.plusDays(1), vm.state.value.today)
        assertEquals(CompletionTotals(0, 1, 1), vm.state.value.snapshot!!.lifetime)
    }

    @Test fun restDayIsNoDueStateAndCannotBeCompleted() = runTest(dispatcher) {
        val dates = Dates(monday.plusDays(1))
        val source = Source().apply { records.value = listOf(record(HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)))) }
        val vm = model(source, dates)
        runCurrent()
        assertTrue(vm.state.value.noDueToday)
        assertFalse(vm.state.value.allDone)
        assertFalse(vm.state.value.isEmpty)
        vm.toggle(vm.state.value.allHabits.single()); runCurrent()
        assertEquals(0, source.writes)
    }

    @Test fun movingDateBackwardPreservesExistingLaterRecordsAndRefreshesForwardAgain() = runTest(dispatcher) {
        val dates = Dates(monday)
        val source = Source().apply { records.value = listOf(record().copy(completions =
            listOf(CompletionEntity(1, monday.toEpochDay(), completedAt = 1)))) }
        val vm = model(source, dates)
        runCurrent()
        assertEquals(1L, vm.state.value.snapshot!!.lifetime.completed)
        dates.actual = monday.minusDays(1); dates.refresh(); runCurrent()
        assertFalse(vm.state.value.readError)
        assertTrue(vm.state.value.isEmpty)
        assertEquals(1, vm.state.value.snapshot!!.records.single().record.completions.size)
        dates.actual = monday; dates.refresh(); runCurrent()
        assertTrue(vm.state.value.allHabits.single().doneToday)
        assertEquals(1L, vm.state.value.snapshot!!.lifetime.completed)
    }

    @Test fun changingDisplayedWeekStartRebuildsBucketsAndKeepsHistoryTotals() = runTest(dispatcher) {
        val prefs = MutableStateFlow(SettingsRepository.Configuration())
        val source = Source().apply { records.value = listOf(record()) }
        val vm = model(source, prefs = prefs)
        runCurrent()
        val before = vm.state.value.snapshot!!
        prefs.value = prefs.value.copy(weekStart = DayOfWeek.SUNDAY); runCurrent()
        val after = vm.state.value.snapshot!!
        assertEquals(before.lifetime, after.lifetime)
        assertEquals(monday.minusDays(1), after.week.first().range.first)
    }
    private fun quantityRecord(schedule: HabitSchedule = HabitSchedule.Daily) = record(schedule).copy(
        tracking = listOf(TrackingHistoryEntity.from(1, monday, TrackingMode.Quantity(java.math.BigDecimal("10"), "pages"))))

    @Test fun quantityPartialAchievedAndClearUseRealObservedFacts() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(quantityRecord()) }
        val vm = model(source); runCurrent()
        vm.complete(vm.state.value.allHabits.single()); vm.changeAmount("2,5"); vm.saveQuantity(); runCurrent()
        assertNull(vm.state.value.quantity)
        assertEquals(0.25f, vm.state.value.allHabits.single().progressToday)
        assertFalse(vm.state.value.allHabits.single().doneToday)
        vm.complete(vm.state.value.allHabits.single()); assertEquals("2.5", vm.state.value.quantity!!.amount)
        vm.changeAmount("10"); vm.saveQuantity(); runCurrent()
        assertTrue(vm.state.value.allHabits.single().doneToday)
        vm.complete(vm.state.value.allHabits.single()); vm.saveQuantity(clear = true); runCurrent()
        assertNull(source.lastQuantity); assertFalse(vm.state.value.allHabits.single().doneToday)
        assertEquals(3, source.writes)
    }
    @Test fun quantityInvalidRepeatedTapFailureAndRetryRetainInput() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(quantityRecord()); writeFailure = true }
        val vm = model(source); runCurrent()
        vm.complete(vm.state.value.allHabits.single()); vm.changeAmount("-1"); vm.saveQuantity(); runCurrent()
        assertTrue(vm.state.value.quantity!!.invalid); assertEquals(0, source.writes)
        vm.changeAmount("4.25"); source.hold = CompletableDeferred()
        vm.saveQuantity(); vm.saveQuantity(); runCurrent()
        assertEquals(1, source.writes); assertTrue(vm.state.value.quantity!!.saving)
        vm.dismissQuantity(); assertNotNull(vm.state.value.quantity)
        source.hold!!.complete(Unit); runCurrent()
        assertTrue(vm.state.value.quantity!!.failed); assertEquals("4.25", vm.state.value.quantity!!.amount)
        source.writeFailure = false; vm.saveQuantity(); runCurrent()
        assertNull(vm.state.value.quantity); assertEquals(2, source.writes)
    }
    @Test fun quantitySavedDraftRestoresButDateChangeCannotBecomePastCorrection() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val source = Source().apply { records.value = listOf(quantityRecord()) }; val dates = Dates(monday)
        val vm = HomeViewModel(source, MutableStateFlow(SettingsRepository.Configuration()), dates, saved)
        store.put("first", vm); runCurrent()
        vm.complete(vm.state.value.allHabits.single()); vm.changeAmount("3.75")
        val restored = HomeViewModel(source, MutableStateFlow(SettingsRepository.Configuration()), dates, saved)
        store.put("restored", restored); runCurrent(); assertEquals("3.75", restored.state.value.quantity!!.amount)
        dates.actual = monday.plusDays(1); restored.saveQuantity(); runCurrent()
        assertEquals(0, source.writes); assertTrue(restored.state.value.quantity!!.stale)
        restored.dismissQuantity(); restored.complete(restored.state.value.allHabits.single())
        assertEquals(monday.plusDays(1), restored.state.value.quantity!!.date)
    }
    @Test fun quantityRestDayDeletionAndReadFailureCannotWrite() = runTest(dispatcher) {
        val source = Source().apply { records.value = listOf(quantityRecord(HabitSchedule.Custom(setOf(DayOfWeek.TUESDAY)))) }
        val vm = model(source); runCurrent(); vm.complete(vm.state.value.allHabits.single()); assertNull(vm.state.value.quantity)
        source.records.value = listOf(quantityRecord()); runCurrent(); vm.complete(vm.state.value.allHabits.single()); vm.changeAmount("1")
        source.fail.value = true; runCurrent(); vm.saveQuantity(); runCurrent(); assertEquals(0, source.writes)
        source.fail.value = false; vm.retry(); runCurrent(); source.records.value = emptyList(); runCurrent()
        vm.saveQuantity(); assertTrue(vm.state.value.quantity!!.stale); assertEquals(0, source.writes)
    }
    @Test fun coachSettingGatesEmptyShortcutAndRespondsToChanges() = runTest(dispatcher) {
        val prefs = MutableStateFlow(SettingsRepository.Configuration(coachEnabled = false)); val vm = model(Source(), prefs = prefs)
        runCurrent(); assertFalse(vm.state.value.coachEnabled)
        prefs.value = prefs.value.copy(coachEnabled = true); runCurrent(); assertTrue(vm.state.value.coachEnabled)
    }

}
