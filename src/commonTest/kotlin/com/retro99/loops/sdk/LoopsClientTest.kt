package com.retro99.loops.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopsClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val successBody = """{"success":true,"teamName":"My team"}"""

    // ── direct mode ──────────────────────────────────────────────────────────

    @Test
    fun `direct - testApiKey sends bearer auth to api-key and parses team name`() = runTest {
        // Given
        var seenAuth: String? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenPath = request.url.encodedPath
            respond(
                content = successBody,
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "test-key",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )

        // When
        val result = classUnderTest.testApiKey()

        // Then
        assertEquals("Bearer test-key", seenAuth)
        assertEquals("/api/v1/api-key", seenPath)
        assertTrue(result.success)
        assertEquals("My team", result.teamName)
    }

    @Test
    fun `direct - testApiKey throws Api error with status and body on non-2xx`() = runTest {
        // Given
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Invalid API key"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "bad",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )

        // When / Then
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.testApiKey()
        }
        assertEquals(401, error.statusCode)
        assertTrue(error.body.contains("Invalid API key"))
    }

    @Test
    fun `direct - testApiKey wraps transport failures as Network error`() = runTest {
        // Given
        val engine = MockEngine {
            throw RuntimeException("connection reset")
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )

        // When / Then
        val error = assertFailsWith<LoopsException.Network> {
            classUnderTest.testApiKey()
        }
        assertTrue(error.message!!.contains("connection reset"))
    }

    @Test
    fun `direct - honours baseUrl override`() = runTest {
        // Given
        var seenHost: String? = null
        val engine = MockEngine { request ->
            seenHost = request.url.host
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = "https://staging.loops.so/api/v1/",
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertEquals("staging.loops.so", seenHost)
    }

    // ── proxy mode ───────────────────────────────────────────────────────────

    @Test
    fun `proxy + None - sends no Authorization header`() = runTest {
        // Given
        var seenAuth: String? = "sentinel"
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.None,
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertNull(seenAuth)
    }

    @Test
    fun `proxy - points at proxy host - never at app loops so`() = runTest {
        // Given
        var seenHost: String? = null
        val engine = MockEngine { request ->
            seenHost = request.url.host
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.None,
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertEquals("my-backend.com", seenHost)
        assertTrue(seenHost != "app.loops.so")
    }

    @Test
    fun `proxy + BearerToken - attaches dynamic token`() = runTest {
        // Given
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.BearerToken { "session-token-abc" },
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertEquals("Bearer session-token-abc", seenAuth)
    }

    @Test
    fun `proxy + BearerToken - refreshes token between calls`() = runTest {
        // Given
        var currentToken = "token-v1"
        val seenTokens = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenTokens += request.headers[HttpHeaders.Authorization]
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.BearerToken { currentToken },
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()
        currentToken = "token-v2"
        classUnderTest.testApiKey()

        // Then — proves the provider is re-evaluated per request, not captured at construction
        assertEquals("Bearer token-v1", seenTokens[0])
        assertEquals("Bearer token-v2", seenTokens[1])
    }

    @Test
    fun `proxy + BearerToken returning null - sends no Authorization header`() = runTest {
        // Given
        var seenAuth: String? = "sentinel"
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.BearerToken { null },
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertNull(seenAuth)
    }

    @Test
    fun `proxy + Headers - attaches arbitrary headers`() = runTest {
        // Given
        var seenSession: String? = null
        val engine = MockEngine { request ->
            seenSession = request.headers["X-App-Session"]
            respond(content = successBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.proxy(
            proxyUrl = "https://my-backend.com/loops/",
            auth = ProxyAuth.Headers { mapOf("X-App-Session" to "sess-xyz") },
            engine = engine,
        )

        // When
        classUnderTest.testApiKey()

        // Then
        assertEquals("sess-xyz", seenSession)
    }
}
