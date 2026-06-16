package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Contact
import com.retro99.loops.sdk.model.ContactIdentifier
import com.retro99.loops.sdk.model.ContactWriteResponse
import com.retro99.loops.sdk.model.CreateContactRequest
import com.retro99.loops.sdk.model.DeleteContactRequest
import com.retro99.loops.sdk.model.DeleteResponse
import com.retro99.loops.sdk.model.SuppressionStatusResponse
import com.retro99.loops.sdk.model.UpdateContactRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/**
 * Contacts API group.
 *
 * Accessed via [LoopsClient.contacts]:
 * ```kotlin
 * val contacts = client.contacts.find(ContactIdentifier.ByEmail("a@b.com"))
 * ```
 */
@JvmAsync
class ContactsApi internal constructor(
    // Internal (not private) so the KSP-generated `ContactsApiAsync` wrappers, top-level
    // extensions in this package, can reach `http.asyncScope`.
    internal val http: LoopsHttp,
) {

    /**
     * Find a contact by [identifier].
     */
    suspend fun find(identifier: ContactIdentifier): List<Contact> =
        http.execute {
            get("contacts/find") {
                when (identifier) {
                    is ContactIdentifier.ByEmail -> parameter("email", identifier.email)
                    is ContactIdentifier.ByUserId -> parameter("userId", identifier.userId)
                }
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

    /**
     * Update an existing contact. At least one of [request.email] or [request.userId]
     * must be provided; [customProperties][UpdateContactRequest.customProperties] are
     * flattened into the top-level JSON body alongside the known fields.
     */
    suspend fun update(request: UpdateContactRequest): ContactWriteResponse =
        http.execute {
            put("contacts/update") {
                setBody(request)
            }.body()
        }

    /**
     * Delete a contact identified by [identifier].
     */
    suspend fun delete(identifier: ContactIdentifier): DeleteResponse =
        http.execute {
            post("contacts/delete") {
                setBody(
                    when (identifier) {
                        is ContactIdentifier.ByEmail -> DeleteContactRequest(email = identifier.email)
                        is ContactIdentifier.ByUserId -> DeleteContactRequest(userId = identifier.userId)
                    },
                )
            }.body()
        }

    /**
     * Check the suppression status of a contact identified by [identifier].
     */
    suspend fun suppressionStatus(identifier: ContactIdentifier): SuppressionStatusResponse =
        http.execute {
            get("contacts/suppression") {
                when (identifier) {
                    is ContactIdentifier.ByEmail -> parameter("email", identifier.email)
                    is ContactIdentifier.ByUserId -> parameter("userId", identifier.userId)
                }
            }.body()
        }
}
