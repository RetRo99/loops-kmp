package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.ContactProperty
import com.retro99.loops.sdk.model.ContactPropertyType
import com.retro99.loops.sdk.model.CreatePropertyRequest
import com.retro99.loops.sdk.model.SuccessResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Contact Properties API group.
 *
 * Accessed via [LoopsClient.contactProperties]:
 * ```kotlin
 * val props = client.contactProperties.list("custom")
 * ```
 */
@JvmAsync
class ContactPropertiesApi internal constructor(
    internal val http: LoopsHttp,
) {

    /**
     * List contact properties, optionally filtered by [list] (`"all"` or `"custom"`, defaults to `"all"`).
     */
    suspend fun list(list: String? = null): List<ContactProperty> =
        http.execute {
            get("contacts/properties") {
                list?.let { parameter("list", it) }
            }.body()
        }

    /**
     * Create a new contact property.
     */
    suspend fun create(request: CreatePropertyRequest): SuccessResponse =
        http.execute {
            post("contacts/properties") {
                setBody(request)
            }.body()
        }
}
