package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Contact
import com.retro99.loops.sdk.model.ContactWriteResponse
import com.retro99.loops.sdk.model.CreateContactRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Contacts API group.
 *
 * Accessed via [LoopsClient.contacts]:
 * ```kotlin
 * val contacts = client.contacts.find(email = "a@b.com")
 * ```
 */
@JvmAsync
class ContactsApi internal constructor(
    // Internal (not private) so the KSP-generated `ContactsApiAsync` wrappers, top-level
    // extensions in this package, can reach `http.asyncScope`.
    internal val http: LoopsHttp,
) {

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

    /**
     * Create a new contact. [request.email] is required;
     * [customProperties][CreateContactRequest.customProperties] are flattened into the
     * top-level JSON body alongside the known fields.
     */
    suspend fun create(request: CreateContactRequest): ContactWriteResponse =
        http.execute {
            post("contacts/create") {
                setBody(request)
            }.body()
        }
}
