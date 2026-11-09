package com.pharmtrack.reflex.core.Support

import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Consultation
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.NotificationSettings
import com.pharmtrack.reflex.core.Domain.Vital
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * Parses a UUID in the canonical 8-4-4-4-12 form, case-insensitively.
 *
 * `java.util.UUID.fromString` is laxer than Swift's `UUID(uuidString:)`: it accepts
 * "1-1-1-1-1" and a leading sign, both of which Swift rejects. The shape is validated here
 * so a malformed identifier in a journey is a usage error (exit 2) on both platforms.
 */
fun parseUUID(text: String): UUID? {
    if (text.length != 36) return null
    for (index in 0 until 36) {
        val character = text[index]
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (character != '-') return null
        } else if (!character.isHexDigit()) {
            return null
        }
    }
    return try {
        UUID.fromString(text)
    } catch (error: IllegalArgumentException) {
        null
    }
}

/** Encodes UUIDs UPPERCASE, matching Swift's `UUID.uuidString`. */
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.pharmtrack.reflex.core.Support.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) =
        encoder.encodeString(ReflexValue.uuidString(value))

    override fun deserialize(decoder: Decoder): UUID {
        val text = decoder.decodeString()
        return parseUUID(text)
            ?: throw SerializationException("Expected a UUID string, got $text")
    }
}

/**
 * The blob codec with Swift's leniency rules.
 *
 * The generated serializer cannot express "a missing key and an explicit null both fall back
 * to the default, but a PRESENT settings object must be complete", so the six top-level keys
 * are decoded element by element here.
 */
@OptIn(ExperimentalSerializationApi::class)
object MedicalDataSerializer : KSerializer<MedicalData> {

    private val appointments = ListSerializer(Appointment.serializer())
    private val medications = ListSerializer(Medication.serializer())
    private val consultations = ListSerializer(Consultation.serializer())
    private val vitals = ListSerializer(Vital.serializer())
    private val logs = ListSerializer(Log.serializer())
    private val settings = NotificationSettings.serializer()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.pharmtrack.reflex.core.Domain.MedicalData") {
            element("appointments", appointments.descriptor)
            element("medications", medications.descriptor)
            element("consultations", consultations.descriptor)
            element("vitals", vitals.descriptor)
            element("logs", logs.descriptor)
            element("notificationSettings", settings.descriptor)
        }

    override fun deserialize(decoder: Decoder): MedicalData {
        val composite = decoder.beginStructure(descriptor)
        var appointmentsValue = emptyList<Appointment>()
        var medicationsValue = emptyList<Medication>()
        var consultationsValue = emptyList<Consultation>()
        var vitalsValue = emptyList<Vital>()
        var logsValue = emptyList<Log>()
        var settingsValue = NotificationSettings.default()

        while (true) {
            val index = composite.decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            when (descriptor.getElementName(index)) {
                "appointments" ->
                    appointmentsValue = composite.decodeNullableSerializableElement(descriptor, index, appointments)
                        ?: emptyList()

                "medications" ->
                    medicationsValue = composite.decodeNullableSerializableElement(descriptor, index, medications)
                        ?: emptyList()

                "consultations" ->
                    consultationsValue = composite.decodeNullableSerializableElement(descriptor, index, consultations)
                        ?: emptyList()

                "vitals" ->
                    vitalsValue = composite.decodeNullableSerializableElement(descriptor, index, vitals)
                        ?: emptyList()

                "logs" ->
                    logsValue = composite.decodeNullableSerializableElement(descriptor, index, logs)
                        ?: emptyList()

                "notificationSettings" ->
                    settingsValue = composite.decodeNullableSerializableElement(descriptor, index, settings)
                        ?: NotificationSettings.default()
            }
        }
        composite.endStructure(descriptor)

        return MedicalData(
            appointments = appointmentsValue,
            medications = medicationsValue,
            consultations = consultationsValue,
            vitals = vitalsValue,
            logs = logsValue,
            notificationSettings = settingsValue
        )
    }

    override fun serialize(encoder: Encoder, value: MedicalData) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, appointments, value.appointments)
        composite.encodeSerializableElement(descriptor, 1, medications, value.medications)
        composite.encodeSerializableElement(descriptor, 2, consultations, value.consultations)
        composite.encodeSerializableElement(descriptor, 3, vitals, value.vitals)
        composite.encodeSerializableElement(descriptor, 4, logs, value.logs)
        composite.encodeSerializableElement(descriptor, 5, settings, value.notificationSettings)
        composite.endStructure(descriptor)
    }
}
