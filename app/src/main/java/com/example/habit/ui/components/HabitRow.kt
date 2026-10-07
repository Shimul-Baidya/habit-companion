package com.example.habit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.habit.ui.theme.habitAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.habit.R
import com.example.habit.data.local.HabitEntity
import com.example.habit.domain.HabitStatus
import com.example.habit.domain.HabitSchedule
import com.example.habit.domain.TrackingMode
import com.example.habit.domain.HabitAttention
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
    onManage: () -> Unit = {},
) {
    Card(
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.habitRowHeight)
            .border(Elevation.cardOutline, HabitTheme.colors.outline, Radius.card),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.habitRowHeight)
                .padding(horizontal = Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget).combinedClickable(onClick = onOpen, onLongClickLabel = "Manage habit", onLongClick = onManage)
                    .padding(vertical = Spacing.sm),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clip(Radius.md)
                    .background(HabitTheme.colors.habitAccent(status.habit.colorKey).copy(alpha = 0.16f))) {
                    Icon(habitIcon(status.habit.iconKey), contentDescription = null,
                        tint = HabitTheme.colors.habitAccent(status.habit.colorKey), modifier = Modifier.size(24.dp))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = status.habit.name,
                        modifier = Modifier.habitNameTransition(status.habit.id),
                        style = HabitTheme.type.title,
                        color = HabitTheme.colors.onSurface,
                    )
                    Text(
                        text = when {
                            !status.scheduledToday -> stringResource(if (status.settings.schedule is HabitSchedule.Weekly) R.string.home_weekly_met else R.string.home_row_not_due)
                            status.settings.tracking is TrackingMode.Quantity -> {
                                val mode = status.settings.tracking
                                val amount = (status.valueToday as? com.example.habit.domain.CompletionValue.Quantity)?.amount
                                val progress = if (amount == null) "${mode.target.stripTrailingZeros().toPlainString()} ${mode.unit}"
                                else "${amount.stripTrailingZeros().toPlainString()} / ${mode.target.stripTrailingZeros().toPlainString()} ${mode.unit}"
                                if (status.settings.schedule is HabitSchedule.Weekly) "$progress · ${frequencyLabel(status.settings.schedule)}" else progress
                            }
                            else -> frequencyLabel(status.settings.schedule)
                        },
                        style = HabitTheme.type.caption,
                        color = HabitTheme.colors.onSurfaceMuted,
                    )
                }

            }
            StreakChip(streak = status.currentStreak, neutral = status.attention == HabitAttention.NEUTRAL, occurrenceUnits = status.usesOccurrenceStreak)
            CompletionRing(
                progress = status.progressToday,
                onClick = onToggle,
                contentDescription = stringResource(when {
                    !status.scheduledToday -> R.string.home_not_due_habit
                    status.settings.tracking != TrackingMode.Binary -> R.string.home_quantity_habit
                    status.doneToday -> R.string.home_unmark_habit
                    else -> R.string.home_toggle_habit
                }, status.habit.name),
                progressDescription = (status.settings.tracking as? TrackingMode.Quantity)?.let { mode ->
                    stringResource(if (status.doneToday) R.string.quantity_achieved_state else R.string.quantity_partial_state,
                        (status.valueToday as? com.example.habit.domain.CompletionValue.Quantity)?.amount?.stripTrailingZeros()?.toPlainString() ?: "0",
                        mode.target.stripTrailingZeros().toPlainString(), mode.unit)
                },
                enabled = status.scheduledToday && status.canLogToday,
                checked = if (status.settings.tracking == TrackingMode.Binary) status.doneToday else null,
            )

        }
    }
}

@Composable
private fun frequencyLabel(schedule: HabitSchedule): String = when (schedule) {
    HabitSchedule.Daily -> stringResource(R.string.frequency_daily)
    is HabitSchedule.Weekly -> pluralStringResource(R.plurals.frequency_weekly_quota, schedule.completions, schedule.completions)
    is HabitSchedule.Custom -> pluralStringResource(R.plurals.frequency_custom_days, schedule.weekdays.size, schedule.weekdays.size)
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
