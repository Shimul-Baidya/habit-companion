package com.example.habit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * HabitFlow ships light and dark from the F-01 token set. Dynamic colour is deliberately
 * off: the spec resolves every colour through a named token, and a wallpaper-derived
 * palette would break that contract.
 */
@Composable
fun HabitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkHabitColors else LightHabitColors
    CompositionLocalProvider(
        LocalHabitColors provides colors,
        LocalHabitTypography provides HabitType,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = HabitMaterialTypography,
            content = content,
        )
    }
}

/** Token access from any composable: `HabitTheme.colors.primary`, `HabitTheme.type.title`. */
object HabitTheme {
    val colors: HabitColors
        @Composable @ReadOnlyComposable get() = LocalHabitColors.current

    val type: HabitTypography
        @Composable @ReadOnlyComposable get() = LocalHabitTypography.current
}

/**
 * Material 3 components (TextField, Switch, ModalBottomSheet…) read the M3 scheme, so the
 * F-01 tokens are mapped onto it. App code should still name the token, not the M3 slot.
 */
private fun HabitColors.toMaterialScheme() = with(this) {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainer = surfaceCard,
        surfaceContainerHigh = surfaceCard,
        surfaceContainerHighest = surfaceCard,
        surfaceContainerLow = surfaceCard,
        surfaceContainerLowest = surfaceCard,
        error = danger,
        errorContainer = dangerContainer,
        onError = onPrimary,
        outline = outline,
        outlineVariant = outline,
        scrim = scrim,
    )
}

private val HabitMaterialTypography = Typography(
    displayLarge = HabitType.display,
    headlineLarge = HabitType.headline,
    titleLarge = HabitType.titleLg,
    titleMedium = HabitType.title,
    bodyLarge = HabitType.body,
    bodyMedium = HabitType.caption,
    labelLarge = HabitType.title,
    labelMedium = HabitType.label,
    labelSmall = HabitType.micro,
)
