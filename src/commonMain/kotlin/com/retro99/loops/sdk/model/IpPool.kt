package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IpAddress(
    @SerialName("ip")
    val ip: String,
    @SerialName("status")
    val status: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
)

@Serializable
data class IpPool(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("ips")
    val ips: List<IpAddress> = emptyList(),
)
