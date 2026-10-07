package com.example.habit.ui.screens.newhabit

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.local.*
import com.example.habit.domain.TrackingMode
import java.time.LocalDate
import com.example.habit.domain.HabitSchedule
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HabitFormUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private class Forms : HabitFormDataSource {
        override val records = MutableStateFlow<List<HabitRecord>>(emptyList())
        var writes = 0
        var failure = false
        var sentId: Long? = null
        var allowedDuplicate = false
        override suspend fun saveForm(id: Long?, draft: HabitDraft, original: HabitDraft?, allowDuplicate: Boolean): Long {
            writes++; sentId = id; allowedDuplicate = allowDuplicate; if (failure) throw IOException("isolated test"); return id ?: 1
        }
    }
    @After fun clear() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    private fun model(forms: Forms, id: Long = 0): NewHabitViewModel {
        lateinit var vm: NewHabitViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = NewHabitViewModel(forms, SavedStateHandle(mapOf(NewHabitViewModel.ARG_HABIT_ID to id)), MutableStateFlow(true)); store.put("form", vm)
        }
        return vm
    }
    private fun screenshot(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chunk04-qa").apply { mkdirs() }
        val file = File(directory, name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun weeklyQuotaCustomWeekdaysAndQuantityFieldsAreDistinct() {
        var state by mutableStateOf(HabitFormUiState(HabitFormDraft(name = "Read"), false, loading = false))
        compose.setContent { HabitTheme { HabitFormContent(state, onBack = {}, onChange = { state = state.copy(draft = it, showValidation = true) },
            onDay = { state = state.copy(draft = state.draft.copy(weekdayMask = state.draft.weekdayMask xor (1 shl (it.value - 1)))) },
            onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) } }
        compose.onNodeWithText("Weekly").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Completions per week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monday").assertDoesNotExist()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithContentDescription("Monday").assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Monday").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithText("Quantity", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Target amount").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Enter a positive amount.").assertExists()
        compose.onNodeWithText("Create habit").assertIsNotEnabled()
    }
    @Test fun disabledCoachIsHiddenAndBlankNameCannotBeSaved() {
        compose.setContent { HabitTheme { HabitFormContent(HabitFormUiState(HabitFormDraft(), false, loading = false, coachEnabled = false),
            onBack = {}, onChange = {}, onDay = {}, onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) } }
        compose.onNodeWithText("Not sure how to make this stick?").assertDoesNotExist()
        compose.onNodeWithText("Create habit").assertIsNotEnabled()
    }
    @Test fun dirtyBackDialogConsumesBackAndDiscardWritesNothing() {
        val forms = Forms(); val vm = model(forms); var backs = 0
        compose.setContent { HabitTheme { NewHabitScreen({ backs++ }, {}, viewModel = vm) } }
        compose.onNodeWithContentDescription("Habit name").performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Habit name").assertTextContains("Unsaved")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, forms.writes) }
    }
    @Test fun failedKeyboardSubmitRetainsDraftAndShowsRetryableSaveError() {
        val forms = Forms().apply { failure = true }; val vm = model(forms)
        compose.setContent { HabitTheme { NewHabitScreen({}, {}, viewModel = vm) } }
        compose.onNodeWithContentDescription("Habit name").performTextInput("Read")
        compose.onNodeWithContentDescription("Habit name").performImeAction()
        compose.onNodeWithText("Could not save. Your changes are kept; please try again.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Habit name").assertTextContains("Read")
        compose.onNodeWithText("Create habit").assertIsEnabled()
        compose.runOnIdle { assertEquals(1, forms.writes) }
    }
    private fun record(): HabitRecord {
        val date = LocalDate.of(2026, 10, 5)
        return HabitRecord(HabitEntity(id = 7, name = "Read", iconKey = "book", colorKey = "purple", createdEpochDay = date.toEpochDay()),
            listOf(ScheduleHistoryEntity.from(7, date, HabitSchedule.Daily)), listOf(TrackingHistoryEntity.from(7, date, TrackingMode.Binary)), emptyList())
    }
    @Test fun duplicateDialogRequiresConfirmationAndPassesExplicitOverride() {
        val forms = Forms().apply { records.value = listOf(record()) }; val vm = model(forms)
        compose.setContent { HabitTheme { NewHabitScreen({}, {}, viewModel = vm) } }
        compose.onNodeWithContentDescription("Habit name").performTextInput(" read ")
        compose.onNodeWithText("Create habit").performClick()
        compose.onNodeWithText("Use this name again?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, forms.writes) }
        compose.onNodeWithText("Create anyway").performClick()
        compose.runOnIdle { assertEquals(1, forms.writes); assertTrue(forms.allowedDuplicate) }
    }
    @Test fun editUsesSharedFormAndSavesTheLoadedId() {
        val forms = Forms().apply { records.value = listOf(record()) }; val vm = model(forms, 7); var saved = 0
        compose.setContent { HabitTheme { NewHabitScreen({}, { saved++ }, viewModel = vm) } }
        compose.onNodeWithText("Edit habit").assertIsDisplayed()
        compose.onNodeWithText("Create habit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Habit name").assertTextContains("Read").performTextReplacement("Read more")
        compose.onNodeWithText("Save changes").performClick()
        compose.runOnIdle { assertEquals(1, saved); assertEquals(7L, forms.sentId); assertEquals(1, forms.writes) }
    }

    @Test fun planningDetourReturnsToSameUnsavedDraftAndTypedApplyDoesNotInsert() {
        val forms = Forms()
        val factory = viewModelFactory { initializer { NewHabitViewModel(forms, createSavedStateHandle(), MutableStateFlow(true)) } }
        var entry: FormCoachEntry.Planning? = null
        lateinit var owner: NewHabitViewModel
        compose.setContent {
            HabitTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "form") {
                    composable("form") { backStack ->
                        owner = viewModel(backStack, factory = factory)
                        val pending by backStack.savedStateHandle.getStateFlow<ArrayList<String>?>(PlanningDraftContract.RESULT_KEY, null).collectAsState()
                        NewHabitScreen({}, {}, planningResult = pending,
                            onPlanningResultConsumed = { backStack.savedStateHandle[PlanningDraftContract.RESULT_KEY] = null }, onOpenCoach = { request -> entry = request as FormCoachEntry.Planning; nav.navigate("detour") }, viewModel = owner)
                    }
                    composable("detour") {
                        androidx.compose.material3.TextButton(onClick = {
                            nav.getBackStackEntry("form").savedStateHandle[PlanningDraftContract.RESULT_KEY] = PlanningDraftContract.encode(DraftPlanningResult(requireNotNull(entry).request.token, DraftPlanningChange.Schedule(HabitSchedule.Weekly(2))))
                            nav.popBackStack()
                        }) { androidx.compose.material3.Text("Apply test schedule") }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Habit name").performTextInput("Read draft")
        compose.onNodeWithText("Not sure how to make this stick?").performScrollTo().performClick()
        compose.onNodeWithText("Apply test schedule").performClick()
        compose.onNodeWithContentDescription("Habit name").performScrollTo().assertTextContains("Read draft")
        compose.onNodeWithContentDescription("Completions per week").performScrollTo().assertTextContains("2")
        compose.runOnIdle { assertEquals(0, forms.writes) }
    }
    @Test fun referenceLightFormUsesOriginalComposition() { referenceForm(false) }
    @Test fun referenceDarkFormUsesOriginalComposition() { referenceForm(true) }
    private fun referenceForm(dark: Boolean) {
        val name = if (dark) "chunk13-form-dark-reference" else "chunk04-form-light"
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(393.dp, 832.dp))) {
            HabitTheme(darkTheme = dark) { HabitFormContent(HabitFormUiState(HabitFormDraft(name = "Read 20 pages", iconKey = "book", colorKey = "purple"), false, loading = false, coachEnabled = true),
                onBack = {}, onChange = {}, onDay = {}, onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) }
        } }
        compose.onNodeWithText("Create habit").assertIsDisplayed()
        screenshot("$name.png")
        compose.onNodeWithText("Not sure how to make this stick?").performScrollTo()
        screenshot("$name-scrolled.png")
    }
    @Test fun darkSmallLargeTextQuantityFormKeepsDockedSaveAccessibleWithKeyboard() {
        compose.runOnIdle { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        var state by mutableStateOf(HabitFormUiState(HabitFormDraft(name = "Read", tracking = "QUANTITY", target = "5", unit = "pages"), false, loading = false, coachEnabled = true))
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                HabitTheme(darkTheme = true) { HabitFormContent(state, onBack = {}, onChange = { state = state.copy(draft = it) }, onDay = {},
                    onSave = {}, onCoach = {}, onRetry = {}, onReload = {}) }
            }
        } }
        compose.onNodeWithText("Create habit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unit (for example, pages or minutes)").performScrollTo().performClick().performTextReplacement("minutes")
        compose.waitUntil(timeoutMillis = 5000) {
            compose.runOnIdle { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        }
        compose.onNodeWithText("Create habit").assertIsDisplayed().assertIsEnabled()
        screenshot("chunk04-form-dark-small-keyboard.png")
        compose.onNodeWithText("Not sure how to make this stick?").performScrollTo().assertIsDisplayed()
    }
}
