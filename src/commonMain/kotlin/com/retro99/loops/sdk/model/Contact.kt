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

/**
 * A contact as returned by `contacts/find`.
 *
 * Loops merges arbitrary **custom properties** as top-level siblings of the known fields (the same
 * shape requests use). Those unknown keys are collected into [customProperties] on decode so callers
 * can read back the values they set via [CreateContactRequest] / [UpdateContactRequest]; the known
 * fields below are decoded as typed properties.
 */
@Serializable(with = ContactSerializer::class)
data class Contact(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val source: String? = null,
    val subscribed: Boolean? = null,
    val userGroup: String? = null,
    val userId: String? = null,
    val mailingLists: Map<String, Boolean>? = null,
    val optInStatus: String? = null,
    val customProperties: Map<String, LoopsValue> = emptyMap(),
)

internal object ContactSerializer : KSerializer<Contact> {

    // Derived from the known-fields descriptor so it can never drift: every key here is a known
    // field, so `deserialize` collects only the remaining top-level keys as custom properties.
    private val knownKeys: Set<String> =
        ContactKnownFields.serializer().descriptor.let { descriptor ->
            (0 until descriptor.elementsCount)
                .map { descriptor.getElementName(it) }
                .toSet()
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Contact")

    override fun serialize(encoder: Encoder, value: Contact) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("Contact can only be serialized with JSON")
        val known = jsonEncoder.json
            .encodeToJsonElement(ContactKnownFields.serializer(), value.toKnownFields())
            .jsonObject
        val merged = ContactPropertiesSerializer.merge(
            known, value.customProperties, jsonEncoder.json,
        )
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): Contact {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("Contact can only be deserialized with JSON")
        val root = jsonDecoder.decodeJsonElement().jsonObject
        val known = jsonDecoder.json.decodeFromJsonElement<ContactKnownFields>(root)
        val custom = ContactPropertiesSerializer.extract(root, knownKeys, jsonDecoder.json)
        return known.toContact(custom)
    }

    private fun Contact.toKnownFields() = ContactKnownFields(
        id = id,
        email = email,
        firstName = firstName,
        lastName = lastName,
        source = source,
        subscribed = subscribed,
        userGroup = userGroup,
        userId = userId,
        mailingLists = mailingLists,
        optInStatus = optInStatus,
    )

    private fun ContactKnownFields.toContact(
        customProperties: Map<String, LoopsValue>,
    ) = Contact(
        id = id,
        email = email,
        firstName = firstName,
        lastName = lastName,
        source = source,
        subscribed = subscribed,
        userGroup = userGroup,
        userId = userId,
        mailingLists = mailingLists,
        optInStatus = optInStatus,
        customProperties = customProperties,
    )
}

@Serializable
private data class ContactKnownFields(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("source")
    val source: String? = null,
    @SerialName("subscribed")
    val subscribed: Boolean? = null,
    @SerialName("userGroup")
    val userGroup: String? = null,
    @SerialName("userId")
    val userId: String? = null,
    @SerialName("mailingLists")
    val mailingLists: Map<String, Boolean>? = null,
    @SerialName("optInStatus")
    val optInStatus: String? = null,
)
