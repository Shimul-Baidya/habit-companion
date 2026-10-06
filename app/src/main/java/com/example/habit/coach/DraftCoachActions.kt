package com.example.habit.coach

import androidx.lifecycle.SavedStateHandle
import com.example.habit.domain.*
import com.example.habit.ui.screens.newhabit.*
import java.time.Clock

/** The creation flow owns these primitive receipts; it never writes a habit or permanent thread. */
class DraftCoachActions(private val saved: SavedStateHandle, private val token: String,
    private val clock: Clock, private val read: () -> HabitFormDraft, private val write: (HabitFormDraft) -> Unit) {
    private fun values(d: HabitFormDraft) = listOf(d.frequency, d.quota, d.weekdayMask.toString(), d.tracking,
        d.target, d.unit, d.cue, d.anchor, d.planNote)
    private fun fields(d: HabitFormDraft) = listOf(values(d).take(3), values(d).subList(3, 6), listOf(d.cue), listOf(d.anchor), listOf(d.planNote))
    private fun revision(index: Int): Long = saved["draftCoachRevision.$index"] ?: 0L
    fun changed(before: HabitFormDraft, after: HabitFormDraft) {
        fields(before).zip(fields(after)).forEachIndexed { i, (old, new) ->
            if (old != new) saved["draftCoachRevision.$i"] = revision(i) + 1L
        }
    }
    fun receipt(id: String): CoachApplyReceipt? = saved.get<ArrayList<String>>("draftCoach.$id")?.let {
        CoachApplyReceipt(it[0], it[4], it[1].toLong(), it[2])
    }
    private fun identity(request: CoachRequest): ArrayList<String> {
        val c = request.context as CoachContext.Planning
        val schedule = when (val s = c.schedule) {
            HabitSchedule.Daily -> listOf("DAILY")
            is HabitSchedule.Weekly -> listOf("WEEKLY", s.completions.toString())
            is HabitSchedule.Custom -> listOf("CUSTOM", s.weekdays.map { it.value }.sorted().joinToString(","))
        }
        val mode = when (val t = c.tracking) {
            TrackingMode.Binary -> listOf("BINARY")
            is TrackingMode.Quantity -> listOf("QUANTITY", t.target.toPlainString(), t.unit)
        }
        return ArrayList(listOf(request.catalogSha256, request.question, c.name, c.activeHabitCount.toString()) + schedule + mode + request.strategies.map { it.card.id })
    }
    fun beginInteraction(draftToken: String, exchangeId: String, request: CoachRequest) {
        require(draftToken == token && exchangeId.matches(Regex("[A-Za-z0-9_-]{1,80}")) && request.context is CoachContext.Planning)
        val context = request.context
        val draft = read()
        val settings = draft.copy(name = "Draft").toHabitDraft().settings
        require(draft.name.trim() == context.name && settings.schedule == context.schedule && settings.tracking == context.tracking)
        val prior = saved.get<ArrayList<String>>("draftCoachRequest.$exchangeId")
        if (prior != null) require(prior == identity(request)) else {
            saved["draftCoachRequest.$exchangeId"] = identity(request)
            saved["draftCoachBaseline.$exchangeId"] = ArrayList((0..4).map { revision(it).toString() })
        }
    }
    fun apply(draftToken: String, exchangeId: String, index: Int, request: CoachRequest,
        response: ValidatedCoachResponse, catalog: StrategyCatalog): CoachApplyReceipt {
        require(draftToken == token && exchangeId.matches(Regex("[A-Za-z0-9_-]{1,80}")) && index in 0..2)
        require(request.context is CoachContext.Planning)
        require(CoachResponseValidator.validate(response.value, request, catalog) is ResponseResult.Valid)
        val id = "$exchangeId:$index"
        require(saved.get<ArrayList<String>>("draftCoachRequest.$exchangeId") == identity(request))
        receipt(id)?.let { return it }
        val baseline = requireNotNull(saved.get<ArrayList<String>>("draftCoachBaseline.$exchangeId")) { "Interaction is no longer available" }
        val before = read()
        val context = request.context
        val settings = before.copy(name = "Draft").toHabitDraft().settings
        val action = response.value.suggestions[index].action
        val after = when (action) {
            CoachAction.AdviceOnly -> before
            is CoachAction.Target -> {
                require(settings.tracking == context.tracking)
                require(settings.tracking is TrackingMode.Quantity && action.unit == settings.tracking.unit)
                before.apply(DraftPlanningChange.Tracking(TrackingMode.Quantity(CoachLimits.amount(action.amount), action.unit)))
            }
            is CoachAction.Schedule -> {
                require(settings.schedule == context.schedule)
                before.apply(DraftPlanningChange.Schedule(action.value))
            }
            is CoachAction.CueAnchor -> before.apply(DraftPlanningChange.CueAnchor(action.cue.trim(), action.anchor.trim()))
            is CoachAction.Plan -> before.apply(DraftPlanningChange.PlanNote(action.note.trim()))
            is CoachAction.NewHabitCount -> before.apply(DraftPlanningChange.PlanNote("Start with ${action.count} new habit${if (action.count == 1) "" else "s"}."))
            is CoachAction.Reminder -> error("Reminder actions require an existing habit")
        }
        val changedFields = fields(before).zip(fields(after)).mapIndexedNotNull { i, (old, new) -> i.takeIf { old != new } }
        changedFields.forEach { check(revision(it) == baseline[it].toLong()) { "Draft field changed; request new suggestions" } }
        write(after) // The form's normal change path also increments field identities.
        val status = if (action == CoachAction.AdviceOnly) "ADVICE" else if (changedFields.isEmpty()) "UNCHANGED" else "APPLIED"
        val result = CoachApplyReceipt(id, CoachActionFeedback.text(action, unchanged = status == "UNCHANGED"), clock.millis(), status)
        saved["draftCoach.$id"] = arrayListOf(id, result.appliedAt.toString(), status, changedFields.joinToString(","), result.confirmation,
            changedFields.joinToString(",") { revision(it).toString() }).apply { addAll(values(before)) }
        return result
    }
    fun undo(id: String): CoachUndoResult {
        val stored = saved.get<ArrayList<String>>("draftCoach.$id") ?: return CoachUndoResult.UNAVAILABLE
        val result = requireNotNull(receipt(id))
        if (result.status == "UNDONE") return CoachUndoResult.ALREADY_UNDONE
        if (result.status == "CONFLICT") return CoachUndoResult.CONFLICT
        if (result.status == "EXPIRED") return CoachUndoResult.EXPIRED
        if (result.status != "APPLIED") return CoachUndoResult.NO_CHANGE
        fun status(value: String) { saved["draftCoach.$id"] = ArrayList(stored).apply { set(2, value) } }
        if (!result.canUndo(clock.millis())) { status("EXPIRED"); return CoachUndoResult.EXPIRED }
        val indices = stored[3].split(',').map(String::toInt)
        val revisions = stored[5].split(',').map(String::toLong)
        if (indices.zip(revisions).any { (i, version) -> revision(i) != version }) { status("CONFLICT"); return CoachUndoResult.CONFLICT }
        var draft = read()
        indices.forEach { i -> draft = when (i) {
            0 -> draft.copy(frequency = stored[6], quota = stored[7], weekdayMask = stored[8].toInt())
            1 -> draft.copy(tracking = stored[9], target = stored[10], unit = stored[11])
            2 -> draft.copy(cue = stored[12])
            3 -> draft.copy(anchor = stored[13])
            4 -> draft.copy(planNote = stored[14])
            else -> error("Invalid draft receipt")
        } }
        write(draft); status("UNDONE")
        return CoachUndoResult.UNDONE
    }
}
