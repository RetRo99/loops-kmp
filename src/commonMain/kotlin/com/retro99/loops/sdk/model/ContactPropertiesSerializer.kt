package com.retro99.loops.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal object ContactPropertiesSerializer {

    private val mapSerializer = MapSerializer(
        String.serializer(),
        LoopsValueSerializer,
    )

    fun merge(known: JsonObject, custom: Map<String, LoopsValue>, json: Json): JsonObject {
        if (custom.isEmpty()) return known
        val customObject = json.encodeToJsonElement(mapSerializer, custom).jsonObject
        // Known fields are authoritative and stay first: drop any custom key that collides with
        // a typed field so it can't emit a clobbered value that `extract` then drops on the way
        // back (which would break round-trips). Known fields keep their declaration order.
        val knownMap = known.toMap()
        return JsonObject(knownMap + customObject.filterKeys { it !in knownMap })
    }

    fun extract(
        full: JsonObject,
        knownKeys: Set<String>,
        json: Json,
    ): Map<String, LoopsValue> {
        val extra = full.filterKeys { it !in knownKeys }
        if (extra.isEmpty()) return emptyMap()
        return json.decodeFromJsonElement(mapSerializer, JsonObject(extra))
    }
}
