package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.model.Contact
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Contacts API group.
 *
 * Accessed via [LoopsClient.contacts]:
 * ```kotlin
 * val contacts = client.contacts.find(email = "a@b.com")
 * ```
 */
class ContactsApi internal constructor(private val http: LoopsHttp) {

    /**
     * Find a contact by [email] or [userId]. Only one identifier should be provided.
     */
    suspend fun find(
        email: String? = null,
        userId: String? = null,
    ): List<Contact> =
        http.execute {
            get("contacts/find") {
                email?.let { parameter("email", it) }
                userId?.let { parameter("userId", it) }
            }.body()
        }
}
