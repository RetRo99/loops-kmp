package com.retro99.loops.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable(with = UpdateContactRequestSerializer::class)
data class UpdateContactRequest(
    val email: String? = null,
    val userId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val subscribed: Boolean? = null,
    val userGroup: String? = null,
    val mailingLists: Map<String, Boolean>? = null,
    val customProperties: Map<String, LoopsValue> = emptyMap(),
)

internal object UpdateContactRequestSerializer : KSerializer<UpdateContactRequest> {

    private val knownKeys: Set<String> =
        UpdateContactKnownFields.serializer().descriptor.let { descriptor ->
            (0 until descriptor.elementsCount)
                .map { descriptor.getElementName(it) }
                .toSet()
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("UpdateContactRequest")

    override fun serialize(encoder: Encoder, value: UpdateContactRequest) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("UpdateContactRequest can only be serialized with JSON")
        val known = jsonEncoder.json
            .encodeToJsonElement(UpdateContactKnownFields.serializer(), value.toKnownFields())
            .jsonObject
        val merged = ContactPropertiesSerializer.merge(
            known, value.customProperties, jsonEncoder.json,
        )
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): UpdateContactRequest {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("UpdateContactRequest can only be deserialized with JSON")
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val known = jsonDecoder.json.decodeFromJsonElement<UpdateContactKnownFields>(root)
        val custom = ContactPropertiesSerializer.extract(root, knownKeys, jsonDecoder.json)
        return known.toRequest(custom)
    }

    private fun UpdateContactRequest.toKnownFields() = UpdateContactKnownFields(
        email = email,
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        subscribed = subscribed,
        userGroup = userGroup,
        mailingLists = mailingLists,
    )

    private fun UpdateContactKnownFields.toRequest(
        customProperties: Map<String, LoopsValue>,
    ) = UpdateContactRequest(
        email = email,
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        subscribed = subscribed,
        userGroup = userGroup,
        mailingLists = mailingLists,
        customProperties = customProperties,
    )
}

@Serializable
private data class UpdateContactKnownFields(
    @SerialName("email")
    val email: String? = null,
    @SerialName("userId")
    val userId: String? = null,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("subscribed")
    val subscribed: Boolean? = null,
    @SerialName("userGroup")
    val userGroup: String? = null,
    @SerialName("mailingLists")
    val mailingLists: Map<String, Boolean>? = null,
)
