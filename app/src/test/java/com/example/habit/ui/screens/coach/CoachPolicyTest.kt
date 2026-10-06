package com.example.habit.ui.screens.coach

import com.example.habit.coach.*
import org.junit.Test
import org.junit.Assert.*

class CoachPolicyTest {
    @Test fun exponentialCooldownIsBoundedAndHonorsProviderDelay() {
        assertEquals(3000L, CoachRetryPolicy.delayMillis(1, CoachFailure.Timeout))
        assertEquals(6000L, CoachRetryPolicy.delayMillis(2, CoachFailure.Offline))
        assertEquals(96000L, CoachRetryPolicy.delayMillis(100, CoachFailure.ServerError))
        assertEquals(3600000L, CoachRetryPolicy.delayMillis(1, CoachFailure.RateLimited(3600)))
        assertEquals(3000L, CoachRetryPolicy.delayMillis(1, CoachFailure.RateLimited(1)))
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
