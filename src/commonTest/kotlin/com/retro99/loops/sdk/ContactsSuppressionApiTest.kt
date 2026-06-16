package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsSuppressionApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contacts suppressionStatus - by email sends GET with query and parses response`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenEmail: String? = null
        val body = """{"contact":{"id":"c1","email":"a@b.com"},"isSuppressed":true,"removalQuota":{"limit":5,"remaining":3}}"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenEmail = request.url.parameters["email"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.suppressionStatus(ContactIdentifier.ByEmail("a@b.com"))
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/contacts/suppression", seenPath)
        assertEquals("a@b.com", seenEmail)
        assertEquals("c1", result.contact.id)
        assertEquals("a@b.com", result.contact.email)
        assertTrue(result.isSuppressed)
        assertEquals(5, result.removalQuota.limit)
        assertEquals(3, result.removalQuota.remaining)
    }

    @Test
    fun `contacts suppressionStatus - by userId sends userId query`() = runTest {
        var seenUserId: String? = null
        val engine = MockEngine { request ->
            seenUserId = request.url.parameters["userId"]
            respond(
                content = """{"contact":{"id":"c1","email":"a@b.com"},"isSuppressed":false,"removalQuota":{"limit":5,"remaining":5}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.suppressionStatus(ContactIdentifier.ByUserId("u-1"))
        assertEquals("u-1", seenUserId)
        assertFalse(result.isSuppressed)
    }

    @Test
    fun `contacts suppressionStatus - non-2xx throws LoopsException Api`() = runTest {
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
            classUnderTest.contacts.suppressionStatus(ContactIdentifier.ByEmail("missing@b.com"))
        }
        assertEquals(404, error.statusCode)
        assertTrue(error.body.contains("Not found"))
    }
}
