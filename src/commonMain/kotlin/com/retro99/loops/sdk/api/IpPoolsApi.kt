package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.IpPool
import io.ktor.client.call.body
import io.ktor.client.request.get

@JvmAsync
class IpPoolsApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(): List<IpPool> =
        http.execute {
            get("ip-pools").body()
        }
}
