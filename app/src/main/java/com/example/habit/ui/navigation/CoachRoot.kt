package com.example.habit.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.ui.containerFactory
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.placeholder.NotBuiltYetScreen
import kotlinx.coroutines.flow.*
import com.example.habit.R

class CoachAvailabilityViewModel(configuration: Flow<Boolean>) : ViewModel() {
    val enabled = configuration.catch { emit(false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    companion object { val Factory = containerFactory { CoachAvailabilityViewModel(it.settings.coachEnabled) } }
}

/** Still no request/service; disabled availability is truthful even on a restored root. */
@Composable
internal fun CoachRoot(onSelectTab: (HomeTab) -> Unit, viewModel: CoachAvailabilityViewModel = viewModel(factory = CoachAvailabilityViewModel.Factory)) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    NotBuiltYetScreen(HomeTab.COACH, onSelectTab,
        titleRes = if (enabled) R.string.not_built_title else R.string.coach_disabled_title,
        body = if (enabled) null else androidx.compose.ui.res.stringResource(R.string.coach_disabled_body))
}
