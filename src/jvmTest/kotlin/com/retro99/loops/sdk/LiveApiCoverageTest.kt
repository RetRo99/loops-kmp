package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactPropertyType
import com.retro99.loops.sdk.model.CreatePropertyRequest
import com.retro99.loops.sdk.model.CreateUploadRequest
import com.retro99.loops.sdk.model.EventRequest
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.NameRequest
import com.retro99.loops.sdk.model.TransactionalSendRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Broader live coverage of the remaining sub-APIs, run through the **compiled SDK** against the
 * real Loops API. Opt-in via `LOOPS_LIVE_KEY` exactly like [LiveApiIntegrationTest] — with no key
 * every test returns early and passes, so CI is unaffected.
 *
 * Anything persistent these create is named with an `sdk-it-` prefix for easy manual cleanup
 * (contact properties, campaigns, and transactional emails have no delete endpoint).
 */
class LiveApiCoverageTest {

    private val key: String? = System.getenv("LOOPS_LIVE_KEY")?.takeIf { it.isNotBlank() }

    private fun client() = LoopsClient.direct(key!!)

    private fun withClient(block: suspend LoopsClient.() -> Unit) {
        key ?: return
        val client = client()
        try {
            runBlocking { client.block() }
        } finally {
            client.close()
        }
    }

    // region Read-only endpoints

    @Test
    fun `lists returns a parseable list`() = withClient {
        val lists = lists.list()
        // Just assert it decodes into the typed model; the account may have zero lists.
        assertNotNull(lists)
    }

    @Test
    fun `dedicated sending ips returns a string array`() = withClient {
        val ips = sendingIps.list()
        assertNotNull(ips)
        // Every element, if any, is a non-blank IP string.
        ips.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `contact properties list returns custom properties with string types`() = withClient {
        val custom = contactProperties.list(list = "custom")
        assertNotNull(custom)
        // type is read as a plain String (Decision 7) — should be one of the known values when present.
        custom.forEach { assertTrue(it.key.isNotBlank()) }
    }

    @Test
    fun `campaigns list returns a Page envelope`() = withClient {
        val page = campaigns.list(perPage = 10)
        assertNotNull(page.pagination)
        // If a campaign exists, get-by-id round-trips to the same id.
        page.data.firstOrNull()?.id?.let { firstId ->
            val fetched = campaigns.get(firstId)
            assertEquals(firstId, fetched.id)
        }
    }

    @Test
    fun `themes list returns a Page envelope`() = withClient {
        val page = themes.list(perPage = 10)
        assertNotNull(page.pagination)
        page.data.firstOrNull()?.let { first ->
            val fetched = themes.get(first.id)
            assertEquals(first.id, fetched.id)
        }
    }

    @Test
    fun `components list returns a Page envelope`() = withClient {
        val page = components.list(perPage = 10)
        assertNotNull(page.pagination)
        page.data.firstOrNull()?.let { first ->
            val fetched = components.get(first.id)
            assertEquals(first.id, fetched.id)
        }
    }

    @Test
    fun `getting a theme with a bogus id maps to LoopsException Api`() = withClient {
        val error = assertFailsWith<LoopsException.Api> {
            themes.get("sdk-it-does-not-exist")
        }
        assertTrue(error.statusCode in 400..499)
    }

    // endregion

    // region Events & transactional send (these trigger real workflow/email processing)

    @Test
    fun `events send accepts flattened custom props and nested event props`() = withClient {
        val response = events.send(
            EventRequest(
                eventName = "sdk_it_event",
                email = "sdk-it-event-${System.currentTimeMillis()}@example.com",
                customProperties = mapOf("plan" to LoopsValue.of("pro")),
                eventProperties = mapOf(
                    "orderId" to LoopsValue.of("sdk-it-123"),
                    "total" to LoopsValue.of(42.5),
                ),
            ),
        )
        assertTrue(response.success)
    }

    @Test
    fun `transactional send to a nonexistent id maps to LoopsException Api 404`() = withClient {
        val error = assertFailsWith<LoopsException.Api> {
            transactional.send(
                TransactionalSendRequest(
                    email = "sdk-it@example.com",
                    transactionalId = "sdk-it-nonexistent",
                ),
            )
        }
        assertEquals(404, error.statusCode)
    }

    // endregion

    // region Creates (persistent — sdk-it- prefixed)

    @Test
    fun `campaign create then get round trips`() = withClient {
        val created = campaigns.create(NameRequest("sdk-it-campaign-${System.currentTimeMillis()}"))
        val createdId = assertNotNull(created.id, "create should return an id")
        val fetched = campaigns.get(createdId)
        assertEquals(createdId, fetched.id)
        assertEquals(created.name, fetched.name)
    }

    @Test
    fun `contact property create succeeds for a fresh name`() = withClient {
        // Unique name so it doesn't collide with an existing property (which would 400).
        val name = "sdkItProp${System.currentTimeMillis()}"
        val response = contactProperties.create(
            CreatePropertyRequest(name = name, type = ContactPropertyType.String),
        )
        assertTrue(response.success)
        // It should now appear in the custom-property list with a string type.
        val listed = contactProperties.list(list = "custom")
        assertTrue(listed.any { it.key == name }, "created property should be listed")
    }

    // endregion

    // region Uploads

    @Test
    fun `uploads create returns an email asset id and presigned url`() = withClient {
        val response = uploads.create(
            CreateUploadRequest(contentType = "image/png", contentLength = 1024),
        )
        assertTrue(response.emailAssetId.isNotBlank())
        assertTrue(response.presignedUrl.startsWith("https://"), "presigned URL should be https")
    }

    @Test
    fun `uploads complete before the file is uploaded maps to LoopsException Api`() = withClient {
        // Obtain a real assetId, then call complete without PUTting any bytes to S3. The SDK only
        // brokers the URLs; completing an upload that never happened is expected to be rejected,
        // which must surface as a typed LoopsException.Api (not a raw Ktor exception).
        val created = uploads.create(
            CreateUploadRequest(contentType = "image/png", contentLength = 1024),
        )
        assertFailsWith<LoopsException.Api> {
            uploads.complete(created.emailAssetId)
        }
    }

    // endregion
}
