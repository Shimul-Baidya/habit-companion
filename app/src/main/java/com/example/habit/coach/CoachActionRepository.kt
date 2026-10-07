package com.example.habit.coach

import androidx.room.withTransaction
import com.example.habit.data.*
import com.example.habit.data.controls.DataGate
import com.example.habit.data.local.*
import com.example.habit.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate

/** UI receipts carry a durable action identity; recreation never restarts the deadline. */
data class CoachApplyReceipt(val id: String, val confirmation: String, val appliedAt: Long, val status: String) {
    fun canUndo(now: Long) = status == "APPLIED" && now - appliedAt in 0L until 10_000L
}
enum class CoachUndoResult { UNDONE, ALREADY_UNDONE, EXPIRED, CONFLICT, UNAVAILABLE, NO_CHANGE }

/** A trusted local handler, independent of screens and transport. No arbitrary prose is executed. */
class CoachActionRepository(private val database: HabitDatabase, private val history: HabitHistoryRepository,
    private val gate: DataGate, private val clock: Clock, private val catalog: suspend () -> StrategyCatalog,
    private val enabled: suspend () -> Boolean) {
    private val dao = database.coachDao()
    private val facts = database.historyDao()
    private suspend fun <T> transaction(block: suspend () -> T): T = gate.access { database.withTransaction { block() } }
    suspend fun exchange(id: Long, exchangeId: String) = dao.cacheById(exchangeId)?.takeIf { it.habitId == id }
    fun messages(id: Long) = dao.messages(id)
    fun cache(id: Long) = dao.latestCache(id)
    fun actions(id: Long) = dao.actions(id)

    /** Reserve before dispatch. Clear/delete/wipe invalidates late results by removing the reservation. */
    suspend fun beginInteraction(id: Long, exchangeId: String, request: CoachRequest): Unit = transaction {
        require(exchangeId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        check(enabled()) { "Coach is disabled" }
        require(request.context is CoachContext.Existing)
        val record = requireNotNull(facts.record(id)) { "Habit no longer exists" }
        require(record.habit.archivedAt == null)
        require(request.context.summary == MeasuredSummary.from(record.toHistory(), LocalDate.now(clock))) { "Habit context changed" }
        require(request.catalogSha256 == catalog().sha256)
        val payload = CoachJson.payload(request)
        val old = dao.cacheById(exchangeId)
        if (old != null) {
            check(old.habitId == id && old.request == payload && old.catalogSha256 == request.catalogSha256)
        } else {
            val baseline = JSONObject()
            listOf("schedule", "tracking", "cue", "anchor", "plan", "reminder").forEach { baseline.put(it, facts.version(id, it) ?: 0L) }
            dao.cache(CoachCacheEntity(exchangeId, id, payload, "", baseline.toString(), request.catalogSha256, clock.millis()))
        }
    }

    /** The interaction ID is retained by the caller; retries/recreation cannot append duplicates. */
    suspend fun saveExchange(id: Long, exchangeId: String, request: CoachRequest, rawResponse: String): CoachCacheEntity = transaction {
        check(enabled()) { "Coach is disabled" }
        require(request.context is CoachContext.Existing)
        val old = requireNotNull(dao.cacheById(exchangeId)) { "Interaction was cleared or removed" }
        check(old.habitId == id && old.request == CoachJson.payload(request) && old.catalogSha256 == request.catalogSha256)
        require(CoachJson.response(rawResponse, request, catalog()) is ResponseResult.Valid) { "Invalid Coach response" }
        if (old.response.isNotEmpty()) {
            check(old.response == rawResponse); return@transaction old
        }
        val record = requireNotNull(facts.record(id)) { "Habit no longer exists" }
        require(record.habit.archivedAt == null)
        require(request.context.summary == MeasuredSummary.from(record.toHistory(), LocalDate.now(clock))) { "Habit context changed" }
        val value = old.copy(response = rawResponse, createdAt = clock.millis())
        dao.updateCache(value)
        if (request.question.isNotBlank()) dao.message(CoachMessageEntity(habitId = id, role = "USER", text = request.question,
            createdAt = value.createdAt, exchangeId = exchangeId))
        dao.message(CoachMessageEntity(habitId = id, role = "COACH", text = rawResponse, createdAt = value.createdAt, exchangeId = exchangeId))
        dao.trim(id)
        value
    }

    suspend fun apply(id: Long, exchangeId: String, index: Int, request: CoachRequest): CoachApplyReceipt = transaction {
        check(enabled()) { "Coach is disabled" }
        require(index in 0..2 && request.context is CoachContext.Existing)
        val cache = requireNotNull(dao.cacheById(exchangeId)) { "Suggestion is no longer available" }
        require(cache.habitId == id && cache.request == CoachJson.payload(request) && cache.catalogSha256 == request.catalogSha256)
        val validated = requireNotNull((CoachJson.response(cache.response, request, catalog()) as? ResponseResult.Valid)?.response)
        val suggestion = requireNotNull(validated.value.suggestions.getOrNull(index)) { "No action at this index" }
        val key = "$exchangeId:$index"
        dao.actionById(key)?.let { return@transaction it.receipt() }
        val record = requireNotNull(facts.record(id)) { "Habit no longer exists" }
        require(record.habit.archivedAt == null && record.habit.createdEpochDay <= LocalDate.now(clock).toEpochDay())
        val action = suggestion.action
        val inverse = JSONObject().put("fields", JSONArray())
        val changed = inverse.getJSONArray("fields")
        val habit = record.habit
        val today = LocalDate.now(clock)
        val baseline = JSONObject(cache.baseline)
        val affected = when (action) {
            is CoachAction.Target -> listOf("tracking")
            is CoachAction.Schedule -> listOf("schedule")
            is CoachAction.CueAnchor -> buildList { if (habit.cue != action.cue.trim()) add("cue"); if (habit.anchor != action.anchor.trim()) add("anchor") }
            is CoachAction.Plan -> listOf("plan")
            is CoachAction.Reminder -> listOf("reminder")
            else -> emptyList()
        }
        affected.forEach { check((facts.version(id, it) ?: 0L) == baseline.getLong(it)) { "Setting changed; request new suggestions" } }
        var effective: Long? = null
        fun field(name: String, before: Any?, after: Any?) {
            if (before != after) changed.put(JSONObject().put("name", name).put("before", before ?: JSONObject.NULL))
        }
        when (action) {
            CoachAction.AdviceOnly -> Unit
            is CoachAction.NewHabitCount -> error("Planning action cannot mutate an existing habit")
            is CoachAction.Target -> {
                val latest = record.editableDraft().settings.tracking
                require(sameTracking(requireNotNull(record.toHistory().settingsOn(today)).tracking, request.context.tracking)) { "Tracking changed; request new suggestions" }
                require(latest is TrackingMode.Quantity && latest.unit == action.unit)
                val target = TrackingMode.Quantity(CoachLimits.amount(action.amount), action.unit)
                if (!sameTracking(latest, target)) {
                    val day = today.plusDays(1).toEpochDay(); effective = day
                    inverse.put("day", day)
                    val previous = facts.tracking(id, day)
                    field("tracking", previous?.let { JSONObject().put("mode", it.mode).put("target", it.target ?: JSONObject.NULL).put("unit", it.unit ?: JSONObject.NULL) }, "changed")
                    history.changeSettings(id, listOf(HabitSettingChange.Tracking(target)))
                }
            }
            is CoachAction.Schedule -> {
                val latest = record.editableDraft().settings.schedule
                require(requireNotNull(record.toHistory().settingsOn(today)).schedule == request.context.summary.schedule) { "Schedule changed; request new suggestions" }
                if (latest != action.value) {
                    val beforeDays = record.schedules.associateBy { it.effectiveDay }
                    history.changeSettings(id, listOf(HabitSettingChange.Schedule(action.value)))
                    val after = requireNotNull(facts.record(id)).schedules.single { beforeDays[it.effectiveDay] != it }
                    effective = after.effectiveDay; inverse.put("day", after.effectiveDay)
                    field("schedule", beforeDays[after.effectiveDay]?.let { JSONObject().put("kind", it.kind).put("mask", it.weekdayMask).put("quota", it.quota ?: JSONObject.NULL) }, "changed")
                }
            }
            is CoachAction.CueAnchor -> {
                field("cue", habit.cue, action.cue.trim()); field("anchor", habit.anchor, action.anchor.trim())
                database.habitDao().update(habit.copy(cue = action.cue.trim(), anchor = action.anchor.trim()))
            }
            is CoachAction.Plan -> {
                field("plan", habit.planNote, action.note.trim())
                database.habitDao().update(habit.copy(planNote = action.note.trim()))
            }
            is CoachAction.Reminder -> {
                val (on, minute) = when (val setting = action.setting) {
                    ReminderSetting.Inherit -> null to null
                    ReminderSetting.Off -> false to null
                    is ReminderSetting.At -> true to setting.minuteOfDay
                }
                if (habit.reminderEnabled != on || habit.reminderMinute != minute) {
                    field("reminder", JSONObject().put("enabled", habit.reminderEnabled ?: JSONObject.NULL).put("minute", habit.reminderMinute ?: JSONObject.NULL), "changed")
                    history.setReminder(id, on, minute, habit.reminderEnabled, habit.reminderMinute)
                }
            }
        }
        check(LocalDate.now(clock) == today) { "Date changed; reopen Coach" }
        for (i in 0 until changed.length()) {
            val f = changed.getJSONObject(i)
            f.put("revision", requireNotNull(facts.version(id, f.getString("name"))))
        }
        val status = if (action == CoachAction.AdviceOnly) "ADVICE" else if (changed.length() == 0) "UNCHANGED" else "APPLIED"
        val confirmation = CoachActionFeedback.text(action, effective?.let(LocalDate::ofEpochDay), status == "UNCHANGED")
        val entry = CoachActionEntity(key, id, suggestion.strategyId, inverse.toString(), clock.millis(), status, confirmation)
        dao.action(entry)
        dao.message(CoachMessageEntity(habitId = id, role = "APPLIED", text = confirmation, createdAt = entry.appliedAt, exchangeId = exchangeId))
        dao.trim(id)
        entry.receipt()
    }

    /** Clears receipts too: history clearing invalidates Undo but leaves already applied settings. */
    suspend fun clearHistory() = transaction { dao.clear() }

    suspend fun undo(actionId: String): CoachUndoResult = transaction {
        val action = dao.actionById(actionId) ?: return@transaction CoachUndoResult.UNAVAILABLE
        if (action.status == "UNDONE") return@transaction CoachUndoResult.ALREADY_UNDONE
        if (action.status != "APPLIED") return@transaction when (action.status) {
            "EXPIRED" -> CoachUndoResult.EXPIRED
            "CONFLICT" -> CoachUndoResult.CONFLICT
            else -> CoachUndoResult.NO_CHANGE
        }
        fun receiptStatus(status: String) = action.copy(status = status)
        if (!action.receipt().canUndo(clock.millis())) {
            dao.action(receiptStatus("EXPIRED")); return@transaction CoachUndoResult.EXPIRED
        }
        val record = facts.record(action.habitId) ?: return@transaction CoachUndoResult.UNAVAILABLE
        if (record.habit.archivedAt != null) return@transaction CoachUndoResult.UNAVAILABLE
        val inverse = JSONObject(action.inverse)
        val fields = inverse.getJSONArray("fields")
        val day = if (inverse.has("day")) inverse.getLong("day") else null
        // A midnight crossing cannot reinterpret a newly effective expectation or a logged amount.
        if (day != null && LocalDate.now(clock).toEpochDay() >= day) {
            dao.action(receiptStatus("CONFLICT")); return@transaction CoachUndoResult.CONFLICT
        }
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            if (facts.version(action.habitId, field.getString("name")) != field.getLong("revision")) {
                dao.action(receiptStatus("CONFLICT")); return@transaction CoachUndoResult.CONFLICT
            }
        }
        var habit = record.habit
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            val before = field.opt("before").takeUnless { it == JSONObject.NULL }
            when (field.getString("name")) {
                "cue" -> habit = habit.copy(cue = before as String)
                "anchor" -> habit = habit.copy(anchor = before as String)
                "plan" -> habit = habit.copy(planNote = before as String)
                "reminder" -> (before as JSONObject).let {
                    habit = habit.copy(reminderEnabled = if (it.isNull("enabled")) null else it.getBoolean("enabled"),
                        reminderMinute = if (it.isNull("minute")) null else it.getInt("minute"))
                }
                "schedule" -> if (before == null) facts.deleteSchedule(action.habitId, requireNotNull(day)) else (before as JSONObject).let {
                    facts.putSchedule(ScheduleHistoryEntity(action.habitId, requireNotNull(day), it.getString("kind"), it.getInt("mask"), if (it.isNull("quota")) null else it.getInt("quota")))
                }
                "tracking" -> if (before == null) facts.deleteTracking(action.habitId, requireNotNull(day)) else (before as JSONObject).let {
                    facts.putTracking(TrackingHistoryEntity(action.habitId, requireNotNull(day), it.getString("mode"), if (it.isNull("target")) null else it.getString("target"), if (it.isNull("unit")) null else it.getString("unit")))
                }
            }
        }
        check(day == null || LocalDate.now(clock).toEpochDay() < day) { "Expectation became effective; Undo is no longer safe" }
        database.habitDao().update(habit)
        requireNotNull(facts.record(action.habitId)).toHistory()
        dao.action(receiptStatus("UNDONE"))
        dao.message(CoachMessageEntity(habitId = action.habitId, role = "UNDONE", text = "Undid: ${action.confirmation}", createdAt = clock.millis(), exchangeId = action.id.substringBeforeLast(':')))
        dao.trim(action.habitId)
        CoachUndoResult.UNDONE
    }
    private fun CoachActionEntity.receipt() = CoachApplyReceipt(id, confirmation, appliedAt, status)
}

