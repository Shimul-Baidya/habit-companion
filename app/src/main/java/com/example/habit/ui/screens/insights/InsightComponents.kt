package com.example.habit.ui.screens.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habit.ui.theme.*
import kotlin.math.roundToInt

fun consistencyText(value: Double?): String = value?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
internal fun InsightCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard),
        border = BorderStroke(1.dp, HabitTheme.colors.outline)) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
internal fun StatCells(values: List<Pair<String, String>>, cards: Boolean) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = if (cards) HabitTheme.type.stat else HabitTheme.type.stat.copy(fontSize = 24.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - 8.dp * (values.size - 1)) / values.size
        val padding = if (cards) 12.dp else 0.dp
        val available = with(density) { (cellWidth - padding * 2 - 4.dp).toPx() }.coerceAtLeast(1f)
        var ratio = 1f
        var numberStyle = base
        // Android can scale large fonts nonlinearly. Re-measure the candidate rather
        // than assuming sp and rendered pixels have a fixed proportion.
        repeat(8) {
            val width = values.maxOf { measurer.measure(it.first, style = numberStyle, maxLines = 1).size.width }.toFloat().coerceAtLeast(1f)
            if (width > available) {
                ratio *= (available / width).coerceAtMost(.98f)
                numberStyle = base.copy(fontSize = base.fontSize * ratio, lineHeight = base.lineHeight * ratio)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, (value, label) ->
                val content: @Composable ColumnScope.() -> Unit = {
                    Text(value, style = numberStyle, color = HabitTheme.colors.onSurface, maxLines = 1)
                    Text(label, style = HabitTheme.type.label.copy(hyphens = Hyphens.Auto, letterSpacing = if (cards && density.fontScale > 1.1f) 0.sp else HabitTheme.type.label.letterSpacing), color = HabitTheme.colors.onSurfaceMuted)
                }
                if (cards) Card(Modifier.weight(1f).fillMaxHeight().testTag("stat-cell-$index"), shape = Radius.card,
                    colors = CardDefaults.cardColors(containerColor = HabitTheme.colors.surfaceCard), border = BorderStroke(1.dp, HabitTheme.colors.outline)) {
                    Column(Modifier.padding(padding), content = content)
                } else Column(Modifier.weight(1f), content = content)
            }
        }
    }
}

@Composable
internal fun ReadNotice(read: SnapshotRead, onRetry: () -> Unit) {
    if (read.error) {
        Text("Couldn’t read local statistics. ${if (read.snapshot != null) "Showing the last available values." else "Your habits are still stored locally."}",
            style = HabitTheme.type.body, color = HabitTheme.colors.danger)
        TextButton(onClick = onRetry) { Text("Retry") }
    } else if (read.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = HabitTheme.colors.primary)
        Text("Loading local statistics…", style = HabitTheme.type.caption, color = HabitTheme.colors.onSurfaceMuted)
    }
}
