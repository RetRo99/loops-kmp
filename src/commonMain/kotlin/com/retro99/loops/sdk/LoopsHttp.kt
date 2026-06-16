package com.retro99.loops.sdk

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
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
     * Executes [block] with [HttpClient] as receiver, catching:
     * - [ResponseException] → [LoopsException.Api] (non-2xx response)
     * - [SerializationException] → [LoopsException.Serialization] (parse failure)
     * - Everything else → [LoopsException.Network] (transport error)
     */
    suspend fun <T> execute(block: suspend HttpClient.() -> T): T =
        try {
            client.run { block() }
        } catch (exception: ResponseException) {
            throw LoopsException.Api(
                statusCode = exception.response.status.value,
                body = exception.response.bodyAsText(),
            )
        } catch (exception: SerializationException) {
            throw LoopsException.Serialization(exception)
        } catch (exception: Exception) {
            throw LoopsException.Network(exception)
        }
}
