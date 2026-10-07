package com.example.habit.ui.screens.insights

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.ui.controls.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.*
import com.example.habit.domain.*
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.navigation.*
import com.example.habit.ui.screens.detail.*
import com.example.habit.ui.screens.home.*
import com.example.habit.ui.screens.progress.*
import com.example.habit.ui.screens.profile.*
import com.example.habit.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.math.BigDecimal
import java.io.File
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InsightWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.of(2026, 10, 16)
    private val start = today.minusDays(14)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val store = ViewModelStore()
    private val settingsJob = SupervisorJob()
    private lateinit var file: File
    private lateinit var db: HabitDatabase
    private lateinit var history: HabitHistoryRepository
    private lateinit var settings: SettingsRepository
    private lateinit var faultStore: FaultStore
    private lateinit var source: Source
    private lateinit var progress: ProgressViewModel
    private lateinit var profile: ProfileViewModel
    private lateinit var actions: ProfileActionsViewModel
    private lateinit var theme: AppThemeViewModel
    private val profileSaved = SavedStateHandle()
    private class Dates(day: LocalDate) : DateProvider {
        override val dates = MutableStateFlow(day)
        override fun today() = dates.value
        override fun refresh() {}
    }
    private class FaultStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var failWrite = false
        override val data = delegate.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrite) throw IOException("isolated test failure")
            return delegate.updateData(transform)
        }
    }
    private class Source(private val delegate: HabitDataSource) : HabitDataSource by delegate {
        val fail = MutableStateFlow(false)
        override fun observeSnapshot(today: LocalDate, weekStart: DayOfWeek) = combine(delegate.observeSnapshot(today, weekStart), fail) { snapshot, failed ->
            if (failed) throw IOException("isolated test failure") else snapshot
        }
    }
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build()
        history = HabitHistoryRepository(db, clock)
        file = File(context.cacheDir, "chunk07-${System.nanoTime()}.preferences_pb")
        faultStore = FaultStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + settingsJob)) { file })
        settings = SettingsRepository(faultStore)
        runBlocking { settings.setUserName("Shimul Barua"); settings.setThemeMode(ThemeMode.LIGHT) }
        source = Source(HabitRepository(history))
        compose.runOnIdle {
            progress = ProgressViewModel(source, settings.configuration, Dates(today), SavedStateHandle()).also { store.put("progress", it) }
            profile = ProfileViewModel(source, settings, Dates(today), profileSaved).also { store.put("profile", it) }
            actions = ProfileActionsViewModel(object : ProfileActions {
                override suspend fun reminder(enabled: Boolean, minute: Int) { settings.setReminder(enabled, minute) }
                override suspend fun export(uri: String) { error("No destination selected in this fixture") }
                override suspend fun clear() { error("This fixture only verifies cancellation") }
            }, SavedStateHandle())
            store.put("actions", actions)
            theme = AppThemeViewModel(settings.configuration).also { store.put("theme", it) }
        }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; runBlocking { settingsJob.cancelAndJoin() }; db.close(); file.delete() }
    private fun create(name: String = "Read 20 pages", mode: TrackingMode = TrackingMode.Binary, schedule: HabitSchedule = HabitSchedule.Daily, count: Int = 15): Long = runBlocking {
        val old = HabitHistoryRepository(db, Clock.fixed(start.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
        val id = old.create(HabitDraft(name, HabitSettings(schedule, mode), "book", "purple"))
        for (offset in 0 until count) history.correct(id, start.plusDays(offset.toLong()), if (mode is TrackingMode.Quantity) CompletionValue.Quantity(mode.target, mode.unit) else CompletionValue.Binary(true))
        id
    }
    private fun ready() { compose.waitUntil(5000) { !progress.state.value.read.loading && profile.state.value.preferences != null && !profile.state.value.read.loading } }
    private fun screen(isProfile: Boolean = false, dark: Boolean = false, small: Boolean = false) {
        runBlocking { settings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) {
                    AppTheme(theme) {
                        Box { if (isProfile) ProfileScreen({}, profile, actions) else ProgressScreen({}, {}, progress)
                            Box(Modifier.size(1.dp).testTag(if (HabitTheme.colors.isDark) "theme-dark" else "theme-light"))
                            Box(Modifier.size(1.dp).testTag(if (androidx.compose.foundation.isSystemInDarkTheme()) "system-dark" else "system-light")) }
                    }
                }
            }
        }; ready()
    }
    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk07-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun profileScroll(label: String) { compose.onNodeWithTag("profile-scroll").performScrollToNode(hasText(label)); compose.onNodeWithText(label).assertIsDisplayed() }
    private fun back() { androidx.test.espresso.Espresso.pressBack() }

    @Test fun progressUsesQuantityQuotaAndWeightedValuesAndWeekStartImmediately() {
        create(mode = TrackingMode.Quantity(BigDecimal.TEN, "pages"))
        val weekly = create("Walk", schedule = HabitSchedule.Weekly(3), count = 0)
        runBlocking { history.correct(weekly, today.minusDays(2), CompletionValue.Binary(true)); history.correct(weekly, today, CompletionValue.Binary(true)) }
        screen()
        val facts = progress.state.value.facts!!
        assertEquals(7L, facts.totals.completed); assertEquals(8L, facts.totals.eligible)
        compose.onNodeWithText("88%").assertIsDisplayed()
        compose.onNodeWithText("7 of 8 required completions · 1 pending").assertIsDisplayed()
        compose.onNodeWithText("Month").performClick()
        compose.onAllNodes(hasTestTag("progress-bar-0")).assertCountEquals(1)
        assertEquals(5, progress.state.value.facts!!.bars.size)
        val lifetime = progress.state.value.read.snapshot!!.lifetime
        runBlocking { settings.setWeekStart(DayOfWeek.SUNDAY) }
        compose.waitUntil(5000) { progress.state.value.read.snapshot!!.week.first().range.first.dayOfWeek == DayOfWeek.SUNDAY }
        assertEquals(lifetime, progress.state.value.read.snapshot!!.lifetime)
    }
    @Test fun zeroArchivedAndDeletedHistoryRemainHonestAndReactive() {
        screen()
        compose.onNodeWithText("No habits yet.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Top streaks").assertDoesNotExist()
        val id = create()
        compose.waitUntil(5000) { progress.state.value.read.snapshot!!.topStreaks.isNotEmpty() }
        runBlocking { history.archive(id) }
        compose.waitUntil(5000) { progress.state.value.read.snapshot!!.active.isEmpty() }
        assertEquals(15L, progress.state.value.read.snapshot!!.lifetime.completed)
        assertEquals(15, progress.state.value.read.snapshot!!.allTimeBest)
        compose.onNodeWithText("Top streaks").assertDoesNotExist()
        runBlocking { history.delete(id) }
        compose.waitUntil(5000) { progress.state.value.read.snapshot!!.records.isEmpty() }
        assertNull(progress.state.value.facts!!.totals.consistency)
    }
    @Test fun profileWritesNameThemeWeekAndCoachThroughRealDataStoreWithoutRestart() {
        create(); screen(isProfile = true)
        compose.onNodeWithText("SB").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit name").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("  Jane Anna  ")
        compose.onNodeWithTag("profile-name").performImeAction()
        compose.waitUntil(5000) { !profile.state.value.editingName && profile.state.value.preferences!!.userName == "Jane Anna" }
        compose.onNodeWithText("JA").assertIsDisplayed()
        profileScroll("Theme"); compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5000) { theme.mode.value == ThemeMode.DARK && profile.state.value.dialog == null }
        compose.onNodeWithTag("theme-dark").assertExists()
        compose.runOnIdle { assertFalse(androidx.core.view.WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
        compose.onNodeWithText("Theme").performClick(); compose.onNodeWithText("Light").performClick()
        compose.waitUntil(5000) { theme.mode.value == ThemeMode.LIGHT }
        compose.onNodeWithTag("theme-light").assertExists()
        compose.runOnIdle { assertTrue(androidx.core.view.WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
        compose.onNodeWithText("Theme").performClick(); compose.onNodeWithText("Follow system").performClick()
        compose.waitUntil(5000) { theme.mode.value == ThemeMode.SYSTEM }
        compose.onNodeWithTag(if (compose.onAllNodesWithTag("system-dark").fetchSemanticsNodes().isNotEmpty()) "theme-dark" else "theme-light").assertExists()
        compose.onNodeWithText("Week starts").performClick(); compose.onNodeWithText("Sunday").performClick()
        compose.waitUntil(5000) { progress.state.value.read.snapshot!!.week.first().range.first.dayOfWeek == DayOfWeek.SUNDAY }
        profileScroll("Coach suggestions"); compose.onNodeWithTag("coach-setting").performClick()
        compose.waitUntil(5000) { !profile.state.value.preferences!!.coachEnabled }
        val persisted = runBlocking { settings.configuration.first() }
        assertEquals("Jane Anna", persisted.userName); assertEquals(ThemeMode.SYSTEM, persisted.themeMode)
        assertEquals(DayOfWeek.SUNDAY, persisted.weekStart); assertFalse(persisted.coachEnabled)
        settingsJob.cancel(); runBlocking { settingsJob.join() }
        val reopenJob = SupervisorJob()
        try { val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenJob)) { file }
            assertEquals(persisted, runBlocking { SettingsRepository(reopened).configuration.first() })
        } finally { runBlocking { reopenJob.cancelAndJoin() } }
    }
    @Test fun failedNameAndThemeWritesRetainInputAndPreviousSetting() {
        screen(isProfile = true); faultStore.failWrite = true
        compose.onNodeWithContentDescription("Edit name").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("Retained")
        compose.onNodeWithTag("profile-name").performImeAction()
        compose.waitUntil(5000) { profile.state.value.writeError }
        compose.onNodeWithTag("profile-name").assertTextContains("Retained")
        assertEquals("Shimul Barua", runBlocking { settings.configuration.first() }.userName)
        faultStore.failWrite = false; compose.onNodeWithContentDescription("Done editing name").performClick()
        compose.waitUntil(5000) { !profile.state.value.editingName }
        profileScroll("Theme"); compose.onNodeWithText("Theme").performClick(); faultStore.failWrite = true
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5000) { profile.state.value.writeError }
        compose.onNodeWithText("Couldn’t save.", substring = true).assertIsDisplayed()
        assertEquals(ThemeMode.LIGHT, theme.mode.value)
        back(); assertNull(profile.state.value.dialog)
    }
    @Test fun productionGraphRetainsMonthScrollAndActualProgressDetailCaller() {
        create()
        val id = (1..8).map { create("Reading habit $it") }.last()
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            val home = remember { HomeViewModel(source, settings.configuration, Dates(today), SavedStateHandle()).also { store.put("home", it) } }
            AppTheme(theme) {
                HabitNavigationGraph(nav, splash = { _, ready -> LaunchedEffect(Unit) { ready(false) } }, onboarding = {},
                    home = { add, coach, open, tab -> HomeScreen(add, coach, open, tab, home) }, form = { _, _ -> },
                    root = { tab, select -> when (tab) {
                        HomeTab.PROGRESS -> ProgressScreen({ nav.navigate(Routes.detail(it)) }, select, progress)
                        HomeTab.PROFILE -> ProfileScreen(select, profile, actions)
                        HomeTab.COACH -> CoachRoot(select, remember { CoachSelectionViewModel(flowOf(emptyList()), settings.coachEnabled, SavedStateHandle()).also { store.put("coach", it) } })
                        else -> Unit
                    } }, detail = { habitId, back ->
                        val detail = remember(habitId) { HabitDetailViewModel(habitId, source, history, settings.configuration, Dates(today), SavedStateHandle()).also { store.put("detail-$habitId", it) } }
                        HabitDetailScreen(back, {}, detail)
                    })
            }
        }
        ready(); compose.onNodeWithText("Progress").performClick()
        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithTag("progress-scroll").performScrollToNode(hasTestTag("top-streak-$id"))
        val retainedScroll = compose.onNodeWithTag("progress-scroll").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Fixture must actually scroll before testing restoration", retainedScroll > 0f)
        compose.onNodeWithTag("top-streak-$id").performClick()
        compose.onNodeWithText("Reading habit 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Routes.PROGRESS, nav.currentDestination!!.route) }
        compose.onNodeWithTag("top-streak-$id").assertIsDisplayed()
        compose.onNodeWithText("Profile").performClick()
        profileScroll("Restore data"); compose.onNodeWithText("Restore data").assertIsNotEnabled()
        compose.onNodeWithText("Progress").performClick()
        assertEquals(ProgressRange.MONTH, progress.state.value.range)
        compose.onNodeWithTag("top-streak-$id").assertIsDisplayed()
        assertEquals(retainedScroll, compose.onNodeWithTag("progress-scroll").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Restore data").assertIsDisplayed()
        back(); compose.runOnIdle { assertEquals(Routes.HOME, nav.currentDestination!!.route) }
    }
    @Test fun loadingFailureRetryRetainsStatisticsOnTheScreen() {
        create(); screen()
        val before = progress.state.value.read.snapshot
        compose.runOnIdle { source.fail.value = true }
        compose.waitUntil(5000) { progress.state.value.read.error }
        compose.onNodeWithText("Couldn’t read local statistics.", substring = true).assertIsDisplayed()
        assertEquals(before, progress.state.value.read.snapshot)
        compose.runOnIdle { source.fail.value = false }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5000) { !progress.state.value.read.loading && !progress.state.value.read.error }
        assertEquals(before, progress.state.value.read.snapshot)
    }
    @Test fun lightProgressReferenceWeekMonthAndStreakVisuals() { progressReference(false) }
    @Test fun darkProgressReferenceWeekMonthAndStreakVisuals() { progressReference(true) }
    private fun progressReference(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val id = create()
        val walk = create("Walk")
        val focus = create("Evening focus")
        runBlocking {
            for (offset in 0L..4L) history.correct(walk, start.plusDays(offset), null)
            for (offset in 0L..6L) history.correct(focus, start.plusDays(offset), null)
        }
        screen(dark = dark)
        capture("progress-$mode-week-reference")
        compose.onNodeWithText("Month").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        capture("progress-$mode-month-reference")
        compose.onNodeWithTag("progress-scroll").performScrollToNode(hasTestTag("top-streak-$id"))
        compose.onNodeWithTag("top-streak-$id").assertHeightIsAtLeast(48.dp)
        capture("progress-$mode-streaks-reference")
    }
    @Test fun darkSmallProgressLargeTextVisual() {
        val id = create("Read a chapter every evening"); screen(dark = true, small = true)
        val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("100%").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        capture("progress-dark-small-week")
        val layout = textLayouts.single()
        assertEquals(1, layout.lineCount)
        assertEquals("All percentage characters must remain on the visible line", 4, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("Stat glyphs must fit the rendered bounds", layout.getLineRight(0) <= layout.size.width + 1f && layout.getLineLeft(0) >= -1f)
        assertFalse(layout.didOverflowHeight)
        val firstHeight = compose.onNodeWithTag("stat-cell-0").fetchSemanticsNode().boundsInRoot.height
        assertEquals(firstHeight, compose.onNodeWithTag("stat-cell-1").fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(firstHeight, compose.onNodeWithTag("stat-cell-2").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithTag("progress-scroll").performScrollToNode(hasTestTag("progress-chart"))
        capture("progress-dark-small-month")
        compose.onNodeWithTag("progress-scroll").performScrollToNode(hasTestTag("top-streak-$id"))
        compose.onNodeWithTag("top-streak-$id").assertIsDisplayed(); capture("progress-dark-small-streaks")
    }
    @Test fun lightProfileReferenceAndScrollableDataControlsVisual() { profileReference(false) }
    @Test fun darkProfileReferenceAndScrollableDataControlsVisual() { profileReference(true) }
    private fun profileReference(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        create(); screen(isProfile = true, dark = dark)
        capture("profile-$mode-reference")
        profileScroll("Clear all data"); capture("profile-$mode-data")
        compose.onNodeWithText("Restore data").assertIsNotEnabled()
        compose.onNodeWithText("Export data").performClick()
        compose.onNodeWithText("Saves a versioned JSON file", substring = true).assertIsDisplayed(); back()
        compose.onNodeWithText("Clear all data").performClick()
        compose.onNodeWithText("Type CLEAR to confirm.", substring = true).assertIsDisplayed(); back()
        assertEquals(1, runBlocking { history.records.first().size })
    }
    @Test fun darkSmallProfileAndThemeDialogVisualAndModalBack() {
        create(); screen(isProfile = true, dark = true, small = true)
        capture("profile-dark-small")
        profileScroll("Theme"); compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Follow system").assertIsDisplayed(); capture("profile-dark-small-theme", dialog = true)
        back(); assertNull(profile.state.value.dialog)
        profileScroll("Clear all data"); compose.onNodeWithText("Clear all data").assertIsDisplayed(); capture("profile-dark-small-data")
    }
    @Test fun largeTextNameKeepsDoneAccessibleWithNativeKeyboardAndCancelAppliesNothing() {
        create(); screen(isProfile = true, dark = true, small = true)
        compose.onNodeWithContentDescription("Edit name").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("Local name")
        compose.waitUntil(5000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithTag("profile-scroll").performScrollToNode(hasContentDescription("Done editing name"))
        compose.onNodeWithContentDescription("Done editing name").assertIsDisplayed()
        capture("profile-dark-small-name-keyboard")
        compose.onNodeWithContentDescription("Cancel name edit").performClick()
        assertEquals("Shimul Barua", runBlocking { settings.configuration.first() }.userName)
    }
    @Suppress("DEPRECATION")
    @Test fun profilePrimitiveDraftRestoresThroughBundleParcel() {
        screen(isProfile = true)
        compose.onNodeWithContentDescription("Edit name").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("Restored name")
        val bundle = android.os.Bundle()
        compose.runOnIdle { profileSaved.keys().forEach { key -> when (val value = profileSaved.get<Any?>(key)) {
            is String -> bundle.putString(key, value); is Boolean -> bundle.putBoolean(key, value)
        } } }
        val parcel = android.os.Parcel.obtain(); parcel.writeBundle(bundle); parcel.setDataPosition(0)
        val restored = parcel.readBundle(javaClass.classLoader)!!; parcel.recycle()
        lateinit var recreated: ProfileViewModel
        compose.runOnIdle { recreated = ProfileViewModel(source, settings, Dates(today), SavedStateHandle(restored.keySet().associateWith { restored.get(it) })).also { store.put("restored", it) } }
        compose.waitUntil(5000) { recreated.state.value.preferences != null }
        assertTrue(recreated.state.value.editingName); assertEquals("Restored name", recreated.state.value.nameInput)
        compose.runOnIdle { recreated.finishName() }
        compose.waitUntil(5000) { !recreated.state.value.editingName }
        assertEquals("Restored name", runBlocking { settings.configuration.first() }.userName)
    }
}
