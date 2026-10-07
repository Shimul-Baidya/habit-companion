package com.example.habit.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.data.HabitDataSource
import com.example.habit.data.HabitSnapshot
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.DateProvider
import com.example.habit.domain.HabitStatus
import com.example.habit.domain.TrackingMode
import com.example.habit.ui.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.math.BigDecimal
import com.example.habit.domain.CompletionValue

/** A dated, primitive-backed amount draft; reopening uses today's historical target. */
data class QuantityEntry(
    val habitId: Long, val date: LocalDate, val target: String, val unit: String,
    val amount: String = "", val saving: Boolean = false, val invalid: Boolean = false,
    val failed: Boolean = false, val stale: Boolean = false,
) {
    val parsed: BigDecimal? get() = com.example.habit.ui.screens.newhabit.HabitFormDraft.decimal(amount)?.takeIf { it.signum() >= 0 }
    val valid: Boolean get() = parsed != null
}

/** Empty, loading, failed and no-due are distinct states of the same Home destination. */
data class HomeUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val today: LocalDate = LocalDate.now(),
    val healthy: List<HabitStatus> = emptyList(),
    val atRisk: List<HabitStatus> = emptyList(),
    val readError: Boolean = false,
    val actionError: Boolean = false,
    val snapshot: HabitSnapshot? = null,
    val bestStreak: Int = 0,
    val coachEnabled: Boolean = false,
    val quantity: QuantityEntry? = null,
    val writingIds: Set<Long> = emptySet(),
) {
    val allHabits: List<HabitStatus> get() = healthy + atRisk
    val hasHistory: Boolean get() = snapshot?.records?.isNotEmpty() == true
    val isEmpty: Boolean get() = !loading && !readError && allHabits.isEmpty()
    val scheduledToday: List<HabitStatus> get() = allHabits.filter { it.scheduledToday }
    val doneToday: Int get() = scheduledToday.count { it.doneToday }
    val totalToday: Int get() = scheduledToday.size
    val ringProgress: Float get() = if (totalToday == 0) 0f else doneToday.toFloat() / totalToday
    val allDone: Boolean get() = totalToday > 0 && doneToday == totalToday
    val noDueToday: Boolean get() = !isEmpty && !loading && !readError && totalToday == 0
    val canWrite: Boolean get() = !loading && !readError && snapshot != null
}

