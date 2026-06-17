package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.LoopsValue
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
    fun `themes list - sends GET and parses Page envelope with mixed styles`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenPerPage: String? = null
        val body = """{"pagination":{"totalResults":1,"perPage":20,"totalPages":1},"data":[{"id":"t-1","name":"Default","isDefault":true,"styles":{"backgroundColor":"#fff","borderRadius":8}}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.themes.list(perPage = 20)
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/themes", seenPath)
        assertEquals("20", seenPerPage)
        assertEquals(1, result.data.size)
        val theme = result.data[0]
        assertEquals("t-1", theme.id)
        assertEquals(true, theme.isDefault)
        assertEquals(LoopsValue.of("#fff"), theme.styles["backgroundColor"])
        assertEquals(LoopsValue.of(8.0), theme.styles["borderRadius"])
    }

    @Test
    fun `themes get - sends GET with path id`() = runTest {
        var seenPath: String? = null
        val body = """{"id":"t-1","name":"Default","isDefault":false,"styles":{}}"""
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.themes.get("t-1")
        assertEquals("/api/v1/themes/t-1", seenPath)
        assertEquals("t-1", result.id)
        assertEquals("Default", result.name)
    }

    @Test
    fun `themes list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.themes.list() }
    }
}
