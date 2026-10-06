package com.example.habit.ui.controls

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.habit.data.prefs.SettingsRepository

@Composable fun ProfileActionDialogs(viewModel: ProfileActionsViewModel, prefs: SettingsRepository.Configuration?) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { viewModel.picked(it?.toString()) }
    when (state.dialog) {
        "reminder" -> if (prefs != null) ReminderDialog(true, prefs.reminderEnabled, prefs.reminderMinute, prefs.reminderMinute,
            prefs.reminderEnabled, state.busy, state.error, { enabled, minute -> viewModel.reminder(enabled == true, requireNotNull(minute)) }, { viewModel.open(null) }, availability = state.availability)
        "export" -> AlertDialog(onDismissRequest = { viewModel.open(null) }, title = { Text("Export data") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Saves a versioned JSON file with all habits (including archives), dated schedules and targets, completions, cues, plans and settings. It includes your profile and habit names. Unsaved drafts are excluded. A cloud location may upload the file. Restore is unavailable in v1.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) Text("Writing export…")
            }
        }, confirmButton = { TextButton(onClick = { if (viewModel.pick()) { try { picker.launch("Habit-Companion.json") } catch (_: Exception) { viewModel.pickerFailed() } } }, enabled = !state.busy && !state.pickerPending) { Text("Choose location") } },
            dismissButton = { TextButton(onClick = { viewModel.open(null) }, enabled = !state.busy && !state.pickerPending) { Text("Cancel") } })
        "clear" -> AlertDialog(onDismissRequest = { viewModel.open(null) }, title = { Text("Clear all data?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Permanently removes habits, completion history, cues, settings and drafts. Cancels reminders and returns to onboarding. Exported files and Android notification settings remain. This cannot be undone. Type CLEAR to confirm.")
                OutlinedTextField(state.confirmation, viewModel::confirmation, label = { Text("Type CLEAR") }, singleLine = true, enabled = !state.busy)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = viewModel::clear, enabled = state.confirmation == "CLEAR" && !state.busy) { Text("Clear all data", color = if (state.confirmation == "CLEAR" && !state.busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) } },
            dismissButton = { TextButton(onClick = { viewModel.open(null) }, enabled = !state.busy) { Text("Cancel") } })
    }
    if (state.dialog == null && state.error != null) AlertDialog(onDismissRequest = { viewModel.open(null) }, title = { Text("Local change") },
        text = { Text(requireNotNull(state.error)) }, confirmButton = { TextButton(onClick = { viewModel.open(null) }) { Text("Close") } })
    if (state.dialog == null) state.message?.let { message -> AlertDialog(onDismissRequest = { viewModel.open(null) }, title = { Text("Export data") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { viewModel.open(null) }) { Text("OK") } }) }
}
