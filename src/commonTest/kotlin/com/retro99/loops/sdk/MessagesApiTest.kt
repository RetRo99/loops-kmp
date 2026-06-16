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

class MessagesApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `messages list - sends GET and parses Page envelope`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenPerPage: String? = null
        val body = """{"pagination":{"totalResults":1,"perPage":10,"totalPages":1},"data":[{"id":"m-1","name":"Welcome Email","subject":"Welcome!"}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.messages.list(perPage = 10)
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/messages", seenPath)
        assertEquals("10", seenPerPage)
        assertEquals(1, result.data.size)
        assertEquals("m-1", result.data[0].id)
        assertEquals("Welcome Email", result.data[0].name)
    }

    @Test
    fun `messages get - sends GET with path id`() = runTest {
        var seenPath: String? = null
        val body = """{"id":"m-1","name":"Welcome Email","subject":"Welcome!","fromName":"Team"}"""
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.messages.get("m-1")
        assertEquals("/api/v1/messages/m-1", seenPath)
        assertEquals("m-1", result.id)
        assertEquals("Welcome Email", result.name)
    }

    @Test
    fun `messages list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.messages.list() }
    }
}
