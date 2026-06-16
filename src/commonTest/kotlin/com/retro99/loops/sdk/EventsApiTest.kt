package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.EventRequest
import com.retro99.loops.sdk.model.EventRequestSerializer
import com.retro99.loops.sdk.model.LoopsValue
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

class EventsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `events send - serializes known fields with nested eventProperties and flattened customProperties`() {
        val request = EventRequest(
            eventName = "signup",
            email = "a@b.com",
            eventProperties = mapOf("source" to LoopsValue.of("web")),
            customProperties = mapOf("plan" to LoopsValue.of("pro")),
        )
        val json = sdkJson.encodeToString(EventRequestSerializer, request)
        assertEquals(
            """{"eventName":"signup","email":"a@b.com","eventProperties":{"source":"web"},"plan":"pro"}""",
            json,
        )
    }

    @Test
    fun `events send - custom property cannot shadow a known field`() {
        val request = EventRequest(
            eventName = "signup",
            email = "real@b.com",
            customProperties = mapOf("eventName" to LoopsValue.of("spoof")),
        )
        val json = sdkJson.encodeToString(EventRequestSerializer, request)
        assertEquals("""{"eventName":"signup","email":"real@b.com"}""", json)
    }

    @Test
    fun `events send - round-trips request through the custom serializer`() {
        val request = EventRequest(
            eventName = "signup",
            email = "a@b.com",
            userId = "u-1",
            eventProperties = mapOf("source" to LoopsValue.of("web")),
            mailingLists = mapOf("l-1" to true),
            customProperties = mapOf(
                "plan" to LoopsValue.of("pro"),
                "score" to LoopsValue.of(42),
                "vip" to LoopsValue.of(true),
            ),
        )
        val json = sdkJson.encodeToString(EventRequestSerializer, request)
        val decoded = sdkJson.decodeFromString(EventRequestSerializer, json)
        assertEquals(request, decoded)
    }

    @Test
    fun `events send - sends POST with idempotency header and parses response`() = runTest {
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
        val result = classUnderTest.events.send(
            request = EventRequest(eventName = "signup", email = "a@b.com"),
            idempotencyKey = "key-123",
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/events/send", seenPath)
        assertEquals("key-123", seenIdempotency)
        assertEquals("""{"eventName":"signup","email":"a@b.com"}""", seenBody)
        assertTrue(result.success)
    }

    @Test
    fun `events send - no idempotency key when null`() = runTest {
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
        classUnderTest.events.send(EventRequest(eventName = "signup", email = "a@b.com"))
        assertEquals(null, seenIdempotency)
    }

    @Test
    fun `events send - non-2xx throws LoopsException Api`() = runTest {
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
            classUnderTest.events.send(EventRequest(eventName = "signup", email = "bad"))
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `events send - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        assertFailsWith<LoopsException.Network> {
            classUnderTest.events.send(EventRequest(eventName = "signup", email = "a@b.com"))
        }
    }
}
