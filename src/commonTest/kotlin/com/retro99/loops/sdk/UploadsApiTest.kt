package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.CreateUploadRequest
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

class UploadsApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `create - sends POST with content type and length and parses presigned URL`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"emailAssetId":"asset-1","presignedUrl":"https://s3.example.com/put?sig=abc"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.uploads.create(
            CreateUploadRequest(contentType = "image/png", contentLength = 12345),
        )
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/uploads", seenPath)
        assertEquals("""{"contentType":"image/png","contentLength":12345}""", seenBody)
        assertEquals("asset-1", result.emailAssetId)
        assertEquals("https://s3.example.com/put?sig=abc", result.presignedUrl)
    }

    @Test
    fun `complete - sends POST to complete path with no body and parses final URL`() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            respond(
                content = """{"emailAssetId":"asset-1","finalUrl":"https://cdn.loops.so/asset-1.png"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val result = classUnderTest.uploads.complete("asset-1")
        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/api/v1/uploads/asset-1/complete", seenPath)
        assertEquals("asset-1", result.emailAssetId)
        assertEquals("https://cdn.loops.so/asset-1.png", result.finalUrl)
    }

    @Test
    fun `create - non-2xx throws LoopsException Api`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"Unsupported content type"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        val error = assertFailsWith<LoopsException.Api> {
            classUnderTest.uploads.create(CreateUploadRequest("image/tiff", 100))
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `complete - wraps transport failure as Network error`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val classUnderTest = LoopsClient.direct(apiKey = "k", baseUrl = LoopsClient.LOOPS_BASE_URL, engine = engine)
        assertFailsWith<LoopsException.Network> { classUnderTest.uploads.complete("asset-1") }
    }
}
