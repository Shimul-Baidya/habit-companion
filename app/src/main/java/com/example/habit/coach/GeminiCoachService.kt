package com.example.habit.coach

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.CharacterCodingException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Swappable provider adapter. No habit store, route, draft token, conversation or UI dependency. */
class GeminiCoachService internal constructor(
    private val apiKey: String,
    private val model: String,
    private val transport: GeminiTransport,
    private val now: () -> Instant = { Instant.now() },
) : CoachService {
    constructor(apiKey: String, model: String) : this(apiKey, model, AndroidGeminiTransport())

    override suspend fun request(request: CoachRequest): CoachServiceResult {
        if (apiKey.isBlank() || model != "gemini-3.5-flash-lite") return failure(CoachFailure.Unconfigured)
        return try {
            withTimeoutOrNull(30_000) {
                val response = transport.post(URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"),
                    apiKey, GeminiWire.body(request))
                when (response.status) {
                    200 -> GeminiWire.extract(response.body)?.let { CoachServiceResult.RawResponse(it) }
                        ?: failure(CoachFailure.MalformedResponse)
                    429 -> failure(CoachFailure.RateLimited(retrySeconds(response.retryAfter, response.body, now())))
                    401, 403, 404 -> failure(CoachFailure.Unconfigured)
                    408, 504 -> failure(CoachFailure.Timeout)
                    else -> failure(CoachFailure.ServerError)
                }
            } ?: failure(CoachFailure.Timeout)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SocketTimeoutException) { failure(CoachFailure.Timeout) }
        catch (_: GeminiEnvelopeTooLarge) { failure(CoachFailure.MalformedResponse) }
        catch (_: CharacterCodingException) { failure(CoachFailure.MalformedResponse) }
        catch (_: IOException) { failure(CoachFailure.Offline) }
        catch (_: Exception) { failure(CoachFailure.ServerError) }
    }

    private fun failure(reason: CoachFailure) = CoachServiceResult.Failure(reason)
    companion object {
        /** Respect both HTTP Retry-After and Google's google.rpc.RetryInfo; never retry here. */
        internal fun retrySeconds(header: String?, body: String, now: Instant): Int {
            val seconds = header?.toLongOrNull()?.toDouble() ?: runCatching {
                ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().epochSecond.toDouble() - now.epochSecond
            }.getOrNull()
            val provider = runCatching {
                val error = CoachJson.providerObject(body)["error"] as? Map<*, *>
                (error?.get("details") as? List<*>)?.mapNotNull { raw ->
                    val detail = raw as? Map<*, *> ?: return@mapNotNull null
                    if (detail["@type"] != "type.googleapis.com/google.rpc.RetryInfo") return@mapNotNull null
                    val duration = detail["retryDelay"] as? String ?: return@mapNotNull null
                    if (!duration.matches(Regex("[0-9]+(?:\\.[0-9]+)?s"))) return@mapNotNull null
                    duration.dropLast(1).toDoubleOrNull()
                }?.maxOrNull()
            }.getOrNull()
            return ceil(maxOf(seconds ?: 3.0, provider ?: 3.0)).coerceIn(1.0, 3600.0).toInt()
        }
    }
}
