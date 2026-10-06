package com.example.habit.ui.screens.progress

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.habit.data.*
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.domain.*
import com.example.habit.ui.container
import com.example.habit.ui.screens.insights.*
import kotlinx.coroutines.flow.*

enum class ProgressRange { WEEK, MONTH }
data class ProgressFacts(val totals: CompletionTotals, val bars: List<CalendarBucket>)
/** Presentation ranges reuse the snapshot's weighted occurrence buckets, including pending slots. */
fun progressFacts(snapshot: HabitSnapshot, range: ProgressRange): ProgressFacts {
    val bars = if (range == ProgressRange.WEEK) snapshot.week else snapshot.month
    return ProgressFacts(bars.fold(CompletionTotals()) { sum, bar -> sum + bar.totals }, bars)
}
data class ProgressUiState(val read: SnapshotRead = SnapshotRead(), val range: ProgressRange = ProgressRange.WEEK) {
    val facts get() = read.snapshot?.let { progressFacts(it, range) }
}
class ProgressViewModel(habits: HabitDataSource, preferences: Flow<SettingsRepository.Configuration>, dates: DateProvider,
    private val saved: SavedStateHandle) : ViewModel() {
    private val reader = SnapshotReader(viewModelScope, habits, preferences, dates)
    private val range = saved.getStateFlow("progressRange", ProgressRange.WEEK.name)
    val state = combine(reader.state, range) { read, selected ->
        ProgressUiState(read, runCatching { ProgressRange.valueOf(selected) }.getOrDefault(ProgressRange.WEEK))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProgressUiState())
    fun selectRange(value: ProgressRange) { saved["progressRange"] = value.name }
    fun retry() = reader.retry()
    companion object { val Factory = viewModelFactory { initializer {
        ProgressViewModel(container.habits, container.settings.configuration, container.dates, createSavedStateHandle())
    } } }
}
