@file:Suppress("DEPRECATION")

package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.Attachment
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.NameRequest
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
        // Deprecated GET /transactional returns minimal TransactionalEmailSummary items
        // (id, name, lastUpdated, dataVariables) per the spec's TransactionalEmail list schema.
        val body = """{"pagination":{"totalResults":5,"perPage":2,"totalPages":3},"data":[{"id":"te-1","name":"Welcome","lastUpdated":"2024-01-01T00:00:00Z","dataVariables":["firstName"]},{"id":"te-2","name":"Receipt","lastUpdated":"2024-01-02T00:00:00Z","dataVariables":[]}]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            seenCursor = request.url.parameters["cursor"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        @Suppress("DEPRECATION")
        val result = classUnderTest.transactional.list(perPage = 2, cursor = "abc")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/transactional", seenPath)
        assertEquals("2", seenPerPage)
        assertEquals("abc", seenCursor)
        assertEquals(5, result.pagination.totalResults)
        assertEquals(2, result.data.size)
        assertEquals("te-1", result.data[0].id)
        assertEquals("Welcome", result.data[0].name)
        assertEquals("2024-01-01T00:00:00Z", result.data[0].lastUpdated)
        assertEquals(listOf("firstName"), result.data[0].dataVariables)
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

    // region Transactional email management (Phase 8)

    private val transactionalEmailBody = """{"id":"te-1","name":"Welcome","draftEmailMessageId":"em-draft","draftEmailMessageContentRevisionId":"rev-1","publishedEmailMessageId":null,"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-02T00:00:00Z","dataVariables":["firstName","lastName"]}"""

    @Test
    fun `createEmail - sends POST with name and parses TransactionalEmail`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(content = transactionalEmailBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.createEmail(NameRequest("Welcome"))
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/transactional-emails", seenPath)
        assertEquals("""{"name":"Welcome"}""", seenBody)
        assertEquals("te-1", result.id)
        assertEquals("Welcome", result.name)
        assertEquals("em-draft", result.draftEmailMessageId)
        assertEquals(null, result.publishedEmailMessageId)
        assertEquals(listOf("firstName", "lastName"), result.dataVariables)
    }

    @Test
    fun `getEmail - sends GET with path id`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = transactionalEmailBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.getEmail("te-1")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/transactional-emails/te-1", seenPath)
        assertEquals("te-1", result.id)
    }

    @Test
    fun `updateEmail - sends POST to path with name`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(content = transactionalEmailBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.updateEmail("te-1", NameRequest("Renamed"))
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/transactional-emails/te-1", seenPath)
        assertEquals("""{"name":"Renamed"}""", seenBody)
        assertEquals("te-1", result.id)
    }

    @Test
    fun `listEmails - sends GET to transactional-emails with pagination and parses Page`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenPerPage: String? = null
        var seenCursor: String? = null
        val body = """{"pagination":{"totalResults":1,"perPage":20,"totalPages":1,"nextCursor":null},"data":[$transactionalEmailBody]}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenPerPage = request.url.parameters["perPage"]
            seenCursor = request.url.parameters["cursor"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.listEmails(perPage = 20, cursor = "cur")
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/transactional-emails", seenPath)
        assertEquals("20", seenPerPage)
        assertEquals("cur", seenCursor)
        assertEquals(1, result.pagination.totalResults)
        assertEquals(1, result.data.size)
        assertEquals("te-1", result.data[0].id)
        assertEquals("Welcome", result.data[0].name)
        assertEquals("em-draft", result.data[0].draftEmailMessageId)
        assertEquals(listOf("firstName", "lastName"), result.data[0].dataVariables)
    }

    @Test
    fun `ensureEmailDraft - sends POST to draft path with no body`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = transactionalEmailBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.ensureEmailDraft("te-1")
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/transactional-emails/te-1/draft", seenPath)
        assertEquals("em-draft", result.draftEmailMessageId)
    }

    @Test
    fun `publishEmail - sends POST to publish path with no body`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(content = transactionalEmailBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.transactional.publishEmail("te-1")
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/transactional-emails/te-1/publish", seenPath)
        assertEquals("te-1", result.id)
    }

    @Test
    fun `getEmail - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(content = """{"message":"Not found"}""", status = HttpStatusCode.NotFound, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val error = assertFailsWith<LoopsException.Api> { classUnderTest.transactional.getEmail("missing") }
        assertEquals(404, error.statusCode)
    }

    // endregion
}
