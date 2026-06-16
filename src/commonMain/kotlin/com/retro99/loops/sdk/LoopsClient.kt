package com.retro99.loops.sdk

import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.ApiKeyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Kotlin Multiplatform client for the loops.so API (https://loops.so/docs/api-reference).
 *
 * **Server-side / trusted use** — holds the real Loops API key, talks to Loops directly:
 * ```kotlin
 * val client = LoopsClient.direct(apiKey = "YOUR_API_KEY")
 * ```
 *
 * **Mobile / untrusted use** — points at your own backend proxy; the proxy holds the key:
 * ```kotlin
 * val client = LoopsClient.proxy(
 *     proxyUrl = "https://your-backend.com/loops/",
 *     auth = ProxyAuth.BearerToken { session.token() },
 * )
 * ```
 *
 * The client owns an [HttpClient]; call [close] when done.
 */
@JvmAsync
class LoopsClient private constructor(
    private val config: LoopsConfig,
    engine: HttpClientEngine,
) {
    private val client: HttpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            // Ktor resolves relative paths against the base only when it ends with '/'.
            url(config.baseUrl.ensureTrailingSlash())
            contentType(ContentType.Application.Json)
            if (config is LoopsConfig.Direct) {
                bearerAuth(config.apiKey)
            }
        }
        if (config is LoopsConfig.Proxy) {
            install(createClientPlugin("LoopsProxyAuth") {
                onRequest { request, _ ->
                    when (val auth = config.auth) {
                        ProxyAuth.None -> {}
                        is ProxyAuth.BearerToken -> auth.token()?.let { request.bearerAuth(it) }
                        is ProxyAuth.Headers -> auth.headers().forEach { (key, value) ->
                            request.headers.append(key, value)
                        }
                    }
                }
            })
        }
    }

    /**
     * Tests the API key (GET /api-key). Returns the team the key belongs to,
     * or throws [LoopsException] if the key is invalid.
     */
    suspend fun testApiKey(): ApiKeyResponse =
        request { client.get("api-key").body() }

    /**
     * Scope for generated JVM `*Async` wrappers (see [JvmAsync]). Tied to this client's
     * lifecycle so [close] cancels any in-flight async work. Unused on platforms without
     * the JVM `CompletableFuture` wrappers.
     */
    internal val asyncScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun close() {
        asyncScope.cancel()
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
        /** The Loops production API base URL. Pass as [direct]'s `baseUrl` only for staging / EU residency overrides. */
        const val LOOPS_BASE_URL: String = "https://app.loops.so/api/v1/"

        /**
         * Creates a client for **server-side / trusted** use. Holds the Loops API key and
         * talks to Loops directly. Never use this in a mobile app — the key would be
         * extractable from the binary.
         *
         * @param apiKey Your Loops account API key.
         * @param baseUrl Override only for Loops staging or EU data-residency endpoints.
         */
        fun direct(
            apiKey: String,
            baseUrl: String = LOOPS_BASE_URL,
            engine: HttpClientEngine = httpClientEngine(),
        ): LoopsClient = LoopsClient(LoopsConfig.Direct(apiKey, baseUrl), engine)

        /**
         * Creates a client for **mobile / untrusted** use. Points at your own backend proxy
         * which holds the real Loops API key server-side. The Loops key is never present here.
         *
         * @param proxyUrl Base URL of your backend proxy (e.g. `"https://your-api.com/loops/"`).
         * @param auth How to authenticate the app to your proxy. Defaults to [ProxyAuth.None].
         */
        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth = ProxyAuth.None,
            engine: HttpClientEngine = httpClientEngine(),
        ): LoopsClient = LoopsClient(LoopsConfig.Proxy(proxyUrl, auth), engine)

        private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
