package com.retro99.loops.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingConfigTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class RecordingLogger : Logger {
        val lines = mutableListOf<String>()
        override fun log(message: String) {
            lines.add(message)
        }
    }

    private fun successEngine() = MockEngine {
        respond(
            content = """{"success":true,"teamName":"Acme"}""",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    @Test
    fun `enabled logging writes to the supplied logger`() = runTest {
        val logger = RecordingLogger()
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig.NONE,
            timeout = TimeoutConfig.NONE,
            logging = LoggingConfig.of(LogLevel.INFO, logger),
            engine = successEngine(),
        )

        client.testApiKey()

        assertTrue(logger.lines.isNotEmpty(), "expected log output, got none")
        assertTrue(
            logger.lines.any { line -> line.contains("api-key") },
            "expected a log line referencing the request path, got: ${logger.lines}",
        )
    }

    @Test
    fun `disabled logging writes nothing`() = runTest {
        val logger = RecordingLogger()
        val client = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            retry = RetryConfig.NONE,
            timeout = TimeoutConfig.NONE,
            logging = LoggingConfig.of(LogLevel.NONE, logger),
            engine = successEngine(),
        )

        client.testApiKey()

        assertEquals(emptyList(), logger.lines)
    }
}
