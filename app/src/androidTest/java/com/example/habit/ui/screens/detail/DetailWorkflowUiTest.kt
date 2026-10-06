package com.example.habit.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.navigation.*
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.management.*
import com.example.habit.ui.navigation.*
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.home.*
import com.example.habit.ui.screens.newhabit.*
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.math.BigDecimal
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DetailWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.of(2026, 10, 16)
    private val start = today.minusDays(14)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var db: HabitDatabase
    private lateinit var history: HabitHistoryRepository
    private lateinit var detail: HabitDetailViewModel
    private val detailSaved = SavedStateHandle()
    private var detailRevision by mutableIntStateOf(0)
    private lateinit var manager: HabitManagementViewModel
    private val store = ViewModelStore()
    private val prefs = MutableStateFlow(SettingsRepository.Configuration())
    private class Dates(val day: LocalDate) : DateProvider {
        override val dates = MutableStateFlow(day)
        override fun today() = day
        override fun refresh() {}
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java).build()
        history = HabitHistoryRepository(db, clock)
        compose.runOnIdle { manager = HabitManagementViewModel(HabitRepository(history), history, prefs, Dates(today), SavedStateHandle()) { clock.millis() }; store.put("manager", manager) }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    private fun create(mode: TrackingMode = TrackingMode.Binary, schedule: HabitSchedule = HabitSchedule.Daily, full: Boolean = false, newHabit: Boolean = false): Long = runBlocking {
        val old = HabitHistoryRepository(db, Clock.fixed((if (newHabit) today else start).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
        val id = old.create(HabitDraft("Read 20 pages", HabitSettings(schedule, mode), "book", "purple"))
        if (full) for (day in 0L..14L) history.correct(id, start.plusDays(day), if (mode is TrackingMode.Quantity) CompletionValue.Quantity(mode.target, mode.unit) else CompletionValue.Binary(true))
        compose.runOnIdle { detail = HabitDetailViewModel(id, HabitRepository(history), history, prefs, Dates(today), detailSaved); store.put("detail", detail) }
        id
    }
    private fun screen(onBack: () -> Unit = {}, dark: Boolean = false, small: Boolean = false) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) {
                    HabitTheme(darkTheme = dark) { Box { key(detailRevision) { HabitDetailScreen(onBack, manager::open, detail) }; HabitManagementHost(manager, {}, {}) } }
                }
            }
        }
        compose.waitUntil(5000) { !detail.state.value.loading && manager.state.value.readable }
    }
    private fun back() {
        compose.waitForIdle()
        compose.waitUntil(5000) {
            android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                it !== compose.activity.window.decorView && it.hasWindowFocus()
            }
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun date(day: LocalDate) = compose.onNodeWithTag("date-$day")
    private fun bringDate(day: LocalDate) {
        val value = detail.state.value.value!!.evaluation
        val index = if (positiveCallout(value) || patternReading(value) != null) 3 else 2
        compose.onNodeWithTag("detail-scroll").performScrollToIndex(index)
        date(day).assertIsDisplayed()
    }
    private fun capture(name: String, dialog: Boolean = false) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk06-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun binaryCorrectionReplaysRiskAndStreakAndModalBackDoesNotSave() {
        val id = create(); screen()
        bringDate(today.minusDays(1))
        date(today.minusDays(1)).performClick()
        compose.onNodeWithText("Mark done").performClick()
        compose.waitUntil(5000) { detail.state.value.entry == null && detail.state.value.value!!.evaluation.metrics.currentStreak == 1 }
        assertNotNull(patternReading(detail.state.value.value!!.evaluation))
        date(today.minusDays(2)).performClick(); compose.onNodeWithText("Mark done").performClick()
        compose.waitUntil(5000) { detail.state.value.value!!.evaluation.metrics.currentStreak == 2 }
        assertNull(patternReading(detail.state.value.value!!.evaluation))
        date(today.minusDays(2)).performClick(); back()
        compose.onNodeWithText("Mark not done").assertDoesNotExist()
        assertEquals(2, runBlocking { history.record(id)!!.completions.size })
        date(today.plusDays(1)).assertIsNotEnabled(); date(start.minusDays(1)).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithTag("detail-calendar").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 11), detail.state.value.month) }
        compose.onNodeWithTag("detail-scroll").performScrollToIndex(3)
        compose.onNodeWithText("November 2026").assertIsDisplayed()
    }
    @Test fun quantityCorrectionUsesOriginalTargetAndClearPreservesOtherDates() {
        val mode = TrackingMode.Quantity(BigDecimal("20"), "pages"); val id = create(mode)
        val past = today.minusDays(3)
        runBlocking { HabitHistoryRepository(db, Clock.fixed(today.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
            .changeSettings(id, listOf(HabitSettingChange.Tracking(mode.copy(target = BigDecimal("5"))))) }
        screen(); bringDate(past)
        date(past).performClick()
        capture("quantity-old-target", true)
        compose.onNodeWithText("Target: 20 pages").assertIsDisplayed()
        compose.onNodeWithTag("correction-amount").performTextInput("7.5")
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { detail.state.value.entry == null && detail.state.value.value!!.record.completions.size == 1 }
        assertEquals(0, detail.state.value.value!!.evaluation.metrics.completed)
        date(today).performClick(); compose.onNodeWithText("Target: 5 pages").assertIsDisplayed()
        compose.onNodeWithTag("correction-amount").performTextInput("5"); compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { detail.state.value.value!!.evaluation.metrics.completed == 1 }
        date(past).performClick(); compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(5000) { detail.state.value.entry == null && detail.state.value.value!!.record.completions.size == 1 }
        assertEquals(today, detail.state.value.value!!.evaluation.history.logs.single().date)
    }
    @Test fun managementNestedBackReminderArchiveUndoAndDeleteUseRealHistory() {
        val id = create(full = true); screen()
        compose.onNodeWithContentDescription("Manage habit").performClick()
        compose.onNodeWithText("Delete habit").performClick()
        compose.onNodeWithText("Delete Read 20 pages?").assertIsDisplayed()
        back(); compose.onNodeWithText("Delete Read 20 pages?").assertDoesNotExist()
        compose.onNodeWithText("Archive habit").assertIsDisplayed()
        compose.onNodeWithText("Change reminder").performClick()
        compose.onNodeWithText("Daily reminders are off.", substring = true).assertIsDisplayed()
        back(); compose.onNodeWithText("Archive habit").performClick()
        compose.waitUntil(5000) { manager.state.value.undo != null && manager.state.value.id == null }
        assertEquals(15, runBlocking { history.record(id)!!.completions.size })
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { manager.state.value.undo == null && detail.state.value.value?.record?.habit?.archivedAt == null }
        compose.onNodeWithContentDescription("Manage habit").performClick(); compose.onNodeWithText("Delete habit").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil(5000) { detail.state.value.missing }
        assertNull(runBlocking { history.record(id) })
    }
    private fun visual(dark: Boolean, small: Boolean, full: Boolean) {
        create(full = full); screen(dark = dark, small = small)
        if (full) compose.onNodeWithText("Longest run yet.").assertIsDisplayed()
        val name = "detail-${if (dark) "dark" else "light"}-${if (small) "small" else "reference"}-${if (full) "healthy" else "risk"}"
        capture(name)
        bringDate(today.minusDays(1))
        date(today.minusDays(1)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val a = date(today.minusDays(1)).fetchSemanticsNode().boundsInRoot
        val b = date(today).fetchSemanticsNode().boundsInRoot
        assertTrue("Calendar targets must not overlap", a.right <= b.left)
        compose.onNodeWithText(if (full) "Ask the Coach" else "Get a plan").assertIsDisplayed()
        bringDate(YearMonth.from(today).atEndOfMonth())
        capture("$name-scrolled")
        compose.onNodeWithContentDescription("Manage habit").performClick()
        compose.onNodeWithText("Edit habit").assertIsDisplayed()
        capture("$name-sheet", true)
    }
    @Test fun lightHealthyReferenceVisual() { visual(false, false, true) }
    @Test fun darkRiskReferenceVisual() { visual(true, false, false) }
    @Test fun darkSmallLargeTextVisual() { visual(true, true, false) }
    @Test fun productionGraphLongPressEditCallerBackAndRemovalKeepValidDestinations() {
        val id = create(full = true)
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            val home = remember { HomeViewModel(HabitRepository(history), prefs, Dates(today), SavedStateHandle()).also { store.put("home", it) } }
            HabitTheme { Box {
                HabitNavigationGraph(nav, splash = { _, ready -> LaunchedEffect(Unit) { ready(false) } }, onboarding = {},
                    home = { add, coach, open, tab -> HomeScreen(add, coach, open, tab, home, manager::open) },
                    form = { entry, back ->
                        val form = remember(entry) { NewHabitViewModel(history, SavedStateHandle(mapOf("habitId" to id)), prefs.map { it.coachEnabled }).also { store.put("form", it) } }
                        NewHabitScreen(back, back, viewModel = form)
                    }, root = { _, _ -> TextButton(onClick = { nav.navigate(Routes.detail(id)) }) { Text("Progress detail fixture") } },
                    detail = { _, back -> HabitDetailScreen(back, manager::open, detail) })
                HabitManagementHost(manager, onEdit = { nav.navigate(Routes.editHabit(it)) }, onRemoved = { removed ->
                    if (nav.currentBackStackEntry?.destination?.route == Routes.HABIT_DETAIL && nav.currentBackStackEntry?.arguments?.getLong("habitId") == removed) nav.popBackStack()
                })
            } }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Read 20 pages").fetchSemanticsNodes().isNotEmpty() && manager.state.value.readable }
        compose.onNodeWithText("Read 20 pages").performTouchInput { longClick() }
        compose.onNodeWithText("Edit habit").performClick()
        compose.onNodeWithText("Edit habit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Routes.HOME, nav.currentDestination!!.route) }
        compose.onNodeWithText("Read 20 pages").performClick()
        compose.onNodeWithContentDescription("Manage habit").performClick(); compose.onNodeWithText("Edit habit").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Routes.HABIT_DETAIL, nav.currentDestination!!.route) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Progress").performClick()
        compose.onNodeWithText("Progress detail fixture").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Routes.PROGRESS, nav.currentDestination!!.route) }
        compose.onNodeWithText("Progress detail fixture").performClick()
        compose.onNodeWithContentDescription("Manage habit").performClick(); compose.onNodeWithText("Archive habit").performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Routes.PROGRESS && manager.state.value.undo != null }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { manager.state.value.undo == null && detail.state.value.value?.record?.habit?.archivedAt == null }
        compose.onNodeWithText("Progress detail fixture").performClick()
        compose.onNodeWithContentDescription("Manage habit").performClick(); compose.onNodeWithText("Delete habit").performClick(); compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Routes.PROGRESS && detail.state.value.missing }
        compose.onNodeWithText("Progress detail fixture").assertIsDisplayed()
    }
    @Test fun largeTextQuantityDialogKeepsInputAndSaveAccessibleWithNativeKeyboard() {
        create(mode = TrackingMode.Quantity(BigDecimal("20"), "pages")); screen(dark = true, small = true)
        bringDate(today.minusDays(1)); date(today.minusDays(1)).performClick()
        compose.onNodeWithTag("correction-amount").performTextInput("4.5")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithText("Save amount").assertIsDisplayed()
        capture("quantity-dark-small-keyboard", true)
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { detail.state.value.entry == null && detail.state.value.value!!.record.completions.size == 1 }
        assertEquals(BigDecimal("4.5"), (detail.state.value.value!!.evaluation.history.logs.single().value as CompletionValue.Quantity).amount)
    }
    @Suppress("DEPRECATION")
    @Test fun newHistoryIsNeutralAndRecreatedCorrectionKeepsAmountAndMonth() {
        val id = create(mode = TrackingMode.Quantity(BigDecimal.TEN, "pages"), newHabit = true)
        screen()
        compose.onNodeWithText("No settled history yet.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Get a plan").assertDoesNotExist()
        date(today.minusDays(1)).assertIsNotEnabled()
        bringDate(today); date(today).performClick()
        compose.onNodeWithTag("correction-amount").performTextInput("3.25")
        lateinit var bundle: android.os.Bundle
        compose.runOnIdle {
            // Save primitives through Bundle/Parcel, as Android's saved UI state does.
            val saved = detailSaved.keys().associateWith { detailSaved.get<Any?>(it) }
            bundle = android.os.Bundle().apply { saved.forEach { (key, value) -> when (value) {
                is Long -> putLong(key, value); is String -> putString(key, value)
            } } }
        }
        val parcel = android.os.Parcel.obtain()
        parcel.writeBundle(bundle); parcel.setDataPosition(0)
        val restored = parcel.readBundle(javaClass.classLoader)!!; parcel.recycle()
        compose.runOnIdle {
            store.put("detail", HabitDetailViewModel(id, HabitRepository(history), history, prefs, Dates(today), SavedStateHandle(restored.keySet().associateWith { restored.get(it) })).also { detail = it })
            detailRevision++
        }
        compose.waitUntil(5000) { !detail.state.value.loading }
        assertEquals("3.25", detail.state.value.entry!!.input)
        compose.onNodeWithTag("correction-amount").assertTextContains("3.25")
        compose.runOnIdle { detail.saveEntry() }
        compose.waitUntil(5000) { detail.state.value.entry == null }
        assertEquals(BigDecimal("3.25"), runBlocking { (history.record(id)!!.toHistory().logs.single().value as CompletionValue.Quantity).amount })
    }
    @Test fun customRestAndCoachDisabledHaveHonestUnavailableControls() {
        create(schedule = HabitSchedule.Custom(setOf(DayOfWeek.MONDAY)))
        prefs.value = prefs.value.copy(coachEnabled = false); screen()
        bringDate(today)
        date(today).assertIsNotEnabled()
        compose.onNodeWithText("Get a plan").assertIsNotEnabled()
        compose.onNodeWithText("Enable Coach suggestions in Profile.").assertIsDisplayed()
    }
}
