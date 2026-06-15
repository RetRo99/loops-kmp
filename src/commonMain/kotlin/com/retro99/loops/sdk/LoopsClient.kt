package com.retro99.loops.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Kotlin Multiplatform client for the loops.so API (https://loops.so/docs/api-reference).
 *
 * Construct with your API key. The client owns an [HttpClient]; call [close] when done.
 */
class LoopsClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    engine: HttpClientEngine = httpClientEngine(),
) {
    private val client: HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
        }
    }

    fun close() {
        client.close()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://app.loops.so/api/v1"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
