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

class MailingListsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `mailing lists list - sends GET and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """[{"id":"l-1","name":"Newsletter","description":"Monthly updates","isPublic":true}]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.lists.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/lists", seenPath)
        assertEquals(1, result.size)
        assertEquals("l-1", result[0].id)
        assertEquals("Newsletter", result[0].name)
        assertEquals("Monthly updates", result[0].description)
        assertEquals(true, result[0].isPublic)
    }

    @Test
    fun `mailing lists list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(content = """{"success":false}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.lists.list() }
    }

    @Test
    fun `mailing lists list - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Network> { classUnderTest.lists.list() }
    }
}
