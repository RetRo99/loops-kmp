package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.NameRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Live, end-to-end integration tests that hit the real Loops API through the **compiled SDK**
 * (real CIO engine, real serializers) — not a MockEngine.
 *
 * These are **opt-in**: they only run when the `LOOPS_LIVE_KEY` environment variable is set to a
 * valid Loops API key. With no key present every test returns early and passes, so CI and normal
 * `allTests` runs are unaffected. Run locally with:
 *
 *
 * ```
 * LOOPS_LIVE_KEY=xxxxxxxx ./gradlew jvmTest --tests '*LiveApiIntegrationTest*'
 * ```
 *
 * They create and then delete their own throwaway contacts; transactional emails created here
 * cannot be deleted via the API (no delete endpoint) and are named `sdk-it-*` for easy cleanup.
 */
class LiveApiIntegrationTest {

    private val key: String? = System.getenv("LOOPS_LIVE_KEY")?.takeIf { it.isNotBlank() }

    private fun client() = LoopsClient.direct(key!!)

    @Test
    fun `testApiKey returns the team for a valid key`() {
        val key = key ?: return
        runBlocking {
            val client = client()
            try {
                val response = client.testApiKey()
                assertTrue(response.success, "expected api-key check to succeed")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `create-find-delete round trip flattens every LoopsValue custom property`() {
        val key = key ?: return
        runBlocking {
            val client = client()
            val email = "sdk-it-${System.currentTimeMillis()}@example.com"
            try {
                val created = client.contacts.create(
                    CreateContactRequest(
                        email = email,
                        firstName = "Integration",
                        customProperties = mapOf(
                            "plan" to LoopsValue.of("pro"),
                            "age" to LoopsValue.of(7.0),
                            "vip" to LoopsValue.of(true),
                            // ms-as-number form — the only numeric date form the API accepts.
                            "sdkDateTest" to LoopsValue.ofDateMillis(1705486871000L),
                        ),
                    ),
                )
                assertTrue(created.success)

                val found = client.contacts.find(ContactIdentifier.ByEmail(email))
                val contact = found.single()
                assertEquals(email, contact.email)
                assertEquals("Integration", contact.firstName)
            } finally {
                // Always clean up the contact we created.
                runCatching { client.contacts.delete(ContactIdentifier.ByEmail(email)) }
                client.close()
            }
        }
    }

    @Test
    fun `transactional email update uses POST and the new list endpoint returns a page`() {
        val key = key ?: return
        runBlocking {
            val client = client()
            try {
                val created = client.transactional.createEmail(
                    NameRequest("sdk-it-${System.currentTimeMillis()}"),
                )
                assertTrue(created.id.isNotBlank())

                // The fix under test: update is POST /transactional-emails/{id}. If this still
                // used PATCH the live API returns 405 -> LoopsException.Api and this would fail.
                val renamed = client.transactional.updateEmail(
                    created.id,
                    NameRequest("sdk-it-renamed"),
                )
                assertEquals("sdk-it-renamed", renamed.name)

                // The added endpoint: GET /transactional-emails -> Page<TransactionalEmail>.
                val page = client.transactional.listEmails(perPage = 10)
                assertTrue(page.data.any { it.id == created.id }, "created email should appear in list")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `a date-typed property rejects a string-encoded millis timestamp`() {
        val key = key ?: return
        runBlocking {
            val client = client()
            val email = "sdk-it-date-${System.currentTimeMillis()}@example.com"
            try {
                client.contacts.create(CreateContactRequest(email = email))
                // `sdkDateTest` is a date-typed property in the test account. Sending millis as a
                // JSON *string* is rejected with HTTP 400 (Decision 8 trap) — proving why
                // ofDateMillis emits a JSON number, not a string.
                val error = assertFailsWith<LoopsException.Api> {
                    client.contacts.update(
                        com.retro99.loops.sdk.model.UpdateContactRequest(
                            email = email,
                            customProperties = mapOf(
                                "sdkDateTest" to LoopsValue.of("1705486871000"),
                            ),
                        ),
                    )
                }
                assertEquals(400, error.statusCode)
            } finally {
                runCatching { client.contacts.delete(ContactIdentifier.ByEmail(email)) }
                client.close()
            }
        }
    }
}
