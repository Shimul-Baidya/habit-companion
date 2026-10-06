package com.example.habit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * F-02 — the nine layout rules. Every measurement on every screen comes from here.
 * Design viewport is 393 x 832 dp (Pixel-class); all values scale from that.
 */
object Spacing {
    /** The spacing scale. No other values appear in the app. */
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** 20dp left and right on every screen. */
    val gutter = 20.dp
}

object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** Pill — 50% of the shorter side. */
    val pill = RoundedCornerShape(percent = 50)
}

object Elevation {
    /** Cards are flat; the 1dp outline does the separating. */
    val card = 0.dp
    val cardOutline = 1.dp
    val bottomSheet = 8.dp
    val dialog = 12.dp
    val fab = 2.dp
}

object Sizes {
    /** Minimum touch target on every tappable element. */
    val touchTarget = 48.dp
    val iconInline = 20.dp
    val iconNav = 24.dp
    val iconEmptyState = 44.dp

    val appBarHeight = 56.dp
    val bottomNavHeight = 72.dp
    val habitRowHeight = 72.dp

    /** C-08 primary button. */
    val primaryButtonHeight = 56.dp
}

/** Motion durations in ms, all on standard easing. */
object Motion {
    const val RING_FILL = 300
    const val SHEET = 250
    const val NAV_FADE = 150
    const val CROSSFADE = 200
    const val BRAND_MARK = 220
    const val ONBOARDING_CARD = 400
    const val BAR_CHART = 400
}
