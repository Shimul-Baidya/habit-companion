package com.example.habit.ui.screens.newhabit

import com.example.habit.data.HabitDraft
import com.example.habit.domain.*
import java.math.BigDecimal
import java.time.DayOfWeek

/** Primitive values remain editable even when incomplete and are saved in SavedStateHandle. */
data class HabitFormDraft(
    val name: String = "",
    val iconKey: String = "mindful",
    val colorKey: String = "blue",
    val frequency: String = "DAILY",
    val quota: String = "3",
    val weekdayMask: Int = 127,
    val tracking: String = "BINARY",
    val target: String = "",
    val unit: String = "",
    val cue: String = "",
    val anchor: String = "",
    val planNote: String = "",
) {
    fun validate(): FormValidation {
        val quantity = tracking == "QUANTITY"
        val parsed = decimal(target)
        return FormValidation(name.isBlank(), frequency !in setOf("DAILY", "WEEKLY", "CUSTOM") ||
            (frequency == "WEEKLY" && quota.trim().toIntOrNull() !in 1..7) ||
            (frequency == "CUSTOM" && weekdayMask !in 1..127),
            tracking !in setOf("BINARY", "QUANTITY") || (quantity && (parsed == null || parsed.signum() <= 0)),
            quantity && (unit.trim().isEmpty() || unit.trim().length > 40))
    }
    fun toHabitDraft(): HabitDraft {
        require(validate().valid)
        val schedule = when (frequency) {
            "DAILY" -> HabitSchedule.Daily
            "WEEKLY" -> HabitSchedule.Weekly(quota.trim().toInt())
            else -> HabitSchedule.Custom(DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet())
        }
        val mode = if (tracking == "BINARY") TrackingMode.Binary else TrackingMode.Quantity(requireNotNull(decimal(target)).stripTrailingZeros(), unit.trim())
        return HabitDraft(name.trim(), HabitSettings(schedule, mode), iconKey, colorKey, cue, anchor, planNote)
    }
    fun apply(change: DraftPlanningChange): HabitFormDraft = when (change) {
        is DraftPlanningChange.Schedule -> withSchedule(change.value)
        is DraftPlanningChange.Tracking -> withTracking(change.value)
        is DraftPlanningChange.CueAnchor -> copy(cue = change.cue, anchor = change.anchor)
        is DraftPlanningChange.PlanNote -> copy(planNote = change.value)
    }
    private fun withSchedule(value: HabitSchedule): HabitFormDraft = when (value) {
        HabitSchedule.Daily -> copy(frequency = "DAILY")
        is HabitSchedule.Weekly -> copy(frequency = "WEEKLY", quota = value.completions.toString())
        is HabitSchedule.Custom -> copy(frequency = "CUSTOM", weekdayMask = value.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) })
    }
    private fun withTracking(value: TrackingMode): HabitFormDraft = when (value) {
        TrackingMode.Binary -> copy(tracking = "BINARY")
        is TrackingMode.Quantity -> copy(tracking = "QUANTITY", target = value.target.toPlainString(), unit = value.unit)
    }
    companion object {
        fun from(draft: HabitDraft) = HabitFormDraft(name = draft.name, iconKey = draft.iconKey, colorKey = draft.colorKey,
            cue = draft.cue, anchor = draft.anchor, planNote = draft.planNote).withSchedule(draft.settings.schedule).withTracking(draft.settings.tracking)
        fun decimal(value: String): BigDecimal? {
            val text = value.trim().replace(',', '.')
            if (!text.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
            return text.toBigDecimalOrNull()
        }
    }
}
data class FormValidation(val name: Boolean = false, val schedule: Boolean = false, val target: Boolean = false, val unit: Boolean = false) {
    val valid get() = !name && !schedule && !target && !unit
}

/** Local draft boundary only. External strategy/response validation belongs to chunks 09–11. */
sealed interface DraftPlanningChange {
    data class Schedule(val value: HabitSchedule) : DraftPlanningChange
    data class Tracking(val value: TrackingMode) : DraftPlanningChange
    data class CueAnchor(val cue: String, val anchor: String) : DraftPlanningChange
    data class PlanNote(val value: String) : DraftPlanningChange
}
data class DraftPlanningRequest(val token: String, val draft: HabitFormDraft, val currentHabitCount: Int = 0)
data class DraftPlanningResult(val token: String, val change: DraftPlanningChange)
sealed interface FormCoachEntry {
    data class Planning(val request: DraftPlanningRequest) : FormCoachEntry
    data class Existing(val habitId: Long) : FormCoachEntry
}
