package com.example.habit.ui.screens.insights

import androidx.lifecycle.*
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.*
import com.example.habit.domain.*
import com.example.habit.ui.screens.progress.*
import com.example.habit.ui.screens.profile.*
import com.example.habit.ui.theme.AppThemeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class InsightViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val today = LocalDate.of(2026, 10, 6)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private class Dates(day: LocalDate) : DateProvider {
        override val dates = MutableStateFlow(day)
        override fun today() = dates.value
        override fun refresh() {}
    }
    private inner class Source : HabitDataSource {
        val records = MutableStateFlow(listOf(record()))
        val failed = MutableStateFlow(false)
        var reads = 0
        override fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek): Flow<HabitSnapshot> {
            reads++
            return combine(records, failed) { values, fail -> if (fail) throw IOException() else HabitSnapshot.from(values, today, weekStart) }
        }
        override suspend fun toggleToday(habit: HabitEntity, today: LocalDate, done: Boolean) = Unit
        override suspend fun quantityToday(habit: HabitEntity, today: LocalDate, value: CompletionValue.Quantity?) = Unit
    }
    private class Settings : ProfileSettings {
        val prefs = MutableStateFlow(SettingsRepository.Configuration(userName = "Shimul Barua"))
        val failed = MutableStateFlow(false)
        override val configuration = combine(prefs, failed) { value, fail -> if (fail) throw IOException() else value }
        var calls = 0
        var failWrite = false
        var hold: CompletableDeferred<Unit>? = null
        private suspend fun write(change: (SettingsRepository.Configuration) -> SettingsRepository.Configuration) {
            calls++; hold?.await(); if (failWrite) throw IOException(); prefs.value = change(prefs.value)
        }
        override suspend fun setUserName(name: String) = write { it.copy(userName = name) }
        override suspend fun setThemeMode(mode: ThemeMode) = write { it.copy(themeMode = mode) }
        override suspend fun setWeekStart(day: DayOfWeek) = write { it.copy(weekStart = day) }
        override suspend fun setCoachEnabled(enabled: Boolean) = write { it.copy(coachEnabled = enabled) }
    }
    private fun record(id: Long = 1, created: LocalDate = today.minusDays(8), schedule: HabitSchedule = HabitSchedule.Daily,
        mode: TrackingMode = TrackingMode.Binary, done: List<LocalDate> = listOf(today.minusDays(1), today)) = HabitRecord(
        HabitEntity(id, "Read", createdEpochDay = created.toEpochDay()), listOf(ScheduleHistoryEntity.from(id, created, schedule)),
        listOf(TrackingHistoryEntity.from(id, created, mode)), done.map { date -> CompletionEntity(id, date.toEpochDay(), completedAt = 1,
            trackingMode = if (mode is TrackingMode.Quantity) "QUANTITY" else "BINARY",
            quantityAmount = (mode as? TrackingMode.Quantity)?.target?.toPlainString(), quantityUnit = (mode as? TrackingMode.Quantity)?.unit) })
    private fun progress(source: Source, settings: Settings = Settings(), dates: Dates = Dates(today), saved: SavedStateHandle = SavedStateHandle()) =
        ProgressViewModel(source, settings.configuration, dates, saved).also { store.put("progress", it) }
    private fun profile(source: Source, settings: Settings, saved: SavedStateHandle = SavedStateHandle()) =
        ProfileViewModel(source, settings, Dates(today), saved).also { store.put("profile", it) }

    @Test fun rangeValuesAreWeightedAndAllTimeBestIncludesArchivedHistory() = runTest(dispatcher) {
        val source = Source()
        source.records.value = listOf(record(), record(2, created = today.minusDays(3), schedule = HabitSchedule.Weekly(3), done = listOf(today.minusDays(1))),
            record(3, created = today.minusDays(700), done = (1L..600L).map { today.minusDays(700).plusDays(it) })
                .let { it.copy(habit = it.habit.copy(archivedAt = 1, archivedEpochDay = today.minusDays(50).toEpochDay())) })
        val vm = progress(source); runCurrent()
        val snapshot = vm.state.value.read.snapshot!!
        assertEquals(600, snapshot.allTimeBest); assertEquals(2, snapshot.topStreaks.size)
        assertEquals(snapshot.totals(StatsAggregator.week(today, DayOfWeek.MONDAY)), vm.state.value.facts!!.totals)
        assertEquals(3L, vm.state.value.facts!!.totals.completed)
        vm.selectRange(ProgressRange.MONTH); runCurrent()
        assertEquals(snapshot.totals(DateRange(today.withDayOfMonth(1), today.withDayOfMonth(31))), vm.state.value.facts!!.totals)
        assertEquals(600, vm.state.value.read.snapshot!!.allTimeBest)
        source.records.value = source.records.value.dropLast(1); runCurrent(); assertEquals(2, vm.state.value.read.snapshot!!.allTimeBest)
    }
    @Test fun actualFourFiveSixMonthBucketsAndDisplayStartDoNotRepartitionQuotas() = runTest(dispatcher) {
        val source = Source(); source.records.value = listOf(record(created = LocalDate.of(2020, 1, 1), schedule = HabitSchedule.Weekly(3), done = emptyList()))
        val settings = Settings(); val dates = Dates(LocalDate.of(2021, 2, 28)); val vm = progress(source, settings, dates)
        vm.selectRange(ProgressRange.MONTH); runCurrent(); assertEquals(4, vm.state.value.facts!!.bars.size)
        dates.dates.value = LocalDate.of(2026, 10, 31); runCurrent(); assertEquals(5, vm.state.value.facts!!.bars.size)
        dates.dates.value = LocalDate.of(2026, 3, 31); runCurrent(); assertEquals(6, vm.state.value.facts!!.bars.size)
        val totals = vm.state.value.facts!!.totals
        settings.prefs.value = settings.prefs.value.copy(weekStart = DayOfWeek.SUNDAY); runCurrent()
        assertEquals(5, vm.state.value.facts!!.bars.size); assertEquals(totals, vm.state.value.facts!!.totals)
        assertEquals(DayOfWeek.SUNDAY, vm.state.value.read.snapshot!!.week.first().range.first.dayOfWeek)
    }
    @Test fun partialQuantityAndHistoricalTargetUseSharedFactsAndZeroIsUndefined() = runTest(dispatcher) {
        val source = Source(); val created = today.minusDays(2)
        source.records.value = listOf(record(created = created, mode = TrackingMode.Quantity(BigDecimal.TEN, "pages"), done = listOf(created, today)).let {
            it.copy(tracking = it.tracking + TrackingHistoryEntity.from(1, today, TrackingMode.Quantity(BigDecimal("20"), "pages"))) })
        val vm = progress(source); runCurrent(); assertEquals(0L, vm.state.value.facts!!.totals.completed)
        assertEquals(1L, vm.state.value.read.snapshot!!.lifetime.completed)
        assertEquals(3L, vm.state.value.read.snapshot!!.lifetime.eligible)
        source.records.value = emptyList(); runCurrent(); assertNull(vm.state.value.facts!!.totals.consistency)
        assertEquals("—", consistencyText(vm.state.value.facts!!.totals.consistency)); assertTrue(vm.state.value.read.snapshot!!.topStreaks.isEmpty())
    }
    @Test fun snapshotFailureRetainsValuesAndRetryRefreshesDateAndCorrections() = runTest(dispatcher) {
        val source = Source(); val dates = Dates(today); val vm = progress(source, dates = dates); runCurrent()
        val before = vm.state.value.read.snapshot; source.failed.value = true; runCurrent()
        assertTrue(vm.state.value.read.error); assertEquals(before, vm.state.value.read.snapshot)
        source.failed.value = false; vm.retry(); runCurrent(); assertFalse(vm.state.value.read.error)
        source.records.value = listOf(record(done = emptyList())); dates.dates.value = today.plusDays(1); runCurrent()
        assertEquals(today.plusDays(1), vm.state.value.read.snapshot!!.today); assertEquals(0L, vm.state.value.facts!!.totals.completed)
    }
    @Test fun rangeAndNameDraftRestoreFromPrimitives() = runTest(dispatcher) {
        val source = Source(); val settings = Settings(); val pSaved = SavedStateHandle(); val nSaved = SavedStateHandle()
        val p = progress(source, saved = pSaved); val n = profile(source, settings, nSaved); runCurrent()
        p.selectRange(ProgressRange.MONTH); n.editName(); n.changeName("Retained name")
        val restoredP = progress(source, saved = SavedStateHandle(pSaved.keys().associateWith { pSaved.get<Any?>(it) }))
        val restoredN = profile(source, settings, SavedStateHandle(nSaved.keys().associateWith { nSaved.get<Any?>(it) })); runCurrent()
        assertEquals(ProgressRange.MONTH, restoredP.state.value.range)
        assertTrue(restoredN.state.value.editingName); assertEquals("Retained name", restoredN.state.value.nameInput)
    }
    @Test fun pendingRepeatedAndFailedPreferenceWritesDoNotPretendSuccess() = runTest(dispatcher) {
        val source = Source(); val settings = Settings(); val vm = profile(source, settings); runCurrent()
        settings.hold = CompletableDeferred(); vm.openDialog("theme"); vm.theme(ThemeMode.DARK); vm.theme(ThemeMode.LIGHT); runCurrent()
        assertEquals(1, settings.calls); assertTrue(vm.state.value.writing); assertEquals(ThemeMode.SYSTEM, vm.state.value.preferences!!.themeMode)
        settings.failWrite = true; settings.hold!!.complete(Unit); runCurrent()
        assertTrue(vm.state.value.writeError); assertEquals("theme", vm.state.value.dialog); assertEquals(ThemeMode.SYSTEM, vm.state.value.preferences!!.themeMode)
        settings.hold = null; settings.failWrite = false; vm.theme(ThemeMode.DARK); runCurrent()
        assertEquals(ThemeMode.DARK, vm.state.value.preferences!!.themeMode); assertNull(vm.state.value.dialog)
        vm.coach(false); runCurrent(); assertFalse(vm.state.value.preferences!!.coachEnabled)
        vm.weekStart(DayOfWeek.SUNDAY); runCurrent(); assertEquals(DayOfWeek.SUNDAY, vm.state.value.preferences!!.weekStart)
    }
    @Test fun nameValidationFailureCancelAndTrimKeepInputAndLocalIdentity() = runTest(dispatcher) {
        val settings = Settings(); val vm = profile(Source(), settings); runCurrent()
        assertEquals("SB", profileInitials(vm.state.value.preferences!!.userName)); assertEquals("HC", profileInitials(""))
        assertEquals("😀R", profileInitials("😀 Reader")); assertEquals("JA", profileInitials("  jane  anna "))
        vm.editName(); vm.changeName("x".repeat(81)); vm.finishName(); runCurrent(); assertTrue(vm.state.value.invalidName); assertEquals(0, settings.calls)
        vm.changeName("  Jane Anna  "); settings.failWrite = true; vm.finishName(); runCurrent()
        assertTrue(vm.state.value.editingName); assertEquals("  Jane Anna  ", vm.state.value.nameInput); assertEquals("Shimul Barua", vm.state.value.preferences!!.userName)
        settings.failWrite = false; vm.finishName(); runCurrent(); assertEquals("Jane Anna", vm.state.value.preferences!!.userName); assertFalse(vm.state.value.editingName)
        vm.editName(); vm.changeName("discard"); vm.cancelName(); assertEquals("Jane Anna", settings.prefs.value.userName)
    }
    @Test fun preferenceFailureDoesNotMaskStatisticsAndDatabaseFailureDoesNotBlockSettings() = runTest(dispatcher) {
        val source = Source(); val settings = Settings(); val vm = profile(source, settings); runCurrent()
        source.failed.value = true; runCurrent(); assertTrue(vm.state.value.read.error)
        vm.coach(false); runCurrent(); assertFalse(settings.prefs.value.coachEnabled)
        settings.failed.value = true; runCurrent(); assertTrue(vm.state.value.preferenceError); vm.theme(ThemeMode.DARK); runCurrent(); assertEquals(1, settings.calls)
        settings.failed.value = false; source.failed.value = false; vm.retry(); runCurrent()
        assertFalse(vm.state.value.preferenceError); assertFalse(vm.state.value.read.error)
    }
    @Test fun nonRangePreferencesDoNotRestartHistoricalCalculation() = runTest(dispatcher) {
        val source = Source(); val settings = Settings(); progress(source, settings); runCurrent(); val before = source.reads
        settings.prefs.value = settings.prefs.value.copy(userName = "Name", themeMode = ThemeMode.DARK, coachEnabled = false); runCurrent()
        assertEquals(before, source.reads)
    }
    @Test fun appThemeObservesAllModesAndRetainsLastValueOnReadFailure() = runTest(dispatcher) {
        val settings = Settings(); val vm = AppThemeViewModel(settings.configuration).also { store.put("theme", it) }; runCurrent()
        settings.setThemeMode(ThemeMode.DARK); runCurrent(); assertEquals(ThemeMode.DARK, vm.mode.value)
        settings.failed.value = true; runCurrent(); assertEquals(ThemeMode.DARK, vm.mode.value)
        settings.failed.value = false; settings.setThemeMode(ThemeMode.LIGHT); advanceTimeBy(1000); runCurrent(); assertEquals(ThemeMode.LIGHT, vm.mode.value)
        settings.setThemeMode(ThemeMode.SYSTEM); runCurrent(); assertEquals(ThemeMode.SYSTEM, vm.mode.value)
    }
}
