package com.example.habit.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habit.ui.management.*
import com.example.habit.ui.controls.LocalDataGeneration
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
import com.example.habit.ui.screens.progress.ProgressScreen
import com.example.habit.ui.screens.profile.ProfileScreen
import com.example.habit.ui.screens.splash.SplashScreen
import com.example.habit.ui.theme.Motion
import com.example.habit.ui.screens.coach.*
import com.example.habit.ui.screens.newhabit.FormCoachEntry

@Composable
fun HabitNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val management: HabitManagementViewModel = viewModel(key = "management-${LocalDataGeneration.current}", factory = HabitManagementViewModel.Factory)
    Box {
    HabitNavigationGraph(navController, modifier,
        splash = { onboarding, home -> SplashScreen(onboarding, home) },
        onboarding = { finished -> OnboardingScreen(finished) },
        home = { add, coach, detail, tab -> HomeScreen(add, coach, detail, tab, onManageHabit = management::open) },
        form = { entry, back ->
            val planningResult by entry.savedStateHandle.getStateFlow<ArrayList<String>?>(PlanningDraftContract.RESULT_KEY, null).collectAsStateWithLifecycle()
            val formModel: NewHabitViewModel = viewModel(viewModelStoreOwner = entry, factory = NewHabitViewModel.Factory)
            NewHabitScreen(viewModel = formModel, planningResult = planningResult,
                onOpenCoach = { context -> navController.navigate(when (context) {
                    is FormCoachEntry.Planning -> Routes.COACH_PLANNING
                    is FormCoachEntry.Existing -> Routes.coach(context.habitId)
                }) { launchSingleTop = true } },
                onPlanningResultConsumed = { entry.savedStateHandle[PlanningDraftContract.RESULT_KEY] = null },
                onBack = back, onCreated = back)
        },
        root = { tab, select -> when (tab) {
            HomeTab.PROGRESS -> ProgressScreen({ navController.navigate(Routes.detail(it)) { launchSingleTop = true } }, select)
            HomeTab.PROFILE -> ProfileScreen(select)
            HomeTab.COACH -> CoachRoot(select)
            HomeTab.HOME -> Unit
        } },
        detail = { id, back -> HabitDetailScreen(back, management::open,
            onOpenCoach = { navController.navigate(Routes.coach(id)) { launchSingleTop = true } }) },
        coach = { entry, back ->
            val id = entry.arguments?.getLong(Routes.ARG_HABIT_ID)?.takeIf { it > 0 }
            val owner = if (entry.destination.route == Routes.COACH_PLANNING) remember(entry) { navController.getBackStackEntry(Routes.NEW_HABIT) } else null
            val formModel: NewHabitViewModel? = owner?.let { viewModel(viewModelStoreOwner = it, factory = NewHabitViewModel.Factory) }
            val coachModel: CoachViewModel = viewModel(viewModelStoreOwner = entry, factory = CoachViewModel.factory(id, formModel))
            CoachScreen(coachModel, back)
        },
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
    coach: @Composable (NavBackStackEntry, () -> Unit) -> Unit = { _, _ -> },
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
                targetState.destination.route in setOf(Routes.COACH_HABIT, Routes.COACH_PLANNING) -> slideInVertically(tween(300)) { it } + fadeIn(tween(300))
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
                Routes.COACH_HABIT, Routes.COACH_PLANNING -> slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
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
            composable(Routes.COACH_HABIT, arguments = listOf(navArgument(Routes.ARG_HABIT_ID) { type = NavType.LongType })) { entry ->
                coach(entry) { if (navController.currentBackStackEntry == entry) navController.popBackStack() }
            }
            composable(Routes.COACH_PLANNING) { entry ->
                coach(entry) { if (navController.currentBackStackEntry == entry) navController.popBackStack() }
            }
            listOf(Routes.PROGRESS to HomeTab.PROGRESS, Routes.COACH to HomeTab.COACH, Routes.PROFILE to HomeTab.PROFILE).forEach { (route, tab) ->
                composable(route) {
                    BackHandler { switchTab(HomeTab.HOME) }
                    CompositionLocalProvider(LocalHabitAnimatedScope provides this) { root(tab, ::switchTab) }
                }
            }
        }
    }
    }
    }
}
