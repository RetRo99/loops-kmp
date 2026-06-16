package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.NameRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CampaignsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `campaigns list - sends GET with pagination params and parses Page envelope`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenPerPage: String? = null
        var seenCursor: String? = null
        val body = """{"pagination":{"totalResults":10,"perPage":5,"totalPages":2},"data":[{"id":"c-1","name":"Campaign 1","status":"Sent","createdAt":"2024-01-01T00:00:00Z"}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            seenCursor = request.url.parameters["cursor"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.campaigns.list(perPage = 5, cursor = "abc")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/campaigns", seenPath)
        assertEquals("5", seenPerPage)
        assertEquals("abc", seenCursor)
        assertEquals(10, result.pagination.totalResults)
        assertEquals(1, result.data.size)
        assertEquals("c-1", result.data[0].id)
        assertEquals("Campaign 1", result.data[0].name)
    }

    @Test
    fun `campaigns get - sends GET with path id`() = runTest {
        var seenPath: String? = null
        val body = """{"id":"c-1","name":"Campaign 1","status":"Draft"}"""
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.campaigns.get("c-1")
        assertEquals("/api/v1/campaigns/c-1", seenPath)
        assertEquals("c-1", result.id)
        assertEquals("Campaign 1", result.name)
    }

    @Test
    fun `campaigns create - sends POST with name`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"id":"c-new","name":"New Campaign","status":"Draft"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.campaigns.create(NameRequest("New Campaign"))
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/campaigns", seenPath)
        assertEquals("""{"name":"New Campaign"}""", seenBody)
        assertEquals("c-new", result.id)
    }

    @Test
    fun `campaigns update - sends POST to path with name`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"id":"c-1","name":"Updated Name","status":"Draft"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.campaigns.update("c-1", NameRequest("Updated Name"))
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/campaigns/c-1", seenPath)
        assertEquals("""{"name":"Updated Name"}""", seenBody)
        assertEquals("Updated Name", result.name)
    }

    @Test
    fun `campaigns list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.InternalServerError, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.campaigns.list() }
    }
}
