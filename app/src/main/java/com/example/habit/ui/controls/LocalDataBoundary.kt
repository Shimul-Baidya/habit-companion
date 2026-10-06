package com.example.habit.ui.controls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habit.AppContainer
import com.example.habit.data.controls.*
import kotlinx.coroutines.flow.StateFlow

val LocalDataGeneration = staticCompositionLocalOf { 0L }
@Composable fun LocalDataBoundary(container: AppContainer, content: @Composable () -> Unit) {
    LocalDataBoundary(container.localData.state, { container.launch { container.localData.initialize(); container.reminders.requestRefresh() } }, content)
}
@Composable internal fun LocalDataBoundary(states: StateFlow<DataState>, retry: () -> Unit, content: @Composable () -> Unit) {
    val state by states.collectAsStateWithLifecycle()
    when (val current = state) {
        is DataState.Ready -> key(current.generation) {
            CompositionLocalProvider(LocalDataGeneration provides current.generation, content = content)
        }
        else -> {
            BackHandler {}
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    if (current == DataState.RecoveryRequired) {
                        Text("Local data needs recovery")
                        Text("Couldn’t open local data. Retry reads settings and finishes any interrupted clear before habits or reminders reopen.")
                        Button(onClick = retry) { Text("Retry") }
                    } else {
                        CircularProgressIndicator()
                        Text(if (current == DataState.Clearing) "Clearing local data…" else "Opening local data…")
                    }
                }
            }
        }
    }
}
