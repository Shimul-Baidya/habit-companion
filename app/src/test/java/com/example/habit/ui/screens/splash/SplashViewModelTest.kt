package com.example.habit.ui.screens.splash

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    @Test fun firstLaunchRoutesToSetupAfterMinimumDuration() = runTest(dispatcher) {
        val vm = SplashViewModel(flowOf(false), {}, { testScheduler.currentTime }); store.put("splash", vm)
        advanceTimeBy(599); runCurrent(); assertNull(vm.route.value)
        advanceTimeBy(1); runCurrent(); assertEquals(SplashRoute.Onboarding, vm.route.value)
        assertFalse(vm.showLoadBar.value)
    }
    @Test fun subsequentLaunchBypassesSetup() = runTest(dispatcher) {
        val vm = SplashViewModel(flowOf(true), {}, { testScheduler.currentTime }); store.put("splash", vm)
        advanceUntilIdle(); assertEquals(SplashRoute.Home(false), vm.route.value)
    }
    @Test fun databaseFailureAndPreferencesFailureReachRetryHome() = runTest(dispatcher) {
        val db = SplashViewModel(flowOf(true), { throw java.io.IOException("test") }, { testScheduler.currentTime }); store.put("db", db)
        val prefs = SplashViewModel(flow { throw java.io.IOException("test") }, {}, { testScheduler.currentTime }); store.put("prefs", prefs)
        advanceUntilIdle(); assertEquals(SplashRoute.Home(true), db.route.value); assertEquals(SplashRoute.Home(true), prefs.route.value)
    }
    @Test fun slowDatabaseShowsLoadBarAndTimesOutWithoutBlockingLaunch() = runTest(dispatcher) {
        val vm = SplashViewModel(flowOf(true), { awaitCancellation() }, { testScheduler.currentTime }); store.put("splash", vm)
        advanceTimeBy(400); runCurrent(); assertTrue(vm.showLoadBar.value); assertNull(vm.route.value)
        advanceTimeBy(2600); runCurrent(); assertEquals(SplashRoute.Home(true), vm.route.value)
    }
}
