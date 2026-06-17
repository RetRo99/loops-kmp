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

/**
 * Verifies automatic retrying of HTTP 429 responses driven by [RetryConfig]. Runs under
 * [runTest], whose virtual clock skips the backoff [kotlinx.coroutines.delay] calls so the
 * tests don't actually wait.
 */
class RateLimitRetryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun rateLimitHeaders() = headersOf(
        HttpHeaders.ContentType to listOf("application/json"),
        "X-RateLimit-Limit" to listOf("10"),
        "X-RateLimit-Remaining" to listOf("0"),
    )

    @Test
    fun `retries 429 then succeeds within maxRetries`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (calls < 3) {
                respond(
                    content = """{"success":false}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = rateLimitHeaders(),
                )
            } else {
                respond(
                    content = """{"success":true,"teamName":"Acme"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig(maxRetries = 3),
            engine = engine,
        )

        val result = client.testApiKey()

        assertEquals(3, calls) // 2 x 429 + 1 success
        assertEquals(true, result.success)
    }

    @Test
    fun `exhausts retries then throws RateLimit with header values`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = """{"success":false}""",
                status = HttpStatusCode.TooManyRequests,
                headers = rateLimitHeaders(),
            )
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig(maxRetries = 2),
            engine = engine,
        )

        val error = assertFailsWith<LoopsException.RateLimit> { client.testApiKey() }

        assertEquals(3, calls) // initial + 2 retries
        assertEquals(10, error.limit)
        assertEquals(0, error.remaining)
    }

    @Test
    fun `RetryConfig NONE throws on first 429 without retrying`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = """{"success":false}""",
                status = HttpStatusCode.TooManyRequests,
                headers = rateLimitHeaders(),
            )
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig.NONE,
            engine = engine,
        )

        assertFailsWith<LoopsException.RateLimit> { client.testApiKey() }

        assertEquals(1, calls)
    }

    @Test
    fun `non-429 errors are not retried`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = """{"success":false,"message":"boom"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig(maxRetries = 3),
            engine = engine,
        )

        assertFailsWith<LoopsException.Api> { client.testApiKey() }

        assertEquals(1, calls)
    }

    @Test
    fun `Retry-After header is honoured without erroring`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    content = """{"success":false}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Retry-After" to listOf("1"),
                    ),
                )
            } else {
                respond(
                    content = """{"success":true}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig(maxRetries = 3),
            engine = engine,
        )

        val result = client.testApiKey()

        assertEquals(2, calls)
        assertEquals(true, result.success)
    }
}
