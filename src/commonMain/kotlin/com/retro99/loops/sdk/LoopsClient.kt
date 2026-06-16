package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ApiKeyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
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
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            // Ktor resolves relative paths against the base only when it ends with '/'.
            url(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Tests the API key (GET /api-key). Returns the team the key belongs to,
     * or throws [LoopsException] if the key is invalid.
     */
    suspend fun testApiKey(): ApiKeyResponse =
        request { client.get("api-key").body() }

    fun close() {
        client.close()
    }

    private suspend inline fun <T> request(block: () -> T): T =
        try {
            block()
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

    companion object {
        const val DEFAULT_BASE_URL: String = "https://app.loops.so/api/v1/"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
