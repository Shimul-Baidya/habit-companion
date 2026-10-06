package com.example.habit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.data.HabitRepository
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.HabitStatus
import com.example.habit.ui.containerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs both SCR-03 and SCR-04: they are one destination whose empty and populated forms
 * crossfade into each other, so they share one state holder.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val today: LocalDate = LocalDate.now(),
    /** Healthy habits, in creation order. */
    val healthy: List<HabitStatus> = emptyList(),
    /** At-risk habits, rendered under the Needs attention divider (SCR-04 element 11). */
    val atRisk: List<HabitStatus> = emptyList(),
) {
    val allHabits: List<HabitStatus> get() = healthy + atRisk

    /** SCR-03 renders only once we know the list is genuinely empty. */
    val isEmpty: Boolean get() = !loading && allHabits.isEmpty()

    /** The counter and the today ring are both scoped to what is scheduled today. */
    val scheduledToday: List<HabitStatus> get() = allHabits.filter { it.scheduledToday }
    val doneToday: Int get() = scheduledToday.count { it.doneToday }
    val totalToday: Int get() = scheduledToday.size
    val ringProgress: Float
        get() = if (totalToday == 0) 0f else doneToday.toFloat() / totalToday

    val allDone: Boolean get() = totalToday > 0 && doneToday == totalToday

    /** SCR-04 element 5 — the longest streak still running, not the all-time best. */
    val longestActiveStreak: Int get() = allHabits.maxOfOrNull { it.currentStreak } ?: 0
}

class HomeViewModel(
    private val habits: HabitRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> =
        combine(today, settings.userName) { date, name -> date to name }
            .flatMapLatest { (date, name) ->
                habits.observeStatuses(date).map { statuses ->
                    HomeUiState(
                        loading = false,
                        userName = name,
                        today = date,
                        healthy = statuses.filterNot { it.atRisk },
                        atRisk = statuses.filter { it.atRisk },
                    )
                }
            }
            .catch {
                // Room failing here is the SCR-01 failure path arriving late: fall back to
                // the empty Home rather than an error screen, because the retry lives there.
                emit(HomeUiState(loading = false))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState(),
            )

    /** Recomputes everything if the app is left open across midnight. */
    fun refreshDate() {
        today.value = LocalDate.now()
    }

    /** SCR-04 element 8 — one tap toggles today. */
    fun toggle(status: HabitStatus) {
        viewModelScope.launch {
            habits.toggleToday(status.habit, today.value, done = !status.doneToday)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = containerFactory {
            HomeViewModel(it.habits, it.settings)
        }
    }
}
