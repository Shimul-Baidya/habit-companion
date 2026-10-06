package com.example.habit.ui.management

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habit.ui.screens.detail.*
import com.example.habit.ui.controls.ReminderDialog
import com.example.habit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HabitManagementHost(viewModel: HabitManagementViewModel, onEdit: (Long) -> Unit, onRemoved: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.removed) { state.removed?.let { onRemoved(it); viewModel.consumeRemoved() } }
    val record = state.record
    if (state.id != null && record != null) {
        ModalBottomSheet(onDismissRequest = viewModel::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.busy || it != SheetValue.Hidden }),
            shape = Radius.sheet, tonalElevation = 8.dp, containerColor = HabitTheme.colors.surfaceCard,
            scrimColor = HabitTheme.colors.scrim.copy(alpha = ScrimAlpha), dragHandle = {
                Box(Modifier.padding(top = 12.dp, bottom = 12.dp).size(32.dp, 4.dp).clip(Radius.pill).background(HabitTheme.colors.onSurfaceFaint))
            }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
                Text(record.record.habit.name, style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                Text("${scheduleText(requireNotNull(record.evaluation.settingsToday).schedule)} · ${record.evaluation.metrics.bestStreak} best ${if (occurrenceUnits(record.evaluation)) "occurrences" else "scheduled days"}",
                    style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg))
                HorizontalDivider(color = HabitTheme.colors.outline)
                ManagementAction("Edit habit", "Name, icon, colour, frequency", Icons.Default.Edit, state.readable && !state.busy) {
                    val id = requireNotNull(state.id); viewModel.dismiss(); onEdit(id)
                }
                ManagementAction("Change reminder", "Reminder settings and availability", Icons.Default.Notifications, state.readable && !state.busy) { viewModel.reminder(true) }
                ManagementAction("Archive habit", "Hides it, keeps the history", Icons.Default.Archive, state.readable && !state.busy, onClick = viewModel::archive)
                HorizontalDivider(color = HabitTheme.colors.outline, modifier = Modifier.padding(vertical = Spacing.sm))
                ManagementAction("Delete habit", "Removes the habit and its history", Icons.Default.Delete, state.readable && !state.busy, danger = true) { viewModel.confirmDelete(true) }
                FilledTonalButton(onClick = viewModel::dismiss, enabled = !state.busy, shape = Radius.pill,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = HabitTheme.colors.surface, contentColor = HabitTheme.colors.onSurfaceMuted),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = Spacing.sm)) { Text("Cancel") }
            }
        }
    }
    if (state.deleteConfirmation && record != null) AlertDialog(onDismissRequest = { viewModel.confirmDelete(false) },
        title = { Text("Delete ${record.record.habit.name}?") },
        text = { Text("This permanently removes this habit and all its completion history, including its ${record.evaluation.metrics.bestStreak}-${if (occurrenceUnits(record.evaluation)) "occurrence" else "scheduled-day"} best streak. This cannot be undone.") },
        confirmButton = { TextButton(onClick = viewModel::delete, enabled = state.readable && !state.busy) { Text(if (state.busy) "Deleting…" else "Delete", color = HabitTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = { viewModel.confirmDelete(false) }, enabled = !state.busy) { Text("Keep habit") } })
    if (state.reminder && record != null) ReminderDialog(false, record.record.habit.reminderEnabled, record.record.habit.reminderMinute,
        state.globalReminderMinute, state.globalReminderEnabled, state.busy, state.error, viewModel::saveReminder, { viewModel.reminder(false) }, availability = state.reminderAvailability)
    state.error?.takeIf { !state.reminder }?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Local habit change") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = { viewModel.clearError(); viewModel.retry() }) { Text("Retry") } },
        dismissButton = { TextButton(onClick = viewModel::clearError) { Text("Close") } }) }
    if (state.undo != null) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
            Snackbar(action = { TextButton(onClick = viewModel::undo, enabled = !state.busy) { Text("Undo", color = HabitTheme.colors.primaryContainer) } }) { Text("Habit archived. History kept.") }
        }
    }
}
@Composable private fun ManagementAction(label: String, helper: String, icon: ImageVector, enabled: Boolean,
    danger: Boolean = false, onClick: () -> Unit) {
    val colors = HabitTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (danger) colors.dangerContainer else colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (danger) colors.danger else colors.primary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = HabitTheme.type.title, color = if (!enabled) colors.onSurfaceFaint else if (danger) colors.danger else colors.onSurface)
            Text(helper, style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
        }
    }
}
