package com.example.habit.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.habit.ui.theme.HabitTheme

/**
 * C-10 — section label. label/12sp caps with +1.5sp tracking in on.surface.muted. The
 * caller owns the 12dp gap to the block above.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HabitTheme.colors.onSurfaceMuted,
) {
    Text(
        text = text.uppercase(),
        style = HabitTheme.type.label,
        color = color,
        modifier = modifier,
    )
}
