package com.example.habit.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.R
import com.example.habit.data.local.HabitEntity
import com.example.habit.domain.HabitStatus
import com.example.habit.ui.components.BottomNav
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Motion
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * SCR-03 and SCR-04 are one destination. Which one renders is decided by whether any
 * habit exists, and the two crossfade into each other over 200ms on the first insert
 * (SCR-03 element 9) rather than the screen being replaced.
 */
@Composable
fun HomeScreen(
    dbError: Boolean,
    onRetry: () -> Unit,
    onAddHabit: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Picks up a date change if the app is left open across midnight.
    LaunchedEffect(Unit) { viewModel.refreshDate() }

    HomeContent(
        state = state,
        dbError = dbError,
        onRetry = onRetry,
        onToggle = viewModel::toggle,
        onAddHabit = onAddHabit,
        onOpenCoach = onOpenCoach,
        onOpenHabit = { onOpenHabit(it.habit.id) },
        onSelectTab = onSelectTab,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    dbError: Boolean,
    onRetry: () -> Unit,
    onToggle: (HabitStatus) -> Unit,
    onAddHabit: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenHabit: (HabitStatus) -> Unit,
    onSelectTab: (HomeTab) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorMessage = stringResource(R.string.home_db_error)
    val retryLabel = stringResource(R.string.home_retry)
    val tabDisabled = stringResource(R.string.empty_tab_disabled)

    // SCR-01's failure path lands here: Room threw, so Home opened empty with a retry.
    LaunchedEffect(dbError) {
        if (dbError) {
            val result = snackbarHostState.showSnackbar(errorMessage, actionLabel = retryLabel)
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }

    // 7 — on the empty Home, Progress and Coach are visible but inert.
    val enabledTabs = if (state.isEmpty) {
        setOf(HomeTab.HOME, HomeTab.PROFILE)
    } else {
        HomeTab.entries.toSet()
    }

    Scaffold(
        containerColor = HabitTheme.colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNav(
                selected = HomeTab.HOME,
                enabled = enabledTabs,
                onSelect = { tab ->
                    if (tab in enabledTabs) {
                        onSelectTab(tab)
                    } else {
                        // "Disabled tabs show a one-line explanation" (SCR-03 element 7).
                        scope.launch { snackbarHostState.showSnackbar(tabDisabled) }
                    }
                },
            )
        },
        floatingActionButton = {
            // 12 — only on the populated Home; SCR-03 already has a filled Add button.
            if (!state.isEmpty) {
                FloatingActionButton(
                    onClick = onAddHabit,
                    shape = Radius.card,
                    containerColor = HabitTheme.colors.primary,
                    contentColor = HabitTheme.colors.onPrimary,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults
                        .elevation(defaultElevation = Elevation.fab),
                    modifier = Modifier.padding(Spacing.lg),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.home_add_habit),
                    )
                }
            }
        },
    ) { innerPadding ->
        Crossfade(
            targetState = state.isEmpty,
            animationSpec = tween(durationMillis = Motion.CROSSFADE),
            label = "emptyToPopulated",
            modifier = Modifier.fillMaxSize(),
        ) { isEmpty ->
            if (isEmpty) {
                EmptyHome(
                    userName = state.userName,
                    today = state.today,
                    onAddHabit = onAddHabit,
                    onOpenCoach = onOpenCoach,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                TodayHome(
                    state = state,
                    onToggle = onToggle,
                    onOpenHabit = onOpenHabit,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun EmptyHomePreview() {
    HabitTheme {
        HomeContent(
            state = HomeUiState(loading = false, userName = "Shimul"),
            dbError = false,
            onRetry = {}, onToggle = {}, onAddHabit = {},
            onOpenCoach = {}, onOpenHabit = {}, onSelectTab = {},
        )
    }
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun TodayHomePreview() {
    HabitTheme { HomeContent(state = sampleState(), dbError = false, onRetry = {}, onToggle = {},
        onAddHabit = {}, onOpenCoach = {}, onOpenHabit = {}, onSelectTab = {}) }
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun TodayHomeDarkPreview() {
    HabitTheme(darkTheme = true) {
        HomeContent(state = sampleState(), dbError = false, onRetry = {}, onToggle = {},
            onAddHabit = {}, onOpenCoach = {}, onOpenHabit = {}, onSelectTab = {})
    }
}

private fun sampleState() = HomeUiState(
    loading = false,
    userName = "Shimul",
    today = LocalDate.now(),
    healthy = listOf(
        status(1, "Read 10 pages", done = true, streak = 12),
        status(2, "Meditate", done = true, streak = 5),
        status(3, "Walk 5,000 steps", done = false, streak = 3),
    ),
    atRisk = listOf(status(4, "Run 3km", done = false, streak = 0, atRisk = true)),
)

private fun status(
    id: Long,
    name: String,
    done: Boolean,
    streak: Int,
    atRisk: Boolean = false,
) = HabitStatus(
    habit = HabitEntity(id = id, name = name),
    scheduledToday = true,
    doneToday = done,
    currentStreak = streak,
    atRisk = atRisk,
)
