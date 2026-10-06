package com.example.habit.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Elements 1 and 2 of SCR-03, elements 2 and 3 of SCR-04 — identical on both, which is
 * why they live in one place: the greeting must not shift when the first habit is added.
 */
@Composable
fun HomeHeader(userName: String, today: LocalDate, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greetingRes()),
                style = HabitTheme.type.titleLg,
                color = HabitTheme.colors.onSurfaceMuted,
            )
            Text(
                text = userName.ifBlank { stringResource(R.string.greeting_no_name) },
                style = HabitTheme.type.display,
                color = HabitTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DateChip(today)
    }
}

/** 72 x 64dp, 12dp radius, surface.card, weekday over date. Static, never tappable. */
@Composable
private fun DateChip(today: LocalDate) {
    Card(
        shape = Radius.md,
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        // The date is already announced by the greeting block; skip it for screen readers.
        modifier = Modifier
            .size(width = 72.dp, height = 64.dp)
            .clearAndSetSemantics {},
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().size(width = 72.dp, height = 64.dp),
        ) {
            Text(
                text = today.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .uppercase(Locale.getDefault()),
                style = HabitTheme.type.label,
                color = HabitTheme.colors.onSurfaceMuted,
            )
            Text(
                text = today.dayOfMonth.toString(),
                style = HabitTheme.type.title,
                color = HabitTheme.colors.onSurface,
            )
        }
    }
}

/** Morning, afternoon or evening by the clock (SCR-04 element 2). */
private fun greetingRes(now: LocalTime = LocalTime.now()): Int = when (now.hour) {
    in 0..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}
