package com.example.habit.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.habit.HabitApplication
import com.example.habit.MainActivity
import com.example.habit.R
import com.example.habit.data.controls.DataState
import com.example.habit.data.local.HabitRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AndroidReminders(private val context: Context, private val namespace: String = "next", private val channel: String = CHANNEL) : ReminderPlatform {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)) }
    override fun available(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
    private fun intent() = Intent(context, ReminderReceiver::class.java).setAction(if (namespace == "next") ALARM else "$ALARM.$namespace").setData("habit-companion://reminder/$namespace".toUri())
    private fun pending(intent: Intent = intent()) = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun schedule(slot: ReminderSlot, generation: Long) {
        val operation = pending(intent().putExtra("generation", generation).putExtra("date", slot.date.toEpochDay())
            .putExtra("minute", slot.minute).putExtra("at", slot.at.toEpochMilli()))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, slot.at.toEpochMilli(), operation)
    }
    override fun cancelAlarm() { val operation = pending(); alarms.cancel(operation); operation.cancel() }
    override fun cancelNotifications(keep: Set<Long>) {
        manager.activeNotifications.filter { it.tag?.startsWith("$TAG$namespace:") == true && it.tag?.removePrefix("$TAG$namespace:")?.toLongOrNull() !in keep }
            .forEach { manager.cancel(it.tag, it.id) }
    }
    override fun post(record: HabitRecord) {
        if (!available()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(record.habit.name).setContentText("A little progress counts. Your habit is still pending today.")
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle("Habit Companion").setContentText("You have a habit left today.").build()).build()
        try { manager.notify("$TAG$namespace:${record.habit.id}", 1, notification) } catch (_: SecurityException) { /* Permission can change between check and post. */ }
    }
    companion object { const val CHANNEL = "habit_reminders"; const val ALARM = "com.example.habit.REMIND"; private const val TAG = "habit-reminder:" }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(AndroidReminders.ALARM, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        val container = (context.applicationContext as HabitApplication).container
        container.launch {
            try { withTimeout(8_000) {
                container.localData.state.first { it != DataState.Loading && it != DataState.Clearing }
                if (container.localData.state.value !is DataState.Ready) return@withTimeout
                if (intent.action == AndroidReminders.ALARM) {
                    val minute = intent.getIntExtra("minute", -1)
                    if (minute in 0..1439) container.reminders.deliver(intent.getLongExtra("generation", -1),
                        LocalDate.ofEpochDay(intent.getLongExtra("date", 0)), minute, intent.getLongExtra("at", -1))
                } else { container.dates.refresh(); container.reminders.refresh() }
            } } catch (_: Exception) { container.reminders.cancelAll(); container.reminders.requestRefresh() }
            finally { pending.finish() }
        }
    }
}
