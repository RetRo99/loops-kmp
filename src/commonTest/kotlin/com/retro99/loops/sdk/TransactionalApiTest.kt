package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.Attachment
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.TransactionalSendRequest
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

class TransactionalApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `transactional send - serializes full request with attachments`() {
        val request = TransactionalSendRequest(
            email = "a@b.com",
            transactionalId = "t-1",
            addToAudience = true,
            dataVariables = mapOf("name" to LoopsValue.of("Alice")),
            attachments = listOf(
                Attachment(filename = "receipt.pdf", contentType = "application/pdf", data = "base64data"),
            ),
        )
        val json = sdkJson.encodeToString(TransactionalSendRequest.serializer(), request)
        assertEquals(
            """{"email":"a@b.com","transactionalId":"t-1","addToAudience":true,"dataVariables":{"name":"Alice"},"attachments":[{"filename":"receipt.pdf","contentType":"application/pdf","data":"base64data"}]}""",
            json,
        )
    }

    @Test
    fun `transactional send - sends POST with idempotency header and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenIdempotency: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenIdempotency = request.headers["Idempotency-Key"]
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.transactional.send(
            request = TransactionalSendRequest(email = "a@b.com", transactionalId = "t-1"),
            idempotencyKey = "key-123",
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/transactional", seenPath)
        assertEquals("key-123", seenIdempotency)
        assertEquals("""{"email":"a@b.com","transactionalId":"t-1"}""", seenBody)
        assertTrue(result.success)
    }

    @Test
    fun `transactional send - no idempotency key when null`() = runTest {
        var seenIdempotency: String? = "sentinel"
        val engine = MockEngine { request ->
            seenIdempotency = request.headers["Idempotency-Key"]
            respond(content = """{"success":true}""", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        classUnderTest.transactional.send(
            TransactionalSendRequest(email = "a@b.com", transactionalId = "t-1"),
        )
        assertEquals(null, seenIdempotency)
    }

    @Test
    fun `transactional list - sends GET with pagination params and parses Page envelope`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenPerPage: String? = null
        var seenCursor: String? = null
        val body = """{"pagination":{"totalResults":5,"perPage":2,"totalPages":3},"data":[{"id":"tm-1","recipient":"a@b.com","status":"sent","sentAt":"2024-01-01T00:00:00Z"},{"id":"tm-2","recipient":"c@d.com","status":"opened","openedAt":"2024-01-02T00:00:00Z"}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            seenCursor = request.url.parameters["cursor"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.list(perPage = 2, cursor = "abc")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/transactional", seenPath)
        assertEquals("2", seenPerPage)
        assertEquals("abc", seenCursor)
        assertEquals(5, result.pagination.totalResults)
        assertEquals(2, result.data.size)
        assertEquals("tm-1", result.data[0].id)
        assertEquals("a@b.com", result.data[0].recipient)
        assertEquals("sent", result.data[0].status)
    }

    @Test
    fun `transactional send - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Bad request"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.transactional.send(
                TransactionalSendRequest(email = "bad", transactionalId = "t-1"),
            )
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `transactional send - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        assertFailsWith<LoopsException.Network> {
            classUnderTest.transactional.send(
                TransactionalSendRequest(email = "a@b.com", transactionalId = "t-1"),
            )
        }
    }
}
