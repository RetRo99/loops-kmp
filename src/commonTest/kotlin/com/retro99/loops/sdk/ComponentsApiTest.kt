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

class ComponentsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `components list - sends GET and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """[{"id":"c-1","name":"Header","html":"<h1>Hi</h1>"}]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.components.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/components", seenPath)
        assertEquals(1, result.size)
        assertEquals("c-1", result[0].id)
        assertEquals("Header", result[0].name)
        assertEquals("<h1>Hi</h1>", result[0].html)
    }

    @Test
    fun `components list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.components.list() }
    }
}
