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

class IpPoolsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `ip pools list - sends GET and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """[{"id":"pool-1","name":"Primary","ips":[{"ip":"192.0.2.1","status":"hot"}]}]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.ipPools.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/ip-pools", seenPath)
        assertEquals(1, result.size)
        assertEquals("pool-1", result[0].id)
        assertEquals("Primary", result[0].name)
        assertEquals(1, result[0].ips.size)
        assertEquals("192.0.2.1", result[0].ips[0].ip)
        assertEquals("hot", result[0].ips[0].status)
    }

    @Test
    fun `ip pools list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.ipPools.list() }
    }
}
