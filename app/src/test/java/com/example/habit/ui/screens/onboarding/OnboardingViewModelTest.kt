package com.example.habit.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    @Test fun repeatedFinishWaitsForPersistentSuccessAndRestoresSuccess() = runTest(dispatcher) {
        val hold = CompletableDeferred<Unit>(); val saved = SavedStateHandle(); var writes = 0
        val vm = OnboardingViewModel({ writes++; hold.await() }, saved); store.put("setup", vm)
        vm.finishOnboarding(); vm.finishOnboarding(); runCurrent()
        assertEquals(1, writes); assertTrue(vm.state.value.saving); assertFalse(vm.state.value.finished)
        hold.complete(Unit); runCurrent(); assertTrue(vm.state.value.finished)
        val restored = OnboardingViewModel({ writes++ }, saved); store.put("restored", restored)
        restored.finishOnboarding(); runCurrent(); assertEquals(1, writes); assertTrue(restored.state.value.finished)
    }
    @Test fun writeFailureDoesNotNavigateAndCanRetry() = runTest(dispatcher) {
        var failed = true
        val vm = OnboardingViewModel({ if (failed) throw java.io.IOException("test") }, SavedStateHandle()); store.put("setup", vm)
        vm.finishOnboarding(); runCurrent(); assertTrue(vm.state.value.failed); assertFalse(vm.state.value.finished)
        failed = false; vm.finishOnboarding(); runCurrent(); assertTrue(vm.state.value.finished); assertFalse(vm.state.value.failed)
    }
}
