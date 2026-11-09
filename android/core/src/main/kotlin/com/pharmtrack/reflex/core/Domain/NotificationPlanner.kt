package com.pharmtrack.reflex.core.Domain

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue

data class ScheduledNotification(
    val identifier: String,
    val channel: String,
    val title: String,
    val body: String,
    val triggerAt: Instant?
) {
    /**
     * Low-stock alerts are immediate: triggerAt is nil and the KEY IS OMITTED, never
     * emitted as null (runner.yaml#canonicalization).
     */
    val reflexValue: ReflexValue
        get() {
            val members: MutableMap<String, ReflexValue> = linkedMapOf(
                "identifier" to ReflexValue.Text(identifier),
                "channel" to ReflexValue.Text(channel),
                "title" to ReflexValue.Text(title),
                "body" to ReflexValue.Text(body)
            )
            if (triggerAt != null) members["triggerAt"] = ReflexValue.instant(triggerAt)
            return ReflexValue.ObjectVal(members)
        }
}

/**
 * D1 / D8 / D9. Pure: no clock, no I/O, no platform calls. `now` is an argument.
 * The output is always the COMPLETE desired set, never a delta — Android's incremental
 * path is a bug and is not ported.
 */
object NotificationPlanner {

    fun plan(data: MedicalData, now: Instant): List<ScheduledNotification> {
        val settings = data.notificationSettings
        if (!settings.allNotificationsEnabled) return emptyList()

        val planned = mutableListOf<ScheduledNotification>()

        if (settings.medicationsEnabled) {
            val offset = settings.medicationReminderTime.offsetSeconds
            for (medication in data.medications) {
                val nextDose = medication.nextDose ?: continue
                if (!medication.active) continue
                val trigger = nextDose.addingSeconds(offset)
                if (trigger <= now) continue
                planned.add(
                    ScheduledNotification(
                        identifier = "med-${ReflexValue.uuidString(medication.id)}",
                        channel = "medication_reminders",
                        title = "Medication Reminder",
                        body = "Time to take ${medication.name} (${medication.dosage}). ${medication.instructions}",
                        triggerAt = trigger
                    )
                )
            }
        }

        if (settings.lowStockEnabled) {
            for (medication in data.medications) {
                if (!Selectors.isLowStock(medication, settings)) continue
                planned.add(
                    ScheduledNotification(
                        identifier = "lowstock-${ReflexValue.uuidString(medication.id)}",
                        channel = "low_stock_alerts",
                        title = "Low Medication Stock",
                        body = "You only have ${medication.stock} doses left of ${medication.name}.",
                        triggerAt = null
                    )
                )
            }
        }

        // D9: consultations read appointmentReminderTime. The default "1 day before"
        // reproduces the legacy fixed -86400s exactly, so default behavior is unchanged.
        val appointmentOffset = settings.appointmentReminderTime.offsetSeconds

        if (settings.appointmentsEnabled) {
            for (appointment in data.appointments) {
                if (!appointment.notify || appointment.date <= now) continue
                val trigger = appointment.date.addingSeconds(appointmentOffset)
                if (trigger <= now) continue
                planned.add(
                    ScheduledNotification(
                        identifier = "appt-${ReflexValue.uuidString(appointment.id)}",
                        channel = "appointment_reminders",
                        title = "Upcoming Appointment",
                        body = "You have an appointment with ${appointment.doctorName} at ${appointment.location}.",
                        triggerAt = trigger
                    )
                )
            }
        }

        if (settings.consultationsEnabled) {
            for (consultation in data.consultations) {
                if (consultation.date <= now) continue
                val trigger = consultation.date.addingSeconds(appointmentOffset)
                if (trigger <= now) continue
                planned.add(
                    ScheduledNotification(
                        identifier = "consult-${ReflexValue.uuidString(consultation.id)}",
                        channel = "consultation_reminders",
                        title = "Upcoming Consultation",
                        body = "You have a consultation with ${consultation.doctorName}.",
                        triggerAt = trigger
                    )
                )
            }
        }

        return planned
    }
}
