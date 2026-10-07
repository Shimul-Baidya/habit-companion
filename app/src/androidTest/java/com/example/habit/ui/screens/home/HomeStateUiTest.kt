package com.example.habit.ui.screens.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habit.data.local.HabitEntity
import com.example.habit.data.local.*
import com.example.habit.data.HabitSnapshot
import com.example.habit.domain.*
import com.example.habit.ui.components.HomeTab
import java.time.*
import com.example.habit.domain.HabitStatus
import com.example.habit.ui.components.HabitRow
import com.example.habit.ui.theme.HabitTheme
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStateUiTest {
    @get:Rule val compose = createComposeRule()
    private fun content(state: HomeUiState, retry: () -> Unit = {}) {
        compose.setContent { HabitTheme { HomeContent(state, retry, {}, {}, {}, {}, {}) } }
    }

    @Test fun failureShowsRetryInsteadOfEmptyHomeAndRetriesLocally() {
        var retries = 0
        content(HomeUiState(loading = false, readError = true)) { retries++ }
        compose.onNodeWithText("Could not open your habits. Tap to retry.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Add your first habit").assertDoesNotExist()
    }

    @Test fun loadingDoesNotExposeEmptyCreationOrFalseAllDoneState() {
        content(HomeUiState())
        compose.onNodeWithText("Add your first habit").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
    }

    @Test fun restDayControlHasNoEnabledCompletionAction() {
        var writes = 0
        val row = HabitStatus(HabitEntity(id = 1, name = "Rest test"), false, false, 1, false)
        compose.setContent { HabitTheme { HabitRow(row, { writes++ }, {}) } }
        compose.onNodeWithContentDescription("Rest test is not due today", useUnmergedTree = true).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun binaryCompletionExposesCheckedStateAndCorrectUndoLabel() {
        val row = HabitStatus(HabitEntity(id = 1, name = "Done test"), true, true, 1, false)
        compose.setContent { HabitTheme { HabitRow(row, {}, {}) } }
        compose.onNodeWithContentDescription("Mark Done test not done for today", useUnmergedTree = true)
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }
    @Test fun archivedOnlyHomeOffersProgressButDoesNotOpenAnExistingHabitCoach() {
        val day = LocalDate.of(2026, 10, 7)
        val created = day.minusDays(2)
        val record = HabitRecord(HabitEntity(id = 1, name = "Archived", createdEpochDay = created.toEpochDay(),
            archivedAt = day.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), archivedEpochDay = day.minusDays(1).toEpochDay()),
            listOf(ScheduleHistoryEntity.from(1, created, HabitSchedule.Daily)),
            listOf(TrackingHistoryEntity.from(1, created, TrackingMode.Binary)),
            listOf(CompletionEntity(1, created.toEpochDay(), completedAt = 1)))
        val snapshot = HabitSnapshot.from(listOf(record), day, DayOfWeek.MONDAY)
        var selected: HomeTab? = null
        compose.setContent { HabitTheme { HomeContent(HomeUiState(loading = false, today = day, snapshot = snapshot),
            {}, {}, {}, {}, {}, { selected = it }) } }
        compose.onNodeWithText("No active habits").assertIsDisplayed()
        compose.onNodeWithText("Add your first habit").assertDoesNotExist()
        compose.onNodeWithText("Progress").performClick()
        compose.runOnIdle { assertEquals(HomeTab.PROGRESS, selected); selected = null }
        compose.onNodeWithText("Coach").performClick()
        compose.runOnIdle { assertEquals(null, selected) }
        compose.onNodeWithText("Add an active habit to use this Coach tab. You can plan a new habit from Home.").assertExists()
    }
}
