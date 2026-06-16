package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Component
import io.ktor.client.call.body
import io.ktor.client.request.get

@JvmAsync
class ComponentsApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(): List<Component> =
        http.execute {
            get("components").body()
        }
}
