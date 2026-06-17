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

@Serializable(with = EventRequestSerializer::class)
data class EventRequest(
    val eventName: String,
    val email: String? = null,
    val userId: String? = null,
    val eventProperties: Map<String, LoopsValue>? = null,
    val mailingLists: Map<String, Boolean>? = null,
    val customProperties: Map<String, LoopsValue> = emptyMap(),
)

internal object EventRequestSerializer : KSerializer<EventRequest> {

    private val knownKeys: Set<String> =
        EventRequestKnownFields.serializer().descriptor.let { descriptor ->
            (0 until descriptor.elementsCount)
                .map { descriptor.getElementName(it) }
                .toSet()
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("EventRequest")

    override fun serialize(encoder: Encoder, value: EventRequest) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EventRequest can only be serialized with JSON")
        val known = jsonEncoder.json
            .encodeToJsonElement(EventRequestKnownFields.serializer(), value.toKnownFields())
            .jsonObject
        val merged = ContactPropertiesSerializer.merge(
            known, value.customProperties, jsonEncoder.json,
        )
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): EventRequest {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("EventRequest can only be deserialized with JSON")
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val known = jsonDecoder.json.decodeFromJsonElement<EventRequestKnownFields>(root)
        val custom = ContactPropertiesSerializer.extract(root, knownKeys, jsonDecoder.json)
        return known.toRequest(custom)
    }

    private fun EventRequest.toKnownFields() = EventRequestKnownFields(
        eventName = eventName,
        email = email,
        userId = userId,
        eventProperties = eventProperties,
        mailingLists = mailingLists,
    )

    private fun EventRequestKnownFields.toRequest(
        customProperties: Map<String, LoopsValue>,
    ) = EventRequest(
        eventName = eventName,
        email = email,
        userId = userId,
        eventProperties = eventProperties,
        mailingLists = mailingLists,
        customProperties = customProperties,
    )
}

@Serializable
private data class EventRequestKnownFields(
    @SerialName("eventName")
    val eventName: String,
    @SerialName("email")
    val email: String? = null,
    @SerialName("userId")
    val userId: String? = null,
    @SerialName("eventProperties")
    val eventProperties: Map<String, LoopsValue>? = null,
    @SerialName("mailingLists")
    val mailingLists: Map<String, Boolean>? = null,
)
