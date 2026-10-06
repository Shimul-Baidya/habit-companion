package com.example.habit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Sizes

/**
 * C-08 — primary button. 56dp tall, full width minus gutters, pill shape, primary fill,
 * title/20sp label. The caller supplies the gutter padding.
 *
 * Appears on SCR-02, SCR-03, SCR-05 and SCR-08.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = HabitTheme.colors.primary,
            contentColor = HabitTheme.colors.onPrimary,
            disabledContainerColor = HabitTheme.colors.surfaceSunken,
            disabledContentColor = HabitTheme.colors.onSurfaceFaint,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.primaryButtonHeight),
    ) {
        Text(text = text, style = HabitTheme.type.title)
    }
}

@Preview(widthDp = 393, backgroundColor = 0xFFF3F7F5, showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    HabitTheme { PrimaryButton(text = "Continue", onClick = {}) }
}
