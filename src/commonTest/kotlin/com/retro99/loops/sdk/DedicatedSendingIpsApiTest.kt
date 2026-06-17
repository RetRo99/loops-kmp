package com.retro99.loops.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DedicatedSendingIpsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `sending ips list - sends GET and parses string array`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """["1.2.3.4","5.6.7.8"]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.sendingIps.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/dedicated-sending-ips", seenPath)
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), result)
    }

    @Test
    fun `sending ips list - empty array parses to empty list`() = runTest {
        val engine = MockEngine { respond(content = """[]""", status = HttpStatusCode.OK, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertEquals(emptyList(), classUnderTest.sendingIps.list())
    }

    @Test
    fun `sending ips list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.sendingIps.list() }
    }
}
