package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Page
import com.retro99.loops.sdk.model.Theme
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

@JvmAsync
class ThemesApi internal constructor(
    internal val http: LoopsHttp,
) {

    /** Lists email themes (GET themes), paginated. */
    suspend fun list(perPage: Int? = null, cursor: String? = null): Page<Theme> =
        http.execute {
            get("themes") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /** Fetches a single theme by id (GET themes/{id}). */
    suspend fun get(id: String): Theme =
        http.execute {
            get("themes/$id").body()
        }
}
