package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Component
import com.retro99.loops.sdk.model.Page
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

@JvmAsync
class ComponentsApi internal constructor(
    internal val http: LoopsHttp,
) {

    /** Lists components (GET components), paginated. */
    suspend fun list(perPage: Int? = null, cursor: String? = null): Page<Component> =
        http.execute {
            get("components") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /** Fetches a single component by id (GET components/{id}). */
    suspend fun get(id: String): Component =
        http.execute {
            get("components/$id").body()
        }
}
