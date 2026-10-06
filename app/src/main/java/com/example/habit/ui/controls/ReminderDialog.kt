package com.example.habit.ui.controls

import android.Manifest
import android.content.Intent
import android.content.Context
import androidx.core.net.toUri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.habit.reminders.AndroidReminders

private fun notificationSettings(context: Context) = if (Build.VERSION.SDK_INT >= 26)
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
fun reminderTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReminderDialog(global: Boolean, enabled: Boolean?, minute: Int?, inheritedMinute: Int,
    globalEnabled: Boolean, busy: Boolean, error: String?, onChange: (Boolean?, Int?) -> Unit,
    onDismiss: () -> Unit, availability: String? = null) {
    val context = LocalContext.current
    var available by remember { mutableStateOf(AndroidReminders(context).available()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { available = AndroidReminders(context).available() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        available = AndroidReminders(context).available()
        (context.applicationContext as? com.example.habit.HabitApplication)?.container?.reminders?.requestRefresh()
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var pendingChange by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableIntStateOf(-1) }
    var pendingMinute by rememberSaveable { mutableIntStateOf(-1) }
    fun request(mode: Boolean?, time: Int?) {
        pendingChange = true; pendingMode = when (mode) { null -> -1; false -> 0; true -> 1 }; pendingMinute = time ?: -1
        onChange(mode, time)
    }
    val confirmedMode = when (enabled) { null -> -1; false -> 0; true -> 1 }
    LaunchedEffect(enabled, minute, busy, error) {
        if (!busy && error == null && pendingMode == confirmedMode && pendingMinute == (minute ?: -1)) pendingChange = false
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (global) "Daily reminder" else "Change reminder") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (global) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily reminder", Modifier.weight(1f))
                    Switch(enabled == true, { request(it, minute ?: inheritedMinute) }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Daily reminder enabled" })
                }
            } else {
                TextButton(onClick = { request(null, null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${if (enabled == null && minute == null) "✓ " else ""}Use daily reminder · ${reminderTime(inheritedMinute)}")
                }
                TextButton(onClick = { request(false, null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${if (enabled == false) "✓ " else ""}Off for this habit")
                }
            }
            TextButton(onClick = { picking = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (global) "Time · ${reminderTime(minute ?: inheritedMinute)}" else "Custom time · ${reminderTime(minute ?: inheritedMinute)}")
            }
            if (!global && !globalEnabled) Text("Daily reminders are off. Turn them on in Profile to use this habit’s reminder.")
            Text("Android may delay reminders, especially during battery saving.")
            if (availability?.contains("Couldn’t") == true) Text(availability, color = MaterialTheme.colorScheme.error)
            if (availability?.contains("Couldn’t") == true) TextButton(onClick = {
                (context.applicationContext as? com.example.habit.HabitApplication)?.container?.reminders?.requestRefresh()
            }, enabled = !busy) { Text("Retry scheduling") }
            if (!available) {
                Text("Notifications are blocked. Your reminder choices are kept.")
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(notificationSettings(context))
                }, enabled = !busy) { Text("Allow notifications") }
                TextButton(onClick = { context.startActivity(notificationSettings(context)) }, enabled = !busy) { Text("Android notification settings") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (pendingChange && !busy) TextButton(onClick = {
                onChange(when (pendingMode) { -1 -> null; 0 -> false; else -> true }, pendingMinute.takeIf { it >= 0 })
            }) { Text("Retry reminder change") }
            if (busy) Text("Saving…")
        } }, confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } })
    if (picking) {
        val initial = minute ?: inheritedMinute
        val time = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { picking = false }, title = { Text("Reminder time") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimeInput(time) } },
            confirmButton = { TextButton(onClick = { picking = false; request(if (global) enabled == true else true, time.hour * 60 + time.minute) }, enabled = !busy) { Text("Set time") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } })
    }
}
