package com.example.habit.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Spacing

/** Skeleton uses the same header/summary/row hierarchy without invented habit data. */
@Composable
internal fun HomeLoading() {
    val description = stringResource(R.string.home_loading)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.gutter)
        .semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Box(Modifier.fillMaxWidth(0.65f).height(64.dp).background(HabitTheme.colors.surfaceSunken, Radius.md))
        Box(Modifier.fillMaxWidth().height(104.dp).background(HabitTheme.colors.surfaceSunken, Radius.card))
        LinearProgressIndicator(color = HabitTheme.colors.primary, modifier = Modifier.fillMaxWidth())
        repeat(3) { Box(Modifier.fillMaxWidth().height(72.dp).background(HabitTheme.colors.surfaceSunken, Radius.card)) }
    }
}
