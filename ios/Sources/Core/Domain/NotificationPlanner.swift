import Foundation

public struct ScheduledNotification: Equatable {
    public let identifier: String
    public let channel: String
    public let title: String
    public let body: String
    public let triggerAt: Instant?

    public init(identifier: String, channel: String, title: String, body: String, triggerAt: Instant?) {
        self.identifier = identifier
        self.channel = channel
        self.title = title
        self.body = body
        self.triggerAt = triggerAt
    }

    /// Low-stock alerts are immediate: triggerAt is nil and the KEY IS OMITTED, never
    /// emitted as null (runner.yaml#canonicalization).
    public var reflexValue: ReflexValue {
        var object: [String: ReflexValue] = [
            "identifier": .string(identifier),
            "channel": .string(channel),
            "title": .string(title),
            "body": .string(body),
        ]
        if let triggerAt { object["triggerAt"] = .instant(triggerAt) }
        return .object(object)
    }
}

/// D1 / D8 / D9. Pure: no clock, no I/O, no platform calls. `now` is an argument.
/// The output is always the COMPLETE desired set, never a delta — Android's incremental
/// path is a bug and is not ported.
public enum NotificationPlanner {
    public static func plan(data: MedicalData, now: Instant) -> [ScheduledNotification] {
        let settings = data.notificationSettings
        guard settings.allNotificationsEnabled else { return [] }

        var planned: [ScheduledNotification] = []

        if settings.medicationsEnabled {
            let offset = settings.medicationReminderTime.offsetSeconds
            for medication in data.medications {
                guard medication.active, let nextDose = medication.nextDose else { continue }
                let trigger = nextDose.addingSeconds(offset)
                guard trigger > now else { continue }
                planned.append(
                    ScheduledNotification(
                        identifier: "med-\(medication.id.uuidString)",
                        channel: "medication_reminders",
                        title: "Medication Reminder",
                        body: "Time to take \(medication.name) (\(medication.dosage)). \(medication.instructions)",
                        triggerAt: trigger
                    )
                )
            }
        }

        if settings.lowStockEnabled {
            for medication in data.medications
            where Selectors.isLowStock(medication: medication, settings: settings) {
                planned.append(
                    ScheduledNotification(
                        identifier: "lowstock-\(medication.id.uuidString)",
                        channel: "low_stock_alerts",
                        title: "Low Medication Stock",
                        body: "You only have \(medication.stock) doses left of \(medication.name).",
                        triggerAt: nil
                    )
                )
            }
        }

        // D9: consultations read appointmentReminderTime. The default "1 day before"
        // reproduces the legacy fixed -86400s exactly, so default behavior is unchanged.
        let appointmentOffset = settings.appointmentReminderTime.offsetSeconds

        if settings.appointmentsEnabled {
            for appointment in data.appointments where appointment.notify && appointment.date > now {
                let trigger = appointment.date.addingSeconds(appointmentOffset)
                guard trigger > now else { continue }
                planned.append(
                    ScheduledNotification(
                        identifier: "appt-\(appointment.id.uuidString)",
                        channel: "appointment_reminders",
                        title: "Upcoming Appointment",
                        body: "You have an appointment with \(appointment.doctorName) at \(appointment.location).",
                        triggerAt: trigger
                    )
                )
            }
        }

        if settings.consultationsEnabled {
            for consultation in data.consultations where consultation.date > now {
                let trigger = consultation.date.addingSeconds(appointmentOffset)
                guard trigger > now else { continue }
                planned.append(
                    ScheduledNotification(
                        identifier: "consult-\(consultation.id.uuidString)",
                        channel: "consultation_reminders",
                        title: "Upcoming Consultation",
                        body: "You have a consultation with \(consultation.doctorName).",
                        triggerAt: trigger
                    )
                )
            }
        }

        return planned
    }
}