class HomeViewModel(
    private val habits: HabitDataSource,
    private val configuration: Flow<SettingsRepository.Configuration>,
    private val dates: DateProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val attempts = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow(HomeUiState(today = dates.today(), quantity = restoreQuantity()))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            attempts.collectLatest {
                mutableState.update { it.copy(loading = true, readError = false) }
                try {
                    combine(dates.dates, configuration) { date, prefs -> date to prefs }.collectLatest { (date, prefs) ->
                        mutableState.update { it.copy(loading = true, readError = false, today = date, userName = prefs.userName, coachEnabled = prefs.coachEnabled) }
                        habits.observeSnapshot(date, prefs.weekStart).collect { snapshot ->
                            mutableState.update { previous ->
                                previous.copy(loading = false, readError = false, today = snapshot.today,
                                    healthy = snapshot.active.filterNot { it.atRisk },
                                    atRisk = snapshot.active.filter { it.atRisk }, snapshot = snapshot,
                                    bestStreak = snapshot.allTimeBest,
                                    quantity = previous.quantity?.let { entry ->
                                        val row = snapshot.active.firstOrNull { it.habit.id == entry.habitId }
                                        entry.copy(stale = row == null || entry.date != snapshot.today ||
                                            !row.canLogToday || row.settings.tracking != TrackingMode.Quantity(BigDecimal(entry.target), entry.unit))
                                    })
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep prior successful data visible, but disable writes until a fresh read succeeds.
                    mutableState.update { it.copy(loading = false, readError = true) }
                }
            }
        }
    }

    fun retry() { dates.refresh(); attempts.update { it + 1 } }
    fun clearActionError() { mutableState.update { it.copy(actionError = false) } }

    fun toggle(status: HabitStatus) {
        val state = mutableState.value
        if (!state.canWrite || status.habit.id in state.writingIds) return
        val current = state.allHabits.firstOrNull { it.habit.id == status.habit.id } ?: return
        if (!current.scheduledToday || !current.canLogToday || current.settings.tracking != TrackingMode.Binary) return
        if (state.today != dates.today()) { dates.refresh(); return }
        mutableState.update { it.copy(writingIds = it.writingIds + current.habit.id, actionError = false) }
        viewModelScope.launch {
            try {
                habits.toggleToday(current.habit, state.today, !current.doneToday)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                dates.refresh()
                mutableState.update { it.copy(actionError = true) }
            } finally {
                mutableState.update { it.copy(writingIds = it.writingIds - current.habit.id) }
            }
        }
    }

    /** The row's separate ring is the only completion entry; row body opens detail. */
    fun complete(status: HabitStatus) {
        if (status.settings.tracking == TrackingMode.Binary) toggle(status) else openQuantity(status)
    }

    fun openQuantity(status: HabitStatus) {
        val state = mutableState.value
        val row = state.allHabits.firstOrNull { it.habit.id == status.habit.id } ?: return
        val mode = row.settings.tracking as? TrackingMode.Quantity ?: return
        if (!state.canWrite || !row.canLogToday || !row.scheduledToday || row.habit.id in state.writingIds) return
        if (state.today != dates.today()) { dates.refresh(); return }
        val entry = QuantityEntry(row.habit.id, state.today, mode.target.toPlainString(), mode.unit,
            (row.valueToday as? CompletionValue.Quantity)?.amount?.toPlainString().orEmpty())
        retainQuantity(entry)
    }

    fun changeAmount(amount: String) {
        val entry = state.value.quantity ?: return
        if (!entry.saving) retainQuantity(entry.copy(amount = amount, invalid = false, failed = false))
    }
    fun dismissQuantity() { if (state.value.quantity?.saving != true) retainQuantity(null) }
    fun saveQuantity(clear: Boolean = false) {
        val state = mutableState.value
        val entry = state.quantity ?: return
        if (entry.saving || !state.canWrite || entry.habitId in state.writingIds) return
        val row = state.allHabits.firstOrNull { it.habit.id == entry.habitId }
        val mode = row?.settings?.tracking as? TrackingMode.Quantity
        if (row == null || !row.scheduledToday || !row.canLogToday || mode == null ||
            entry.stale || entry.date != state.today || entry.date != dates.today() ||
            mode.unit != entry.unit || mode.target.compareTo(BigDecimal(entry.target)) != 0) {
            retainQuantity(entry.copy(stale = true)); dates.refresh(); return
        }
        if (!clear && !entry.valid) { retainQuantity(entry.copy(invalid = true)); return }
        retainQuantity(entry.copy(saving = true, failed = false, invalid = false))
        mutableState.update { it.copy(writingIds = it.writingIds + row.habit.id) }
        viewModelScope.launch {
            try {
                habits.quantityToday(row.habit, entry.date,
                    if (clear) null else CompletionValue.Quantity(requireNotNull(entry.parsed), entry.unit))
                retainQuantity(null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                retainQuantity(entry.copy(failed = true)); dates.refresh()
            } finally {
                mutableState.update { it.copy(writingIds = it.writingIds - row.habit.id) }
            }
        }
    }

    private fun retainQuantity(entry: QuantityEntry?) {
        saved["amountHabit"] = entry?.habitId; saved["amountDate"] = entry?.date?.toEpochDay()
        saved["amountTarget"] = entry?.target; saved["amountUnit"] = entry?.unit; saved["amountInput"] = entry?.amount
        mutableState.update { it.copy(quantity = entry) }
    }
    private fun restoreQuantity(): QuantityEntry? {
        val id = saved.get<Long>("amountHabit") ?: return null
        val date = saved.get<Long>("amountDate") ?: return null
        val target = saved.get<String>("amountTarget") ?: return null
        val unit = saved.get<String>("amountUnit") ?: return null
        return QuantityEntry(id, LocalDate.ofEpochDay(date), target, unit, saved["amountInput"] ?: "")
    }
    companion object {
        val Factory = viewModelFactory { initializer {
            HomeViewModel(container.habits, container.settings.configuration, container.dates, createSavedStateHandle())
        } }
    }
}
