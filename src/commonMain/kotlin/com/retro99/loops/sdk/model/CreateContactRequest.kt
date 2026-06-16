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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable(with = CreateContactRequestSerializer::class)
data class CreateContactRequest(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val subscribed: Boolean? = null,
    val userGroup: String? = null,
    val userId: String? = null,
    val mailingLists: Map<String, Boolean>? = null,
    val customProperties: Map<String, LoopsValue> = emptyMap(),
)

internal object CreateContactRequestSerializer : KSerializer<CreateContactRequest> {

    private val knownKeys = setOf(
        "email", "firstName", "lastName", "subscribed",
        "userGroup", "userId", "mailingLists",
    )

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CreateContactRequest")

    override fun serialize(encoder: Encoder, value: CreateContactRequest) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("CreateContactRequest can only be serialized with JSON")
        val known = buildJsonObject {
            put("email", value.email)
            value.firstName?.let { put("firstName", it) }
            value.lastName?.let { put("lastName", it) }
            value.subscribed?.let { put("subscribed", it) }
            value.userGroup?.let { put("userGroup", it) }
            value.userId?.let { put("userId", it) }
            value.mailingLists?.let {
                put("mailingLists", jsonEncoder.json.encodeToJsonElement(it))
            }
        }
        val merged = ContactPropertiesSerializer.merge(
            known, value.customProperties, jsonEncoder.json,
        )
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): CreateContactRequest {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("CreateContactRequest can only be deserialized with JSON")
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val known = jsonDecoder.json.decodeFromJsonElement<CreateContactKnownFields>(root)
        val custom = ContactPropertiesSerializer.extract(root, knownKeys, jsonDecoder.json)
        return CreateContactRequest(
            email = known.email,
            firstName = known.firstName,
            lastName = known.lastName,
            subscribed = known.subscribed,
            userGroup = known.userGroup,
            userId = known.userId,
            mailingLists = known.mailingLists,
            customProperties = custom,
        )
    }
}

@Serializable
private data class CreateContactKnownFields(
    @SerialName("email")
    val email: String,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("subscribed")
    val subscribed: Boolean? = null,
    @SerialName("userGroup")
    val userGroup: String? = null,
    @SerialName("userId")
    val userId: String? = null,
    @SerialName("mailingLists")
    val mailingLists: Map<String, Boolean>? = null,
)
