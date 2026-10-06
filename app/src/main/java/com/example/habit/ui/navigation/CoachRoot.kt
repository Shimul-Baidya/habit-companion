package com.example.habit.ui.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.habit.R
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.ui.container
import com.example.habit.ui.components.*
import com.example.habit.ui.screens.coach.*
import com.example.habit.ui.theme.*
import com.example.habit.data.local.HabitRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CoachSelection(val habits: List<HabitRecord> = emptyList(), val enabled: Boolean = false,
    val ready: Boolean = false, val error: Boolean = false, val selected: Long = 0, val sheet: Boolean = false)
class CoachSelectionViewModel(records: Flow<List<HabitRecord>>, enabled: Flow<Boolean>, private val saved: SavedStateHandle) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val mutable = MutableStateFlow(CoachSelection(selected = saved["selected"] ?: 0, sheet = saved["sheet"] ?: false))
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { attempts.collectLatest {
        try { combine(records, enabled.catch { emit(false) }) { list, on -> list.filter { it.habit.archivedAt == null } to on }.collect { (list, on) ->
            val selection = state.value.selected.takeIf { id -> list.any { it.habit.id == id } } ?: 0
            if (state.value.selected > 0 && selection == 0L) saved["needsChoice"] = true
            val next = if (selection == 0L && list.size == 1 && on && saved.get<Boolean>("needsChoice") != true) list.single().habit.id else selection
            val sheet = state.value.sheet || (next == 0L && list.size > 1 && on && saved.get<Boolean>("shown") != true)
            saved["selected"] = next; saved["sheet"] = sheet
            if (sheet) saved["shown"] = true
            mutable.value = CoachSelection(list, on, true, false, next, sheet && on)
        } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { mutable.update { it.copy(error = true, ready = true) } }
    } } }
    fun choose(id: Long) { if (state.value.enabled && state.value.habits.any { it.habit.id == id }) {
        saved["selected"] = id; saved["sheet"] = false; saved["needsChoice"] = false; mutable.update { it.copy(selected = id, sheet = false) }
    } }
    fun sheet(value: Boolean) { saved["sheet"] = value; mutable.update { it.copy(sheet = value && it.enabled) } }
    fun unavailable() { saved["selected"] = 0L; mutable.update { it.copy(selected = 0) } }
    fun retry() { attempts.update { it + 1 } }
    companion object { val Factory = viewModelFactory { initializer {
        CoachSelectionViewModel(container.habitHistory.records, container.settings.coachEnabled, createSavedStateHandle())
    } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachRoot(onSelectTab: (HomeTab) -> Unit,
    viewModel: CoachSelectionViewModel = viewModel(factory = CoachSelectionViewModel.Factory),
    modelFactory: (Long) -> ViewModelProvider.Factory = { CoachViewModel.factory(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.selected > 0 && state.ready && !state.error) {
        val model: CoachViewModel = viewModel(key = "coach-habit-${state.selected}", factory = modelFactory(state.selected))
        CoachScreen(model, { onSelectTab(HomeTab.HOME) }, bottomNavigation = { BottomNav(HomeTab.COACH, onSelectTab) },
            onChooseHabit = if (state.habits.size > 1) ({ viewModel.sheet(true) }) else null)
    } else Scaffold(containerColor = HabitTheme.colors.surface, bottomBar = { Column(Modifier.navigationBarsPadding()) { BottomNav(HomeTab.COACH, onSelectTab) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Coach", style = HabitTheme.type.headline, color = HabitTheme.colors.onSurface)
            when {
                !state.ready -> CircularProgressIndicator()
                state.error -> { Text("Couldn't read local habits. Your data remains on this device."); TextButton(onClick = viewModel::retry) { Text("Retry") } }
                !state.enabled -> {
                    Text(stringResource(R.string.coach_disabled_title), style = HabitTheme.type.titleLg, color = HabitTheme.colors.onSurface)
                    Text(stringResource(R.string.coach_disabled_body), color = HabitTheme.colors.onSurfaceMuted)
                }
                state.habits.isEmpty() -> Text("Add a habit to use this tab. You can plan your first habit from Home.", color = HabitTheme.colors.onSurfaceMuted)
                else -> { Text("Choose one habit for the Coach.", color = HabitTheme.colors.onSurfaceMuted)
                    Button(onClick = { viewModel.sheet(true) }) { Text("Choose habit") } }
            }
        }
    }
    if (state.sheet && state.enabled) ModalBottomSheet(onDismissRequest = { viewModel.sheet(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = Radius.sheet, containerColor = HabitTheme.colors.surfaceCard) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Choose a habit", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
            Text("Only the selected habit's measured summary is used.", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
            state.habits.forEach { record ->
                TextButton(onClick = { viewModel.choose(record.habit.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(record.habit.name, color = HabitTheme.colors.onSurface, modifier = Modifier.fillMaxWidth())
                }
            }
            TextButton(onClick = { viewModel.sheet(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}
