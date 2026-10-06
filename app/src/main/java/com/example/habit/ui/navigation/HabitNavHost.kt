package com.example.habit.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.habit.ui.screens.newhabit.NewHabitViewModel
import com.example.habit.ui.screens.newhabit.PlanningDraftContract
import com.example.habit.ui.screens.onboarding.OnboardingScreen
import com.example.habit.ui.screens.placeholder.NotBuiltYetScreen
import com.example.habit.ui.screens.splash.SplashScreen
import com.example.habit.ui.theme.Motion

/**
 * The current graph includes launch, Home and the shared New/Edit form with a retained
 * planning-result port. Other roots remain placeholders. Root anchoring, caller-aware
 * detail routing and destination-specific transitions are scheduled for chunk 05.
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
        ) {
            HomeScreen(
                onAddHabit = { navController.navigate(Routes.newHabit()) },
                onOpenCoach = { navController.navigate(Routes.newHabit(planning = true)) },
                onOpenHabit = { navController.navigate(Routes.COACH) },
                onSelectTab = ::switchTab,
            )
        }

        composable(Routes.NEW_HABIT, arguments = listOf(
            navArgument(NewHabitViewModel.ARG_HABIT_ID) { type = NavType.LongType; defaultValue = 0L },
            navArgument(NewHabitViewModel.ARG_PLANNING) { type = NavType.BoolType; defaultValue = false },
        )) { entry ->
            val planningResult by entry.savedStateHandle.getStateFlow<ArrayList<String>?>(PlanningDraftContract.RESULT_KEY, null).collectAsStateWithLifecycle()
            NewHabitScreen(
                planningResult = planningResult,
                onPlanningResultConsumed = { entry.savedStateHandle[PlanningDraftContract.RESULT_KEY] = null },
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
