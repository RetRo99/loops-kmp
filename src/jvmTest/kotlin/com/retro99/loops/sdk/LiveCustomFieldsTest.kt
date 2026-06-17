package com.retro99.loops.sdk

import com.retro99.loops.sdk.model.ContactIdentifier
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.LoopsValue
import com.retro99.loops.sdk.model.UpdateContactRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live, end-to-end coverage of **custom contact properties** — the SDK's hardest serialization
 * surface (top-level flattening, every [LoopsValue] case, the JSON-null reset, and the
 * known-field shadow guard) — exercised through the compiled SDK against the real Loops API.
 *
 * Opt-in via `LOOPS_LIVE_KEY`, like the other live tests; self-skips with no key set.
 */
class LiveCustomFieldsTest {

    private val key: String? = System.getenv("LOOPS_LIVE_KEY")?.takeIf { it.isNotBlank() }

    private fun withClient(block: suspend LoopsClient.(email: String) -> Unit) {
        val key = key ?: return
        val client = LoopsClient.direct(key)
        val email = "sdk-cf-${System.currentTimeMillis()}@example.com"
        try {
            runBlocking {
                try {
                    client.block(email)
                } finally {
                    runCatching { client.contacts.delete(ContactIdentifier.ByEmail(email)) }
                }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `create flattens string number boolean and date custom props to top level`() = withClient { email ->
        // The whole point of the custom serializer: these are sent as top-level siblings of the
        // known fields, not nested under a customProperties key. If flattening were wrong the API
        // would not auto-create the typed properties below.
        val created = contacts.create(
            CreateContactRequest(
                email = email,
                firstName = "CF",
                customProperties = mapOf(
                    "sdkCfStr" to LoopsValue.of("hello"),
                    "sdkCfNum" to LoopsValue.of(123.0),
                    "sdkCfBool" to LoopsValue.of(true),
                    "sdkDateTest" to LoopsValue.ofDateMillis(1705486871000L),
                ),
            ),
        )
        assertTrue(created.success)

        // The contact comes back and the custom properties we set are now surfaced on the typed
        // Contact (read-side custom-property collection — the fix for the write/read asymmetry).
        val contact = contacts.find(ContactIdentifier.ByEmail(email)).single()
        assertEquals(email, contact.email)
        assertEquals("CF", contact.firstName)
        assertEquals(LoopsValue.of("hello"), contact.customProperties["sdkCfStr"])
        assertEquals(LoopsValue.of(123.0), contact.customProperties["sdkCfNum"])
        assertEquals(LoopsValue.of(true), contact.customProperties["sdkCfBool"])
        // Known fields are not leaked into the custom map.
        assertTrue("email" !in contact.customProperties)
        assertTrue("firstName" !in contact.customProperties)
    }

    @Test
    fun `the property types Loops infers match the LoopsValue cases sent`() = withClient { email ->
        contacts.create(
            CreateContactRequest(
                email = email,
                customProperties = mapOf(
                    "sdkCfStr" to LoopsValue.of("hello"),
                    "sdkCfNum" to LoopsValue.of(123.0),
                    "sdkCfBool" to LoopsValue.of(true),
                ),
            ),
        )
        // Read the property registry and confirm Loops inferred the type we intended for each — this
        // proves the wire form of each LoopsValue case was correct (a number sent as a string would
        // have registered as type "string", etc.).
        val byKey = contactProperties.list(list = "custom").associate { it.key to it.type }
        assertEquals("string", byKey["sdkCfStr"])
        assertEquals("number", byKey["sdkCfNum"])
        assertEquals("boolean", byKey["sdkCfBool"])
    }

    @Test
    fun `updating a custom prop to NullValue resets it`() = withClient { email ->
        // Set, then reset via LoopsValue.NullValue (which the serializer emits as JSON null). The
        // live API clears the property — proving NullValue's reset semantics end-to-end.
        contacts.create(
            CreateContactRequest(
                email = email,
                customProperties = mapOf("sdkCfStr" to LoopsValue.of("blue")),
            ),
        )
        val reset = contacts.update(
            UpdateContactRequest(
                email = email,
                customProperties = mapOf("sdkCfStr" to LoopsValue.NullValue),
            ),
        )
        assertTrue(reset.success)
        // After a null reset the property is cleared: it no longer appears on read-back.
        val contact = contacts.find(ContactIdentifier.ByEmail(email)).single()
        assertTrue("sdkCfStr" !in contact.customProperties)
    }

    @Test
    fun `a custom prop cannot shadow a known typed field`() = withClient { email ->
        // The serializer drops a custom key that collides with a known field, so the typed
        // firstName wins. If shadowing leaked through, firstName would be the custom value.
        val created = contacts.create(
            CreateContactRequest(
                email = email,
                firstName = "RealFirstName",
                customProperties = mapOf("firstName" to LoopsValue.of("ShadowAttempt")),
            ),
        )
        assertTrue(created.success)
        val contact = contacts.find(ContactIdentifier.ByEmail(email)).single()
        assertEquals("RealFirstName", contact.firstName)
    }

    @Test
    fun `date custom prop accepts ISO-8601 string form`() = withClient { email ->
        // ofDateString emits an ECMA-262 string, the other wire form a date-typed property accepts.
        val created = contacts.create(
            CreateContactRequest(
                email = email,
                customProperties = mapOf(
                    "sdkDateTest" to LoopsValue.ofDateString("2024-01-17T10:21:11Z"),
                ),
            ),
        )
        assertTrue(created.success)
    }
}
