package com.example.habit.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.ui.management.*
import com.example.habit.ui.screens.detail.HabitDetailScreen
import com.example.habit.ui.components.LocalHabitSharedScope
import com.example.habit.ui.components.LocalHabitAnimatedScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.habit.ui.components.HomeTab
import com.example.habit.ui.screens.home.HomeScreen
import com.example.habit.ui.screens.newhabit.NewHabitScreen
import com.example.habit.ui.screens.newhabit.NewHabitViewModel
import com.example.habit.ui.screens.newhabit.PlanningDraftContract
import com.example.habit.ui.screens.onboarding.OnboardingScreen
import com.example.habit.ui.screens.placeholder.NotBuiltYetScreen
import com.example.habit.ui.screens.splash.SplashScreen
import com.example.habit.ui.theme.Motion

@Composable
fun HabitNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val management: HabitManagementViewModel = viewModel(factory = HabitManagementViewModel.Factory)
    Box {
    HabitNavigationGraph(navController, modifier,
        splash = { onboarding, home -> SplashScreen(onboarding, home) },
        onboarding = { finished -> OnboardingScreen(finished) },
        home = { add, coach, detail, tab -> HomeScreen(add, coach, detail, tab, onManageHabit = management::open) },
        form = { entry, back ->
            val planningResult by entry.savedStateHandle.getStateFlow<ArrayList<String>?>(PlanningDraftContract.RESULT_KEY, null).collectAsStateWithLifecycle()
            NewHabitScreen(planningResult = planningResult,
                onPlanningResultConsumed = { entry.savedStateHandle[PlanningDraftContract.RESULT_KEY] = null },
                onBack = back, onCreated = back)
        },
        root = { tab, select -> if (tab == HomeTab.COACH) CoachRoot(select) else NotBuiltYetScreen(tab, select) },
        detail = { _, back -> HabitDetailScreen(back, management::open) },
    )
    HabitManagementHost(management, onEdit = { navController.navigate(Routes.editHabit(it)) { launchSingleTop = true } },
        onRemoved = { id -> if (navController.currentBackStackEntry?.destination?.route == Routes.HABIT_DETAIL &&
            navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_HABIT_ID) == id) navController.popBackStack() })
    }
}

/** Screen ports let isolated device tests exercise the production graph and Back contracts. */
@Composable
internal fun HabitNavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    splash: @Composable (() -> Unit, (Boolean) -> Unit) -> Unit,
    onboarding: @Composable (() -> Unit) -> Unit,
    home: @Composable (() -> Unit, () -> Unit, (Long) -> Unit, (HomeTab) -> Unit) -> Unit,
    form: @Composable (NavBackStackEntry, () -> Unit) -> Unit,
    root: @Composable (HomeTab, (HomeTab) -> Unit) -> Unit,
    detail: @Composable (Long, () -> Unit) -> Unit,
) {
    fun switchTab(tab: HomeTab) {
        val route = when (tab) {
            HomeTab.HOME -> Routes.home()
            HomeTab.PROGRESS -> Routes.PROGRESS
            HomeTab.COACH -> Routes.COACH
            HomeTab.PROFILE -> Routes.PROFILE
        }
        navController.navigate(route) {
            // Home is retained after launch destinations are removed, including restored roots.
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun open(route: String) { navController.navigate(route) { launchSingleTop = true } }
    fun enterMain(dbError: Boolean, launch: String) {
        navController.navigate(Routes.home(dbError)) { popUpTo(launch) { inclusive = true }; launchSingleTop = true }
    }
    SharedTransitionLayout {
    CompositionLocalProvider(LocalHabitSharedScope provides this) {
    NavHost(navController, startDestination = Routes.SPLASH, modifier = modifier,
        enterTransition = {
            when {
                targetState.destination.route == Routes.NEW_HABIT -> slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))
                targetState.destination.route == Routes.HABIT_DETAIL -> fadeIn(tween(300))
                initialState.destination.route in setOf(Routes.SPLASH, Routes.ONBOARDING) -> fadeIn(tween(200))
                else -> fadeIn(tween(Motion.NAV_FADE))
            }
        }, exitTransition = {
            fadeOut(tween(if (initialState.destination.route in setOf(Routes.SPLASH, Routes.ONBOARDING)) 200 else Motion.NAV_FADE))
        }, popEnterTransition = { fadeIn(tween(if (initialState.destination.route == Routes.HABIT_DETAIL) 300 else 250)) },
        popExitTransition = {
            when (initialState.destination.route) {
                Routes.NEW_HABIT -> slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250))
                Routes.HABIT_DETAIL -> fadeOut(tween(300))
                else -> fadeOut(tween(Motion.NAV_FADE))
            }
        }) {
        composable(Routes.SPLASH) { splash(
            { navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true }; launchSingleTop = true } },
            { enterMain(it, Routes.SPLASH) }) }
        composable(Routes.ONBOARDING) { onboarding { enterMain(false, Routes.ONBOARDING) } }
        navigation(startDestination = Routes.HOME, route = Routes.MAIN) {
            composable(Routes.HOME, arguments = listOf(navArgument(Routes.ARG_DB_ERROR) { type = NavType.BoolType; defaultValue = false })) {
                CompositionLocalProvider(LocalHabitAnimatedScope provides this) {
                home({ open(Routes.newHabit()) }, { open(Routes.newHabit(planning = true)) }, { open(Routes.detail(it)) }, ::switchTab)
                }
            }
            composable(Routes.NEW_HABIT, arguments = listOf(
                navArgument(NewHabitViewModel.ARG_HABIT_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(NewHabitViewModel.ARG_PLANNING) { type = NavType.BoolType; defaultValue = false },
            )) { entry -> form(entry) { navController.popBackStack() } }
            composable(Routes.HABIT_DETAIL, arguments = listOf(navArgument(Routes.ARG_HABIT_ID) { type = NavType.LongType })) { entry ->
                CompositionLocalProvider(LocalHabitAnimatedScope provides this) {
                detail(requireNotNull(entry.arguments).getLong(Routes.ARG_HABIT_ID)) {
                    if (navController.currentBackStackEntry == entry) navController.popBackStack()
                }
                }
            }
            listOf(Routes.PROGRESS to HomeTab.PROGRESS, Routes.COACH to HomeTab.COACH, Routes.PROFILE to HomeTab.PROFILE).forEach { (route, tab) ->
                composable(route) {
                    BackHandler { switchTab(HomeTab.HOME) }
                    root(tab, ::switchTab)
                }
            }
        }
    }
    }
    }
}
