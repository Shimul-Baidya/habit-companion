package com.example.habit.ui.management

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.*
import com.example.habit.data.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.container
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ManagementState(val id: Long? = null, val record: EvaluatedRecord? = null,
    val readable: Boolean = false, val busy: Boolean = false, val deleteConfirmation: Boolean = false,
    val reminder: Boolean = false, val error: String? = null, val undo: ArchiveChange? = null,
    val removed: Long? = null)
/** Lives above destinations so archive Undo remains available after detail returns to its caller. */
class HabitManagementViewModel(private val source: HabitDataSource, private val operations: HabitOperations,
    private val prefs: Flow<SettingsRepository.Configuration>, private val dates: DateProvider,
    private val saved: SavedStateHandle, private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val mutable = MutableStateFlow(ManagementState(id = saved["manageId"], deleteConfirmation = saved["manageDelete"] ?: false,
        reminder = saved["manageReminder"] ?: false, undo = saved.get<Long>("undoHabit")?.let { ArchiveChange(it, requireNotNull(saved["undoAt"])) }))
    val state = mutable.asStateFlow()
    private var snapshot: HabitSnapshot? = null
    init {
        viewModelScope.launch { attempts.collectLatest {
            mutable.update { it.copy(readable = false) }
            try { combine(dates.dates, prefs) { date, config -> date to config }.collectLatest { (date, config) ->
                mutable.update { it.copy(readable = false) }
                source.observeSnapshot(date, config.weekStart).collect { snap ->
                    snapshot = snap
                    val record = snap.records.singleOrNull { it.record.habit.id == state.value.id && it.record.habit.archivedAt == null && it.evaluation.settingsToday != null }
                    if (state.value.id != null && record == null && !state.value.busy) dismiss()
                    mutable.update { it.copy(readable = true, record = record) }
                }
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(readable = false, error = "Could not read local habits. Retry before making changes.") } }
        } }
        viewModelScope.launch { state.map { it.undo }.distinctUntilChanged().collectLatest { change ->
            if (change != null) { delay((5_000L - (now() - change.archivedAt)).coerceIn(0, 5_000)); retainUndo(null) }
        } }
    }
    fun retry() { dates.refresh(); attempts.update { it + 1 } }
    fun open(id: Long) {
        if (!state.value.readable || state.value.busy) return
        val record = snapshot?.records?.singleOrNull { it.record.habit.id == id && it.record.habit.archivedAt == null && it.evaluation.settingsToday != null } ?: return
        saved["manageId"] = id
        mutable.update { it.copy(id = id, record = record, deleteConfirmation = false, reminder = false, error = null) }
    }
    fun dismiss() { if (state.value.busy) return; saved["manageId"] = null; saved["manageDelete"] = false; saved["manageReminder"] = false
        mutable.update { it.copy(id = null, record = null, deleteConfirmation = false, reminder = false) } }
    fun confirmDelete(show: Boolean) { if (!state.value.busy) { saved["manageDelete"] = show; mutable.update { it.copy(deleteConfirmation = show) } } }
    fun reminder(show: Boolean) { if (!state.value.busy) { saved["manageReminder"] = show; mutable.update { it.copy(reminder = show) } } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun consumeRemoved() { mutable.update { it.copy(removed = null) } }
    fun archive() = mutate(delete = false)
    fun delete() { if (state.value.deleteConfirmation) mutate(delete = true) }
    private fun mutate(delete: Boolean) {
        val s = state.value; val id = s.id ?: return
        if (!s.readable || s.busy || s.record == null) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (delete) operations.delete(id) else retainUndo(operations.archive(id))
                mutable.update { it.copy(busy = false, removed = id) }; dismiss()
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.update { it.copy(busy = false, error = "Could not ${if (delete) "delete" else "archive"} the habit. Your saved history is retained.") }
            }
        }
    }
    fun undo() {
        val s = state.value; val change = s.undo ?: return
        if (s.busy) return
        if (now() - change.archivedAt !in 0L until 5_000) { retainUndo(null); return }
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { if (!operations.undoArchive(change)) mutable.update { it.copy(error = "Undo expired or the habit changed.") }
                retainUndo(null)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(error = "Could not undo archive. Try again before the five-second window ends.") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun retainUndo(change: ArchiveChange?) { saved["undoHabit"] = change?.habitId; saved["undoAt"] = change?.archivedAt; mutable.update { it.copy(undo = change) } }
    companion object { val Factory = viewModelFactory { initializer {
        HabitManagementViewModel(container.habits, container.habitHistory, container.settings.configuration, container.dates, createSavedStateHandle())
    } } }
}
