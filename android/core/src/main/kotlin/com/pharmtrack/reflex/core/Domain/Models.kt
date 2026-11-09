package com.pharmtrack.reflex.core.Domain

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalDataSerializer
import com.pharmtrack.reflex.core.Support.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Persisted entity shapes. Field sets and optionality mirror
 * specs/contracts/medical_data.schema.json exactly.
 *
 * The leniency asymmetry persistence.yaml requires comes from two rules applied consistently:
 *
 *  - a NON-nullable property has no default, so a missing key or an explicit null throws;
 *  - a NULLABLE property defaults to null, so a missing key and an explicit null both
 *    decode to null.
 *
 * That is the Kotlin spelling of Swift's `decode()` versus `decodeIfPresent()`.
 */
@Serializable
data class Appointment(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val doctorName: String,
    val specialty: String,
    val type: String,
    val date: Instant,
    val location: String,
    val notes: String? = null,
    val notify: Boolean
)

/**
 * `schedule` stays a String, not an enum: Q3 requires a legacy file holding "Night" or
 * "morning" to decode without dropping the medication, and grouping is case-sensitive
 * string equality. `MedicationSchedule` is the form picker's enum only.
 */
@Serializable
data class Medication(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val dosage: String,
    val instructions: String,
    val schedule: String,
    val stock: Long,
    val active: Boolean,
    val nextDose: Instant? = null
)

/** The form picker's schedule enum. Its raw values are the persisted strings. */
enum class MedicationSchedule(val rawValue: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening");

    companion object {
        fun fromRaw(raw: String): MedicationSchedule? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class Consultation(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val doctorName: String,
    val specialty: String,
    val date: Instant,
    val notes: String,
    val linkedMedications: List<@Serializable(with = UUIDSerializer::class) UUID>
)

@Serializable
enum class VitalType(val rawValue: String) {
    @SerialName("weight")
    WEIGHT("weight"),

    @SerialName("bloodPressure")
    BLOOD_PRESSURE("bloodPressure"),

    @SerialName("bloodSugar")
    BLOOD_SUGAR("bloodSugar");

    companion object {
        fun fromRaw(raw: String): VitalType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class Vital(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val type: VitalType,
    val value: Double,
    val value2: Double? = null,
    val date: Instant,
    val notes: String? = null
)

@Serializable
data class Log(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val type: String,
    val description: String,
    val date: Instant,
    val source: String,
    val medicationId: @Serializable(with = UUIDSerializer::class) UUID? = null
)

@Serializable
enum class ReminderTime(val rawValue: String, val offsetSeconds: Double) {
    @SerialName("At time of event")
    AT_TIME_OF_EVENT("At time of event", 0.0),

    @SerialName("5 minutes before")
    FIVE_MINUTES_BEFORE("5 minutes before", -300.0),

    @SerialName("1 hour before")
    ONE_HOUR_BEFORE("1 hour before", -3_600.0),

    @SerialName("1 day before")
    ONE_DAY_BEFORE("1 day before", -86_400.0);

    companion object {
        fun fromRaw(raw: String): ReminderTime? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * persistence.yaml#leniency: all eight fields are REQUIRED once `notificationSettings` is
 * present. The properties therefore carry no defaults — a partial object is rejected rather
 * than silently defaulted. `default()` is the domain-level factory, not a decode default.
 */
@Serializable
data class NotificationSettings(
    val allNotificationsEnabled: Boolean,
    val medicationsEnabled: Boolean,
    val appointmentsEnabled: Boolean,
    val consultationsEnabled: Boolean,
    val lowStockEnabled: Boolean,
    val lowStockThreshold: Long,
    val medicationReminderTime: ReminderTime,
    val appointmentReminderTime: ReminderTime
) {
    companion object {
        fun default(): NotificationSettings = NotificationSettings(
            allNotificationsEnabled = true,
            medicationsEnabled = true,
            appointmentsEnabled = true,
            consultationsEnabled = true,
            lowStockEnabled = true,
            lowStockThreshold = 5,
            medicationReminderTime = ReminderTime.AT_TIME_OF_EVENT,
            appointmentReminderTime = ReminderTime.ONE_DAY_BEFORE
        )
    }
}

/**
 * The whole persisted blob.
 *
 * Every top-level key is optional on read — a missing key AND an explicit null both fall
 * back to the empty collection / default settings — while a PRESENT `notificationSettings`
 * must be complete. The custom serializer exists precisely for that asymmetry: the
 * generated one cannot distinguish "absent" from "null" for a defaulted property.
 */
@Serializable(with = MedicalDataSerializer::class)
data class MedicalData(
    val appointments: List<Appointment> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val consultations: List<Consultation> = emptyList(),
    val vitals: List<Vital> = emptyList(),
    val logs: List<Log> = emptyList(),
    val notificationSettings: NotificationSettings = NotificationSettings.default()
) {
    fun appointment(id: UUID): Appointment? = appointments.firstOrNull { it.id == id }

    fun medication(id: UUID): Medication? = medications.firstOrNull { it.id == id }

    fun consultation(id: UUID): Consultation? = consultations.firstOrNull { it.id == id }

    fun vital(id: UUID): Vital? = vitals.firstOrNull { it.id == id }
}
