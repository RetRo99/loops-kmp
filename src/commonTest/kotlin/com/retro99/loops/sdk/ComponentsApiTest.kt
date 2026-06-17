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
    fun `components list - sends GET and parses Page envelope`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenCursor: String? = null
        val body = """{"pagination":{"totalResults":1,"perPage":20,"totalPages":1},"data":[{"id":"c-1","name":"Header","lmx":"<Header/>"}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenCursor = request.url.parameters["cursor"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.components.list(cursor = "abc")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/components", seenPath)
        assertEquals("abc", seenCursor)
        assertEquals(1, result.data.size)
        assertEquals("c-1", result.data[0].id)
        assertEquals("Header", result.data[0].name)
        assertEquals("<Header/>", result.data[0].lmx)
    }

    @Test
    fun `components get - sends GET with path id`() = runTest {
        var seenPath: String? = null
        val body = """{"id":"c-1","name":"Header","lmx":"<Header/>"}"""
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.components.get("c-1")
        assertEquals("/api/v1/components/c-1", seenPath)
        assertEquals("c-1", result.id)
        assertEquals("<Header/>", result.lmx)
    }

    @Test
    fun `components list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.components.list() }
    }
}
