package com.example.habit.ui.screens.detail

import androidx.lifecycle.*
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.management.HabitManagementViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val day = LocalDate.of(2026, 10, 6)
    private val created = day.minusDays(10)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private class Dates(day: LocalDate) : DateProvider {
        val flow = MutableStateFlow(day)
        var actual = day
        override val dates: Flow<LocalDate> = flow
        override fun today() = actual
        override fun refresh() { flow.value = actual }
    }
    private inner class Source : HabitDataSource, HabitOperations {
        val data = MutableStateFlow(listOf(record()))
        val fail = MutableStateFlow(false)
        var calls = 0
        var failWrite = false
        var hold: CompletableDeferred<Unit>? = null
        var last: CompletionValue? = null
        var millis = 10_000L
        override fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek) = combine(data, fail) { list, failed ->
            if (failed) throw IOException()
            HabitSnapshot.from(list, today, weekStart)
        }
        override suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean) = Unit
        override suspend fun quantityToday(habit: HabitEntity, today: LocalDate, value: CompletionValue.Quantity?) = Unit
        override suspend fun correctChecked(id: Long, date: LocalDate, openedOn: LocalDate, expected: HabitSettings, before: CompletionValue?, value: CompletionValue?) {
            calls++; hold?.await(); if (failWrite) throw IOException(); last = value
        }
        override suspend fun archive(id: Long): ArchiveChange {
            calls++; hold?.await(); if (failWrite) throw IOException()
            data.value = data.value.map { it.copy(habit = it.habit.copy(archivedAt = millis, archivedEpochDay = day.toEpochDay())) }
            return ArchiveChange(id, millis)
        }
        override suspend fun undoArchive(change: ArchiveChange): Boolean {
            calls++; if (failWrite) throw IOException()
            data.value = data.value.map { it.copy(habit = it.habit.copy(archivedAt = null, archivedEpochDay = null)) }; return true
        }
        override suspend fun delete(id: Long) { calls++; hold?.await(); if (failWrite) throw IOException(); data.value = emptyList() }
    }
    private fun record(mode: TrackingMode = TrackingMode.Binary, schedule: HabitSchedule = HabitSchedule.Daily) = HabitRecord(
        HabitEntity(1, "Test", createdEpochDay = created.toEpochDay()), listOf(ScheduleHistoryEntity.from(1, created, schedule)),
        listOf(TrackingHistoryEntity.from(1, created, mode)), emptyList())
    private fun detail(source: Source, saved: SavedStateHandle = SavedStateHandle(), dates: Dates = Dates(day),
        prefs: Flow<SettingsRepository.Configuration> = MutableStateFlow(SettingsRepository.Configuration())) =
        HabitDetailViewModel(1, source, source, prefs, dates, saved).also { store.put("detail", it) }
    private fun manager(source: Source, saved: SavedStateHandle = SavedStateHandle()) = HabitManagementViewModel(source, source,
        MutableStateFlow(SettingsRepository.Configuration()), Dates(day), saved) { source.millis }.also { store.put("manager", it) }
    @Test fun correctionGuardsEligibilityRepeatAndFailure() = runTest(dispatcher) {
        val source = Source(); val vm = detail(source); runCurrent()
        vm.openDate(day.plusDays(1)); assertNull(vm.state.value.entry)
        vm.openDate(created.minusDays(1)); assertNull(vm.state.value.entry)
        vm.openDate(day.minusDays(1)); source.hold = CompletableDeferred(); vm.saveEntry(); vm.saveEntry(); runCurrent()
        assertEquals(1, source.calls); assertTrue(vm.state.value.entry!!.saving)
        vm.dismissEntry(); assertNotNull(vm.state.value.entry)
        source.failWrite = true; source.hold!!.complete(Unit); runCurrent()
        assertNotNull(vm.state.value.entry!!.error); assertFalse(vm.state.value.entry!!.saving)
        source.hold = null; source.failWrite = false; vm.saveEntry(); runCurrent()
        assertEquals(CompletionValue.Binary(true), source.last); assertNull(vm.state.value.entry)
    }
    @Test fun quantityRestorationPreservesOriginalTargetAndInput() = runTest(dispatcher) {
        val source = Source().apply { data.value = listOf(record(TrackingMode.Quantity(BigDecimal.TEN, "pages"))) }
        val saved = SavedStateHandle(); var vm = detail(source, saved); runCurrent()
        vm.openDate(day.minusDays(2)); vm.changeInput("2.5"); vm.moveMonth(-2)
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        vm = detail(source, restored); runCurrent()
        assertEquals("2.5", vm.state.value.entry!!.input); assertEquals(BigDecimal.TEN, vm.state.value.entry!!.quantity!!.target)
        assertEquals(YearMonth.of(2026, 8), vm.state.value.month)
        vm.saveEntry(); runCurrent(); assertEquals(CompletionValue.Quantity(BigDecimal("2.5"), "pages"), source.last)
    }
    @Test fun changedHistoryOrMidnightBlocksStaleCorrection() = runTest(dispatcher) {
        val source = Source(); val dates = Dates(day); val vm = detail(source, dates = dates); runCurrent()
        vm.openDate(day); dates.actual = day.plusDays(1); vm.saveEntry(); runCurrent()
        assertEquals(0, source.calls); assertTrue(vm.state.value.entry!!.stale)
        vm.dismissEntry(); vm.openDate(day.minusDays(1))
        source.data.value = listOf(record().copy(completions = listOf(CompletionEntity(1, day.minusDays(1).toEpochDay(), completedAt = 1))))
        runCurrent(); assertTrue(vm.state.value.entry!!.stale); vm.saveEntry(); runCurrent(); assertEquals(0, source.calls)
    }
    @Test fun readFailureRetainsDataAndRetryReenablesWrites() = runTest(dispatcher) {
        val source = Source(); val vm = detail(source); runCurrent(); val old = vm.state.value.value
        source.fail.value = true; runCurrent(); assertTrue(vm.state.value.readError); assertEquals(old, vm.state.value.value)
        vm.openDate(day); assertNull(vm.state.value.entry)
        source.fail.value = false; vm.retry(); runCurrent(); assertTrue(vm.state.value.canWrite)
        source.data.value = emptyList(); runCurrent(); assertTrue(vm.state.value.missing)
    }
    @Test fun disabledCoachExplainsAvailabilityAndMakesNoWrite() = runTest(dispatcher) {
        val source = Source(); val prefs = MutableStateFlow(SettingsRepository.Configuration(coachEnabled = false))
        val vm = detail(source, prefs = prefs); runCurrent(); vm.coach()
        assertTrue(vm.state.value.notice!!.contains("Enable")); assertEquals(0, source.calls)
        prefs.value = prefs.value.copy(coachEnabled = true); runCurrent(); vm.coach(); assertTrue(vm.state.value.notice!!.contains("not connected"))
    }
    @Test fun managementArchivesOnceRetainsUndoAcrossDestinationsAndExpires() = runTest(dispatcher) {
        val source = Source(); val vm = manager(source); runCurrent(); vm.open(1)
        source.hold = CompletableDeferred(); vm.archive(); vm.archive(); runCurrent(); assertEquals(1, source.calls)
        source.hold!!.complete(Unit); runCurrent(); assertNull(vm.state.value.id); assertNotNull(vm.state.value.undo); assertEquals(1L, vm.state.value.removed)
        source.hold = null; vm.undo(); runCurrent(); assertNull(vm.state.value.undo); assertNull(source.data.value.single().habit.archivedAt)
        vm.open(1); vm.archive(); runCurrent(); advanceTimeBy(5_000); runCurrent(); assertNull(vm.state.value.undo)
        vm.undo(); runCurrent(); assertEquals(3, source.calls)
    }
    @Test fun undoRestorationAndDeleteRequireConfirmationAndPreserveOnFailure() = runTest(dispatcher) {
        val source = Source(); val saved = SavedStateHandle(); var vm = manager(source, saved); runCurrent()
        vm.open(1); vm.delete(); runCurrent(); assertEquals(0, source.calls)
        vm.confirmDelete(true); source.failWrite = true; vm.delete(); runCurrent(); assertNotNull(vm.state.value.error); assertEquals(1, source.data.value.size)
        vm.confirmDelete(false); source.failWrite = false; vm.archive(); runCurrent()
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }); vm = manager(source, restored); runCurrent()
        assertNotNull(vm.state.value.undo); vm.undo(); runCurrent(); assertNull(source.data.value.single().habit.archivedAt)
        vm.open(1); vm.confirmDelete(true); vm.delete(); runCurrent(); assertTrue(source.data.value.isEmpty()); assertEquals(1L, vm.state.value.removed)
    }
}
