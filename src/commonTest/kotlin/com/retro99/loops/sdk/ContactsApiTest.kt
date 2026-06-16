package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContactsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contacts find - by email parses contact list from response`() = runTest {
        var seenEmail: String? = null
        val body = """[{"id":"c1","email":"a@b.com","firstName":"Alice"}]"""
        val engine = MockEngine { request ->
            seenEmail = request.url.parameters["email"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "test-key",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contacts.find(ContactIdentifier.ByEmail("a@b.com"))
        assertEquals("a@b.com", seenEmail)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
        assertEquals("a@b.com", result[0].email)
        assertEquals("Alice", result[0].firstName)
    }

    @Test
    fun `contacts find - by userId sends userId query param`() = runTest {
        var seenUserId: String? = null
        val engine = MockEngine { request ->
            seenUserId = request.url.parameters["userId"]
            respond(content = """[]""", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        classUnderTest.contacts.find(ContactIdentifier.ByUserId("u-1"))
        assertEquals("u-1", seenUserId)
    }

    @Test
    fun `contacts find - routes non-2xx through LoopsException Api`() = runTest {
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
            classUnderTest.contacts.find(ContactIdentifier.ByEmail("missing@b.com"))
        }
        assertEquals(404, error.statusCode)
        assertTrue(error.body.contains("Not found"))
    }

    @Test
    fun `contacts find - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine {
            throw RuntimeException("connection refused")
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val error = assertFailsWith<LoopsException.Network> {
            classUnderTest.contacts.find(ContactIdentifier.ByEmail("a@b.com"))
        }
        assertTrue(error.message!!.contains("connection refused"))
    }
}
