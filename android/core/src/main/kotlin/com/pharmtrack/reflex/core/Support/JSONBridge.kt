package com.pharmtrack.reflex.core.Support

import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Consultation
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.NotificationSettings
import com.pharmtrack.reflex.core.Domain.Vital
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The real platform codec. The fixture MedicalStore adapter encodes through this on save
 * and decodes through it on load, so `store.saved` is decode(encode(x)) — a genuine
 * round-trip, never the in-memory object handed to save (runner.yaml#fixture_adapters).
 */
object MedicalCodec {

    /**
     * Swift's DecodingError descriptions and Kotlin's SerializationException messages can
     * never match, and `loadError` is part of the byte-compared stream. Both adapters emit
     * this fixed token instead of a platform exception string.
     */
    const val DECODE_FAILURE_TOKEN = "medical_data.json could not be decoded"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
        allowStructuredMapKeys = false
    }

    fun encode(data: MedicalData): ByteArray = json.encodeToString(MedicalData.serializer(), data).toByteArray()

    fun decode(bytes: ByteArray): MedicalData =
        json.decodeFromString(MedicalData.serializer(), bytes.toString(Charsets.UTF_8))
}

/**
 * Parses JSON into the canonical tree, keeping integers and doubles distinct so
 * canonicalization can honour runner.yaml's number rules.
 */
fun parseJSON(bytes: ByteArray): ReflexValue {
    val element = MedicalCodec.json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
    return fromJsonElement(element)
}

fun fromJsonElement(element: kotlinx.serialization.json.JsonElement): ReflexValue = when (element) {
    is JsonNull -> ReflexValue.Null
    is JsonPrimitive -> {
        if (element.isString) {
            ReflexValue.Text(element.content)
        } else {
            val flag = element.booleanOrNull
            if (flag != null) {
                ReflexValue.of(flag)
            } else {
                val body = element.content
                if (body.contains('.') || body.contains('e') || body.contains('E')) {
                    ReflexValue.DoubleNum(body.toDouble())
                } else {
                    val asLong = body.toLongOrNull()
                    if (asLong != null) ReflexValue.IntNum(asLong) else ReflexValue.DoubleNum(body.toDouble())
                }
            }
        }
    }

    is JsonArray -> ReflexValue.ArrayVal(element.map { fromJsonElement(it) })
    is JsonObject -> ReflexValue.ObjectVal(element.entries.associate { it.key to fromJsonElement(it.value) })
}

/**
 * Re-serializes the tree. `canonicalJSON` is itself valid JSON, so the merged fixture bytes
 * handed to the real decoder are deterministic on both platforms.
 */
val ReflexValue.jsonData: ByteArray
    get() = canonicalJSON.toByteArray(Charsets.UTF_8)
