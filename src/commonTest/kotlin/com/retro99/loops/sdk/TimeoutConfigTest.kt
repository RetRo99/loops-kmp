package com.retro99.loops.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeoutConfigTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `request slower than requestTimeout fails as Network`() = runTest {
        val engine = MockEngine {
            delay(60_000) // far beyond the 50ms request timeout below
            respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig.NONE,
            timeout = TimeoutConfig(requestTimeoutMillis = 50),
            logging = LoggingConfig.none(),
            engine = engine,
        )

        // HttpRequestTimeoutException is not a ResponseException/SerializationException,
        // so it maps to Network.
        assertFailsWith<LoopsException.Network> { client.testApiKey() }
    }

    @Test
    fun `fast request within timeout succeeds`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"success":true,"teamName":"Acme"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig.NONE,
            timeout = TimeoutConfig(requestTimeoutMillis = 5_000),
            logging = LoggingConfig.none(),
            engine = engine,
        )

        assertEquals(true, client.testApiKey().success)
    }

    @Test
    fun `negative timeout is rejected`() {
        assertFailsWith<IllegalArgumentException> { TimeoutConfig(requestTimeoutMillis = -1) }
        assertFailsWith<IllegalArgumentException> { TimeoutConfig(connectTimeoutMillis = 0) }
    }

    @Test
    fun `NONE leaves all timeouts null`() {
        assertEquals(null, TimeoutConfig.NONE.requestTimeoutMillis)
        assertEquals(null, TimeoutConfig.NONE.connectTimeoutMillis)
        assertEquals(null, TimeoutConfig.NONE.socketTimeoutMillis)
    }
}
