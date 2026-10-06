package com.example.habit.ui.screens.detail

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.*
import com.example.habit.data.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.container
import com.example.habit.ui.screens.newhabit.HabitFormDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.math.BigDecimal

data class CorrectionEntry(val date: LocalDate, val openedOn: LocalDate, val settings: HabitSettings,
    val before: CompletionValue?, val input: String = "", val saving: Boolean = false,
    val error: String? = null, val stale: Boolean = false) {
    val quantity get() = settings.tracking as? TrackingMode.Quantity
    val amount get() = HabitFormDraft.decimal(input)?.takeIf { it.signum() >= 0 }
}
data class DetailUiState(val loading: Boolean = true, val readError: Boolean = false,
    val value: EvaluatedRecord? = null, val missing: Boolean = false,
    val month: YearMonth, val weekStart: DayOfWeek = DayOfWeek.MONDAY, val coachEnabled: Boolean = false,
    val entry: CorrectionEntry? = null, val notice: String? = null) {
    val canWrite get() = !loading && !readError && value != null && !missing && value.record.habit.archivedAt == null && value.evaluation.settingsToday != null
}
class HabitDetailViewModel(private val id: Long, private val source: HabitDataSource,
    private val operations: HabitOperations, private val configuration: Flow<SettingsRepository.Configuration>,
    private val dates: DateProvider, private val saved: SavedStateHandle) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val mutable = MutableStateFlow(DetailUiState(month = saved.get<String>("detailMonth")?.let(YearMonth::parse) ?: YearMonth.from(dates.today())))
    val state = mutable.asStateFlow()
    init { viewModelScope.launch {
        attempts.collectLatest {
            mutable.update { it.copy(loading = true, readError = false) }
            try {
                combine(dates.dates, configuration) { date, prefs -> date to prefs }.collectLatest { (date, prefs) ->
                    mutable.update { it.copy(loading = true, coachEnabled = prefs.coachEnabled, weekStart = prefs.weekStart) }
                    source.observeSnapshot(date, prefs.weekStart).collect { snap ->
                        val record = snap.records.singleOrNull { it.record.habit.id == id }
                        mutable.update { old -> old.copy(loading = false, readError = false, value = record, missing = record == null,
                            entry = (old.entry ?: restoreEntry(record))?.let { entry -> entry.copy(stale = record == null ||
                                record.record.habit.archivedAt != null || !CompletionRules.canCorrect(record.evaluation.history, entry.date, date) ||
                                (entry.date == entry.openedOn && date != entry.openedOn) ||
                                record.evaluation.history.settingsOn(entry.date) != entry.settings ||
                                record.evaluation.history.logs.singleOrNull { it.date == entry.date }?.value != entry.before) }) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(loading = false, readError = true) } }
        }
    } }
    fun retry() { dates.refresh(); attempts.update { it + 1 } }
    fun moveMonth(delta: Int) { val month = state.value.month.plusMonths(delta.toLong()); saved["detailMonth"] = month.toString(); mutable.update { it.copy(month = month) } }
    fun clearNotice() { mutable.update { it.copy(notice = null) } }
    fun coach() { mutable.update { it.copy(notice = if (it.coachEnabled) "Coach suggestions are not connected yet. Your local history remains available." else "Enable Coach suggestions in Profile to use the Coach.") } }
    fun openDate(date: LocalDate) {
        val s = state.value
        val value = s.value?.evaluation ?: return
        if (!s.canWrite || s.entry?.saving == true || !CompletionRules.canCorrect(value.history, date, value.today)) return
        val settings = requireNotNull(value.history.settingsOn(date))
        val before = value.history.logs.singleOrNull { it.date == date }?.value
        retain(CorrectionEntry(date, value.today, settings, before, (before as? CompletionValue.Quantity)?.amount?.toPlainString().orEmpty()))
    }
    fun changeInput(input: String) { state.value.entry?.takeUnless { it.saving }?.let { retain(it.copy(input = input, error = null)) } }
    fun dismissEntry() { if (state.value.entry?.saving != true) retain(null) }
    fun saveEntry(clear: Boolean = false) {
        val s = state.value; val entry = s.entry ?: return
        if (entry.saving || !s.canWrite || entry.stale) return
        if (entry.date == entry.openedOn && dates.today() != entry.openedOn) { retain(entry.copy(stale = true)); dates.refresh(); return }
        val value = when {
            clear -> null
            entry.quantity != null -> {
                val amount = entry.amount ?: run { retain(entry.copy(error = "Enter a non-negative decimal amount.")); return }
                CompletionValue.Quantity(amount, requireNotNull(entry.quantity).unit)
            }
            else -> CompletionValue.Binary(entry.before != CompletionValue.Binary(true))
        }
        retain(entry.copy(saving = true, error = null))
        viewModelScope.launch {
            try { operations.correctChecked(id, entry.date, entry.openedOn, entry.settings, entry.before, value); retain(null)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                retain(entry.copy(error = "Could not save. History may have changed; close and reopen the date, or retry.")); dates.refresh()
            }
        }
    }
    private fun retain(entry: CorrectionEntry?) {
        saved["correctionDate"] = entry?.date?.toEpochDay(); saved["correctionOpened"] = entry?.openedOn?.toEpochDay()
        saved["correctionInput"] = entry?.input
        // Primitive expectation and original value detect changes across recreation.
        saved["correctionTarget"] = entry?.quantity?.target?.toPlainString(); saved["correctionUnit"] = entry?.quantity?.unit
        saved["correctionBefore"] = when (val value = entry?.before) { is CompletionValue.Quantity -> value.amount.toPlainString(); is CompletionValue.Binary -> value.done.toString(); null -> null }
        saved["correctionSchedule"] = entry?.settings?.schedule?.let { when (it) { HabitSchedule.Daily -> "daily"; is HabitSchedule.Weekly -> "weekly:${it.completions}"; is HabitSchedule.Custom -> "custom:" + it.weekdays.sortedBy { d -> d.value }.joinToString(",") { d -> d.value.toString() } } }
        mutable.update { it.copy(entry = entry) }
    }
    private fun restoreEntry(record: EvaluatedRecord?): CorrectionEntry? {
        val day = saved.get<Long>("correctionDate") ?: return null
        if (record == null) return null
        val date = LocalDate.ofEpochDay(day)
        val schedule = saved.get<String>("correctionSchedule")?.let { raw -> when {
            raw == "daily" -> HabitSchedule.Daily
            raw.startsWith("weekly:") -> HabitSchedule.Weekly(raw.substringAfter(':').toInt())
            else -> HabitSchedule.Custom(raw.substringAfter(':').split(',').map { DayOfWeek.of(it.toInt()) }.toSet())
        } } ?: return null
        val target = saved.get<String>("correctionTarget")
        val mode = if (target == null) TrackingMode.Binary else TrackingMode.Quantity(BigDecimal(target), requireNotNull(saved["correctionUnit"]))
        val before = saved.get<String>("correctionBefore")?.let { if (mode is TrackingMode.Quantity) CompletionValue.Quantity(BigDecimal(it), mode.unit) else CompletionValue.Binary(it.toBooleanStrict()) }
        return CorrectionEntry(date, LocalDate.ofEpochDay(requireNotNull(saved["correctionOpened"])), HabitSettings(schedule, mode), before, saved["correctionInput"] ?: "")
    }
    companion object { val Factory = viewModelFactory { initializer {
        val saved = createSavedStateHandle()
        HabitDetailViewModel(requireNotNull(saved.get<Long>("habitId")), container.habits, container.habitHistory, container.settings.configuration, container.dates, saved)
    } } }
}
