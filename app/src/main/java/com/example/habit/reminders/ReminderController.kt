package com.example.habit.reminders

import com.example.habit.data.controls.*
import com.example.habit.data.local.HabitRecord
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.DateProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.LocalDate

interface ReminderPlatform {
    fun available(): Boolean
    fun schedule(slot: ReminderSlot, generation: Long)
    fun cancelAlarm()
    fun cancelNotifications(keep: Set<Long>)
    fun post(record: HabitRecord)
}
class ReminderController(private val records: Flow<List<HabitRecord>>, private val readRecords: suspend () -> List<HabitRecord>,
    private val settings: SettingsRepository, private val dates: DateProvider, private val gate: DataGate,
    private val dataState: StateFlow<DataState>, private val platform: ReminderPlatform, private val clock: Clock,
    private val scope: CoroutineScope) {
    private val attempts = MutableStateFlow(0L)
    private val mutable = MutableStateFlow("Checking reminder availability…")
    val status = mutable.asStateFlow()
    fun start() { scope.launch { attempts.collectLatest {
        try { combine(records, settings.configuration.distinctUntilChanged(), dates.dates) { _, _, _ -> Unit }.collect { refresh() }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { cancelAll(); mutable.value = "Couldn’t schedule reminders. Open the app and retry." }
    } } }
    fun requestRefresh() { attempts.update { it + 1 } }
    fun cancelAll() { platform.cancelAlarm(); platform.cancelNotifications(emptySet()) }
    suspend fun refresh() = gate.access { refreshLocked() }
    private suspend fun refreshLocked() {
        val generation = (dataState.value as? DataState.Ready)?.generation ?: return
        platform.cancelAlarm()
        try {
            val all = readRecords()
            val config = settings.configuration.first()
            val today = clock.instant().atZone(clock.zone).toLocalDate()
            val available = platform.available()
            val due = if (available) all.filter { ReminderPlanner.minute(it, config) != null && ReminderPlanner.due(it, today) } else emptyList()
            platform.cancelNotifications(due.map { it.habit.id }.toSet())
            if (!available) { mutable.value = "Notifications are blocked. Allow them in Android settings."; return }
            if (!config.reminderEnabled) { mutable.value = "Reminders are off."; return }
            val slot = ReminderPlanner.next(all, config, clock.instant(), clock.zone, settings.delivered())
            if (slot != null) platform.schedule(slot, generation)
            mutable.value = "Android may delay reminders, especially during battery saving."
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { cancelAll(); mutable.value = "Couldn’t schedule reminders. Retry or reopen the app." }
    }
    suspend fun deliver(generation: Long, date: LocalDate, minute: Int, expectedAt: Long) = gate.access {
        val ready = dataState.value as? DataState.Ready ?: return@access
        val now = clock.instant()
        val currentDate = now.atZone(clock.zone).toLocalDate()
        val localAt = date.atTime(minute / 60, minute % 60).atZone(clock.zone).toInstant()
        if (generation == ready.generation && date == currentDate && now >= localAt && localAt.toEpochMilli() == expectedAt && platform.available()) {
            val config = settings.configuration.first()
            val delivered = settings.delivered()
            val due = readRecords().filter { ReminderPlanner.minute(it, config) == minute && ReminderPlanner.due(it, date) && ReminderPlanner.key(it.habit.id, date) !in delivered }
            // Persist the attempt before posting, preventing duplicate notifications after recreation/reboot.
            if (due.isNotEmpty()) {
                settings.markDelivered(delivered + due.map { ReminderPlanner.key(it.habit.id, date) })
                due.forEach { platform.post(it) }
            }
        }
        refreshLocked()
    }
}
