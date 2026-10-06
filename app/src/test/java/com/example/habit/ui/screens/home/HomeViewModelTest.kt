package com.example.habit.ui.screens.home

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
        MutableStateFlow(SettingsRepository.Configuration())) = HomeViewModel(source, prefs, dates).also { store.put("home", it) }

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
}
