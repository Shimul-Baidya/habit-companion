package com.example.habit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.habit.R
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Spacing

/**
 * C-03 — streak chip. A 16dp flame plus the count at title/20sp in primary; both switch
 * to the danger token and a broken glyph at zero after settled history. New habits
 * stay neutral; Weekly/mixed histories announce completed occurrences rather than days.
 *
 * Appears on SCR-04, SCR-07 and SCR-08.
 */
@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier, neutral: Boolean = false, occurrenceUnits: Boolean = false) {
    val broken = streak == 0 && !neutral
    val tint = when { neutral -> HabitTheme.colors.onSurfaceMuted; broken -> HabitTheme.colors.danger; else -> HabitTheme.colors.primary }
    val description = pluralStringResource(if (occurrenceUnits) R.plurals.streak_occurrences else R.plurals.streak_days, streak, streak)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (broken) Icons.Filled.HeartBroken else Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(text = streak.toString(), style = HabitTheme.type.title, color = tint)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun StreakChipPreview() {
    HabitTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StreakChip(streak = 12)
            StreakChip(streak = 0)
        }
    }
}
