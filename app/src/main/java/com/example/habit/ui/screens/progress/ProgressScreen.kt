package com.example.habit.ui.screens.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.domain.*
import com.example.habit.ui.components.*
import com.example.habit.ui.screens.insights.*
import com.example.habit.ui.theme.*
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(onOpenHabit: (Long) -> Unit, onSelectTab: (HomeTab) -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressContent(state, viewModel::selectRange, viewModel::retry, onOpenHabit, onSelectTab)
}

@Composable
internal fun ProgressContent(state: ProgressUiState, onRange: (ProgressRange) -> Unit, onRetry: () -> Unit,
    onOpenHabit: (Long) -> Unit, onSelectTab: (HomeTab) -> Unit) {
    val scroll = rememberLazyListState()
    Scaffold(containerColor = HabitTheme.colors.surface,
        bottomBar = { BottomNav(HomeTab.PROGRESS, onSelectTab) }) { insets ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(insets).testTag("progress-scroll"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Each half has a separate 48dp target; the visible pill retains the supplied 36dp track.
                FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Progress", style = HabitTheme.type.headline, color = HabitTheme.colors.onSurface, modifier = Modifier.align(Alignment.CenterVertically))
                    Row(Modifier.background(HabitTheme.colors.surfaceSunken, Radius.pill).selectableGroup()) {
                        ProgressRange.entries.forEach { range ->
                            Box(Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp)
                                .clip(Radius.pill).selectable(state.range == range, role = Role.Tab, onClick = { onRange(range) })
                                .padding(vertical = 6.dp)
                                .background(if (state.range == range) HabitTheme.colors.surfaceCard else HabitTheme.colors.surfaceSunken, Radius.pill)
                                .padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(if (range == ProgressRange.WEEK) "Week" else "Month", style = HabitTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = if (state.range == range) HabitTheme.colors.primary else HabitTheme.colors.onSurfaceMuted)
                            }
                        }
                    }
                }
            }
            if (state.read.loading || state.read.error) item { ReadNotice(state.read, onRetry) }
            val snapshot = state.read.snapshot
            val facts = state.facts
            if (snapshot != null && facts != null) {
                item {
                    Box(Modifier.padding(bottom = 12.dp)) { StatCells(listOf(consistencyText(facts.totals.consistency) to "CONSISTENCY",
                        facts.totals.completed.toString() to if (state.range == ProgressRange.WEEK) "DONE THIS WK" else "DONE THIS MO",
                        snapshot.allTimeBest.toString() to "BEST STREAK"), cards = true) }
                }
                item {
                    InsightCard {
                        val month = state.range == ProgressRange.MONTH
                        if (month || LocalDensity.current.fontScale > 1.1f) {
                            Column {
                                Text(if (month) "Weekly consistency" else "Daily activity", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                                Text(if (month) "% required completions" else "required completions", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Daily activity", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface)
                            Text("required completions", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                        }
                        Text(if (month) snapshot.today.format(DateTimeFormatter.ofPattern("MMMM yyyy")) else
                            "${facts.bars.first().range.first.format(DateTimeFormatter.ofPattern("d MMM"))} – ${facts.bars.last().range.last.format(DateTimeFormatter.ofPattern("d MMM"))}",
                            style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        key(state.range, facts.bars.map { it.range }) { ActivityChart(facts.bars, month, snapshot.today) }
                        if (facts.totals.eligible == 0L) Text("No required completions in this period.", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
                        else Text("${facts.totals.completed} of ${facts.totals.eligible} required completions · ${facts.totals.pending} pending",
                            style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (snapshot.records.isEmpty()) item { Text("No habits yet. Add a habit on Home to start your history.", style = HabitTheme.type.body, color = HabitTheme.colors.onSurfaceMuted) }
                if (snapshot.topStreaks.isNotEmpty()) {
                    item { Text("Top streaks", style = HabitTheme.type.title, color = HabitTheme.colors.onSurface, modifier = Modifier.padding(top = 16.dp)) }
                    items(snapshot.topStreaks, key = { it.habit.id }) { row -> StreakRail(row, snapshot.topStreaks.first().currentStreak, { onOpenHabit(row.habit.id) }, !state.read.error && !state.read.loading) }
                    item { Text("Streaks count completed scheduled occurrences; Weekly quotas count required slots.", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted) }
                }
            }
        }
    }
}

@Composable
private fun ActivityChart(bars: List<CalendarBucket>, month: Boolean, today: java.time.LocalDate) {
    val colors = HabitTheme.colors
    val max = bars.maxOfOrNull { it.totals.completed }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().testTag("progress-chart"), horizontalArrangement = Arrangement.SpaceBetween) {
        bars.forEachIndexed { index, bar ->
            val proportion = if (month) (bar.totals.consistency ?: 0.0).toFloat() else bar.totals.completed.toFloat() / max
            val progress = remember { Animatable(0f) }
            LaunchedEffect(proportion) { delay(index * 40L); progress.animateTo(proportion, tween(400)) }
            val description = if (month) "${bar.range.first} to ${bar.range.last}: ${consistencyText(bar.totals.consistency)}, ${bar.totals.completed} of ${bar.totals.eligible} required completions"
                else "${bar.range.first}: ${bar.totals.completed} required completions, ${bar.totals.pending} pending"
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description }
                .testTag("progress-bar-$index"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(if (month) 34.dp else 24.dp).height(120.dp).clip(Radius.sm).background(colors.surfaceSunken), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(progress.value).clip(Radius.sm)
                            .background(if (month && proportion < .75f) colors.primary.copy(alpha = .55f) else colors.primary))
                    }
                    if (month && proportion >= .1f) Text("${(proportion * 100).roundToInt()}%", style = HabitTheme.type.micro, color = colors.onSurfaceMuted,
                        modifier = Modifier.offset { IntOffset(0, -(120.dp * progress.value + 2.dp).roundToPx()) })
                }
                Text(if (month) "W${index + 1}" else bar.range.first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = HabitTheme.type.caption.copy(fontWeight = if (!month && today in bar.range) FontWeight.Bold else FontWeight.Normal),
                    color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun StreakRail(row: HabitStatus, best: Int, onOpen: () -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClickLabel = "Open ${row.habit.name} details", onClick = onOpen).testTag("top-streak-${row.habit.id}"),
        shape = Radius.md, border = BorderStroke(1.dp, HabitTheme.colors.outline),
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val accent = HabitTheme.colors.habitAccent(row.habit.colorKey)
            Box(Modifier.size(36.dp).background(accent.copy(alpha = .16f), Radius.md), contentAlignment = Alignment.Center) {
                Icon(habitIcon(row.habit.iconKey), null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Text(row.habit.name, style = HabitTheme.type.title, color = HabitTheme.colors.onSurface, modifier = Modifier.weight(1f).habitNameTransition(row.habit.id))
            Box(Modifier.width(56.dp).height(6.dp).clip(Radius.pill).background(HabitTheme.colors.surfaceSunken)) {
                Box(Modifier.fillMaxWidth(row.currentStreak.toFloat() / best).fillMaxHeight().background(HabitTheme.colors.primary, Radius.pill))
            }
            Text(row.currentStreak.toString(), style = HabitTheme.type.title, color = HabitTheme.colors.primary,
                modifier = Modifier.semantics { contentDescription = "${row.currentStreak} completed occurrences" })
        }
    }
}
