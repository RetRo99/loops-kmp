package com.retro99.loops.sdk

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth

/**
 * Applies a [ProxyAuth] strategy to an outgoing request. Re-evaluated per request so rotating
 * tokens/headers are picked up without rebuilding the client. Extracted from [loopsHttpClient]'s
 * proxy-auth plugin so the auth dispatch can be unit-tested directly against an
 * [HttpRequestBuilder], independent of the HttpClient.
 */
internal suspend fun HttpRequestBuilder.applyProxyAuth(auth: ProxyAuth) {
    when (auth) {
        ProxyAuth.None -> {}
        is ProxyAuth.BearerToken -> auth.token()?.let { token -> bearerAuth(token) }
        is ProxyAuth.Headers -> auth.headers().forEach { (key, value) ->
            headers.append(key, value)
        }
    }
}
