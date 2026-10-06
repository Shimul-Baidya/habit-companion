package com.example.habit.data

import androidx.room.withTransaction
import com.example.habit.data.local.*
import com.example.habit.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HabitDraft(
    val name: String,
    val settings: HabitSettings = HabitSettings(HabitSchedule.Daily),
    val iconKey: String = "default",
    val colorKey: String = "primary",
)
data class HabitMetadata(
    val name: String, val iconKey: String, val colorKey: String,
    val cue: String = "", val anchor: String = "", val planNote: String = "",
    val reminderEnabled: Boolean? = null, val reminderMinute: Int? = null,
) {
    init {
        require(name.trim().isNotEmpty() && iconKey.isNotBlank() && colorKey.isNotBlank())
        require(reminderMinute == null || reminderMinute in 0..1439)
    }
}
data class ArchiveChange(val habitId: Long, val archivedAt: Long)

/** All related writes are atomic; the clock is injected for reproducible date guards. */
class HabitHistoryRepository(private val database: HabitDatabase, private val clock: Clock) {
    private val habits = database.habitDao()
    private val historyDao = database.historyDao()
    private val completions = database.completionDao()
    val records: Flow<List<HabitRecord>> = historyDao.observeRecords()
    private fun today() = LocalDate.now(clock)
    suspend fun record(id: Long): HabitRecord? = historyDao.record(id)

    suspend fun create(draft: HabitDraft): Long = database.withTransaction {
        require(draft.name.trim().isNotEmpty() && draft.iconKey.isNotBlank() && draft.colorKey.isNotBlank())
        val at = clock.instant()
        insert(HabitEntity(name = draft.name.trim(), iconKey = draft.iconKey, colorKey = draft.colorKey,
            createdAt = at.toEpochMilli(), createdEpochDay = at.atZone(clock.zone).toLocalDate().toEpochDay()), draft.settings)
    }

    /** Compatibility bridge for the existing name-only binary form. */
    suspend fun createLegacy(habit: HabitEntity): Long = database.withTransaction {
        require(habit.archivedAt == null && habit.createdEpochDay <= today().toEpochDay())
        val schedule = if (habit.frequency == Frequency.DAILY) HabitSchedule.Daily else
            ScheduleHistoryEntity(habit.id, habit.createdEpochDay, "CUSTOM", habit.scheduledDays, null).toDomain()
        insert(habit.copy(name = habit.name.trim()), HabitSettings(schedule))
    }

    private suspend fun insert(habit: HabitEntity, settings: HabitSettings): Long {
        require(habit.name.isNotBlank())
        val day = LocalDate.ofEpochDay(habit.createdEpochDay)
        val projection = ScheduleHistoryEntity.from(0, day, settings.schedule)
        val id = habits.insert(habit.copy(frequency = Frequency.valueOf(projection.kind), scheduledDays = projection.weekdayMask))
        historyDao.putSchedule(projection.copy(habitId = id))
        historyDao.putTracking(TrackingHistoryEntity.from(id, day, settings.tracking))
        return id
    }

    suspend fun updateMetadata(id: Long, metadata: HabitMetadata) = database.withTransaction {
        val habit = requireNotNull(historyDao.record(id)).habit
        habits.update(habit.copy(name = metadata.name.trim(), iconKey = metadata.iconKey, colorKey = metadata.colorKey,
            cue = metadata.cue, anchor = metadata.anchor, planNote = metadata.planNote,
            reminderEnabled = metadata.reminderEnabled, reminderMinute = metadata.reminderMinute))
    }

    suspend fun changeSettings(id: Long, changes: List<HabitSettingChange>) = database.withTransaction {
        val today = today()
        require(changes.isNotEmpty())
        require(changes.count { it is HabitSettingChange.Schedule } <= 1 && changes.count { it is HabitSettingChange.Tracking } <= 1)
        val record = requireNotNull(historyDao.record(id))
        require(record.habit.archivedAt == null) { "Archived habit cannot be edited" }
        val history = record.toHistory()
        val current = requireNotNull(history.settingsOn(today))
        changes.forEach { change ->
            // Include a pending Weekly transition when deciding whether another schedule edit is delayed.
            val pendingWeekly = record.schedules.any { it.effectiveDay > today.toEpochDay() && it.kind == "WEEKLY" }
            val from = if (change is HabitSettingChange.Schedule && pendingWeekly) {
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            } else change.effectiveOn(current, today)
            when (change) {
                is HabitSettingChange.Schedule -> historyDao.putSchedule(ScheduleHistoryEntity.from(id, from, change.value))
                is HabitSettingChange.Tracking -> historyDao.putTracking(TrackingHistoryEntity.from(id, from, change.value))
            }
        }
        // Invalid timeline or mode/unit collisions roll back all writes, including combined changes.
        requireNotNull(historyDao.record(id)).toHistory()
    }

    suspend fun correct(id: Long, date: LocalDate, value: CompletionValue?) = writeCompletion(id, date, value, requireToday = false)
    suspend fun logToday(id: Long, date: LocalDate, value: CompletionValue) = writeCompletion(id, date, value, requireToday = true)

    private suspend fun writeCompletion(id: Long, date: LocalDate, value: CompletionValue?, requireToday: Boolean) = database.withTransaction {
        val today = today()
        require(!requireToday || date == today) { "Date changed; refresh before logging today" }
        val record = requireNotNull(historyDao.record(id))
        val history = record.toHistory()
        require(CompletionRules.canCorrect(history, date, today)) { "Date is not eligible" }
        require(record.habit.archivedAt == null || date < today) { "Archived habit cannot be logged today" }
        val tracking = requireNotNull(history.settingsOn(date)).tracking
        if (value == null) {
            completions.clear(id, date.toEpochDay())
        } else {
            CompletionRules.isComplete(tracking, value)
            when (value) {
                is CompletionValue.Binary -> if (value.done) completions.upsert(
                    CompletionEntity(id, date.toEpochDay(), completedAt = clock.millis())) else completions.clear(id, date.toEpochDay())
                is CompletionValue.Quantity -> completions.upsert(CompletionEntity(id, date.toEpochDay(), completedAt = clock.millis(),
                    trackingMode = "QUANTITY", quantityAmount = value.amount.toPlainString(), quantityUnit = value.unit))
            }
        }
    }

    suspend fun archive(id: Long): ArchiveChange = database.withTransaction {
        val record = requireNotNull(historyDao.record(id))
        require(record.habit.archivedAt == null)
        val at = clock.instant()
        habits.update(record.habit.copy(archivedAt = at.toEpochMilli(), archivedEpochDay = at.atZone(clock.zone).toLocalDate().toEpochDay()))
        ArchiveChange(id, at.toEpochMilli())
    }

    suspend fun undoArchive(change: ArchiveChange): Boolean = database.withTransaction {
        if (clock.millis() - change.archivedAt !in 0L until 5_000L) return@withTransaction false
        val habit = historyDao.record(change.habitId)?.habit ?: return@withTransaction false
        if (habit.archivedAt != change.archivedAt) return@withTransaction false
        habits.update(habit.copy(archivedAt = null, archivedEpochDay = null))
        true
    }
    suspend fun delete(id: Long) = database.withTransaction { historyDao.delete(id) }
}
