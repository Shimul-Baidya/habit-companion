package com.example.habit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.data.prefs.*
import com.example.habit.ui.containerFactory
import com.example.habit.ui.SystemBarIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** A failed preference read retains the last theme; Profile exposes the read error and Retry. */
class AppThemeViewModel(configuration: Flow<SettingsRepository.Configuration>) : ViewModel() {
    private val mutable = MutableStateFlow(ThemeMode.SYSTEM)
    val mode = mutable.asStateFlow()
    init { viewModelScope.launch {
        configuration.map { it.themeMode }.distinctUntilChanged().retryWhen { cause, _ ->
            if (cause is CancellationException) false else { delay(1000); true }
        }.collect { mutable.value = it }
    } }
    companion object { val Factory = containerFactory { AppThemeViewModel(it.settings.configuration) } }
}
@Composable
fun AppTheme(viewModel: AppThemeViewModel = viewModel(factory = AppThemeViewModel.Factory), content: @Composable () -> Unit) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    HabitTheme(darkTheme = when (mode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }) {
        SystemBarIcons(HabitTheme.colors.isDark)
        content()
    }
}
