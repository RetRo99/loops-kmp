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
import kotlin.test.assertTrue

class LoopsClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `testApiKey sends bearer auth to api-key and parses team name`() = runTest {
        // Given
        var seenAuth: String? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenPath = request.url.encodedPath
            respond(
                content = """{"success":true,"teamName":"My team"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient(apiKey = "test-key", engine = engine)

        // When
        val result = classUnderTest.testApiKey()

        // Then
        assertEquals("Bearer test-key", seenAuth)
        assertEquals("/api/v1/api-key", seenPath)
        assertTrue(result.success)
        assertEquals("My team", result.teamName)
    }

    @Test
    fun `testApiKey throws Api error with status and body on non-2xx`() = runTest {
        // Given
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Invalid API key"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient(apiKey = "bad", engine = engine)

        // When / Then
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.testApiKey()
        }
        assertEquals(401, error.statusCode)
        assertTrue(error.body.contains("Invalid API key"))
    }

    @Test
    fun `testApiKey wraps transport failures as Network error`() = runTest {
        // Given
        val engine = MockEngine {
            throw RuntimeException("connection reset")
        }
        val classUnderTest = LoopsClient(apiKey = "k", engine = engine)

        // When / Then
        val error = assertFailsWith<LoopsException.Network> {
            classUnderTest.testApiKey()
        }
        assertTrue(error.message!!.contains("connection reset"))
    }
}
