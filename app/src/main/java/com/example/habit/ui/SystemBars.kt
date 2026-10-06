package com.example.habit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Sets system-bar icon colour for as long as a screen is on top, and puts it back on the
 * way out. SCR-01 asks for light icons whichever theme is active, because the splash is
 * always dark.
 *
 * @param lightIcons true for white icons (a dark bar background), false for dark icons.
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, lightIcons) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, view)
        // The platform flag is inverted: "light appearance bars" means dark icons.
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            controller.isAppearanceLightStatusBars = previous
            controller.isAppearanceLightNavigationBars = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
