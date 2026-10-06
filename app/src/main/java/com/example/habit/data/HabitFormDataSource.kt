package com.example.habit.data

import com.example.habit.data.local.HabitRecord
import kotlinx.coroutines.flow.Flow

/** Form saves are one transaction, including metadata and dated expectation changes. */
interface HabitFormDataSource {
    val records: Flow<List<HabitRecord>>
    suspend fun saveForm(id: Long?, draft: HabitDraft, original: HabitDraft?, allowDuplicate: Boolean = false): Long
}
class DuplicateHabitName : IllegalArgumentException("An active habit has this name")
class HabitFormConflict : IllegalStateException("Habit changed while this form was open")

/** Pending settings are what Edit edits; today's history keeps its existing meaning. */
fun HabitRecord.editableDraft() = HabitDraft(
    habit.name,
    com.example.habit.domain.HabitSettings(schedules.maxBy { it.effectiveDay }.toDomain(), tracking.maxBy { it.effectiveDay }.toDomain()),
    habit.iconKey, habit.colorKey, habit.cue, habit.anchor, habit.planNote,
)

internal fun sameTracking(a: com.example.habit.domain.TrackingMode, b: com.example.habit.domain.TrackingMode): Boolean =
    if (a is com.example.habit.domain.TrackingMode.Quantity && b is com.example.habit.domain.TrackingMode.Quantity)
        a.unit == b.unit && a.target.compareTo(b.target) == 0 else a == b
