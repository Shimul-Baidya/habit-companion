package com.example.habit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * F-02 — the nine type roles. No size outside this set appears on any screen.
 *
 * The face is Roboto, the Compose default; no font is bundled with the APK.
 */
@Immutable
data class HabitTypography(
    /** 32sp Bold — greeting name, splash wordmark. */
    val display: TextStyle,
    /** 26sp Bold — app bar titles, screen titles. */
    val headline: TextStyle,
    /** 22sp Bold — section headings, empty-state titles. */
    val titleLg: TextStyle,
    /** 20sp Bold — habit names, card titles. */
    val title: TextStyle,
    /** 32sp Bold — streak and percentage figures. */
    val stat: TextStyle,
    /** 16sp Regular — coach prose, descriptions. */
    val body: TextStyle,
    /** 14sp Regular — sub-labels, goal text, units. */
    val caption: TextStyle,
    /** 12sp Bold, +1.5sp tracking — ALL-CAPS section labels. */
    val label: TextStyle,
    /** 11sp Bold — strategy tags, nav labels. */
    val micro: TextStyle,
)

private val Face = FontFamily.Default

val HabitType = HabitTypography(
    display = TextStyle(fontFamily = Face, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headline = TextStyle(fontFamily = Face, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLg = TextStyle(fontFamily = Face, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    title = TextStyle(fontFamily = Face, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    stat = TextStyle(fontFamily = Face, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    body = TextStyle(fontFamily = Face, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    caption = TextStyle(fontFamily = Face, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp),
    label = TextStyle(
        fontFamily = Face,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
    ),
    micro = TextStyle(
        fontFamily = Face,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
)

val LocalHabitTypography = staticCompositionLocalOf { HabitType }
