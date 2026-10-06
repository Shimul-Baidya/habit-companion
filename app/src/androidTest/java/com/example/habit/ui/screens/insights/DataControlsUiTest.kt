package com.example.habit.ui.screens.insights

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.*
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.*
import com.example.habit.data.controls.*
import com.example.habit.data.local.*
import com.example.habit.data.prefs.*
import com.example.habit.domain.*
import com.example.habit.ui.controls.*
import com.example.habit.ui.management.*
import com.example.habit.ui.navigation.*
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.profile.*
import com.example.habit.ui.theme.HabitTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.time.*

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DataControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val day = LocalDate.of(2026, 10, 6)
    private val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val job = SupervisorJob()
    private val models = ViewModelStore()
    private lateinit var db: HabitDatabase
    private lateinit var file: File
    private lateinit var settings: SettingsRepository
    private lateinit var history: HabitHistoryRepository
    private lateinit var data: LocalDataControls
    private lateinit var profile: ProfileViewModel
    private lateinit var actions: ProfileActionsViewModel
    private lateinit var manager: HabitManagementViewModel
    private val actionSaved = SavedStateHandle()
    private class Dates(private val day: LocalDate) : DateProvider {
        override val dates = flowOf(day); override fun today() = day; override fun refresh() {}
    }
    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java).build()
        file = File(context.cacheDir, "chunk08-ui-${System.nanoTime()}.preferences_pb")
        val gate = DataGate()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }, gate)
        history = HabitHistoryRepository(db, clock, gate)
        data = LocalDataControls(db, settings, gate, clock) {}
        data.initialize(); settings.setOnboardingComplete(true)
        compose.runOnIdle {
            profile = ProfileViewModel(HabitRepository(history), settings, Dates(day), SavedStateHandle())
            actions = ProfileActionsViewModel(LocalProfileActions(settings, data) { uri ->
                requireNotNull(context.contentResolver.openOutputStream(android.net.Uri.parse(uri), "wt"))
            }, actionSaved)
            manager = HabitManagementViewModel(HabitRepository(history), history, settings.configuration, Dates(day), SavedStateHandle(), changeReminder = history::setReminder)
            models.put("profile", profile); models.put("actions", actions); models.put("manager", manager)
        }
    }
    @After fun finish() { setQaRoot(false); compose.runOnIdle { models.clear() }; job.cancel(); db.close(); file.delete() }
    private fun screen(dark: Boolean = false, small: Boolean = false) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(if (small) DpSize(360.dp, 640.dp) else DpSize(393.dp, 832.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (small) 1.5f else 1f)) {
                    HabitTheme(darkTheme = dark) { Box(Modifier.fillMaxSize().testTag("qa-root")) { ProfileScreen({}, profile, actions) } }
                }
            }
        }
        compose.waitUntil(5000) { profile.state.value.preferences != null }
    }
    private fun scroll(label: String) = compose.onNodeWithTag("profile-scroll").performScrollToNode(hasText(label))
    private fun back() { androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle() }
    private fun capture(name: String, dialog: Boolean = false) {
        val image = if (dialog) compose.onAllNodes(isDialog()).let { it[it.fetchSemanticsNodes().lastIndex] }.captureToImage() else compose.onNodeWithTag("qa-root").captureToImage()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "chunk08-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun globalReminderWritesImmediatelyAndNestedTimeCancelKeepsSetting() {
        screen(); scroll("Daily reminder"); compose.onNodeWithContentDescription("Profile daily reminder").performClick()
        compose.waitUntil(5000) { runBlocking { settings.configuration.first().reminderEnabled } && !actions.state.value.busy }
        compose.onNodeWithText("Android may delay", substring = true).assertIsDisplayed()
        capture("global-light", dialog = true)
        compose.onNodeWithText("Time · 20:00").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("21")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1200, runBlocking { settings.configuration.first().reminderMinute })
        compose.onNodeWithText("Time · 20:00").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("21")
        compose.onNodeWithText("Set time").performClick()
        compose.waitUntil(5000) { runBlocking { settings.configuration.first().reminderMinute } == 1260 }
        back(); assertNull(actions.state.value.dialog)
    }
    @Test fun perHabitInheritedOverrideOffAndBackReturnToManagementSheet() {
        val id = runBlocking { history.create(HabitDraft("Read")) }
        compose.setContent { HabitTheme { Box { Text("Underlying detail"); HabitManagementHost(manager, {}, {}) } } }
        compose.waitUntil(5000) { manager.state.value.readable }
        compose.runOnIdle { manager.open(id) }
        compose.onNodeWithText("Change reminder").performClick()
        compose.onNodeWithText("Custom time · 20:00").performClick()
        compose.onNodeWithText("Set time").performClick()
        compose.waitUntil(5000) { runBlocking { history.record(id)!!.habit.reminderEnabled == true } }
        compose.onNodeWithText("Off for this habit").performClick()
        compose.waitUntil(5000) { runBlocking { history.record(id)!!.habit.reminderEnabled == false } }
        compose.onNodeWithText("Use daily reminder", substring = true).performClick()
        compose.waitUntil(5000) { runBlocking { history.record(id)!!.habit.reminderEnabled == null } }
        back(); compose.waitUntil(5000) { !manager.state.value.reminder }; assertEquals(id, manager.state.value.id)
        back(); compose.waitUntil(5000) { manager.state.value.id == null }
    }
    @Test fun nativeExportPickerCancelWritesNothingAndRestoreStaysDisabled() {
        runBlocking { history.create(HabitDraft("Retain")) }
        screen(); scroll("Export data"); compose.onNodeWithText("Restore data").assertIsNotEnabled()
        compose.onNodeWithText("Export data").performClick(); capture("export-light", dialog = true)
        compose.onNodeWithText("Choose location").performClick()
        compose.waitUntil(7000) {
            documentsRoot() != null
        }
        repeat(3) {
            if (actions.state.value.pickerPending) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
                try { compose.waitUntil(2000) { !actions.state.value.pickerPending } } catch (_: ComposeTimeoutException) { /* Back may first close the native keyboard. */ }
            }
        }
        assertFalse(actions.state.value.pickerPending)
        assertNull(actions.state.value.dialog); assertNull(actionSaved.get<String>("exportUri")); assertEquals(1, runBlocking { db.historyDao().records().size })
    }
    private fun setQaRoot(active: Boolean) {
        val done = java.util.concurrent.CountDownLatch(1)
        var result = android.app.Activity.RESULT_CANCELED
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.sendOrderedBroadcast(android.content.Intent("com.example.habit.test.QA_EXPORT_ROOT")
            .setComponent(android.content.ComponentName("com.example.habit.test", "com.example.habit.ExportQaControlReceiver"))
            .putExtra("active", active), null, object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) { result = resultCode; done.countDown() }
            }, android.os.Handler(android.os.Looper.getMainLooper()), android.app.Activity.RESULT_CANCELED, null, null)
        assertTrue("QA root control did not finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("QA root control was rejected", android.app.Activity.RESULT_OK, result)
    }
    private fun documentsRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        return automation.windows.mapNotNull { it.root }.firstOrNull { it.packageName?.toString()?.contains("documentsui") == true }
    }
    private fun nativeNode(text: String): android.view.accessibility.AccessibilityNodeInfo? {
        val root = documentsRoot() ?: return null
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.text?.toString()?.equals(text, ignoreCase = true) == true || node.contentDescription?.toString()?.equals(text, ignoreCase = true) == true) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it) }?.let { return it }
            return null
        }
        return find(root)
    }
    private fun nativeClick(text: String) {
        compose.waitUntil(7000) {
            var node = nativeNode(text)
            if (node == null) false else {
                while (!node!!.isClickable && node.parent != null) node = node.parent
                node.isEnabled && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }
    @Test fun systemPickerExportsThroughGrantedDocumentProviderAndReadsCompleteVersionedFile() {
        runBlocking { history.create(HabitDraft("Isolated export")) }
        setQaRoot(true)
        screen(); scroll("Export data"); compose.onNodeWithText("Export data").performClick(); compose.onNodeWithText("Choose location").performClick()
        compose.waitUntil(7000) { documentsRoot() != null }
        nativeClick("Show roots"); nativeClick("Habit QA")
        nativeClick("Save")
        compose.waitUntil(7000) { actions.state.value.message != null }
        val uri = android.net.Uri.parse(requireNotNull(actionSaved.get<String>("exportUri")))
        assertEquals("com.example.habit.test.exports", uri.authority)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        try {
            val text = requireNotNull(resolver.openInputStream(uri)).bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(text)
            assertEquals(1, json.getInt("formatVersion")); assertEquals("Isolated export", json.getJSONArray("habits").getJSONObject(0).getString("name"))
        } finally { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
        assertEquals(1, runBlocking { db.historyDao().records().size })
    }
    @Test fun clearRequiresExactTextCancelRetainsDataAndSmallDarkDialogGrows() {
        runBlocking { history.create(HabitDraft("Retain")) }
        screen(dark = true, small = true); scroll("Clear all data"); capture("profile-dark-small-data")
        compose.onNodeWithText("Clear all data").performClick()
        compose.onNodeWithText("Type CLEAR").performTextInput("clear")
        compose.onNode(hasText("Clear all data") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        capture("clear-dark-small", dialog = true)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, runBlocking { db.historyDao().records().size }); assertEquals(DataState.Ready(0), data.state.value)
    }
    @Test fun confirmedClearRebuildsProductionGraphAtOnboardingWithoutOldRootsOrDrafts() {
        runBlocking { history.create(HabitDraft("Remove")); settings.setUserName("Clear me") }
        lateinit var currentNav: androidx.navigation.NavHostController
        compose.setContent { HabitTheme { LocalDataBoundary(data.state, {}, content = {
            val nav = rememberNavController(); currentNav = nav
            HabitNavigationGraph(nav, splash = { onboard, home ->
                LaunchedEffect(Unit) { val completed = settings.configuration.first().onboardingComplete; withContext(Dispatchers.Main.immediate) { if (completed) home(false) else onboard() } }
            }, onboarding = { Text("Onboarding after clear") }, home = { _, _, _, tab ->
                LaunchedEffect(Unit) { withContext(Dispatchers.Main.immediate) { tab(HomeTab.PROFILE) } }
            }, form = { _, _ -> Text("Old form") }, root = { _, _ -> ProfileScreen({}, profile, actions) }, detail = { _, _ -> Text("Old detail") })
        }) } }
        compose.waitUntil(7000) { profile.state.value.preferences?.userName == "Clear me" }
        scroll("Clear all data"); compose.onNodeWithText("Clear all data").performClick()
        compose.onNodeWithText("Type CLEAR").performTextInput("CLEAR")
        compose.onNode(hasText("Clear all data") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(7000) { data.state.value == DataState.Ready(1) }
        compose.onNodeWithText("Onboarding after clear").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Routes.ONBOARDING, currentNav.currentDestination!!.route) }
        assertEquals(SettingsRepository.Configuration(), runBlocking { settings.configuration.first() })
        assertTrue(runBlocking { db.historyDao().records().isEmpty() }); compose.runOnIdle { assertNull(currentNav.previousBackStackEntry) }
    }
    @Test fun reminderTimeDraftRestoresAndFailedChangeRetainsExactRetry() {
        val restoration = StateRestorationTester(compose)
        var error by mutableStateOf<String?>(null)
        var result: Pair<Boolean?, Int?>? = null
        var writes = 0
        restoration.setContent { HabitTheme { ReminderDialog(false, null, null, 1200, true, false, error,
            { enabled, minute -> result = enabled to minute; writes++; error = "Couldn’t save this change." }, {}) } }
        compose.onNodeWithText("Custom time · 20:00").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("21")
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("21")
        compose.onNodeWithText("Set time").performClick()
        compose.onNodeWithText("Retry reminder change").assertIsDisplayed()
        compose.onNodeWithText("Retry reminder change").performClick()
        assertEquals(2, writes); assertEquals(true to 1260, result)
    }
    @Test fun smallDarkReminderTimeAndExportCopyKeepActionsAccessible() {
        screen(dark = true, small = true); scroll("Daily reminder"); compose.onNodeWithText("Daily reminder").performClick()
        compose.onNodeWithText("Close").assertIsDisplayed(); capture("global-dark-small", dialog = true)
        compose.onNodeWithText("Time · 20:00").performClick(); compose.onNodeWithText("Set time").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performClick()
        documentsRoot() // Enable interactive-window inspection for the native keyboard assertion.
        compose.waitUntil(5000) { InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        compose.onNodeWithText("Set time").assertIsDisplayed()
        capture("time-dark-small", dialog = true)
        compose.onNodeWithText("Cancel").performClick(); compose.onNodeWithText("Close").performClick()
        scroll("Export data"); compose.onNodeWithText("Export data").performClick()
        compose.onNodeWithText("Choose location").assertIsDisplayed(); capture("export-dark-small", dialog = true)
    }
}
