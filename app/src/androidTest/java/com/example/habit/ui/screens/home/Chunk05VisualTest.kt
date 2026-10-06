package com.example.habit.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.rememberPagerState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.domain.*
import com.example.habit.ui.screens.onboarding.*
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.math.BigDecimal

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class Chunk05VisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 6)
    private fun snapshot(): HabitSnapshot {
        fun record(id: Long, name: String, icon: String, color: String, quantity: Boolean = false, risk: Boolean = false): HabitRecord {
            val created = date.minusDays(4)
            return HabitRecord(HabitEntity(id, name, iconKey = icon, colorKey = color, createdEpochDay = created.toEpochDay()),
                listOf(ScheduleHistoryEntity.from(id, created, HabitSchedule.Daily)),
                listOf(TrackingHistoryEntity.from(id, created, if (quantity) TrackingMode.Quantity(BigDecimal("10"), "pages") else TrackingMode.Binary)),
                if (quantity) listOf(CompletionEntity(id, date.toEpochDay(), completedAt = 1, trackingMode = "QUANTITY", quantityAmount = "2.5", quantityUnit = "pages"))
                else (if (risk) listOf(date.minusDays(1)) else (0L..4L).map { created.plusDays(it) }).map { CompletionEntity(id, it.toEpochDay(), completedAt = 1) })
        }
        return HabitSnapshot.from(listOf(record(1, "Drink water", "mindful", "blue"), record(2, "Morning run", "activity", "teal"),
            record(3, "Read 20 pages", "book", "purple", quantity = true), record(4, "Meditate", "leaf", "green", risk = true)), date, DayOfWeek.MONDAY)
    }
    private fun state(empty: Boolean): HomeUiState {
        if (empty) return HomeUiState(loading = false, userName = "Shimul", today = date, coachEnabled = true)
        val snap = snapshot()
        return HomeUiState(loading = false, userName = "Shimul", today = date, coachEnabled = true,
            healthy = snap.active.filterNot { it.atRisk }, atRisk = snap.active.filter { it.atRisk }, snapshot = snap, bestStreak = snap.allTimeBest)
    }
    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk05-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun homeVisual(dark: Boolean, small: Boolean) {
        var empty by mutableStateOf(true)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) {
                    HabitTheme(darkTheme = dark) { HomeContent(state(empty), {}, {}, {}, {}, {}, {}) }
                }
            }
        }
        val name = "home-${if (dark) "dark" else "light"}-${if (small) "small" else "reference"}"
        compose.onNodeWithText("Add your first habit").assertIsDisplayed(); capture("$name-empty")
        compose.runOnIdle { empty = false }
        compose.onNodeWithText("Drink water").assertIsDisplayed(); capture("$name-populated")
        compose.onNodeWithTag("home-habits").performScrollToNode(hasText("Meditate"))
        compose.onNodeWithText("Meditate").assertIsDisplayed(); capture("$name-scrolled")
        val completionBounds = compose.onNodeWithContentDescription("Mark Meditate done for today").fetchSemanticsNode().boundsInRoot
        val addBounds = compose.onNodeWithContentDescription("Add habit").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Add must not overlap a visible completion target", completionBounds.bottom <= addBounds.top)
        compose.onNodeWithTag("home-habits").performScrollToNode(hasText("Read 20 pages"))
        compose.onNodeWithContentDescription("Enter or change today’s amount for Read", substring = true).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
    @Test fun lightHomeReference() { homeVisual(false, false) }
    @Test fun darkHomeReference() { homeVisual(true, false) }
    @Test fun darkHomeSmallLargeText() { homeVisual(true, true) }

    private fun onboardingVisual(dark: Boolean, small: Boolean) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) {
                    HabitTheme(darkTheme = dark) {
                        val pager = rememberPagerState(pageCount = { 3 })
                        val scope = rememberCoroutineScope()
                        OnboardingContent(pager, {}, { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(2)) } })
                    }
                }
            }
        }
        val name = "onboarding-${if (dark) "dark" else "light"}-${if (small) "small" else "reference"}"
        compose.onNodeWithText("Continue").assertIsDisplayed(); capture("$name-pane1")
        repeat(2) { compose.onNodeWithText("Continue").performClick() }
        compose.onNodeWithText("Continue").assertIsDisplayed(); capture("$name-pane3")
        compose.onNodeWithText("The Coach uses", substring = true).performScrollTo().assertIsDisplayed()
        capture("$name-pane3-scrolled")
        compose.onNodeWithText("Restore a backup").assertIsNotEnabled()
    }
    @Test fun lightOnboardingReference() { onboardingVisual(false, false) }
    @Test fun darkOnboardingReference() { onboardingVisual(true, false) }
    @Test fun darkOnboardingSmallLargeText() { onboardingVisual(true, true) }
}
