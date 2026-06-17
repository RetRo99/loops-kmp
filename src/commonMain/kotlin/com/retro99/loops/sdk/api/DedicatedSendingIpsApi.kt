package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import io.ktor.client.call.body
import io.ktor.client.request.get

@JvmAsync
class DedicatedSendingIpsApi internal constructor(
    internal val http: LoopsHttp,
) {

    /**
     * Lists the team's dedicated sending IP addresses (GET dedicated-sending-ips).
     * Returns an array of IP address strings, or an empty list if none exist.
     */
    suspend fun list(): List<String> =
        http.execute {
            get("dedicated-sending-ips").body()
        }
}
