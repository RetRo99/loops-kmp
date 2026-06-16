package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.UpdateContactRequest
import com.retro99.loops.sdk.model.UpdateContactRequestSerializer
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

class ContactsUpdateApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contacts update - serializes known fields and flattened custom properties`() {
        val request = UpdateContactRequest(
            email = "a@b.com",
            firstName = "Alice",
            customProperties = mapOf(
                "plan" to LoopsValue.of("pro"),
                "score" to LoopsValue.of(42),
            ),
        )
        val json = sdkJson.encodeToString(UpdateContactRequestSerializer, request)
        assertEquals(
            """{"email":"a@b.com","firstName":"Alice","plan":"pro","score":42.0}""",
            json,
        )
    }

    @Test
    fun `contacts update - custom property cannot shadow a known field`() {
        val request = UpdateContactRequest(
            email = "real@b.com",
            customProperties = mapOf("email" to LoopsValue.of("spoof@b.com")),
        )
        val json = sdkJson.encodeToString(UpdateContactRequestSerializer, request)
        assertEquals("""{"email":"real@b.com"}""", json)
    }

    @Test
    fun `contacts update - round-trips request through the custom serializer`() {
        val request = UpdateContactRequest(
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
        val json = sdkJson.encodeToString(UpdateContactRequestSerializer, request)
        val decoded = sdkJson.decodeFromString(UpdateContactRequestSerializer, json)
        assertEquals(request, decoded)
    }

    @Test
    fun `contacts update - decode collects unknown keys as custom properties`() {
        val body = """{"email":"a@b.com","firstName":"Alice","plan":"pro","score":42}"""
        val decoded = sdkJson.decodeFromString(UpdateContactRequestSerializer, body)
        assertEquals("a@b.com", decoded.email)
        assertEquals("Alice", decoded.firstName)
        assertEquals(
            mapOf("plan" to LoopsValue.of("pro"), "score" to LoopsValue.of(42)),
            decoded.customProperties,
        )
    }

    @Test
    fun `contacts update - by email sends PUT with flattened body and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true,"id":"c-updated"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.update(
            UpdateContactRequest(
                email = "a@b.com",
                customProperties = mapOf("plan" to LoopsValue.of("pro")),
            ),
        )
        assertEquals(HttpMethod.Put, seenMethod)
        assertEquals("/api/v1/contacts/update", seenPath)
        assertEquals("""{"email":"a@b.com","plan":"pro"}""", seenBody)
        assertTrue(result.success)
        assertEquals("c-updated", result.id)
    }

    @Test
    fun `contacts update - by userId sends PUT with flattened body`() = runTest {
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true,"id":"c-updated"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        classUnderTest.contacts.update(
            UpdateContactRequest(userId = "u-1", firstName = "Bob"),
        )
        assertEquals("""{"userId":"u-1","firstName":"Bob"}""", seenBody)
    }

    @Test
    fun `contacts update - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        assertFailsWith<LoopsException.Network> {
            classUnderTest.contacts.update(UpdateContactRequest(email = "a@b.com"))
        }
    }

    @Test
    fun `contacts update - non-2xx throws LoopsException Api`() = runTest {
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
            classUnderTest.contacts.update(UpdateContactRequest(email = "bad"))
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.body.contains("Validation error"))
    }
}
