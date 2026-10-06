package com.example.habit.data.controls

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DataGateTest {
    @Test fun nestedWritesWorkAndQueuedOldWritesCannotReappearAfterReset() = runTest {
        val gate = DataGate()
        assertEquals(5, gate.access { gate.access { 5 } })
        val hold = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val writer = launch { gate.access { hold.await(); events += "first" } }
        runCurrent()
        val reset = launch { gate.reset { events += "clear" } }; runCurrent()
        val stale = async { runCatching { gate.access { events += "stale" } }.isFailure }; runCurrent()
        hold.complete(Unit); writer.join(); reset.join()
        assertTrue(stale.await()); assertEquals(listOf("first", "clear"), events)
        gate.access { events += "new" }; assertEquals("new", events.last())
    }
    @Test fun failedResetBlocksWritesUntilRecovery() = runTest {
        val gate = DataGate()
        assertTrue(runCatching { gate.reset { throw IllegalStateException() } }.isFailure)
        assertTrue(gate.blocked)
        assertTrue(runCatching { gate.access { error("Must never execute") } }.isFailure)
        gate.reset { }; assertFalse(gate.blocked)
        assertEquals(1, gate.access { 1 })
    }
}
