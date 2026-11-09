import Foundation

/// Persisted entity shapes. Field sets and optionality mirror
/// specs/contracts/medical_data.schema.json exactly; the synthesized Codable conformance
/// gives the leniency asymmetry persistence.yaml requires for free:
/// `decodeIfPresent` tolerates an explicit null on an optional and `encodeIfPresent`
/// omits the key when nil, while a required field throws on null or absence.
public struct Appointment: Codable, Equatable, Hashable, Identifiable {
    public var id: UUID
    public var doctorName: String
    public var specialty: String
    public var type: String
    public var date: Instant
    public var location: String
    public var notes: String?
    public var notify: Bool

    public init(
        id: UUID,
        doctorName: String,
        specialty: String,
        type: String,
        date: Instant,
        location: String,
        notes: String? = nil,
        notify: Bool
    ) {
        self.id = id
        self.doctorName = doctorName
        self.specialty = specialty
        self.type = type
        self.date = date
        self.location = location
        self.notes = notes
        self.notify = notify
    }
}

/// `schedule` stays a String, not an enum: Q3 requires a legacy file holding "Night" or
/// "morning" to decode without dropping the medication, and grouping is case-sensitive
/// string equality. `MedicationSchedule` is the form picker's enum only.
public struct Medication: Codable, Equatable, Hashable, Identifiable {
    public var id: UUID
    public var name: String
    public var dosage: String
    public var instructions: String
    public var schedule: String
    public var stock: Int
    public var active: Bool
    public var nextDose: Instant?

    public init(
        id: UUID,
        name: String,
        dosage: String,
        instructions: String,
        schedule: String,
        stock: Int,
        active: Bool,
        nextDose: Instant? = nil
    ) {
        self.id = id
        self.name = name
        self.dosage = dosage
        self.instructions = instructions
        self.schedule = schedule
        self.stock = stock
        self.active = active
        self.nextDose = nextDose
    }
}

public enum MedicationSchedule: String, CaseIterable, Codable {
    case morning = "Morning"
    case afternoon = "Afternoon"
    case evening = "Evening"
}

public struct Consultation: Codable, Equatable, Hashable, Identifiable {
    public var id: UUID
    public var doctorName: String
    public var specialty: String
    public var date: Instant
    public var notes: String
    public var linkedMedications: [UUID]

    public init(
        id: UUID,
        doctorName: String,
        specialty: String,
        date: Instant,
        notes: String,
        linkedMedications: [UUID]
    ) {
        self.id = id
        self.doctorName = doctorName
        self.specialty = specialty
        self.date = date
        self.notes = notes
        self.linkedMedications = linkedMedications
    }
}

public enum VitalType: String, CaseIterable, Codable {
    case weight
    case bloodPressure
    case bloodSugar
}

public struct Vital: Codable, Equatable, Hashable, Identifiable {
    public var id: UUID
    public var type: VitalType
    public var value: Double
    public var value2: Double?
    public var date: Instant
    public var notes: String?

    public init(
        id: UUID,
        type: VitalType,
        value: Double,
        value2: Double? = nil,
        date: Instant,
        notes: String? = nil
    ) {
        self.id = id
        self.type = type
        self.value = value
        self.value2 = value2
        self.date = date
        self.notes = notes
    }
}

public struct Log: Codable, Equatable, Hashable, Identifiable {
    public var id: UUID
    public var type: String
    public var description: String
    public var date: Instant
    public var source: String
    public var medicationId: UUID?

    public init(
        id: UUID,
        type: String,
        description: String,
        date: Instant,
        source: String,
        medicationId: UUID? = nil
    ) {
        self.id = id
        self.type = type
        self.description = description
        self.date = date
        self.source = source
        self.medicationId = medicationId
    }
}

public enum ReminderTime: String, CaseIterable, Codable {
    case atTimeOfEvent = "At time of event"
    case fiveMinutesBefore = "5 minutes before"
    case oneHourBefore = "1 hour before"
    case oneDayBefore = "1 day before"

    public var offsetSeconds: Double {
        switch self {
        case .atTimeOfEvent: return 0
        case .fiveMinutesBefore: return -300
        case .oneHourBefore: return -3_600
        case .oneDayBefore: return -86_400
        }
    }
}

public struct NotificationSettings: Codable, Equatable, Hashable {
    public var allNotificationsEnabled: Bool
    public var medicationsEnabled: Bool
    public var appointmentsEnabled: Bool
    public var consultationsEnabled: Bool
    public var lowStockEnabled: Bool
    public var lowStockThreshold: Int
    public var medicationReminderTime: ReminderTime
    public var appointmentReminderTime: ReminderTime

    public init(
        allNotificationsEnabled: Bool = true,
        medicationsEnabled: Bool = true,
        appointmentsEnabled: Bool = true,
        consultationsEnabled: Bool = true,
        lowStockEnabled: Bool = true,
        lowStockThreshold: Int = 5,
        medicationReminderTime: ReminderTime = .atTimeOfEvent,
        appointmentReminderTime: ReminderTime = .oneDayBefore
    ) {
        self.allNotificationsEnabled = allNotificationsEnabled
        self.medicationsEnabled = medicationsEnabled
        self.appointmentsEnabled = appointmentsEnabled
        self.consultationsEnabled = consultationsEnabled
        self.lowStockEnabled = lowStockEnabled
        self.lowStockThreshold = lowStockThreshold
        self.medicationReminderTime = medicationReminderTime
        self.appointmentReminderTime = appointmentReminderTime
    }

    public static let `default` = NotificationSettings()
}

public struct MedicalData: Codable, Equatable {
    public var appointments: [Appointment]
    public var medications: [Medication]
    public var consultations: [Consultation]
    public var vitals: [Vital]
    public var logs: [Log]
    public var notificationSettings: NotificationSettings

    public init(
        appointments: [Appointment] = [],
        medications: [Medication] = [],
        consultations: [Consultation] = [],
        vitals: [Vital] = [],
        logs: [Log] = [],
        notificationSettings: NotificationSettings = .default
    ) {
        self.appointments = appointments
        self.medications = medications
        self.consultations = consultations
        self.vitals = vitals
        self.logs = logs
        self.notificationSettings = notificationSettings
    }

    private enum CodingKeys: String, CodingKey {
        case appointments, medications, consultations, vitals, logs, notificationSettings
    }

    /// persistence.yaml#leniency: every top-level key is optional on read, but once
    /// notificationSettings is PRESENT all eight of its fields are required.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        appointments = try container.decodeIfPresent([Appointment].self, forKey: .appointments) ?? []
        medications = try container.decodeIfPresent([Medication].self, forKey: .medications) ?? []
        consultations = try container.decodeIfPresent([Consultation].self, forKey: .consultations) ?? []
        vitals = try container.decodeIfPresent([Vital].self, forKey: .vitals) ?? []
        logs = try container.decodeIfPresent([Log].self, forKey: .logs) ?? []
        notificationSettings = try container.decodeIfPresent(
            NotificationSettings.self, forKey: .notificationSettings
        ) ?? .default
    }
}

extension MedicalData {
    public func appointment(_ id: UUID) -> Appointment? { appointments.first { $0.id == id } }
    public func medication(_ id: UUID) -> Medication? { medications.first { $0.id == id } }
    public func consultation(_ id: UUID) -> Consultation? { consultations.first { $0.id == id } }
    public func vital(_ id: UUID) -> Vital? { vitals.first { $0.id == id } }
}