package com.example.habit.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

val LocalHabitSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalHabitAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Name/container transform is shared by Home and detail; Progress can use the same key. */
@Composable fun Modifier.habitNameTransition(id: Long): Modifier {
    val shared = LocalHabitSharedScope.current ?: return this
    val animated = LocalHabitAnimatedScope.current ?: return this
    return with(shared) { this@habitNameTransition.sharedBounds(rememberSharedContentState("habit-name-$id"), animated,
        boundsTransform = { _, _ -> tween(300) }) }
}
