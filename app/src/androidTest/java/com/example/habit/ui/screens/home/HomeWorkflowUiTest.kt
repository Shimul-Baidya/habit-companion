package com.example.habit.ui.screens.home

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.DpSize
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.math.BigDecimal

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeWorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 5)
    private lateinit var db: HabitDatabase
    private lateinit var history: HabitHistoryRepository
    private lateinit var vm: HomeViewModel
    private val store = ViewModelStore()
    private val prefs = MutableStateFlow(SettingsRepository.Configuration())
    private class Dates(val date: LocalDate) : DateProvider {
        override val dates = MutableStateFlow(date)
        override fun today() = date
        override fun refresh() {}
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java).build()
        history = HabitHistoryRepository(db, Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
        compose.runOnIdle { vm = HomeViewModel(HabitRepository(history), prefs, Dates(date), SavedStateHandle()); store.put("home", vm) }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    private fun screen(open: (Long) -> Unit = {}) {
        compose.setContent { HabitTheme { HomeScreen({}, {}, open, {}, vm) } }
    }
    private fun waitRead() { compose.waitUntil(5000) { !vm.state.value.loading } }
    @Test fun binaryAndQuantityRingsPersistWithoutNavigatingAndClearRecomputes() = runBlocking {
        history.create(HabitDraft("Read", settings = HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("10"), "pages"))))
        val binary = history.create(HabitDraft("Walk")); var opened = 0L
        screen { opened = it }; waitRead()
        compose.onNodeWithContentDescription("Mark Walk done for today").assertWidthIsAtLeast(48.dp)
            .performClick()
        compose.waitUntil(5000) { vm.state.value.doneToday == 1 }
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("2.5")
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { vm.state.value.quantity == null && vm.state.value.allHabits.first { it.habit.name == "Read" }.progressToday == 0.25f }
        compose.onNodeWithText("2.5 / 10 pages").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2.5 of 10 pages; target not yet reached"))
        compose.runOnIdle { assertEquals(0L, opened) }
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read").performClick()
        compose.onNodeWithContentDescription("Amount").performTextReplacement("10")
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { vm.state.value.allDone }
        compose.onNodeWithText("All done for today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read").performClick()
        compose.onNodeWithText("Clear amount").performClick()
        compose.waitUntil(5000) { vm.state.value.quantity == null && vm.state.value.doneToday == 1 }
        compose.onNodeWithText("Walk").performClick(); compose.runOnIdle { assertEquals(binary, opened) }
        assertEquals(1, history.records.first().sumOf { it.completions.size })
    }
    @Test fun amountDialogBackRetainsStoredFactsAndRestDatesCannotWrite() = runBlocking {
        history.create(HabitDraft("Rest", settings = HabitSettings(HabitSchedule.Custom(setOf(DayOfWeek.TUESDAY)))))
        history.create(HabitDraft("Read", settings = HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("5"), "pages"))))
        screen(); waitRead()
        compose.onNodeWithContentDescription("Rest is not due today").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("3")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Today’s amount").assertDoesNotExist()
        assertTrue(history.records.first().all { it.completions.isEmpty() })
    }
    @Test fun weeklyQuantityQuotaStopsExtraActionsWithoutInflatingCounts() = runBlocking {
        history.create(HabitDraft("Quota", settings = HabitSettings(HabitSchedule.Weekly(1), TrackingMode.Quantity(BigDecimal("1"), "km"))))
        screen(); waitRead()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Quota").performClick()
        compose.onNodeWithContentDescription("Amount").performTextInput("1")
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { vm.state.value.doneToday == 1 }
        assertEquals(1L, vm.state.value.snapshot!!.lifetime.completed)
        assertEquals(1, vm.state.value.allHabits.single().currentStreak)
        compose.onNodeWithContentDescription("Enter or change today’s amount for Quota").assertIsEnabled()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Quota").performClick()
        compose.onNodeWithText("Clear amount").performClick()
        compose.waitUntil(5000) { vm.state.value.quantity == null && vm.state.value.doneToday == 0 }
        assertEquals(0L, vm.state.value.snapshot!!.lifetime.completed)
    }
    @Test fun disabledCoachShortcutHiddenAndSettingsChangesUpdateHome() {
        prefs.value = prefs.value.copy(coachEnabled = false); screen(); waitRead()
        compose.onNodeWithText("Not sure where to start?").assertDoesNotExist()
        prefs.value = prefs.value.copy(coachEnabled = true)
        compose.waitUntil(5000) { vm.state.value.coachEnabled }
        compose.onNodeWithText("Not sure where to start?").assertExists()
    }
    @Test fun coachRootExplainsDisabledStateAndRespondsWhileOpen() {
        val enabled = MutableStateFlow(false)
        lateinit var availability: com.example.habit.ui.navigation.CoachAvailabilityViewModel
        compose.runOnIdle { availability = com.example.habit.ui.navigation.CoachAvailabilityViewModel(enabled); store.put("coach", availability) }
        compose.setContent { HabitTheme { com.example.habit.ui.navigation.CoachRoot({}, availability) } }
        compose.onNodeWithText("Coach is disabled").assertIsDisplayed()
        compose.onNodeWithText("Enable Coach suggestions in Profile", substring = true).assertIsDisplayed()
        enabled.value = true
        compose.waitUntil(5000) { availability.enabled.value }
        compose.onNodeWithText("Coach is disabled").assertDoesNotExist()
    }

    @Test fun quantityDialogLargeTextWithKeyboardKeepsInputAndSaveAccessible() = runBlocking {
        history.create(HabitDraft("Read", settings = HabitSettings(HabitSchedule.Daily, TrackingMode.Quantity(BigDecimal("10"), "pages"))))
        compose.runOnIdle { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                    HabitTheme(darkTheme = true) { HomeScreen({}, {}, {}, {}, vm) }
                }
            }
        }
        waitRead()
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read").performClick()
        compose.onNodeWithContentDescription("Amount").performClick().performTextReplacement("2.5")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            compose.waitUntil(5000) {
                compose.runOnIdle {
                    android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                        androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                    }
                }
            }
        }
        compose.onNodeWithText("Save amount").assertIsDisplayed().assertIsEnabled()
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk05-qa").apply { mkdirs() }
        java.io.File(directory, "quantity-dark-small-keyboard.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Save amount").performClick()
        compose.waitUntil(5000) { vm.state.value.quantity == null && vm.state.value.allHabits.single().progressToday == 0.25f }
        assertEquals("2.5", history.records.first().single().completions.single().quantityAmount)
    }

}
