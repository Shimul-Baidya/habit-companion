package com.example.habit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * F-01 — the colour tokens. This file is the only place in the app where a hex value
 * appears; every screen names a token and resolves it here.
 *
 * Dark values are tuned for contrast, not inverted: [HabitColors.primary] lifts to 2DD4BF
 * so a completion ring stays legible on a dark surface, and danger lifts for the same reason.
 */
@Immutable
data class HabitColors(
    /** Screen background. */
    val surface: Color,
    /** Habit rows, stat cards, sheets. */
    val surfaceCard: Color,
    /** Chart tracks, progress rails. */
    val surfaceSunken: Color,
    /** Habit names, headings, values. */
    val onSurface: Color,
    /** Sub-labels, units, helper text. */
    val onSurfaceMuted: Color,
    /** Placeholders, inactive nav. */
    val onSurfaceFaint: Color,
    /** Filled buttons, done rings, active nav. */
    val primary: Color,
    /** Text and icons on [primary]. */
    val onPrimary: Color,
    /** Apply pills, coach callouts, chips. */
    val primaryContainer: Color,
    /** Text on [primaryContainer]. */
    val onPrimaryContainer: Color,
    /** Broken streak, destructive actions. */
    val danger: Color,
    /** Needs-attention banner background. */
    val dangerContainer: Color,
    /** Card hairlines, dividers. */
    val outline: Color,
    /** Behind bottom sheets, at 55% alpha. */
    val scrim: Color,
    /** True when these are the dark tokens; drives status-bar icon colour. */
    val isDark: Boolean,
)

val LightHabitColors = HabitColors(
    surface = Color(0xFFF3F7F5),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE7EEEB),
    onSurface = Color(0xFF0D1B1E),
    onSurfaceMuted = Color(0xFF5C7075),
    onSurfaceFaint = Color(0xFF8A9B9E),
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7EBE5),
    onPrimaryContainer = Color(0xFF0B5A54),
    danger = Color(0xFFC2410C),
    dangerContainer = Color(0xFFFBEBE8),
    outline = Color(0xFFE5ECE9),
    scrim = Color(0xFF0D1B1E),
    isDark = false,
)

val DarkHabitColors = HabitColors(
    surface = Color(0xFF0F1A1C),
    surfaceCard = Color(0xFF162427),
    surfaceSunken = Color(0xFF0A1416),
    onSurface = Color(0xFFECF3F1),
    onSurfaceMuted = Color(0xFF9DB0B3),
    onSurfaceFaint = Color(0xFF6E8285),
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF04201E),
    primaryContainer = Color(0xFF123A38),
    onPrimaryContainer = Color(0xFFA7E8DF),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF33201E),
    outline = Color(0xFF24363A),
    scrim = Color(0xFF000000),
    isDark = true,
)

/** Alpha the scrim is drawn at behind bottom sheets and dialogs (F-01). */
const val ScrimAlpha = 0.55f

val LocalHabitColors = staticCompositionLocalOf { LightHabitColors }
