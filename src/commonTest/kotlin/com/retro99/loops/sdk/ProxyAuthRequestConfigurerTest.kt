package com.retro99.loops.sdk

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyAuthRequestConfigurerTest {

    @Test
    fun `None - appends no Authorization header`() = runTest {
        // Given
        val request = HttpRequestBuilder()

        // When
        request.applyProxyAuth(ProxyAuth.None)

        // Then
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `BearerToken with value - sets bearer Authorization header`() = runTest {
        // Given
        val request = HttpRequestBuilder()

        // When
        request.applyProxyAuth(ProxyAuth.BearerToken { "session-token-abc" })

        // Then
        assertEquals("Bearer session-token-abc", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `BearerToken returning null - appends no Authorization header`() = runTest {
        // Given
        val request = HttpRequestBuilder()

        // When
        request.applyProxyAuth(ProxyAuth.BearerToken { null })

        // Then
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Headers - appends each arbitrary header verbatim`() = runTest {
        // Given
        val request = HttpRequestBuilder()

        // When
        request.applyProxyAuth(
            ProxyAuth.Headers {
                mapOf(
                    "X-App-Session" to "sess-xyz",
                    "X-Tenant" to "acme",
                )
            },
        )

        // Then
        assertEquals("sess-xyz", request.headers["X-App-Session"])
        assertEquals("acme", request.headers["X-Tenant"])
    }

    @Test
    fun `BearerToken - re-evaluates the provider on each call`() = runTest {
        // Given — a provider whose value changes between calls
        var currentToken = "token-v1"
        val auth = ProxyAuth.BearerToken { currentToken }

        // When
        val first = HttpRequestBuilder().apply { applyProxyAuth(auth) }
        currentToken = "token-v2"
        val second = HttpRequestBuilder().apply { applyProxyAuth(auth) }

        // Then — proves the provider is read per request, not captured once
        assertEquals("Bearer token-v1", first.headers[HttpHeaders.Authorization])
        assertEquals("Bearer token-v2", second.headers[HttpHeaders.Authorization])
    }
}
