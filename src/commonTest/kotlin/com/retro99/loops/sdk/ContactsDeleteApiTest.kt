package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
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

class ContactsDeleteApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contacts delete - by email sends POST with email body`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true,"message":"Contact deleted"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.delete(ContactIdentifier.ByEmail("a@b.com"))
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/contacts/delete", seenPath)
        assertEquals("""{"email":"a@b.com"}""", seenBody)
        assertTrue(result.success)
        assertEquals("Contact deleted", result.message)
    }

    @Test
    fun `contacts delete - by userId sends POST with userId body`() = runTest {
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"success":true,"message":"Contact deleted"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.delete(ContactIdentifier.ByUserId("u-1"))
        assertEquals("""{"userId":"u-1"}""", seenBody)
        assertTrue(result.success)
    }

    @Test
    fun `contacts delete - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Not found"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.contacts.delete(ContactIdentifier.ByEmail("missing@b.com"))
        }
        assertEquals(404, error.statusCode)
        assertTrue(error.body.contains("Not found"))
    }
}