object CoachActionFeedback {
    fun text(action: CoachAction, effective: LocalDate? = null, unchanged: Boolean = false): String {
        if (unchanged) return "This setting already matches the suggestion."
        return when (action) {
            CoachAction.AdviceOnly -> "Advice saved; no habit setting changed."
            is CoachAction.Target -> "Target set to ${action.amount} ${action.unit}${effective?.let { " from $it" } ?: " in this draft"}."
            is CoachAction.Schedule -> "Schedule set to ${when (val s = action.value) {
                HabitSchedule.Daily -> "Daily"
                is HabitSchedule.Weekly -> "${s.completions} completions per week"
                is HabitSchedule.Custom -> s.weekdays.sortedBy { it.value }.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }
            }}${effective?.let { " from $it" } ?: " in this draft"}."
            is CoachAction.CueAnchor -> "Cue and anchor updated."
            is CoachAction.Plan -> "Local plan updated."
            is CoachAction.Reminder -> when (val s = action.setting) {
                ReminderSetting.Inherit -> "Reminder now follows the daily reminder setting."
                ReminderSetting.Off -> "Reminder turned off for this habit."
                is ReminderSetting.At -> "Reminder set to %02d:%02d; daily reminders and notification availability still apply.".format(java.util.Locale.ROOT, s.minuteOfDay / 60, s.minuteOfDay % 60)
            }
            is CoachAction.NewHabitCount -> "Draft plan recommends starting ${action.count} new habit${if (action.count == 1) "" else "s"}; none created."
        }
    }
}
