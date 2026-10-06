package com.example.habit.data.controls

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Serializes exports/reset with writes to both local stores. Queued old writes expire at reset. */
class DataGate {
    private val mutex = Mutex()
    @Volatile private var epoch = 0L
    @Volatile var blocked = false
        private set
    private class Token(val gate: DataGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Token>
    }
    suspend fun <T> access(block: suspend () -> T): T {
        if (currentCoroutineContext()[Token]?.gate === this) return block()
        val entered = epoch
        return mutex.withLock {
            check(!blocked && entered == epoch) { "Local data changed; reopen this screen." }
            withContext(Token(this)) { block() }
        }
    }
    suspend fun <T> reset(block: suspend () -> T): T = mutex.withLock {
        epoch++; blocked = true
        val result = block()
        blocked = false
        result
    }
}
