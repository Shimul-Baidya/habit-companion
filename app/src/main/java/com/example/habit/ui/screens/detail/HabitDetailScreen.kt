package com.example.habit.ui.screens.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.domain.*
import com.example.habit.ui.components.habitIcon
import com.example.habit.ui.components.habitNameTransition
import com.example.habit.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitDetailScreen(onBack: () -> Unit, onManage: (Long) -> Unit,
    viewModel: HabitDetailViewModel = viewModel(factory = HabitDetailViewModel.Factory), onOpenCoach: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing, state.value?.record?.habit?.archivedAt) { if (state.missing || state.value?.record?.habit?.archivedAt != null) onBack() }
    HabitDetailContent(state, onBack, { state.value?.record?.habit?.id?.let(onManage) },
        viewModel::moveMonth, viewModel::openDate, { if (state.coachEnabled && !state.readError && onOpenCoach != null) onOpenCoach() else viewModel.coach() }, viewModel::retry)
    state.entry?.let { CorrectionDialog(it, state.canWrite, viewModel::changeInput, viewModel::saveEntry, viewModel::dismissEntry, viewModel::retry) }
    state.notice?.let { notice -> AlertDialog(onDismissRequest = viewModel::clearNotice,
        title = { Text("Coach") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = viewModel::clearNotice) { Text("OK") } }) }
}

