package com.example.habit.ui.screens.splash

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habit.ui.containerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where SCR-01 hands off to once Room has opened. */
sealed interface SplashRoute {
    /** SCR-02 — first run. */
    data object Onboarding : SplashRoute

    /**
     * SCR-03 or SCR-04. [dbError] is the failure path: Room threw or took too long, so
     * Home opens empty and shows a retry instead of the splash hanging.
     */
    data class Home(val dbError: Boolean) : SplashRoute
}

/**
 * SCR-01 — holds the first frame while Room opens and the habit list is read.
 *
 * The screen is on for at least [MIN_ON_SCREEN_MS] so the brand mark is not a flash, and
 * at most [MAX_ON_SCREEN_MS] so a slow or broken database never blocks the launch.
 */
class SplashViewModel(
    private val onboarding: Flow<Boolean>,
    private val openDatabase: suspend () -> Unit,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _route = MutableStateFlow<SplashRoute?>(null)

    /** Null until the decision is made; the screen navigates on the first non-null value. */
    val route: StateFlow<SplashRoute?> = _route.asStateFlow()

    private val _showLoadBar = MutableStateFlow(false)

    /** The load bar is hidden entirely when the app is ready in under 400ms. */
    val showLoadBar: StateFlow<Boolean> = _showLoadBar.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = elapsedRealtime()

            val loadBarJob = launch {
                delay(LOAD_BAR_DELAY_MS)
                _showLoadBar.value = true
            }

            val read = withTimeoutOrNull(MAX_ON_SCREEN_MS) {
                try {
                    val complete = onboarding.first()
                    // Opening the database here is the point: the cost is paid on the
                    // splash rather than on the first frame of Home.
                    openDatabase()
                    Result.success(complete)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { Result.failure(error) }
            }
            loadBarJob.cancel()

            // A timeout (null) and a thrown read are the same failure from here: we do not
            // know whether onboarding ran, and Home is the safe place to land.
            val onboardingComplete = read?.getOrNull()
            val failed = read == null || read.isFailure

            val elapsed = elapsedRealtime() - startedAt
            if (elapsed < MIN_ON_SCREEN_MS) delay(MIN_ON_SCREEN_MS - elapsed)

            _route.value = when {
                failed -> SplashRoute.Home(dbError = true)
                onboardingComplete == true -> SplashRoute.Home(dbError = false)
                else -> SplashRoute.Onboarding
            }
        }
    }

    companion object {
        const val MIN_ON_SCREEN_MS = 600L
        const val MAX_ON_SCREEN_MS = 3000L
        const val LOAD_BAR_DELAY_MS = 400L

        val Factory = containerFactory { SplashViewModel(it.settings.onboardingComplete, { it.habitDao.count() }) }
    }
}
