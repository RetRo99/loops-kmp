package com.retro99.loops.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Assembles the configured Ktor [HttpClient] for a [LoopsClient] from its [config] and [engine].
 * Sole responsibility: turn a [LoopsConfig] + engine into a client with JSON content negotiation,
 * the base URL, and the appropriate authentication wired in. Auth dispatch is delegated to
 * [applyProxyAuth].
 */
internal fun loopsHttpClient(
    config: LoopsConfig,
    engine: HttpClientEngine,
): HttpClient =
    HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(sdkJson)
        }
        config.timeout.let { timeout ->
            if (timeout != TimeoutConfig.NONE) {
                install(HttpTimeout) {
                    requestTimeoutMillis = timeout.requestTimeoutMillis
                    connectTimeoutMillis = timeout.connectTimeoutMillis
                    socketTimeoutMillis = timeout.socketTimeoutMillis
                }
            }
        }
        if (config.logging.level != LogLevel.NONE) {
            install(Logging) {
                level = config.logging.level
                config.logging.logger?.let { logger = it }
            }
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
                onRequest { request, _ -> request.applyProxyAuth(config.auth) }
            })
        }
    }

private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"
