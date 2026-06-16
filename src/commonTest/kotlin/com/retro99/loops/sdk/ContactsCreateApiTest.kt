package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactWriteResponse
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.CreateContactRequestSerializer
import com.retro99.loops.sdk.model.LoopsValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContactsCreateApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contacts create - serializes known fields and flattened custom properties`() {
        val request = CreateContactRequest(
            email = "a@b.com",
            firstName = "Alice",
            customProperties = mapOf(
                "plan" to LoopsValue.of("pro"),
                "score" to LoopsValue.of(42),
            ),
        )
        val json = sdkJson.encodeToString(CreateContactRequestSerializer, request)
        assertEquals(
            """{"email":"a@b.com","firstName":"Alice","plan":"pro","score":42.0}""",
            json,
        )
    }

    @Test
    fun `contacts create - custom property cannot shadow a known field`() {
        val request = CreateContactRequest(
            email = "real@b.com",
            customProperties = mapOf("email" to LoopsValue.of("spoof@b.com")),
        )
        val json = sdkJson.encodeToString(CreateContactRequestSerializer, request)
        assertEquals("""{"email":"real@b.com"}""", json)
    }

    @Test
    fun `contacts create - round-trips request through the custom serializer`() {
        val request = CreateContactRequest(
            email = "a@b.com",
            firstName = "Alice",
            subscribed = true,
            userGroup = "beta",
            userId = "u-1",
            mailingLists = mapOf("l-1" to true, "l-2" to false),
            customProperties = mapOf(
                "plan" to LoopsValue.of("pro"),
                "score" to LoopsValue.of(42),
                "vip" to LoopsValue.of(true),
                "churned" to LoopsValue.NullValue,
                "tags" to LoopsValue.of(listOf(LoopsValue.of("a"), LoopsValue.of("b"))),
            ),
        )
        val json = sdkJson.encodeToString(CreateContactRequestSerializer, request)
        val decoded = sdkJson.decodeFromString(CreateContactRequestSerializer, json)
        assertEquals(request, decoded)
    }

    @Test
    fun `contacts create - decode collects unknown keys as custom properties`() {
        val body = """{"email":"a@b.com","firstName":"Alice","plan":"pro","score":42}"""
        val decoded = sdkJson.decodeFromString(CreateContactRequestSerializer, body)
        assertEquals("a@b.com", decoded.email)
        assertEquals("Alice", decoded.firstName)
        assertEquals(
            mapOf("plan" to LoopsValue.of("pro"), "score" to LoopsValue.of(42)),
            decoded.customProperties,
        )
    }

    @Test
    fun `contacts create - sends POST with flattened body and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true,"id":"c-new"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.create(
            CreateContactRequest(
                email = "a@b.com",
                customProperties = mapOf("plan" to LoopsValue.of("pro")),
            ),
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/contacts/create", seenPath)
        // Asserts the real Ktor content-negotiation path flattens custom props, not just the
        // direct sdkJson.encodeToString path.
        assertEquals("""{"email":"a@b.com","plan":"pro"}""", seenBody)
        assertTrue(result.success)
        assertEquals("c-new", result.id)
    }

    @Test
    fun `contacts create - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        assertFailsWith<LoopsException.Network> {
            classUnderTest.contacts.create(CreateContactRequest(email = "a@b.com"))
        }
    }

    @Test
    fun `contacts create - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Validation error"}""",
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
            classUnderTest.contacts.create(CreateContactRequest(email = "bad"))
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.body.contains("Validation error"))
    }
}
