package com.example.habit.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.ui.components.PrimaryButton
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Sizes
import com.example.habit.ui.theme.Spacing
import java.time.LocalDate

/**
 * SCR-03 — Home, empty state. The only job of this screen is to get one habit created,
 * so there is exactly one filled button on it and everything else defers to it.
 *
 * Content is short, so this is a scrolling Column, not a LazyColumn — the list only
 * arrives with the first habit, at which point SCR-04 takes over.
 */
@Composable
fun EmptyHome(
    userName: String,
    today: LocalDate,
    onAddHabit: () -> Unit,
    onOpenCoach: () -> Unit,
    modifier: Modifier = Modifier,
    coachEnabled: Boolean = true,
    hasHistory: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        HomeHeader(userName = userName, today = today)

        Spacer(Modifier.height(Spacing.xxxl))

        EmptyCard(onAddHabit = onAddHabit, hasHistory = hasHistory)

        Spacer(Modifier.height(Spacing.lg))

        if (coachEnabled) CoachShortcut(onOpenCoach = onOpenCoach, hasHistory = hasHistory)

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/** Elements 3, 4 and 5 — 20dp radius and 32dp inner padding are specified on this card. */
@Composable
private fun EmptyCard(onAddHabit: () -> Unit, hasHistory: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        modifier = Modifier
            .fillMaxWidth()
            .border(Elevation.cardOutline, HabitTheme.colors.outline, RoundedCornerShape(20.dp)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xxxl),
        ) {
            // 4 — decorative, so it is hidden from screen readers rather than described.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(HabitTheme.colors.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = HabitTheme.colors.primary,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            Text(
                text = stringResource(if (hasHistory) R.string.empty_active_title else R.string.empty_title),
                style = HabitTheme.type.titleLg,
                color = HabitTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = stringResource(if (hasHistory) R.string.empty_active_body else R.string.empty_body),
                style = HabitTheme.type.caption,
                color = HabitTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xxl))

            // 5 — C-08, the one filled button on the screen.
            PrimaryButton(
                text = stringResource(if (hasHistory) R.string.home_add_habit else R.string.empty_add_first),
                onClick = onAddHabit,
            )
        }
    }
}

/** 6 — 88dp tall, 16dp radius, 1dp outline, 44dp leading circle. Opens SCR-06. */
@Composable
private fun CoachShortcut(onOpenCoach: () -> Unit, hasHistory: Boolean) {
    val description = stringResource(R.string.empty_coach_title)
    Card(
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .border(Elevation.cardOutline, HabitTheme.colors.outline, Radius.card)
            .clickable(onClick = onOpenCoach)
            .semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(Spacing.lg),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.iconEmptyState)
                    .clip(CircleShape)
                    .background(HabitTheme.colors.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = HabitTheme.colors.onPrimaryContainer,
                    modifier = Modifier.size(Sizes.iconInline),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.empty_coach_title),
                    style = HabitTheme.type.title,
                    color = HabitTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(if (hasHistory) R.string.empty_active_coach_body else R.string.empty_coach_body),
                    style = HabitTheme.type.caption,
                    color = HabitTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}
