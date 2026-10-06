package com.example.habit.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.R
import com.example.habit.ui.SystemBarIcons
import com.example.habit.ui.theme.HabitType
import com.example.habit.ui.theme.LightHabitColors
import com.example.habit.ui.theme.Motion
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Spacing

/**
 * SCR-01 — Splash. Holds the first frame while Room opens and the habit list is read.
 *
 * The splash is always dark and never switches theme, so it resolves its colours from the
 * light token set directly: background is `on.surface`, the wordmark is `on.primary`.
 */
@Composable
fun SplashScreen(
    onRouteToOnboarding: () -> Unit,
    onRouteToHome: (dbError: Boolean) -> Unit,
    viewModel: SplashViewModel = viewModel(factory = SplashViewModel.Factory),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val showLoadBar by viewModel.showLoadBar.collectAsStateWithLifecycle()

    LaunchedEffect(route) {
        when (val destination = route) {
            null -> Unit
            SplashRoute.Onboarding -> onRouteToOnboarding()
            is SplashRoute.Home -> onRouteToHome(destination.dbError)
        }
    }

    SplashContent(showLoadBar = showLoadBar)
}

@Composable
private fun SplashContent(showLoadBar: Boolean) {
    // 1 — light status-bar icons, on a transparent bar, for as long as this screen is up.
    SystemBarIcons(lightIcons = true)

    // 6 — the splash background is on.surface in both themes; it never switches.
    Surface(color = LightHabitColors.onSurface, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.gutter),
            ) {
                BrandMark()

                // 3 — wordmark.
                Text(
                    text = stringResource(R.string.app_name),
                    style = HabitType.display,
                    color = LightHabitColors.onPrimary,
                    modifier = Modifier.padding(top = Spacing.xxl),
                )

                // 4 — tagline.
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = HabitType.caption,
                    color = LightHabitColors.primaryContainer.copy(alpha = 0.70f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }

            // 5 — indeterminate load bar, docked so that appearing after 400ms does not
            // shift the brand mark. Hidden entirely when the app is ready sooner.
            if (showLoadBar) {
                LinearProgressIndicator(
                    color = LightHabitColors.primary,
                    trackColor = LightHabitColors.primary.copy(alpha = 0.20f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .width(80.dp)
                        .height(4.dp)
                        .clip(Radius.pill),
                )
            }
        }
    }
}

/** 2 — brand mark: a 192dp ring with a check inside, scaling 0.92 to 1.0 over 220ms. */
@Composable
private fun BrandMark() {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.92f,
        animationSpec = tween(durationMillis = Motion.BRAND_MARK),
        label = "brandMarkScale",
    )

    val description = stringResource(R.string.splash_brand_mark)
    Canvas(
        modifier = Modifier
            .size(192.dp)
            .scale(scale)
            .semantics { contentDescription = description },
    ) {
        drawBrandRing(LightHabitColors.primary)
        drawCheckGlyph(LightHabitColors.onPrimary)
    }
}

private fun DrawScope.drawBrandRing(color: Color) {
    val stroke = 5.dp.toPx()
    drawCircle(
        color = color,
        radius = (size.minDimension - stroke) / 2f,
        style = Stroke(width = stroke),
    )
}

/** A 24dp check, centred in the ring. */
private fun DrawScope.drawCheckGlyph(color: Color) {
    val glyph = 24.dp.toPx()
    val left = center.x - glyph / 2f
    val top = center.y - glyph / 2f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    val start = Offset(left + glyph * 0.16f, top + glyph * 0.54f)
    val corner = Offset(left + glyph * 0.40f, top + glyph * 0.78f)
    val end = Offset(left + glyph * 0.84f, top + glyph * 0.24f)

    drawLine(color, start, corner, strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(color, corner, end, strokeWidth = stroke.width, cap = stroke.cap)
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun SplashPreview() {
    SplashContent(showLoadBar = true)
}
