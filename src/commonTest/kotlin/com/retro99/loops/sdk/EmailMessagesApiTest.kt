package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.UpdateEmailMessageRequest
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

class EmailMessagesApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `get - sends GET with email-messages path and parses message`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val body = """{"id":"m-1","campaignId":"c-1","subject":"Welcome!","previewText":"Hi","fromName":"Team","fromEmail":"team","replyToEmail":"reply@x.com","lmx":"<lmx/>","contentRevisionId":"rev-1","updatedAt":"2026-06-17T00:00:00Z"}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.emailMessages.get("m-1")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/email-messages/m-1", seenPath)
        assertEquals("m-1", result.id)
        assertEquals("c-1", result.campaignId)
        assertEquals("Welcome!", result.subject)
        assertEquals("reply@x.com", result.replyToEmail)
        assertEquals("<lmx/>", result.lmx)
        assertEquals("rev-1", result.contentRevisionId)
    }

    @Test
    fun `update - sends POST with body carrying only set fields plus expectedRevisionId`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val response = """{"id":"m-1","subject":"New subject","contentRevisionId":"rev-2","warnings":[{"rule":"r","severity":"warn","message":"msg","path":"p"}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(content = response, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.emailMessages.update(
            "m-1",
            UpdateEmailMessageRequest(expectedRevisionId = "rev-1", subject = "New subject"),
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/email-messages/m-1", seenPath)
        assertEquals("""{"expectedRevisionId":"rev-1","subject":"New subject"}""", seenBody)
        assertEquals("rev-2", result.contentRevisionId)
        val warnings = result.warnings
        assertEquals(1, warnings?.size)
        assertEquals("warn", warnings?.get(0)?.severity)
    }

    @Test
    fun `get - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine { respond(content = """{}""", status = HttpStatusCode.NotFound, headers = jsonHeaders) }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Api> { classUnderTest.emailMessages.get("missing") }
    }
}
