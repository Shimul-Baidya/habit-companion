package com.example.habit.data.controls

import androidx.lifecycle.SavedStateHandle
import com.example.habit.ui.controls.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileActionsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { Dispatchers.resetMain() }
    private class Actions : ProfileActions {
        var reminders = 0; var exports = 0; var clears = 0; var fail = false
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun reminder(enabled: Boolean, minute: Int) { reminders++; pending?.await(); if (fail) error("failure") }
        override suspend fun export(uri: String) { exports++; pending?.await(); if (fail) error("failure") }
        override suspend fun clear() { clears++; pending?.await() }
    }
    @Test fun pickerCancellationAndWrongConfirmationHaveNoEffects() = runTest(dispatcher) {
        val ops = Actions(); val vm = ProfileActionsViewModel(ops, SavedStateHandle())
        vm.open("export"); assertTrue(vm.pick()); assertFalse(vm.pick()); vm.picked(null)
        vm.open("clear"); vm.confirmation("clear"); vm.clear(); runCurrent()
        assertEquals(0, ops.exports); assertEquals(0, ops.clears)
        vm.open(null); assertNull(vm.state.value.dialog)
    }
    @Test fun repeatedWritesAreGuardedAndFailuresKeepInputAndConfirmedDialog() = runTest(dispatcher) {
        val ops = Actions().apply { pending = CompletableDeferred(); fail = true }
        val vm = ProfileActionsViewModel(ops, SavedStateHandle())
        vm.open("reminder"); vm.reminder(true, 800); vm.reminder(false, 900); runCurrent()
        assertEquals(1, ops.reminders); assertTrue(vm.state.value.busy)
        ops.pending!!.complete(Unit); runCurrent()
        assertNotNull(vm.state.value.error); assertEquals("reminder", vm.state.value.dialog)
        ops.fail = false; vm.reminder(true, 800); runCurrent(); assertNull(vm.state.value.error)
    }
    @Test fun exportFailureRetainsDestinationAndSuccessIsOnlyReportedAfterWrite() = runTest(dispatcher) {
        val ops = Actions().apply { fail = true }
        val saved = SavedStateHandle(); val vm = ProfileActionsViewModel(ops, saved)
        vm.open("export"); vm.pick(); vm.picked("content://chosen/file"); runCurrent()
        assertEquals("export", vm.state.value.dialog); assertNull(vm.state.value.message)
        assertTrue(vm.state.value.error!!.contains("incomplete")); assertEquals("content://chosen/file", saved.get<String>("exportUri"))
        ops.fail = false; vm.export(); runCurrent()
        assertEquals(2, ops.exports); assertNull(vm.state.value.dialog); assertNotNull(vm.state.value.message)
    }
    @Test fun clearConfirmationSurvivesSavedPrimitivesAndRepeatedTapClearsOnce() = runTest(dispatcher) {
        val ops = Actions().apply { pending = CompletableDeferred() }
        val vm = ProfileActionsViewModel(ops, SavedStateHandle(mapOf("controlDialog" to "clear", "clearText" to "CLEAR")))
        vm.clear(); vm.clear(); runCurrent(); assertEquals(1, ops.clears)
        ops.pending!!.complete(Unit); runCurrent(); assertFalse(vm.state.value.busy)
    }
}
