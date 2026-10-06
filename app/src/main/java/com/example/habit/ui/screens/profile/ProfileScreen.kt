package com.example.habit.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.data.prefs.*
import com.example.habit.ui.controls.*
import com.example.habit.ui.components.*
import com.example.habit.ui.screens.insights.*
import com.example.habit.ui.theme.*
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun themeLabel(mode: ThemeMode) = when (mode) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.SYSTEM -> "Follow system" }

@Composable
fun ProfileScreen(onSelectTab: (HomeTab) -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    actions: ProfileActionsViewModel = viewModel(factory = ProfileActionsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionState by actions.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.dialog) {
        if (state.dialog in setOf("reminder", "export", "clear")) {
            actions.open(state.dialog); viewModel.openDialog(null)
        }
    }
    val focus = LocalFocusManager.current
    BackHandler(state.editingName) { focus.clearFocus(); viewModel.cancelName() }
    val scroll = rememberLazyListState()
    val prefs = state.preferences
    val enabled = prefs != null && !state.preferenceError && !state.writing
    Scaffold(containerColor = HabitTheme.colors.surface,
        bottomBar = { BottomNav(HomeTab.PROFILE, onSelectTab) }, modifier = Modifier.imePadding()) { insets ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(insets).testTag("profile-scroll"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { Text("Profile", style = HabitTheme.type.headline, color = HabitTheme.colors.onSurface) }
            if (state.preferenceError || prefs == null || state.writing || state.writeError) item {
                if (state.preferenceError) {
                    Text("Couldn’t read preferences. Your last available settings are shown.", style = HabitTheme.type.body, color = HabitTheme.colors.danger)
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                } else if (prefs == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.writing) Text("Saving preference…", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                if (state.writeError && state.dialog == null && !state.editingName) Text("Couldn’t save this change. Try it again.", style = HabitTheme.type.body, color = HabitTheme.colors.danger)
            }
            item {
                InsightCard {
                    // The explicit 100dp avatar overrides the smaller drawing in the mockup.
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(100.dp).background(HabitTheme.colors.primaryContainer, Radius.pill), contentAlignment = Alignment.Center) {
                            Text(profileInitials(prefs?.userName.orEmpty()), style = HabitTheme.type.titleLg, color = HabitTheme.colors.onPrimaryContainer)
                        }
                        Column(Modifier.widthIn(min = 180.dp).weight(1f).align(Alignment.CenterVertically)) {
                            if (state.editingName) {
                                val requester = remember { FocusRequester() }
                                LaunchedEffect(Unit) { requester.requestFocus() }
                                OutlinedTextField(state.nameInput, viewModel::changeName, label = { Text("Your name") }, singleLine = true,
                                    enabled = !state.writing, isError = state.invalidName || state.writeError,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { viewModel.finishName() }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(requester).testTag("profile-name"))
                                if (state.invalidName) Text("Use at most 80 characters without line breaks.", color = HabitTheme.colors.danger, style = HabitTheme.type.caption)
                                if (state.writeError) Text("Couldn’t save your name. Your input is kept; try Done again.", color = HabitTheme.colors.danger, style = HabitTheme.type.caption)
                                Row {
                                    IconButton(onClick = viewModel::finishName, enabled = enabled) { Icon(Icons.Default.Check, "Done editing name") }
                                    IconButton(onClick = { focus.clearFocus(); viewModel.cancelName() }, enabled = !state.writing) { Icon(Icons.Default.Close, "Cancel name edit") }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(prefs?.userName?.ifBlank { "Your profile" } ?: "Your profile", style = HabitTheme.type.title,
                                        color = HabitTheme.colors.onSurface, modifier = Modifier.weight(1f))
                                    IconButton(onClick = viewModel::editName, enabled = enabled) { Icon(Icons.Default.Edit, "Edit name", tint = HabitTheme.colors.onSurfaceMuted) }
                                }
                            }
                            val snapshot = state.read.snapshot
                            val first = snapshot?.records?.minOfOrNull { it.evaluation.history.createdOn }?.takeIf { it <= snapshot.today }
                            Text(if (snapshot == null) "Local profile · no account" else "${snapshot.active.size} active ${if (snapshot.active.size == 1) "habit" else "habits"}" +
                                (first?.let { " · tracking since ${it.format(DateTimeFormatter.ofPattern("MMM yyyy"))}" } ?: " · no history yet"),
                                style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                        }
                    }
                }
            }
            item {
                ReadNotice(state.read, viewModel::retry)
                state.read.snapshot?.let { snapshot ->
                    StatCells(listOf(snapshot.allTimeBest.toString() to "BEST STREAK", snapshot.lifetime.completed.toString() to "COMPLETED",
                        consistencyText(snapshot.lifetime.consistency) to "ALL TIME"), cards = false)
                    Text("Required completions and occurrence streaks · lifetime", style = HabitTheme.type.caption,
                        color = HabitTheme.colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                SettingsGroup("PREFERENCES") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = enabled && !actionState.busy) { actions.open("reminder") }.heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                            Text("Daily reminder", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                            Text(if (prefs?.reminderEnabled == true) "${reminderTime(prefs.reminderMinute)} · tap to change time" else "Off · tap to choose time", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                        }
                        Switch(prefs?.reminderEnabled ?: false, { value ->
                            if (value) actions.open("reminder")
                            actions.reminder(value, prefs?.reminderMinute ?: 1200)
                        }, enabled = enabled && !actionState.busy,
                            modifier = Modifier.semantics { contentDescription = "Profile daily reminder" })
                    }
                    HorizontalDivider(color = HabitTheme.colors.outline)
                    SettingRow("Theme", prefs?.let { themeLabel(it.themeMode) } ?: "Loading…", { viewModel.openDialog("theme") }, enabled)
                    HorizontalDivider(color = HabitTheme.colors.outline)
                    SettingRow("Week starts", prefs?.weekStart?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Loading…", { viewModel.openDialog("week") }, enabled)
                }
            }
            item {
                SettingsGroup("COACH") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Coach suggestions", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                            Text("Google Gemini receives Coach context and questions; free-tier data may be reviewed", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                        }
                        Switch(prefs?.coachEnabled ?: false, viewModel::coach, enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = "Coach suggestions" }.testTag("coach-setting"))
                    }
                    HorizontalDivider(color = HabitTheme.colors.outline)
                    SettingRow("Clear coach history", "Local conversations", { viewModel.openDialog("history") }, enabled = !state.writing)
                    if (state.historyCleared) Text("Coach history cleared.", style = HabitTheme.type.caption, color = HabitTheme.colors.primary)
                }
            }
            item {
                SettingsGroup("DATA") {
                    SettingRow("Export data", "Save a local copy", { actions.open("export") })
                    HorizontalDivider(color = HabitTheme.colors.outline)
                    SettingRow("Restore data", "Not available in v1", {}, enabled = false)
                    HorizontalDivider(color = HabitTheme.colors.outline)
                    SettingRow("Clear all data", "Remove local habits, history and settings", { actions.open("clear") }, danger = true)
                }
            }
        }
    }
    ProfileActionDialogs(actions, prefs)
    state.dialog?.let { dialog ->
        if (dialog == "theme" || dialog == "week") AlertDialog(onDismissRequest = { viewModel.openDialog(null) },
            title = { Text(if (dialog == "theme") "Theme" else "Week starts") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.writeError) Text("Couldn’t save. Your previous setting is kept; try again.", color = HabitTheme.colors.danger)
                    if (state.preferenceError) Text("Preferences are unavailable. Close this dialog and Retry.", color = HabitTheme.colors.danger)
                    if (dialog == "theme") ThemeMode.entries.forEach { mode ->
                        ChoiceRow(themeLabel(mode), prefs?.themeMode == mode, enabled) { viewModel.theme(mode) }
                    } else listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                        ChoiceRow(day.getDisplayName(TextStyle.FULL, Locale.getDefault()), prefs?.weekStart == day, enabled) { viewModel.weekStart(day) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { viewModel.openDialog(null) }, enabled = !state.writing) { Text("Cancel") } })
        else AlertDialog(onDismissRequest = { viewModel.openDialog(null) }, title = { Text("Clear coach history?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Remove all local Coach conversations, cached suggestions and Undo receipts? Applied settings, habits and completions stay saved.")
                if (state.writeError) Text("Couldn’t clear Coach history. Try again.", color = HabitTheme.colors.danger)
            } },
            confirmButton = { TextButton(onClick = viewModel::clearHistory, enabled = !state.writing) { Text(if (state.writing) "Clearing…" else if (state.writeError) "Retry" else "Clear history") } },
            dismissButton = { TextButton(onClick = { viewModel.openDialog(null) }, enabled = !state.writing) { Text("Cancel") } })
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = HabitTheme.type.label, color = HabitTheme.colors.onSurfaceMuted)
        InsightCard(content = content)
    }
}
@Composable
private fun SettingRow(title: String, helper: String, action: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    val color = if (danger) HabitTheme.colors.danger else if (!enabled) HabitTheme.colors.onSurfaceFaint else HabitTheme.colors.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = action).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HabitTheme.type.title, color = color)
            Text(helper, style = HabitTheme.type.caption, color = if (danger) color else HabitTheme.colors.onSurfaceMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color, modifier = Modifier.size(24.dp))
    }
}
@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = action),
        verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, null, enabled = enabled); Text(label) }
}
