package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Theme
import io.ktor.client.call.body
import io.ktor.client.request.get

@JvmAsync
class ThemesApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(): List<Theme> =
        http.execute {
            get("themes").body()
        }
}
