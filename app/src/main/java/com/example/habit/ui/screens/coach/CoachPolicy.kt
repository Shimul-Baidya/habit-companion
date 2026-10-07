package com.example.habit.ui.screens.coach

import com.example.habit.coach.CoachFailure

internal fun CoachUiState.inputStatus(): String = when {
    !enabled -> "Enable Coach in Profile to send a question."
    localError -> "Retry loading Coach data before sending."
    loading -> "Sending…"
    busy -> "Finishing your change…"
    now < retryAt -> (failure?.let { "${it.headline()}. " } ?: "") +
        "You can send again in ${((retryAt - now - 1) / 1_000) + 1}s."
    failure == CoachFailure.InsufficientContext -> "Ask about starting, remembering or scheduling."
    failure != null -> "${failure.headline()}. Try again."
    else -> "Ask your own question. Mention the activity for specific advice."
}

object CoachRetryPolicy {
    fun delayMillis(attempt: Int, failure: CoachFailure): Long = if (
        failure == CoachFailure.InsufficientContext || failure == CoachFailure.InvalidInput) 0L else maxOf(
        (3_000L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(96_000L),
        if (failure is CoachFailure.RateLimited) failure.retryAfterSeconds * 1_000L else 0L)
}
internal fun CoachFailure.headline(): String = when (this) {
    CoachFailure.Disabled -> "Coach suggestions are off"
    CoachFailure.Unconfigured -> "Coach is not connected yet"
    CoachFailure.Offline -> "Can't reach the Coach"
    CoachFailure.Timeout -> "The Coach took too long"
    is CoachFailure.RateLimited -> "The Coach needs a moment"
    CoachFailure.InsufficientContext -> "Not enough context yet"
    CoachFailure.InvalidInput -> "Check this habit's plan"
    CoachFailure.CatalogMissing, CoachFailure.CatalogInvalid -> "Strategies are unavailable"
    CoachFailure.MalformedResponse -> "Couldn't read the suggestions"
    CoachFailure.ServerError -> "The Coach is unavailable"
}
internal fun CoachFailure.explanation(): String = when (this) {
    CoachFailure.Disabled -> "Enable Coach suggestions in Profile."
    CoachFailure.Unconfigured -> "The Coach connection is unavailable on this installation."
    CoachFailure.Offline -> "Suggestions need a connection. Try again when you're online."
    CoachFailure.Timeout -> "The request timed out. You can retry here."
    is CoachFailure.RateLimited -> "Please wait before trying again."
    CoachFailure.InsufficientContext -> "There isn't enough context to choose three relevant strategies yet. Ask about starting, remembering or scheduling, or use Help me get started."
    CoachFailure.InvalidInput -> "Use a valid schedule and quantity target, and keep your question within 1,000 characters."
    CoachFailure.CatalogMissing, CoachFailure.CatalogInvalid -> "The local strategy library could not be read. Try again."
    CoachFailure.MalformedResponse -> "The response didn't pass validation. No suggestion was applied."
    CoachFailure.ServerError -> "The request could not finish. Try again here."
} + " Local habits and history remain available."

internal object CoachFailureState {
    fun encode(failure: CoachFailure): String = when (failure) {
        CoachFailure.Disabled -> "DISABLED"
        CoachFailure.Unconfigured -> "UNCONFIGURED"
        CoachFailure.CatalogMissing -> "CATALOG_MISSING"
        CoachFailure.CatalogInvalid -> "CATALOG_INVALID"
        CoachFailure.InsufficientContext -> "CONTEXT"
        CoachFailure.InvalidInput -> "INPUT"
        CoachFailure.MalformedResponse -> "MALFORMED"
        CoachFailure.Offline -> "OFFLINE"
        CoachFailure.Timeout -> "TIMEOUT"
        is CoachFailure.RateLimited -> "RATE"
        CoachFailure.ServerError -> "SERVER"
    }
    fun decode(code: String?, rateSeconds: Int?): CoachFailure? = when (code) {
        "DISABLED" -> CoachFailure.Disabled
        "UNCONFIGURED" -> CoachFailure.Unconfigured
        "CATALOG_MISSING" -> CoachFailure.CatalogMissing
        "CATALOG_INVALID" -> CoachFailure.CatalogInvalid
        "CONTEXT" -> CoachFailure.InsufficientContext
        "INPUT" -> CoachFailure.InvalidInput
        "MALFORMED" -> CoachFailure.MalformedResponse
        "OFFLINE" -> CoachFailure.Offline
        "TIMEOUT" -> CoachFailure.Timeout
        "RATE" -> CoachFailure.RateLimited((rateSeconds ?: 3).coerceIn(1, 3600))
        "SERVER" -> CoachFailure.ServerError
        else -> null
    }
}
