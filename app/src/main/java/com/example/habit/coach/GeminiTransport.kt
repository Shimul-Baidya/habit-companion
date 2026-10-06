package com.example.habit.coach

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class GeminiHttpResponse(val status: Int, val body: String, val retryAfter: String? = null)
internal fun interface GeminiTransport {
    suspend fun post(url: URL, apiKey: String, body: String): GeminiHttpResponse
}
internal class GeminiEnvelopeTooLarge : IOException()

/** Platform HTTPS, one POST, no redirects/retries/logging. Cancellation closes the active socket. */
internal class AndroidGeminiTransport(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : GeminiTransport {
    override suspend fun post(url: URL, apiKey: String, body: String): GeminiHttpResponse = suspendCancellableCoroutine { continuation ->
        val connection = AtomicReference<HttpURLConnection?>()
        val task = workers.submit {
            var active: HttpURLConnection? = null
            try {
                require(url.protocol == "https" && url.host == "generativelanguage.googleapis.com" && url.query == null)
                if (!continuation.isActive) return@submit
                active = open(url)
                connection.set(active)
                if (!continuation.isActive) return@submit
                active.requestMethod = "POST"
                active.instanceFollowRedirects = false
                active.connectTimeout = 10_000
                active.readTimeout = 20_000
                active.useCaches = false
                active.doOutput = true
                active.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                active.setRequestProperty("Accept", "application/json")
                active.setRequestProperty("x-goog-api-key", apiKey)
                val bytes = body.toByteArray(Charsets.UTF_8)
                active.setFixedLengthStreamingMode(bytes.size)
                active.outputStream.use { it.write(bytes) }
                val status = active.responseCode
                val stream = if (status in 200..299) active.inputStream else active.errorStream
                val response = stream?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        if (!continuation.isActive) return@submit
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > 65_536) throw GeminiEnvelopeTooLarge()
                        out.write(buffer, 0, read)
                    }
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString()
                } ?: ""
                continuation.resume(GeminiHttpResponse(status, response, active.getHeaderField("Retry-After")))
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally {
                active?.disconnect()
            }
        }
        continuation.invokeOnCancellation {
            task.cancel(true)
            connection.get()?.disconnect()
        }
    }
    companion object {
        private val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "habit-coach-https").apply { isDaemon = true }
        }
    }
}
