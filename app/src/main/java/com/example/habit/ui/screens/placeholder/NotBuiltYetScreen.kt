package com.example.habit.ui.screens.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.habit.R
import com.example.habit.ui.components.BottomNav
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Spacing

/**
 * Holds a nav root that the deck specifies but this phase has not built, so the bottom
 * nav is fully wired and nothing dead-ends.
 */
@Composable
fun NotBuiltYetScreen(tab: HomeTab, onSelectTab: (HomeTab) -> Unit, titleRes: Int = R.string.not_built_title, body: String? = null) {
    Scaffold(
        containerColor = HabitTheme.colors.surface,
        bottomBar = { BottomNav(selected = tab, onSelect = onSelectTab) },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.gutter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(titleRes),
                    style = HabitTheme.type.titleLg,
                    color = HabitTheme.colors.onSurface,
                )
                Text(
                    text = body ?: stringResource(R.string.not_built_body, stringResource(tab.labelRes)),
                    style = HabitTheme.type.body,
                    color = HabitTheme.colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
