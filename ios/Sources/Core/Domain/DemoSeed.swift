import Foundation

/// Reserved seed identifiers from fixtures/determinism.yaml#ids.reserved. These are fixed
/// literals, not generator draws — demoSeed consumes ZERO ids from the sequence.
public enum SeedID {
    public static let appointment1 = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!
    public static let medication1 = UUID(uuidString: "00000000-0000-4000-8000-000000000002")!
    public static let medication2 = UUID(uuidString: "00000000-0000-4000-8000-000000000003")!
    public static let medication3 = UUID(uuidString: "00000000-0000-4000-8000-000000000004")!
    public static let consultation1 = UUID(uuidString: "00000000-0000-4000-8000-000000000005")!
    public static let vital1 = UUID(uuidString: "00000000-0000-4000-8000-000000000006")!
    public static let log1 = UUID(uuidString: "00000000-0000-4000-8000-000000000007")!
}

/// The repaired demo set (D7). At the pinned clock this equals fixtures/seeded_demo.json
/// field for field; app_load asserts that equivalence rather than assuming it.
///
/// The iOS seed dates are canonical for both platforms — Android's `#else` fallback to a
/// plain `today` is eliminated, not ported (determinism.yaml#seed).
public enum DemoSeed {
    public static func data(now: Instant, zone: Instant.Zone = .utc) -> MedicalData {
        let startOfToday = now.startOfDay(in: zone)
        return MedicalData(
            appointments: [
                Appointment(
                    id: SeedID.appointment1,
                    doctorName: "Dr. Michael Smith",
                    specialty: "Cardiology",
                    type: "General Checkup",
                    date: startOfToday.addingSeconds(14 * 3_600 + 30 * 60),
                    location: "City Clinic",
                    notes: "Bring previous reports",
                    notify: true
                ),
            ],
            medications: [
                Medication(
                    id: SeedID.medication1,
                    name: "Vitamin D3",
                    dosage: "1000IU",
                    instructions: "With Food",
                    schedule: MedicationSchedule.morning.rawValue,
                    stock: 24,
                    active: true,
                    nextDose: startOfToday.addingSeconds(9 * 3_600)
                ),
                Medication(
                    id: SeedID.medication2,
                    name: "Atorvastatin",
                    dosage: "10mg",
                    instructions: "After breakfast",
                    schedule: MedicationSchedule.morning.rawValue,
                    stock: 24,
                    active: true
                ),
                Medication(
                    id: SeedID.medication3,
                    name: "Ibuprofen",
                    dosage: "400mg",
                    instructions: "Every 6 hours",
                    schedule: MedicationSchedule.afternoon.rawValue,
                    stock: 3,
                    active: true
                ),
            ],
            consultations: [
                Consultation(
                    id: SeedID.consultation1,
                    doctorName: "Dr. Sarah Jenkins",
                    specialty: "Cardiologist",
                    date: now.addingDays(-30),
                    notes: "Continue 5mg Lisinopril daily; follow-up in 3 months.",
                    linkedMedications: []
                ),
            ],
            vitals: [
                Vital(
                    id: SeedID.vital1,
                    type: .weight,
                    value: 75.5,
                    date: now.addingDays(-1),
                    notes: "Morning weigh-in"
                ),
            ],
            logs: [
                Log(
                    id: SeedID.log1,
                    type: "Medication Taken",
                    description: "Vitamin D3 Dose Taken",
                    date: startOfToday.addingSeconds(8 * 3_600),
                    source: "Manual Log",
                    medicationId: SeedID.medication1
                ),
            ],
            notificationSettings: .default
        )
    }
}