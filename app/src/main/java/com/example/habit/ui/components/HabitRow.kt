package com.example.habit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.habit.R
import com.example.habit.data.local.HabitEntity
import com.example.habit.domain.HabitStatus
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Sizes
import com.example.habit.ui.theme.Spacing

/**
 * C-01 — habit row. 72dp tall, 16dp radius, surface.card, 1dp outline, 16dp inner
 * padding. The at-risk variant (SCR-04 element 10) keeps every one of those metrics;
 * only the streak reads differently, through C-03.
 */
@Composable
fun HabitRow(
    status: HabitStatus,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.habitRowHeight)
            .border(Elevation.cardOutline, HabitTheme.colors.outline, Radius.card)
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.habitRowHeight)
                .padding(horizontal = Spacing.lg),
        ) {
            CompletionRing(
                progress = if (status.doneToday) 1f else 0f,
                onClick = onToggle,
                contentDescription = stringResource(R.string.home_toggle_habit, status.habit.name),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = status.habit.name,
                    style = HabitTheme.type.title,
                    color = HabitTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = frequencyLabel(status.habit),
                    style = HabitTheme.type.caption,
                    color = HabitTheme.colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            StreakChip(streak = status.currentStreak)
        }
    }
}

@Composable
private fun frequencyLabel(habit: HabitEntity): String {
    val scheduled = Integer.bitCount(habit.scheduledDays)
    return when {
        scheduled >= 7 -> stringResource(R.string.frequency_daily)
        else -> stringResource(R.string.frequency_days_a_week, scheduled)
    }
}

@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFFF3F7F5)
@Composable
private fun HabitRowPreview() {
    HabitTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.gutter),
        ) {
            HabitRow(
                status = HabitStatus(
                    habit = HabitEntity(id = 1, name = "Read 10 pages"),
                    scheduledToday = true,
                    doneToday = true,
                    currentStreak = 12,
                    atRisk = false,
                ),
                onToggle = {},
                onOpen = {},
            )
            HabitRow(
                status = HabitStatus(
                    habit = HabitEntity(id = 2, name = "Run 3km"),
                    scheduledToday = true,
                    doneToday = false,
                    currentStreak = 0,
                    atRisk = true,
                ),
                onToggle = {},
                onOpen = {},
            )
        }
    }
}
