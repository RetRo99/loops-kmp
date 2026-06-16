package com.retro99.loops.sdk

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.SerializationException

/**
 * Thin wrapper around Ktor's [HttpClient] that maps all exceptions into
 * [LoopsException] subtypes so callers never see third-party exceptions.
 *
 * Sub-APIs (e.g. [ContactsApi]) receive one instance in their constructor
 * and use [execute] for every network call.
 */
internal class LoopsHttp(private val client: HttpClient) {

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
     * - [ResponseException] → [LoopsException.Api] (non-2xx response)
     * - [SerializationException] → [LoopsException.Serialization] (parse failure)
     * - Everything else → [LoopsException.Network] (transport error)
     */
    suspend fun <T> execute(block: suspend HttpClient.() -> T): T =
        try {
            client.run { block() }
        } catch (exception: ResponseException) {
            val statusCode = exception.response.status.value
            if (statusCode == 429) {
                val limit = exception.response.headers["X-RateLimit-Limit"]?.toIntOrNull() ?: 0
                val remaining = exception.response.headers["X-RateLimit-Remaining"]?.toIntOrNull() ?: 0
                throw LoopsException.RateLimit(limit = limit, remaining = remaining)
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

    /** Cancels any in-flight async work and closes the underlying [HttpClient]. */
    fun close() {
        asyncScope.cancel()
        client.close()
    }
}
