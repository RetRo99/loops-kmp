package com.retro99.loops.sdk

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException

/**
 * Thin wrapper around Ktor's [HttpClient] that maps all exceptions into
 * [LoopsException] subtypes so callers never see third-party exceptions.
 *
 * Sub-APIs (e.g. [ContactsApi]) receive one instance in their constructor
 * and use [execute] for every network call.
 *
 * Requests that fail with HTTP 429 are retried per [retry] (exponential backoff, honouring
 * a `Retry-After` header when present) before [LoopsException.RateLimit] is thrown.
 */
internal class LoopsHttp(
    private val client: HttpClient,
    private val retry: RetryConfig = RetryConfig.DEFAULT,
) {

    /**
     * Scope for the KSP-generated JVM `*Async` wrappers (see [JvmAsync]). Lives here so a
     * single scope is shared by [LoopsClient] and every sub-API: the generated wrappers all
     * reference `http.asyncScope`. Tied to the owning client's lifecycle via [close]. Unused on
     * platforms without the JVM `CompletableFuture` wrappers.
     */
    internal val asyncScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Executes [block] with [HttpClient] as receiver, catching:
     * - HTTP 429 → retried up to [RetryConfig.maxRetries] times, then [LoopsException.RateLimit]
     * - [ResponseException] (other non-2xx) → [LoopsException.Api]
     * - [SerializationException] → [LoopsException.Serialization] (parse failure)
     * - Everything else → [LoopsException.Network] (transport error)
     */
    suspend fun <T> execute(block: suspend HttpClient.() -> T): T {
        var attempt = 0
        while (true) {
            try {
                return client.run { block() }
            } catch (exception: ResponseException) {
                val statusCode = exception.response.status.value
                if (statusCode == HttpStatusCode.TooManyRequests.value) {
                    if (attempt < retry.maxRetries) {
                        attempt++
                        delay(retry.backoffMillis(attempt, exception.response.retryAfterMillis()))
                        continue
                    }
                    throw exception.toRateLimit()
                }
                throw LoopsException.Api(
                    statusCode = statusCode,
                    body = exception.response.bodyAsText(),
                )
            } catch (exception: SerializationException) {
                throw LoopsException.Serialization(exception)
            } catch (exception: Exception) {
                throw LoopsException.Network(exception)
            }
        }
    }

    /** Cancels any in-flight async work and closes the underlying [HttpClient]. */
    fun close() {
        asyncScope.cancel()
        client.close()
    }
}

/** Builds a [LoopsException.RateLimit] from a 429 response's rate-limit headers. */
private fun ResponseException.toRateLimit(): LoopsException.RateLimit {
    val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull() ?: 0
    val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull() ?: 0
    return LoopsException.RateLimit(limit = limit, remaining = remaining)
}

/**
 * Parses a `Retry-After` header into milliseconds, or `null` when absent/unparseable.
 * Only the delta-seconds form is supported (Loops emits seconds); HTTP-date values are
 * ignored so the computed backoff is used instead.
 */
private fun HttpResponse.retryAfterMillis(): Long? =
    headers["Retry-After"]?.trim()?.toLongOrNull()?.let { seconds -> seconds * 1000 }
