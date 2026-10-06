package com.example.habit.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.habit.ui.theme.Radius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.domain.HabitStatus
import com.example.habit.ui.components.CompletionRing
import com.example.habit.ui.components.HabitRow
import com.example.habit.ui.components.SectionLabel
import com.example.habit.ui.theme.Elevation
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Spacing

/**
 * SCR-04 — Home, today. The daily hub: every habit and its streak on the first screen,
 * one tap to log.
 *
 * At-risk habits are not hidden and not recoloured into a wall of red — they are simply
 * sorted beneath a Needs attention divider, which only exists when one of them does.
 */
@Composable
fun TodayHome(
    state: HomeUiState,
    onToggle: (HabitStatus) -> Unit,
    onOpenHabit: (HabitStatus) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth().padding(contentPadding).testTag("home-habits"),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                Spacer(Modifier.height(Spacing.sm))
                HomeHeader(userName = state.userName, today = state.today)
                Spacer(Modifier.height(Spacing.xxl))
                TodaySummary(state)
                Spacer(Modifier.height(Spacing.xxl))
                SectionHeader(done = state.doneToday, total = state.totalToday)
            }
        }

        items(state.healthy, key = { "habit-${it.habit.id}" }) { status ->
            HabitRow(
                status = status.copy(canLogToday = status.canLogToday && state.canWrite && status.habit.id !in state.writingIds),
                onToggle = { onToggle(status) },
                onOpen = { onOpenHabit(status) },
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
        }

        // 11 — only rendered when at least one habit is at risk.
        if (state.atRisk.isNotEmpty()) {
            item(key = "needs-attention") {
                NeedsAttentionDivider(modifier = Modifier.padding(horizontal = Spacing.gutter))
            }
            items(state.atRisk, key = { "at-risk-${it.habit.id}" }) { status ->
                HabitRow(
                    status = status.copy(canLogToday = status.canLogToday && state.canWrite && status.habit.id !in state.writingIds),
                    onToggle = { onToggle(status) },
                    onOpen = { onOpenHabit(status) },
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
        }

        // The FAB has its own reserved strip; only a normal trailing gutter is needed.
        item(key = "list-end") { Spacer(Modifier.height(Spacing.lg)) }
    }
}

/** 4 and 5 — the 64dp today ring beside the all-time best streak (including archived history). */
@Composable
private fun TodaySummary(state: HomeUiState) {
    val ringDescription = stringResource(
        R.string.home_today_ring,
        state.doneToday,
        state.totalToday,
    )
    val count = "${state.doneToday}/${state.totalToday}"
    val ringSize = maxOf(64, count.length * 11 + 16).dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Card(shape = Radius.card, colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        modifier = Modifier.fillMaxWidth().border(Elevation.cardOutline, HabitTheme.colors.outline, Radius.card)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { contentDescription = ringDescription }) {
                CompletionRing(progress = state.ringProgress, size = ringSize, stroke = 6.dp, showCheck = false)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count, style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                    Text(stringResource(R.string.home_section_today).uppercase(), style = HabitTheme.type.micro, color = HabitTheme.colors.onSurfaceMuted)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val summary = when {
                    state.noDueToday -> stringResource(R.string.home_no_due_today)
                    state.allDone -> stringResource(R.string.home_all_done)
                    else -> pluralStringResource(R.plurals.home_left_today, state.totalToday - state.doneToday, state.totalToday - state.doneToday)
                }
                Text(summary, style = HabitTheme.type.body,
                    color = HabitTheme.colors.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(state.bestStreak.toString(), style = HabitTheme.type.stat, color = HabitTheme.colors.primary)
                    SectionLabel(text = stringResource(R.string.home_streak_label))
                }
            }
        }
    }
}

/** 6 — title/20sp heading with the done-of-total counter at caption/14sp. */
@Composable
private fun SectionHeader(done: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.home_section_today),
            style = HabitTheme.type.title,
            color = HabitTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.home_counter, done, total),
            style = HabitTheme.type.caption,
            color = HabitTheme.colors.onSurfaceMuted,
        )
    }
}

/** 11 — label/12sp caps in danger, 20dp above and 12dp below. */
@Composable
private fun NeedsAttentionDivider(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Spacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionLabel(
                text = stringResource(R.string.home_needs_attention),
                color = HabitTheme.colors.danger,
            )
            HorizontalDivider(
                thickness = Elevation.cardOutline,
                color = HabitTheme.colors.outline,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}
