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

class ThemesApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `themes list - sends GET and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """[{"id":"t-1","name":"Default"}]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.themes.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/themes", seenPath)
        assertEquals(1, result.size)
        assertEquals("t-1", result[0].id)
        assertEquals("Default", result[0].name)
    }

    @Test
    fun `themes list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.themes.list() }
    }
}