@Composable
internal fun HabitDetailContent(state: DetailUiState, onBack: () -> Unit, onManage: () -> Unit,
    onMonth: (Int) -> Unit, onDate: (LocalDate) -> Unit, onCoach: () -> Unit, onRetry: () -> Unit) {
    val colors = HabitTheme.colors
    val evaluated = state.value
    val value = evaluated?.evaluation
    val habit = evaluated?.record?.habit
    val scroll = rememberLazyListState()
    val collapsed by remember { derivedStateOf { scroll.firstVisibleItemIndex > 0 } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(150), label = "collapsed-title")
    Scaffold(containerColor = colors.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onSurface) }
                    Text(if (collapsed) habit?.name.orEmpty() else "", style = HabitTheme.type.title, color = colors.onSurface,
                        modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha })
                    IconButton(onClick = onManage, enabled = state.canWrite) { Icon(Icons.Default.MoreVert, "Manage habit", tint = colors.onSurface) }
                }
            }
        }, bottomBar = {
            if (value != null && habit?.archivedAt == null) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter)) {
                    if (value.metrics.attention == HabitAttention.AT_RISK) {
                        Button(onClick = onCoach, enabled = state.coachEnabled && !state.readError,
                            shape = Radius.pill, colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Get a plan", style = HabitTheme.type.title) }
                    } else {
                        OutlinedButton(onClick = onCoach, enabled = state.coachEnabled && !state.readError, shape = Radius.pill,
                            border = BorderStroke(2.dp, if (state.coachEnabled) colors.primary else colors.outline),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Ask the Coach", color = if (state.coachEnabled) colors.primary else colors.onSurfaceFaint, style = HabitTheme.type.title) }
                    }
                    if (!state.coachEnabled) Text("Enable Coach suggestions in Profile.", color = colors.onSurfaceMuted,
                        style = HabitTheme.type.caption, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
        }) { padding ->
        LazyColumn(state = scroll, contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl), modifier = Modifier.fillMaxSize().padding(padding).testTag("detail-scroll")) {
            if (state.readError) item { Column { Text("Could not read local history. Your saved data is retained.", color = colors.onSurface); TextButton(onClick = onRetry) { Text("Retry") } } }
            if (value == null || habit == null) {
                item { if (state.loading) CircularProgressIndicator() else Text("Habit is unavailable.", color = colors.onSurface) }
            } else {
                item("title") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Box(Modifier.size(44.dp).clip(Radius.md).background(colors.habitAccent(habit.colorKey).copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                            Icon(habitIcon(habit.iconKey), null, tint = colors.habitAccent(habit.colorKey), modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(habit.name, style = HabitTheme.type.headline, color = colors.onSurface, modifier = Modifier.habitNameTransition(habit.id))
                            Text(value.settingsToday?.let { scheduleText(it.schedule) } ?: "Not yet eligible on the current device date", style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
                        }
                    }
                }
                item("stats") {
                    val totals = value.totals(DateRange(state.month.atDay(1), state.month.atEndOfMonth()))
                    val units = if (occurrenceUnits(value)) "occ." else "d"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DetailStat("${value.metrics.currentStreak}$units", "CURRENT", Modifier.weight(1f),
                            if (value.metrics.attention == HabitAttention.HEALTHY) colors.primary else colors.onSurface)
                        DetailStat("${value.metrics.bestStreak}$units", "BEST", Modifier.weight(1f), colors.onSurface)
                        DetailStat(totals.consistency?.let { "${(it * 100).toInt()}%" } ?: "—", "RATE", Modifier.weight(1f), colors.onSurface)
                    }
                    if (value.metrics.attention == HabitAttention.NEUTRAL) Text("No settled history yet. Start with the next scheduled occurrence.",
                        style = HabitTheme.type.caption, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = Spacing.md))
                }
                val pattern = patternReading(value)
                if (pattern != null || positiveCallout(value)) item("callout") {
                    Row(Modifier.fillMaxWidth().clip(Radius.card).background(if (pattern != null) colors.dangerContainer else colors.primaryContainer)
                        .heightIn(min = 76.dp).padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        if (pattern == null) Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = colors.onPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            if (pattern == null) Text("Longest run yet.", style = HabitTheme.type.body, color = colors.onPrimaryContainer)
                            Text(pattern ?: "${value.metrics.currentStreak} completed ${if (occurrenceUnits(value)) "occurrences" else "scheduled days"} — protect the next.",
                                style = if (pattern == null) HabitTheme.type.caption else HabitTheme.type.body, color = colors.onSurface)
                        }
                    }
                }
                item("calendar") { DetailCalendar(value, state.month, state.weekStart, state.canWrite && state.entry?.saving != true, onMonth, onDate) }
                item("legend") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Legend("Done", colors.primary); Legend("Missed", colors.surfaceSunken); Legend("Today", colors.surface, true)
                        if (value.history.settings.any { it.settings.tracking is TrackingMode.Quantity }) Legend("Partial", colors.primaryContainer)
                        if (value.history.settings.any { it.settings.schedule is HabitSchedule.Weekly }) Legend("Flexible", colors.surfaceCard)
                    }
                    if (value.history.settings.any { it.settings.schedule is HabitSchedule.Weekly }) Text("Weekly misses are quota shortfalls at week end; flexible dates are not daily misses.",
                        style = HabitTheme.type.caption, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = Spacing.md))
                }
            }
        }
    }
}
@Composable private fun DetailStat(text: String, label: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("stat-$label")) {
            val suffix = if (text.endsWith("occ.")) "occ." else if (text.endsWith("d")) "d" else ""
            Text(text.removeSuffix(suffix), style = HabitTheme.type.stat, color = color)
            Text(if (suffix == "d") "days" else if (suffix == "occ.") "occurrences" else "", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
        }
        Text(label, style = HabitTheme.type.label, color = HabitTheme.colors.onSurfaceMuted)
    }
}
@Composable private fun Legend(label: String, color: androidx.compose.ui.graphics.Color, outline: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(color).then(if (outline) Modifier.border(2.dp, HabitTheme.colors.primary, CircleShape) else Modifier))
        Text(label, style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
    }
}
@Composable private fun DetailCalendar(value: HabitEvaluation, month: YearMonth, weekStart: DayOfWeek,
    enabled: Boolean, onMonth: (Int) -> Unit, onDate: (LocalDate) -> Unit) {
    val colors = HabitTheme.colors
    val totals = value.totals(DateRange(month.atDay(1), month.atEndOfMonth()))
    val days = calendarDays(value, month)
    val offset = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
    val cells = List<DetailDay?>(offset) { null } + days
    Column(Modifier.fillMaxWidth().testTag("detail-calendar").pointerInput(month) {
        var drag = 0f
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (kotlin.math.abs(drag) > 60.dp.toPx()) onMonth(if (drag < 0) 1 else -1) },
            onHorizontalDrag = { change, amount -> change.consume(); drag += amount })
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = HabitTheme.type.title, color = colors.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMonth(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous month", tint = colors.onSurfaceMuted) }
            IconButton(onClick = { onMonth(1) }) { Icon(Icons.Default.ChevronRight, "Next month", tint = colors.onSurfaceMuted) }
        }
        Text("${totals.completed} / ${totals.eligible} ${if (occurrenceUnits(value)) "occurrences" else "scheduled days"}", style = HabitTheme.type.caption, color = colors.onSurfaceMuted)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gridWidth = maxOf(maxWidth, 336.dp)
        Column(Modifier.then(if (maxWidth < 336.dp) Modifier.horizontalScroll(rememberScrollState()) else Modifier).width(gridWidth)) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md)) { repeat(7) { day ->
            val weekday = DayOfWeek.of((weekStart.value - 1 + day) % 7 + 1)
            Text(weekday.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = HabitTheme.type.micro, color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) { repeat(7) { column ->
                val day = row.getOrNull(column)
                // Seven equal, non-overlapping columns retain at least 48dp each on narrow screens.
                Box(Modifier.weight(1f).heightIn(min = 48.dp).then(if (day != null) Modifier.testTag("date-${day.date}").semantics {
                    contentDescription = "${day.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}, ${day.mark.name.lowercase()}"
                }.clickable(enabled = enabled && day.editable, role = Role.Button) { onDate(day.date) } else Modifier), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val fill by animateColorAsState(when (day.mark) {
                            DayMark.DONE -> colors.primary; DayMark.PARTIAL -> colors.primaryContainer
                            DayMark.MISSED -> colors.surfaceSunken; else -> colors.surfaceCard.copy(alpha = if (day.mark == DayMark.UNAVAILABLE) .35f else 1f)
                        }, tween(300), label = "day-state")
                        Box(Modifier.size(28.dp).clip(CircleShape).background(fill)
                            .then(if (day.date == value.today) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                            if (day.mark == DayMark.PARTIAL) Icon(Icons.Default.Remove, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } }
        }
        }
        }
    }
}
@Composable internal fun CorrectionDialog(entry: CorrectionEntry, canWrite: Boolean, onInput: (String) -> Unit,
    onSave: (Boolean) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val quantity = entry.quantity
    AlertDialog(onDismissRequest = { if (!entry.saving) onDismiss() }, title = { Text(entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (quantity != null) {
                Text("Target: ${quantity.target.stripTrailingZeros().toPlainString()} ${quantity.unit}")
                OutlinedTextField(entry.input, onInput, label = { Text("Amount (${quantity.unit})") }, singleLine = true,
                    enabled = !entry.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("correction-amount"))
                Text("Partial amounts are saved; reaching this date's target marks it done.", style = HabitTheme.type.caption)
            } else Text(if (entry.before == CompletionValue.Binary(true)) "This date is done. Mark it not done?" else "Mark this scheduled date done?")
            if (entry.stale) Text("History or date changed. Close and reopen this date.", color = HabitTheme.colors.danger)
            entry.error?.let { Text(it, color = HabitTheme.colors.danger) }
            if (!canWrite) { Text("Local history is unavailable."); TextButton(onClick = onRetry) { Text("Retry") } }
        } }, confirmButton = { TextButton(onClick = { onSave(false) }, enabled = canWrite && !entry.saving && !entry.stale) {
            Text(if (entry.saving) "Saving…" else if (quantity != null) "Save amount" else if (entry.before == CompletionValue.Binary(true)) "Mark not done" else "Mark done")
        } }, dismissButton = {
            Row { if (quantity != null) TextButton(onClick = { onSave(true) }, enabled = canWrite && !entry.saving && !entry.stale) { Text("Clear") }
                TextButton(onClick = onDismiss, enabled = !entry.saving) { Text("Cancel") } }
        })
}
