package com.example.habit.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.onboarding.*
import com.example.habit.ui.screens.splash.*
import com.example.habit.ui.theme.HabitTheme
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var nav: NavHostController
    private var persisted = false
    @After fun cleanup() { compose.runOnIdle { store.clear() } }
    @Composable private fun Graph(firstLaunch: Boolean) {
        nav = rememberNavController()
        val splashVm = remember { SplashViewModel(flowOf(!firstLaunch), {}).also { store.put("splash", it) } }
        val setupVm = remember { OnboardingViewModel({ persisted = true }, androidx.lifecycle.SavedStateHandle()).also { store.put("setup", it) } }
        HabitTheme {
            HabitNavigationGraph(nav,
                splash = { setup, home -> SplashScreen(setup, home, splashVm) },
                onboarding = { finished -> OnboardingScreen(finished, setupVm) },
                home = { add, coach, detail, select ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        TextButton(onClick = { count++ }) { Text("Home count $count") }
                        TextButton(onClick = add) { Text("Add fixture") }
                        TextButton(onClick = coach) { Text("Plan fixture") }
                        TextButton(onClick = { detail(42) }) { Text("Open habit") }
                        HomeTab.entries.forEach { tab -> TextButton(onClick = { select(tab) }) { Text("Go ${tab.name}") } }
                    }
                },
                form = { entry, back -> Column {
                    Text("Planning ${entry.arguments?.getBoolean("planning")}")
                    TextButton(onClick = back) { Text("Form Back") }
                } },
                root = { tab, select ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        TextButton(onClick = { count++ }) { Text("${tab.name} count $count") }
                        TextButton(onClick = { nav.navigate(Routes.detail(7)) }) { Text("Root detail") }
                        HomeTab.entries.forEach { target -> TextButton(onClick = { select(target) }) { Text("Go ${target.name}") } }
                    }
                },
                detail = { id, back -> Column {
                    Text("Detail $id")
                    TextButton(onClick = back) { Text("Detail Back") }
                } })
        }
    }
    private fun waitHome() { compose.waitUntil(5000) { compose.onAllNodesWithText("Home count 0").fetchSemanticsNodes().isNotEmpty() } }
    private fun absentLaunch() { compose.runOnIdle {
        assertThrows(IllegalArgumentException::class.java) { nav.getBackStackEntry(Routes.SPLASH) }
        assertThrows(IllegalArgumentException::class.java) { nav.getBackStackEntry(Routes.ONBOARDING) }
    } }
    @Test fun firstLaunchPaneBackContinuePersistsBeforeRemovingSetup() {
        compose.setContent { Graph(true) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithContentDescription("Pane 2 of 3").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Pane 1 of 3").assertExists()
        repeat(3) { compose.onNodeWithText("Continue").performClick() }
        waitHome(); compose.runOnIdle { assertTrue(persisted) }; absentLaunch()
        compose.onNodeWithText("Go PROFILE").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home count 0").assertExists(); absentLaunch()
    }
    @Test fun skipPersistsAndPlanningReturnsToRetainedHome() {
        compose.setContent { Graph(true) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Skip").performClick(); waitHome(); absentLaunch()
        compose.runOnIdle { assertTrue(persisted) }
        compose.onNodeWithText("Plan fixture").performClick()
        compose.onNodeWithText("Planning true").assertExists()
        compose.onNodeWithText("Form Back").performClick(); compose.onNodeWithText("Home count 0").assertExists()
    }
    @Test fun subsequentLaunchRapidTabsStateRestorationAndDetailCaller() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Graph(false) }; waitHome(); absentLaunch()
        compose.onNodeWithText("Home count 0").performClick()
        compose.onNodeWithText("Go PROGRESS").performClick()
        compose.onNodeWithText("PROGRESS count 0").performClick()
        compose.onNodeWithText("Go PROFILE").performClick()
        compose.onNodeWithText("Go COACH").performClick()
        compose.onNodeWithText("Go PROGRESS").performClick()
        compose.onNodeWithText("Go PROGRESS").performClick() // same-tab repeated tap must not stack
        compose.onNodeWithText("PROGRESS count 1").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("PROGRESS count 1").assertExists()
        compose.onNodeWithText("Root detail").performClick()
        compose.onNodeWithText("Detail 7").assertExists()
        compose.onNodeWithText("Detail Back").performClick()
        compose.onNodeWithText("PROGRESS count 1").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home count 1").assertExists()
        compose.onNodeWithText("Open habit").performClick()
        compose.onNodeWithText("Detail 42").assertExists()
        compose.runOnIdle { assertEquals(Routes.HABIT_DETAIL, nav.currentDestination?.route) }
        compose.onNodeWithText("Detail Back").performClick()
        compose.onNodeWithText("Home count 1").assertExists(); absentLaunch()
    }
}
