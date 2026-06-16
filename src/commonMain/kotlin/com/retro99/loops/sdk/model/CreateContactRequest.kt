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

    // Derived from the known-fields descriptor so it can never drift from the actual fields:
    // every key here is a known field, so `deserialize` collects only the rest as custom props.
    private val knownKeys: Set<String> =
        CreateContactKnownFields.serializer().descriptor.let { descriptor ->
            (0 until descriptor.elementsCount)
                .map { descriptor.getElementName(it) }
                .toSet()
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CreateContactRequest")

    override fun serialize(encoder: Encoder, value: CreateContactRequest) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("CreateContactRequest can only be serialized with JSON")
        // Let the typed known-fields model emit the base object (nulls dropped via
        // explicitNulls=false), then flatten the custom properties as top-level siblings.
        val known = jsonEncoder.json
            .encodeToJsonElement(CreateContactKnownFields.serializer(), value.toKnownFields())
            .jsonObject
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
        return known.toRequest(custom)
    }

    private fun CreateContactRequest.toKnownFields() = CreateContactKnownFields(
        email = email,
        firstName = firstName,
        lastName = lastName,
        subscribed = subscribed,
        userGroup = userGroup,
        userId = userId,
        mailingLists = mailingLists,
    )

    private fun CreateContactKnownFields.toRequest(
        customProperties: Map<String, LoopsValue>,
    ) = CreateContactRequest(
        email = email,
        firstName = firstName,
        lastName = lastName,
        subscribed = subscribed,
        userGroup = userGroup,
        userId = userId,
        mailingLists = mailingLists,
        customProperties = customProperties,
    )
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
