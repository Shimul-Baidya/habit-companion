package com.example.habit.data.controls

import androidx.room.withTransaction
import com.example.habit.data.local.*
import com.example.habit.data.prefs.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Clock

sealed interface DataState {
    data class Ready(val generation: Long) : DataState
    data object Loading : DataState
    data object Clearing : DataState
    data object RecoveryRequired : DataState
}
interface DataControls {
    suspend fun export(output: suspend (String) -> OutputStream)
    suspend fun clear()
}
/** A durable intent bridges Room/DataStore; failed/interrupted resets resume before UI or alarms. */
class LocalDataControls(private val database: HabitDatabase, private val settings: SettingsRepository,
    private val gate: DataGate, private val clock: Clock, private val cancelReminders: () -> Unit) : DataControls {
    private val mutable = MutableStateFlow<DataState>(DataState.Loading)
    val state = mutable.asStateFlow()
    suspend fun initialize() {
        mutable.value = DataState.Loading
        try { gate.reset {
            val (pending, generation) = settings.resetState()
            if (pending) finish(generation)
            mutable.value = DataState.Ready(generation)
        } } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutable.value = DataState.RecoveryRequired }
    }
    override suspend fun clear() {
        val before = mutable.value
        check(before is DataState.Ready && mutable.compareAndSet(before, DataState.Clearing))
        // Once the user confirms, navigation/recreation must not abandon the coordinated reset.
        withContext(NonCancellable) {
            try { gate.reset {
                val generation = settings.resetState().second + 1
                settings.beginReset(generation)
                finish(generation)
                mutable.value = DataState.Ready(generation)
            } } catch (_: Exception) { mutable.value = DataState.RecoveryRequired; throw IllegalStateException("Local clearing needs recovery") }
        }
    }
    private suspend fun finish(generation: Long) {
        cancelReminders()
        database.withTransaction { database.historyDao().deleteAll() }
        settings.finishReset(generation)
    }
    override suspend fun export(output: suspend (String) -> OutputStream): Unit = withContext(Dispatchers.IO) { gate.access {
        check(mutable.value is DataState.Ready)
        val records = database.historyDao().records()
        val prefs = settings.configuration.first()
        val text = ExportDocument.encode(records, prefs, clock)
        output("Habit-Companion-${clock.instant().atZone(clock.zone).toLocalDate()}.json").use {
            it.write(text.toByteArray(Charsets.UTF_8)); it.flush()
        }
    } }
}

/** Raw versioned facts preserve legacy metadata, pending revisions and exact decimal amounts. */
object ExportDocument {
    fun encode(records: List<HabitRecord>, prefs: SettingsRepository.Configuration, clock: Clock): String {
        fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
        fun array(values: List<JSONObject>) = JSONArray(values)
        return json("format" to "habit-companion", "formatVersion" to 1, "roomSchemaVersion" to 2,
            "exportedAt" to clock.instant().toString(), "deviceZone" to clock.zone.id,
            "settings" to json("onboardingComplete" to prefs.onboardingComplete, "userName" to prefs.userName,
                "themeMode" to prefs.themeMode.name, "weekStart" to prefs.weekStart.value, "coachEnabled" to prefs.coachEnabled,
                "reminderEnabled" to prefs.reminderEnabled, "reminderMinute" to prefs.reminderMinute),
            "habits" to array(records.map { r -> val h = r.habit
                json("id" to h.id, "name" to h.name, "iconKey" to h.iconKey, "colorKey" to h.colorKey,
                    "legacyFrequency" to h.frequency.name, "legacyScheduledDays" to h.scheduledDays, "legacyGoal" to h.goal,
                    "createdAt" to h.createdAt, "archivedAt" to h.archivedAt, "createdEpochDay" to h.createdEpochDay,
                    "archivedEpochDay" to h.archivedEpochDay, "cue" to h.cue, "anchor" to h.anchor, "planNote" to h.planNote,
                    "reminderEnabled" to h.reminderEnabled, "reminderMinute" to h.reminderMinute,
                    "scheduleHistory" to array(r.schedules.sortedBy { it.effectiveDay }.map {
                        json("effectiveDay" to it.effectiveDay, "kind" to it.kind, "weekdayMask" to it.weekdayMask, "quota" to it.quota) }),
                    "trackingHistory" to array(r.tracking.sortedBy { it.effectiveDay }.map {
                        json("effectiveDay" to it.effectiveDay, "mode" to it.mode, "target" to it.target, "unit" to it.unit) }),
                    "completions" to array(r.completions.sortedBy { it.epochDay }.map {
                        json("epochDay" to it.epochDay, "legacyCount" to it.count, "completedAt" to it.completedAt,
                            "trackingMode" to it.trackingMode, "quantityAmount" to it.quantityAmount, "quantityUnit" to it.quantityUnit) })) }),
            "coachHistory" to json("storageVersion" to 0, "threads" to JSONArray(), "caches" to JSONArray()),
            "notice" to "Contains private local data. Restore/import is unavailable in v1. Coach storage is not yet implemented. Operational reminder/reset state and unsaved drafts are excluded.").toString(2)
    }
}
