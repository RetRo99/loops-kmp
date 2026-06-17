package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Theme(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    /**
     * The theme's style attributes. Keys use the same names as LMX `<Style>` tag attributes;
     * values arrive as either strings (colors) or numbers (sizes), both preserved by [LoopsValue].
     */
    @SerialName("styles")
    val styles: Map<String, LoopsValue> = emptyMap(),
    @SerialName("isDefault")
    val isDefault: Boolean? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
)
