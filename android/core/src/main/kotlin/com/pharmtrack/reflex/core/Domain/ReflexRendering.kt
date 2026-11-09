package com.pharmtrack.reflex.core.Domain

import com.pharmtrack.reflex.core.Support.ReflexValue

/**
 * Entity rendering for the emitted stream.
 *
 * Nil optionals are OMITTED here (never emitted as null) because these objects mirror the
 * persisted file, where `encodeIfPresent` drops the key — runner.yaml#canonicalization.
 * Machine state is the opposite: it emits explicit nulls for `loadError`, `editingId`,
 * `cutoff` and friends, because those are state fields, not file fields.
 */

val Appointment.reflexValue: ReflexValue
    get() {
        val members: MutableMap<String, ReflexValue> = linkedMapOf(
            "id" to ReflexValue.uuid(id),
            "doctorName" to ReflexValue.Text(doctorName),
            "specialty" to ReflexValue.Text(specialty),
            "type" to ReflexValue.Text(type),
            "date" to ReflexValue.instant(date),
            "location" to ReflexValue.Text(location),
            "notify" to ReflexValue.of(notify)
        )
        if (notes != null) members["notes"] = ReflexValue.Text(notes)
        return ReflexValue.ObjectVal(members)
    }

val Medication.reflexValue: ReflexValue
    get() {
        val members: MutableMap<String, ReflexValue> = linkedMapOf(
            "id" to ReflexValue.uuid(id),
            "name" to ReflexValue.Text(name),
            "dosage" to ReflexValue.Text(dosage),
            "instructions" to ReflexValue.Text(instructions),
            "schedule" to ReflexValue.Text(schedule),
            "stock" to ReflexValue.of(stock),
            "active" to ReflexValue.of(active)
        )
        if (nextDose != null) members["nextDose"] = ReflexValue.instant(nextDose)
        return ReflexValue.ObjectVal(members)
    }

val Consultation.reflexValue: ReflexValue
    get() = ReflexValue.of(
        mapOf(
            "id" to ReflexValue.uuid(id),
            "doctorName" to ReflexValue.Text(doctorName),
            "specialty" to ReflexValue.Text(specialty),
            "date" to ReflexValue.instant(date),
            "notes" to ReflexValue.Text(notes),
            "linkedMedications" to ReflexValue.uuids(linkedMedications)
        )
    )

val Vital.reflexValue: ReflexValue
    get() {
        val members: MutableMap<String, ReflexValue> = linkedMapOf(
            "id" to ReflexValue.uuid(id),
            "type" to ReflexValue.Text(type.rawValue),
            "value" to ReflexValue.of(value),
            "date" to ReflexValue.instant(date)
        )
        if (value2 != null) members["value2"] = ReflexValue.of(value2)
        if (notes != null) members["notes"] = ReflexValue.Text(notes)
        return ReflexValue.ObjectVal(members)
    }

val Log.reflexValue: ReflexValue
    get() {
        val members: MutableMap<String, ReflexValue> = linkedMapOf(
            "id" to ReflexValue.uuid(id),
            "type" to ReflexValue.Text(type),
            "description" to ReflexValue.Text(description),
            "date" to ReflexValue.instant(date),
            "source" to ReflexValue.Text(source)
        )
        if (medicationId != null) members["medicationId"] = ReflexValue.uuid(medicationId)
        return ReflexValue.ObjectVal(members)
    }

val NotificationSettings.reflexValue: ReflexValue
    get() = ReflexValue.of(
        mapOf(
            "allNotificationsEnabled" to ReflexValue.of(allNotificationsEnabled),
            "medicationsEnabled" to ReflexValue.of(medicationsEnabled),
            "appointmentsEnabled" to ReflexValue.of(appointmentsEnabled),
            "consultationsEnabled" to ReflexValue.of(consultationsEnabled),
            "lowStockEnabled" to ReflexValue.of(lowStockEnabled),
            "lowStockThreshold" to ReflexValue.of(lowStockThreshold),
            "medicationReminderTime" to ReflexValue.Text(medicationReminderTime.rawValue),
            "appointmentReminderTime" to ReflexValue.Text(appointmentReminderTime.rawValue)
        )
    )

val MedicalData.reflexValue: ReflexValue
    get() = ReflexValue.of(
        mapOf(
            "appointments" to ReflexValue.of(appointments.map { it.reflexValue }),
            "medications" to ReflexValue.of(medications.map { it.reflexValue }),
            "consultations" to ReflexValue.of(consultations.map { it.reflexValue }),
            "vitals" to ReflexValue.of(vitals.map { it.reflexValue }),
            "logs" to ReflexValue.of(logs.map { it.reflexValue }),
            "notificationSettings" to notificationSettings.reflexValue
        )
    )
