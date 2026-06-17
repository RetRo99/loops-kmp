package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactPropertyType
import com.retro99.loops.sdk.model.CreatePropertyRequest
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

class ContactPropertiesApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `contactProperties list - sends GET with no query by default`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenList: String? = null
        val body = """[{"key":"email","label":"Email","type":"string"}]"""
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenList = request.url.parameters["list"]
            respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val result = classUnderTest.contactProperties.list()
        assertEquals(HttpMethod.Get, seenMethod)
        assertEquals("/api/v1/contacts/properties", seenPath)
        assertEquals(null, seenList)
        assertEquals(1, result.size)
        assertEquals("email", result[0].key)
        assertEquals("Email", result[0].label)
        assertEquals("string", result[0].type)
    }

    @Test
    fun `contactProperties list - forwards list query param`() = runTest {
        var seenList: String? = null
        val engine = MockEngine { request ->
            seenList = request.url.parameters["list"]
            respond(content = """[]""", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        classUnderTest.contactProperties.list("custom")
        assertEquals("custom", seenList)
    }

    @Test
    fun `contactProperties list - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"success":false,"message":"Error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.contactProperties.list()
        }
        assertEquals(500, error.statusCode)
    }

    @Test
    fun `contactProperties create - sends POST with name and type`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
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
        val result = classUnderTest.contactProperties.create(
            CreatePropertyRequest(name = "plan", type = ContactPropertyType.String),
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/contacts/properties", seenPath)
        assertEquals("""{"name":"plan","type":"string"}""", seenBody)
        assertTrue(result.success)
    }

    @Test
    fun `contactProperties create - serializes all ContactPropertyType variants`() {
        assertEquals(
            """{"name":"p1","type":"string"}""",
            sdkJson.encodeToString(
                CreatePropertyRequest(name = "p1", type = ContactPropertyType.String),
            ),
        )
        assertEquals(
            """{"name":"p2","type":"number"}""",
            sdkJson.encodeToString(
                CreatePropertyRequest(name = "p2", type = ContactPropertyType.Number),
            ),
        )
        assertEquals(
            """{"name":"p3","type":"boolean"}""",
            sdkJson.encodeToString(
                CreatePropertyRequest(name = "p3", type = ContactPropertyType.Boolean),
            ),
        )
        assertEquals(
            """{"name":"p4","type":"date"}""",
            sdkJson.encodeToString(
                CreatePropertyRequest(name = "p4", type = ContactPropertyType.Date),
            ),
        )
    }

    @Test
    fun `contactProperties create - non-2xx throws LoopsException Api`() = runTest {
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
            classUnderTest.contactProperties.create(
                CreatePropertyRequest(name = "bad", type = ContactPropertyType.String),
            )
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `contactProperties list - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(
            apiKey = "k",
            baseUrl = LoopsClient.LOOPS_BASE_URL,
            engine = engine,
        )
        assertFailsWith<LoopsException.Network> {
            classUnderTest.contactProperties.list()
        }
    }
}
