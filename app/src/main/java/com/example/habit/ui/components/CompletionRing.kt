package com.example.habit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Motion
import com.example.habit.ui.theme.Sizes

/**
 * C-02 — completion ring. 40dp outer, 4dp stroke, primary sweep on a surface.sunken
 * track, filling over 300ms. SCR-04 uses it twice: 40dp per habit row, and a 64dp /
 * 6dp variant as the day's summary.
 *
 * The touch target is 48dp regardless of the drawn size (F-02).
 */
@Composable
fun CompletionRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    stroke: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    showCheck: Boolean = true,
    contentDescription: String? = null,
    enabled: Boolean = true,
    progressDescription: String? = null,
    checked: Boolean? = null,
) {
    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = Motion.RING_FILL),
        label = "ringSweep",
    )
    val filled = sweep >= 1f
    val colors = HabitTheme.colors
    val haptics = LocalHapticFeedback.current

    val ring = @Composable {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            RingCanvas(
                sweep = sweep,
                stroke = stroke,
                track = colors.surfaceSunken,
                fill = colors.primary,
                modifier = Modifier.size(size),
            )
            if (showCheck && filled) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(size / 2),
                )
            }
        }
    }

    val described = modifier.semantics {
        contentDescription?.let { this.contentDescription = it }
        progressDescription?.let { stateDescription = it }
        checked?.let {
            role = Role.Checkbox
            toggleableState = if (it) ToggleableState.On else ToggleableState.Off
        }
    }
    if (onClick == null) {
        Box(modifier = described) { ring() }
    } else {
        IconButton(
            enabled = enabled,
            onClick = {
                // Completing is the moment worth confirming in the hand; undoing is not.
                if (!filled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
            modifier = described
                .size(Sizes.touchTarget)
                .clip(CircleShape),
        ) {
            ring()
        }
    }
}

@Composable
private fun RingCanvas(
    sweep: Float,
    stroke: Dp,
    track: Color,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = stroke.toPx()
        val radius = (this.size.minDimension - width) / 2f
        drawCircle(color = track, radius = radius, style = Stroke(width = width))
        if (sweep > 0f) {
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    (this.size.width - radius * 2) / 2f,
                    (this.size.height - radius * 2) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}

@Preview(widthDp = 260, heightDp = 100, showBackground = true, backgroundColor = 0xFFF3F7F5)
@Composable
private fun CompletionRingPreview() {
    HabitTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.size(260.dp, 100.dp),
        ) {
            CompletionRing(progress = 0f, onClick = {})
            CompletionRing(progress = 0.6f, onClick = {})
            CompletionRing(progress = 1f, onClick = {})
            CompletionRing(progress = 0.65f, size = 64.dp, stroke = 6.dp, showCheck = false)
        }
    }
}
