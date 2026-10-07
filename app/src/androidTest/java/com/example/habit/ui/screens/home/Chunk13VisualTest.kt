package com.example.habit.ui.screens.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.R
import com.example.habit.data.HabitSnapshot
import com.example.habit.data.local.*
import com.example.habit.domain.*
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.newhabit.*
import com.example.habit.ui.screens.onboarding.OnboardingContent
import com.example.habit.ui.theme.HabitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

/** Actual production composables, synthetic state only. Never saves or opens a live Coach. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class Chunk13VisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private data class Frame(val name: String, val dark: Boolean, val small: Boolean = false)
    private val frames = listOf(Frame("light-reference", false), Frame("dark-reference", true), Frame("dark-small-large-text", true, true))
    private fun render(content: @Composable () -> Unit): (Frame) -> Unit {
        var frame by mutableStateOf(frames.first())
        compose.runOnIdle { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            key(frame) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (frame.small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (frame.small) 1.5f else 1f)) {
                        HabitTheme(darkTheme = frame.dark) { content() }
                    }
                }
            }
        }
        return { next -> compose.runOnIdle { frame = next } }
    }
    private fun capture(name: String, native: Boolean = false) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk13-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            val bitmap = if (native) InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                else compose.onRoot().captureToImage().asAndroidBitmap()
            requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun archivedOnlyHomeKeepsProgressAndPlanningAccessibleAcrossFrames() {
        val day = LocalDate.of(2026, 10, 7)
        val created = day.minusDays(2)
        val record = HabitRecord(HabitEntity(id = 1, name = "Synthetic archive", createdEpochDay = created.toEpochDay(),
            archivedAt = 1, archivedEpochDay = day.minusDays(1).toEpochDay()),
            listOf(ScheduleHistoryEntity.from(1, created, HabitSchedule.Daily)),
            listOf(TrackingHistoryEntity.from(1, created, TrackingMode.Binary)),
            listOf(CompletionEntity(1, created.toEpochDay(), completedAt = 1)))
        val state = HomeUiState(loading = false, today = day, coachEnabled = true, snapshot = HabitSnapshot.from(listOf(record), day, DayOfWeek.MONDAY))
        var selected: HomeTab? = null
        var planned = 0
        val frame = render { HomeContent(state, {}, {}, {}, { planned++ }, {}, { selected = it }) }
        frames.forEachIndexed { index, configuration ->
            frame(configuration)
            compose.onNodeWithText("No active habits").assertIsDisplayed()
            compose.onNodeWithText("Add habit").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            capture("archived-home-${configuration.name}")
            compose.onNodeWithText("Progress").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(HomeTab.PROGRESS, selected) }
            compose.onNodeWithText("Not sure where to start?").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(index + 1, planned) }
            capture("archived-planning-${configuration.name}")
        }
    }
    @Test fun editMeasuredContextDisclosureGrowsAndKeepsSaveAccessibleAcrossFrames() {
        val body = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.form_existing_coach_body)
        val state = HabitFormUiState(HabitFormDraft(name = "Synthetic reading", frequency = "WEEKLY", quota = "3",
            tracking = "QUANTITY", target = "5", unit = "pages"), editing = true, loading = false, coachEnabled = true)
        val frame = render { HabitFormContent(state, onBack = {}, onChange = {}, onDay = {}, onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) }
        frames.forEach { configuration ->
            frame(configuration)
            compose.onNodeWithText("Edit habit").assertIsDisplayed()
            compose.onNodeWithText(body).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Not sure how to make this stick?").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            compose.onNodeWithText("Save changes").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Get a starting plan with Google Gemini.", substring = true).assertDoesNotExist()
            capture("edit-privacy-${configuration.name}")
            compose.onNodeWithContentDescription("Unit (for example, pages or minutes)").performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.runOnIdle { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            }
            compose.onNodeWithText("Save changes").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            capture("edit-keyboard-${configuration.name}", native = true)
            compose.runOnIdle { WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
            compose.waitUntil(5000) {
                compose.runOnIdle { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true }
            }
        }
    }
    @Test fun quantityWeeklyOnboardingCopyAndContinueFitAcrossFrames() {
        val body = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.onboarding_body_2)
        val frame = render { OnboardingContent(rememberPagerState(initialPage = 1, pageCount = { 3 }), {}, {}) }
        frames.forEach { configuration ->
            frame(configuration)
            compose.onNodeWithText(body).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Continue").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            capture("onboarding-occurrences-${configuration.name}")
        }
    }
}
