package com.example.habit.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.R
import com.example.habit.ui.components.PrimaryButton
import com.example.habit.ui.theme.HabitTheme
import com.example.habit.ui.theme.Motion
import com.example.habit.ui.theme.Radius
import com.example.habit.ui.theme.Sizes
import com.example.habit.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * SCR-02 — Onboarding. Three panes, one exit: `onboardingComplete` is written and the app
 * lands on the empty Home (SCR-03). Back never returns to the splash.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPanes.size })
    val scope = rememberCoroutineScope()

    OnboardingContent(
        pagerState = pagerState,
        onSkip = { viewModel.finishOnboarding(onFinished) },
        onContinue = {
            if (pagerState.currentPage == OnboardingPanes.lastIndex) {
                viewModel.finishOnboarding(onFinished)
            } else {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        },
    )
}

@Composable
private fun OnboardingContent(
    pagerState: PagerState,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 9 — panes 2 and 3 step back; pane 1 is not intercepted, so back exits the app.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(color = HabitTheme.colors.surface, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // 1 — Skip.
            Row(
                horizontalArrangement = Arrangement.End,
                // 8dp here plus the TextButton's own 12dp content padding puts the
                // label on the 20dp screen gutter.
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.height(Sizes.touchTarget),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = HabitTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = HabitTheme.colors.onSurfaceMuted,
                    )
                }
            }

            // 8 — the pager. Swipe is enabled in both directions.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                OnboardingPaneContent(page = page)
            }

            // 5 — pager dots.
            PagerDots(
                pageCount = OnboardingPanes.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(Spacing.xxl))

            // 6 — Continue (C-08).
            PrimaryButton(
                text = stringResource(
                    if (pagerState.currentPage == OnboardingPanes.lastIndex) {
                        R.string.onboarding_get_started
                    } else {
                        R.string.onboarding_continue
                    },
                ),
                onClick = onContinue,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )

            // 7 — Restore. Phase 2; disabled at v1.
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(Sizes.touchTarget),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_restore),
                    style = HabitTheme.type.caption,
                    // Faint, not muted: this reads as unavailable rather than tappable.
                    color = HabitTheme.colors.onSurfaceFaint,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun OnboardingPaneContent(page: Int) {
    val pane = OnboardingPanes[page]
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
    ) {
        // 2 — illustration.
        StackedCardIllustration(
            page = page,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(Spacing.xxxl))

        // 3 — headline.
        Text(
            text = stringResource(pane.titleRes),
            style = HabitTheme.type.headline,
            color = HabitTheme.colors.onSurface,
        )

        Spacer(Modifier.height(Spacing.md))

        // 4 — body. The 24sp line height is a per-element override of the 22sp body role.
        Text(
            text = stringResource(pane.bodyRes),
            style = HabitTheme.type.body.copy(lineHeight = 24.sp),
            color = HabitTheme.colors.onSurfaceMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 2 — three cards stacked 60dp apart at alpha 0.35 / 0.6 / 1.0. The top card re-enters
 * over 400ms whenever the pane changes.
 */
@Composable
private fun StackedCardIllustration(page: Int, modifier: Modifier = Modifier) {
    val cardHeight = 88.dp
    val step = 60.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight + step * 2),
    ) {
        StackCard(alpha = 0.35f, offsetY = 0.dp, insetX = Spacing.xxxl, height = cardHeight)
        StackCard(alpha = 0.60f, offsetY = step, insetX = Spacing.lg, height = cardHeight)

        key(page) {
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            val progress by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(durationMillis = Motion.ONBOARDING_CARD),
                label = "topCardEntry",
            )
            StackCard(
                alpha = progress,
                offsetY = step * 2 + ((1f - progress) * 16).dp,
                insetX = 0.dp,
                height = cardHeight,
                filled = true,
            )
        }
    }
}

@Composable
private fun StackCard(
    alpha: Float,
    offsetY: Dp,
    insetX: Dp,
    height: Dp,
    filled: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = insetX)
            .offset(y = offsetY)
            .height(height)
            .alpha(alpha)
            .clip(Radius.card)
            .background(HabitTheme.colors.surfaceCard)
            .border(1.dp, HabitTheme.colors.outline, Radius.card)
            .padding(horizontal = Spacing.lg),
    ) {
        // A completion ring, filled on the front card — the tap the app is about.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(HabitTheme.colors.primary)
                    } else {
                        Modifier.border(4.dp, HabitTheme.colors.surfaceSunken, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                Modifier
                    .height(12.dp)
                    .width(if (filled) 132.dp else 108.dp)
                    .clip(Radius.pill)
                    .background(HabitTheme.colors.onSurface.copy(alpha = 0.18f)),
            )
            Box(
                Modifier
                    .height(10.dp)
                    .width(72.dp)
                    .clip(Radius.pill)
                    .background(HabitTheme.colors.onSurface.copy(alpha = 0.10f)),
            )
        }
    }
}

/** 5 — 8dp dots, 10dp apart; the active one is a 28 x 8dp primary pill. */
@Composable
private fun PagerDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_pane_indicator, currentPage + 1, pageCount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 28.dp else 8.dp,
                animationSpec = tween(durationMillis = Motion.NAV_FADE),
                label = "dotWidth",
            )
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(Radius.pill)
                    .background(
                        if (active) HabitTheme.colors.primary else HabitTheme.colors.onSurfaceFaint,
                    ),
            )
        }
    }
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun OnboardingPreview() {
    HabitTheme {
        OnboardingContent(
            pagerState = rememberPagerState(pageCount = { OnboardingPanes.size }),
            onSkip = {},
            onContinue = {},
        )
    }
}

@Preview(widthDp = 393, heightDp = 832)
@Composable
private fun OnboardingDarkPreview() {
    HabitTheme(darkTheme = true) {
        OnboardingContent(
            pagerState = rememberPagerState(pageCount = { OnboardingPanes.size }),
            onSkip = {},
            onContinue = {},
        )
    }
}
