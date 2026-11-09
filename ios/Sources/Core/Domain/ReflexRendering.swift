import Foundation

/// Entity rendering for the emitted stream.
///
/// Nil optionals are OMITTED here (never emitted as null) because these objects mirror the
/// persisted file, where `encodeIfPresent` drops the key — runner.yaml#canonicalization.
/// Machine state is the opposite: it emits explicit nulls for `loadError`, `editingId`,
/// `cutoff` and friends, because those are state fields, not file fields.
extension Appointment {
    public var reflexValue: ReflexValue {
        var object: [String: ReflexValue] = [
            "id": .uuid(id),
            "doctorName": .string(doctorName),
            "specialty": .string(specialty),
            "type": .string(type),
            "date": .instant(date),
            "location": .string(location),
            "notify": .bool(notify),
        ]
        if let notes { object["notes"] = .string(notes) }
        return .object(object)
    }
}

extension Medication {
    public var reflexValue: ReflexValue {
        var object: [String: ReflexValue] = [
            "id": .uuid(id),
            "name": .string(name),
            "dosage": .string(dosage),
            "instructions": .string(instructions),
            "schedule": .string(schedule),
            "stock": .int(stock),
            "active": .bool(active),
        ]
        if let nextDose { object["nextDose"] = .instant(nextDose) }
        return .object(object)
    }
}

extension Consultation {
    public var reflexValue: ReflexValue {
        .object([
            "id": .uuid(id),
            "doctorName": .string(doctorName),
            "specialty": .string(specialty),
            "date": .instant(date),
            "notes": .string(notes),
            "linkedMedications": .uuids(linkedMedications),
        ])
    }
}

extension Vital {
    public var reflexValue: ReflexValue {
        var object: [String: ReflexValue] = [
            "id": .uuid(id),
            "type": .string(type.rawValue),
            "value": .double(value),
            "date": .instant(date),
        ]
        if let value2 { object["value2"] = .double(value2) }
        if let notes { object["notes"] = .string(notes) }
        return .object(object)
    }
}

extension Log {
    public var reflexValue: ReflexValue {
        var object: [String: ReflexValue] = [
            "id": .uuid(id),
            "type": .string(type),
            "description": .string(description),
            "date": .instant(date),
            "source": .string(source),
        ]
        if let medicationId { object["medicationId"] = .uuid(medicationId) }
        return .object(object)
    }
}

extension NotificationSettings {
    public var reflexValue: ReflexValue {
        .object([
            "allNotificationsEnabled": .bool(allNotificationsEnabled),
            "medicationsEnabled": .bool(medicationsEnabled),
            "appointmentsEnabled": .bool(appointmentsEnabled),
            "consultationsEnabled": .bool(consultationsEnabled),
            "lowStockEnabled": .bool(lowStockEnabled),
            "lowStockThreshold": .int(lowStockThreshold),
            "medicationReminderTime": .string(medicationReminderTime.rawValue),
            "appointmentReminderTime": .string(appointmentReminderTime.rawValue),
        ])
    }
}

extension MedicalData {
    public var reflexValue: ReflexValue {
        .object([
            "appointments": .array(appointments.map(\.reflexValue)),
            "medications": .array(medications.map(\.reflexValue)),
            "consultations": .array(consultations.map(\.reflexValue)),
            "vitals": .array(vitals.map(\.reflexValue)),
            "logs": .array(logs.map(\.reflexValue)),
            "notificationSettings": notificationSettings.reflexValue,
        ])
    }
}