package com.example.habit.ui.screens.coach

import com.example.habit.coach.*
import org.junit.Test
import org.junit.Assert.*

class CoachPolicyTest {
    @Test fun inputExplainsSendingAndCooldownAndKeepsLocalFailuresHonest() {
        val ready = CoachUiState(ready = true, enabled = true, now = 10_000)
        assertEquals("Ask your own question. Mention the activity for specific advice.", ready.inputStatus())
        assertEquals("Sending…", ready.copy(loading = true).inputStatus())
        assertEquals("You can send again in 3s.", ready.copy(retryAt = 12_001).inputStatus())
        assertEquals("You can send again in 1s.", ready.copy(retryAt = 10_001).inputStatus())
        assertEquals(ready.inputStatus(), ready.copy(retryAt = 10_000).inputStatus())
        assertEquals("Retry loading Coach data before sending.", ready.copy(localError = true).inputStatus())
        assertEquals("Enable Coach in Profile to send a question.", ready.copy(enabled = false).inputStatus())
        assertEquals("Ask your own question about this habit.", ready.copy(failure = CoachFailure.InsufficientContext).inputStatus())
        assertEquals("The Coach needs a moment. You can send again in 3s.",
            ready.copy(failure = CoachFailure.RateLimited(3), retryAt = 13_000).inputStatus())
        assertEquals("The Coach is unavailable. Try again.", ready.copy(failure = CoachFailure.ServerError).inputStatus())
    }
    @Test fun exponentialCooldownIsBoundedAndHonorsProviderDelay() {
        assertEquals(3000L, CoachRetryPolicy.delayMillis(1, CoachFailure.Timeout))
        assertEquals(6000L, CoachRetryPolicy.delayMillis(2, CoachFailure.Offline))
        assertEquals(96000L, CoachRetryPolicy.delayMillis(100, CoachFailure.ServerError))
        assertEquals(3600000L, CoachRetryPolicy.delayMillis(1, CoachFailure.RateLimited(3600)))
        assertEquals(3000L, CoachRetryPolicy.delayMillis(1, CoachFailure.RateLimited(1)))
    }
    @Test fun localInputChecksAllowARevisedQuestionWithoutANetworkCooldown() {
        assertEquals(0L, CoachRetryPolicy.delayMillis(100, CoachFailure.InsufficientContext))
        assertEquals(0L, CoachRetryPolicy.delayMillis(100, CoachFailure.InvalidInput))
        assertEquals(60000L, CoachRetryPolicy.delayMillis(1, CoachFailure.RateLimited(60)))
    }
    @Test fun everyErrorRetainsHonestLocalDataCopyAndDistinctHeading() {
        val errors = listOf(CoachFailure.Disabled, CoachFailure.Unconfigured, CoachFailure.CatalogMissing, CoachFailure.CatalogInvalid,
            CoachFailure.InsufficientContext, CoachFailure.InvalidInput, CoachFailure.MalformedResponse, CoachFailure.Offline,
            CoachFailure.Timeout, CoachFailure.RateLimited(60), CoachFailure.ServerError)
        errors.forEach { assertTrue(it.explanation().contains("Local habits and history remain available.")) }
        assertEquals(4, listOf(CoachFailure.Offline, CoachFailure.Timeout, CoachFailure.RateLimited(60), CoachFailure.ServerError).map { it.headline() }.distinct().size)
        errors.forEach { assertEquals(it, CoachFailureState.decode(CoachFailureState.encode(it), (it as? CoachFailure.RateLimited)?.retryAfterSeconds)) }
        assertNull(CoachFailureState.decode(null, null))
    }
}
