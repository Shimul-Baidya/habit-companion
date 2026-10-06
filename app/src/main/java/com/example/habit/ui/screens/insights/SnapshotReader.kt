package com.example.habit.ui.screens.insights

import com.example.habit.data.HabitDataSource
import com.example.habit.data.HabitSnapshot
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.DateProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Both insight roots retain the last successful facts and distinguish failure from no data. */
data class SnapshotRead(val snapshot: HabitSnapshot? = null, val loading: Boolean = true, val error: Boolean = false)
internal class SnapshotReader(
    scope: CoroutineScope, habits: HabitDataSource,
    preferences: Flow<SettingsRepository.Configuration>, private val dates: DateProvider,
) {
    private val attempts = MutableStateFlow(0)
    private val mutable = MutableStateFlow(SnapshotRead())
    val state = mutable.asStateFlow()
    init { scope.launch {
        attempts.collectLatest {
            mutable.update { it.copy(loading = true, error = false) }
            try {
                combine(dates.dates, preferences.map { it.weekStart }.distinctUntilChanged()) { date, start -> date to start }
                    .collectLatest { (date, start) ->
                        mutable.update { it.copy(loading = true, error = false) }
                        habits.observeSnapshot(date, start).collect { snapshot -> mutable.value = SnapshotRead(snapshot, false, false) }
                    }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutable.update { it.copy(loading = false, error = true) } }
        }
    } }
    fun retry() { dates.refresh(); attempts.update { it + 1 } }
}
