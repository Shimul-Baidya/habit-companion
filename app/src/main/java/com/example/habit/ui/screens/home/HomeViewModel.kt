package com.example.habit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.data.HabitDataSource
import com.example.habit.data.HabitSnapshot
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.DateProvider
import com.example.habit.domain.HabitStatus
import com.example.habit.domain.TrackingMode
import com.example.habit.ui.containerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val writingIds: Set<Long> = emptySet(),
) {
    val allHabits: List<HabitStatus> get() = healthy + atRisk
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
) : ViewModel() {
    private val attempts = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow(HomeUiState(today = dates.today()))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            attempts.collectLatest {
                mutableState.update { it.copy(loading = true, readError = false) }
                try {
                    combine(dates.dates, configuration) { date, prefs -> date to prefs }.collectLatest { (date, prefs) ->
                        mutableState.update { it.copy(loading = true, readError = false, today = date, userName = prefs.userName) }
                        habits.observeSnapshot(date, prefs.weekStart).collect { snapshot ->
                            mutableState.update { previous ->
                                previous.copy(loading = false, readError = false, today = snapshot.today,
                                    healthy = snapshot.active.filterNot { it.atRisk },
                                    atRisk = snapshot.active.filter { it.atRisk }, snapshot = snapshot,
                                    bestStreak = snapshot.allTimeBest)
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

    companion object {
        val Factory = containerFactory { HomeViewModel(it.habits, it.settings.configuration, it.dates) }
    }
}
