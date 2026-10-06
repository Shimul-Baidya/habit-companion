package com.example.habit.ui.screens.newhabit

import com.example.habit.domain.*
import java.time.DayOfWeek

/** Primitive, saveable navigation results. This is not an external AI-response parser. */
object PlanningDraftContract {
    const val RESULT_KEY = "planningDraftResult"
    fun encode(result: DraftPlanningResult): ArrayList<String> = arrayListOf(result.token).apply {
        when (val change = result.change) {
            is DraftPlanningChange.Schedule -> when (val value = change.value) {
                HabitSchedule.Daily -> addAll(listOf("DAILY"))
                is HabitSchedule.Weekly -> addAll(listOf("WEEKLY", value.completions.toString()))
                is HabitSchedule.Custom -> addAll(listOf("CUSTOM", value.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }.toString()))
            }
            is DraftPlanningChange.Tracking -> when (val value = change.value) {
                TrackingMode.Binary -> add("BINARY")
                is TrackingMode.Quantity -> addAll(listOf("QUANTITY", value.target.toPlainString(), value.unit))
            }
            is DraftPlanningChange.CueAnchor -> addAll(listOf("CUE_ANCHOR", change.cue, change.anchor))
            is DraftPlanningChange.PlanNote -> addAll(listOf("PLAN", change.value))
        }
    }
    fun decode(values: List<String>): DraftPlanningResult? = runCatching {
        require(values.size >= 2 && values[0].isNotBlank())
        val change = when (values[1]) {
            "DAILY" -> { require(values.size == 2); DraftPlanningChange.Schedule(HabitSchedule.Daily) }
            "WEEKLY" -> { require(values.size == 3); DraftPlanningChange.Schedule(HabitSchedule.Weekly(values[2].toInt())) }
            "CUSTOM" -> {
                require(values.size == 3)
                val mask = values[2].toInt(); require(mask in 1..127)
                DraftPlanningChange.Schedule(HabitSchedule.Custom(DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()))
            }
            "BINARY" -> { require(values.size == 2); DraftPlanningChange.Tracking(TrackingMode.Binary) }
            "QUANTITY" -> {
                require(values.size == 4)
                DraftPlanningChange.Tracking(TrackingMode.Quantity(requireNotNull(HabitFormDraft.decimal(values[2])), values[3]))
            }
            "CUE_ANCHOR" -> { require(values.size == 4); DraftPlanningChange.CueAnchor(values[2], values[3]) }
            "PLAN" -> { require(values.size == 3); DraftPlanningChange.PlanNote(values[2]) }
            else -> error("Unsupported draft result")
        }
        DraftPlanningResult(values[0], change)
    }.getOrNull()
}
