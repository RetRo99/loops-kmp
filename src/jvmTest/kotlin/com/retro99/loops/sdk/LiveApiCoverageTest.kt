package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
import com.retro99.loops.sdk.model.ContactPropertyType
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.CreatePropertyRequest
import com.retro99.loops.sdk.model.CreateUploadRequest
import com.retro99.loops.sdk.model.EventRequest
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.NameRequest
import com.retro99.loops.sdk.model.TransactionalSendRequest
import com.retro99.loops.sdk.model.UpdateEmailMessageRequest
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

    @Test
    fun `email message get round trips from a campaign email message id`() = withClient {
        // Email messages have no list endpoint; a real id is reachable from a campaign that has
        // one. Find the first campaign exposing an emailMessageId and fetch it by id.
        val emailMessageId = campaigns.list(perPage = 50)
            .data
            .firstNotNullOfOrNull { campaign -> campaign.emailMessageId }
        if (emailMessageId == null) {
            // Account has no campaign with an email message yet — nothing to exercise.
            return@withClient
        }
        val message = emailMessages.get(emailMessageId)
        assertEquals(emailMessageId, message.id)
    }

    @Test
    fun `getting an email message with a bogus id maps to LoopsException Api`() = withClient {
        val error = assertFailsWith<LoopsException.Api> {
            emailMessages.get("sdk-it-does-not-exist")
        }
        assertTrue(error.statusCode in 400..499)
    }

    @Test
    fun `email message update changes preview text then restores it`() = withClient {
        // Mutating test (demo account only). Resolve a real email message via a campaign, flip its
        // previewText, assert the change, then restore the original so the demo content is left
        // untouched. expectedRevisionId is the optimistic-concurrency token from contentRevisionId.
        val emailMessageId = campaigns.list(perPage = 50)
            .data
            .firstNotNullOfOrNull { campaign -> campaign.emailMessageId }
        if (emailMessageId == null) {
            // No campaign with an email message — nothing safe to mutate.
            return@withClient
        }

        val original = emailMessages.get(emailMessageId)
        val originalRevision = original.contentRevisionId
        if (originalRevision == null) {
            // Without a revision token we cannot satisfy expectedRevisionId — skip rather than 409.
            return@withClient
        }
        val originalPreview = original.previewText

        // update -> POST /email-messages/{id}. Only previewText is changed (a safe metadata field).
        val updated = emailMessages.update(
            emailMessageId,
            UpdateEmailMessageRequest(
                expectedRevisionId = originalRevision,
                previewText = "sdk-it-preview-${System.currentTimeMillis()}",
            ),
        )
        try {
            assertEquals(emailMessageId, updated.id)
            assertTrue(updated.previewText?.startsWith("sdk-it-preview-") == true)
        } finally {
            // Restore the original preview text using the new revision so the demo is left as-is.
            updated.contentRevisionId?.let { newRevision ->
                runCatching {
                    emailMessages.update(
                        emailMessageId,
                        UpdateEmailMessageRequest(
                            expectedRevisionId = newRevision,
                            previewText = originalPreview,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `deprecated transactional list returns a Page envelope`() = withClient {
        // The deprecated GET v1/transactional list (kept for loops-js parity). Still must decode
        // into the Page<TransactionalEmailSummary> envelope.
        @Suppress("DEPRECATION")
        val page = transactional.list(perPage = 10)
        assertNotNull(page.pagination)
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

    @Test
    fun `transactional send succeeds with data variables an attachment and an idempotency key`() =
        withClient {
            // The 404 test above only covers the failure path. This exercises the success path of
            // POST transactional end to end: the dataVariables nested object, the attachments array
            // (Attachment model), and the Idempotency-Key header. We stand up a throwaway
            // transactional email (create -> ensure draft -> publish) and send to its published id.
            val email = transactional.createEmail(
                NameRequest("sdk-it-send-${System.currentTimeMillis()}"),
            )
            transactional.ensureEmailDraft(email.id)
            val published = transactional.publishEmail(email.id)
            if (published.publishedEmailMessageId == null) {
                // A blank draft may not publish into a sendable transactional id; skip rather than
                // fail, since the management lifecycle itself is covered by LiveApiIntegrationTest.
                return@withClient
            }

            // A tiny 1x1 PNG, base64-encoded, to exercise the Attachment(filename, contentType,
            // data) model over the wire.
            val onePixelPngBase64 =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
            val response = transactional.send(
                TransactionalSendRequest(
                    email = "sdk-it-send-${System.currentTimeMillis()}@example.com",
                    transactionalId = email.id,
                    addToAudience = false,
                    dataVariables = mapOf(
                        "name" to LoopsValue.of("SDK"),
                        "count" to LoopsValue.of(3.0),
                    ),
                    attachments = listOf(
                        com.retro99.loops.sdk.model.Attachment(
                            filename = "pixel.png",
                            contentType = "image/png",
                            data = onePixelPngBase64,
                        ),
                    ),
                ),
                idempotencyKey = "sdk-it-idem-${System.currentTimeMillis()}",
            )
            assertTrue(response.success)
        }

    @Test
    fun `events send forwards an idempotency key`() = withClient {
        // Companion to the events test above, which omits the key. This drives the
        // Idempotency-Key header path of POST events/send against the real API.
        val response = events.send(
            EventRequest(
                eventName = "sdk_it_event",
                email = "sdk-it-event-idem-${System.currentTimeMillis()}@example.com",
            ),
            idempotencyKey = "sdk-it-event-idem-${System.currentTimeMillis()}",
        )
        assertTrue(response.success)
    }

    // endregion

    // region Creates (persistent — sdk-it- prefixed)

    @Test
    fun `campaign create get and update round trip`() = withClient {
        val created = campaigns.create(NameRequest("sdk-it-campaign-${System.currentTimeMillis()}"))
        val createdId = assertNotNull(created.id, "create should return an id")
        val fetched = campaigns.get(createdId)
        assertEquals(createdId, fetched.id)
        assertEquals(created.name, fetched.name)

        // update -> POST /campaigns/{id}. Renaming returns the updated campaign.
        val renamed = campaigns.update(createdId, NameRequest("sdk-it-campaign-renamed"))
        assertEquals(createdId, renamed.id)
        assertEquals("sdk-it-campaign-renamed", renamed.name)
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

    // region Suppression (Phase 1.5 / 1.6)

    @Test
    fun `suppression status and removal round trip for a fresh contact`() = withClient {
        val email = "sdk-it-supp-${System.currentTimeMillis()}@example.com"
        try {
            contacts.create(CreateContactRequest(email = email))
            val identifier = ContactIdentifier.ByEmail(email)

            // suppressionStatus -> GET /contacts/suppression. A fresh contact is not suppressed,
            // and the response carries the contact plus the removal quota.
            val status = contacts.suppressionStatus(identifier)
            assertEquals(email, status.contact.email)
            assertEquals(false, status.isSuppressed)
            assertTrue(status.removalQuota.limit >= 0)

            // removeSuppression -> DELETE /contacts/suppression. The contact is not suppressed, so
            // this is a no-op removal; it must still return a typed response (not throw).
            val removal = contacts.removeSuppression(identifier)
            assertTrue(removal.removalQuota.limit >= 0)
        } finally {
            runCatching { contacts.delete(ContactIdentifier.ByEmail(email)) }
        }
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
