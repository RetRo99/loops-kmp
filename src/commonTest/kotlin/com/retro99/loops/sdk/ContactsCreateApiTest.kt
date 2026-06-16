package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactWriteResponse
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.CreateContactRequestSerializer
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
    fun `contacts create - sends POST to correct path and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
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
            CreateContactRequest(email = "a@b.com"),
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/contacts/create", seenPath)
        assertTrue(result.success)
        assertEquals("c-new", result.id)
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
