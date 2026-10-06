package com.example.habit.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.habit.R
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Spacing

/** Temporary SCR-07/08 port. The local ID and caller stack are ready for chunk 06. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PendingHabitDetail(habitId: Long, onBack: () -> Unit) {
    require(habitId > 0)
    Scaffold(containerColor = HabitTheme.colors.surface, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.detail_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigation_back)) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = HabitTheme.colors.surface))
    }) { padding -> Text(stringResource(R.string.detail_pending), style = HabitTheme.type.body,
        color = HabitTheme.colors.onSurfaceMuted, modifier = Modifier.padding(padding).padding(Spacing.gutter)) }
}
