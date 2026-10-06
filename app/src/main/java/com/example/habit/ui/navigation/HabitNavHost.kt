package com.example.habit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.home.HomeScreen
import com.example.habit.ui.screens.newhabit.NewHabitScreen
import com.example.habit.ui.screens.onboarding.OnboardingScreen
import com.example.habit.ui.screens.placeholder.NotBuiltYetScreen
import com.example.habit.ui.screens.splash.SplashScreen
import com.example.habit.ui.theme.Motion

/**
 * The graph so far: SCR-01 → SCR-02 → Home (SCR-03 / SCR-04), plus the SCR-05 stub and
 * the three other nav roots. Screen transitions are a fade, never a slide (F-02: nav
 * fade 150ms), which also satisfies SCR-04 element 13.
 */
@Composable
fun HabitNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val fade = tween<Float>(durationMillis = Motion.NAV_FADE)

    /** Tab switches replace the root rather than stacking it. */
    fun switchTab(tab: HomeTab) {
        val route = when (tab) {
            HomeTab.HOME -> Routes.home()
            HomeTab.PROGRESS -> Routes.PROGRESS
            HomeTab.COACH -> Routes.COACH
            HomeTab.PROFILE -> Routes.PROFILE
        }
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
        enterTransition = { fadeIn(fade) },
        exitTransition = { fadeOut(fade) },
        popEnterTransition = { fadeIn(fade) },
        popExitTransition = { fadeOut(fade) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onRouteToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onRouteToHome = { dbError ->
                    navController.navigate(Routes.home(dbError)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.home()) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.HOME,
            arguments = listOf(
                navArgument(Routes.ARG_DB_ERROR) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            HomeScreen(
                dbError = entry.arguments?.getBoolean(Routes.ARG_DB_ERROR) == true,
                onRetry = {
                    // Retry means re-running the read the splash failed on.
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onAddHabit = { navController.navigate(Routes.NEW_HABIT) },
                onOpenCoach = { navController.navigate(Routes.COACH) },
                onOpenHabit = { navController.navigate(Routes.COACH) },
                onSelectTab = ::switchTab,
            )
        }

        composable(Routes.NEW_HABIT) {
            NewHabitScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable(Routes.PROGRESS) {
            NotBuiltYetScreen(tab = HomeTab.PROGRESS, onSelectTab = ::switchTab)
        }
        composable(Routes.COACH) {
            NotBuiltYetScreen(tab = HomeTab.COACH, onSelectTab = ::switchTab)
        }
        composable(Routes.PROFILE) {
            NotBuiltYetScreen(tab = HomeTab.PROFILE, onSelectTab = ::switchTab)
        }
    }
}
