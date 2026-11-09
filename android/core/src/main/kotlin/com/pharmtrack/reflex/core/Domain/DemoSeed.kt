package com.pharmtrack.reflex.core.Domain

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

/**
 * Reserved seed identifiers from fixtures/determinism.yaml#ids.reserved. These are fixed
 * literals, not generator draws — demoSeed consumes ZERO ids from the sequence.
 */
object SeedID {
    val appointment1: UUID = parseUUID("00000000-0000-4000-8000-000000000001")!!
    val medication1: UUID = parseUUID("00000000-0000-4000-8000-000000000002")!!
    val medication2: UUID = parseUUID("00000000-0000-4000-8000-000000000003")!!
    val medication3: UUID = parseUUID("00000000-0000-4000-8000-000000000004")!!
    val consultation1: UUID = parseUUID("00000000-0000-4000-8000-000000000005")!!
    val vital1: UUID = parseUUID("00000000-0000-4000-8000-000000000006")!!
    val log1: UUID = parseUUID("00000000-0000-4000-8000-000000000007")!!
}

/**
 * The repaired demo set (D7). At the pinned clock this equals fixtures/seeded_demo.json
 * field for field; app_load asserts that equivalence rather than assuming it.
 *
 * The iOS seed dates are canonical for both platforms — Android's `#else` fallback to a
 * plain `today` is eliminated, not ported (determinism.yaml#seed).
 */
object DemoSeed {

    fun data(now: Instant, zone: Instant.Zone = Instant.Zone.utc): MedicalData {
        val startOfToday = now.startOfDay(zone)
        return MedicalData(
            appointments = listOf(
                Appointment(
                    id = SeedID.appointment1,
                    doctorName = "Dr. Michael Smith",
                    specialty = "Cardiology",
                    type = "General Checkup",
                    date = startOfToday.addingSeconds(14 * 3_600.0 + 30 * 60.0),
                    location = "City Clinic",
                    notes = "Bring previous reports",
                    notify = true
                )
            ),
            medications = listOf(
                Medication(
                    id = SeedID.medication1,
                    name = "Vitamin D3",
                    dosage = "1000IU",
                    instructions = "With Food",
                    schedule = MedicationSchedule.MORNING.rawValue,
                    stock = 24,
                    active = true,
                    nextDose = startOfToday.addingSeconds(9 * 3_600.0)
                ),
                Medication(
                    id = SeedID.medication2,
                    name = "Atorvastatin",
                    dosage = "10mg",
                    instructions = "After breakfast",
                    schedule = MedicationSchedule.MORNING.rawValue,
                    stock = 24,
                    active = true,
                    nextDose = null
                ),
                Medication(
                    id = SeedID.medication3,
                    name = "Ibuprofen",
                    dosage = "400mg",
                    instructions = "Every 6 hours",
                    schedule = MedicationSchedule.AFTERNOON.rawValue,
                    stock = 3,
                    active = true,
                    nextDose = null
                )
            ),
            consultations = listOf(
                Consultation(
                    id = SeedID.consultation1,
                    doctorName = "Dr. Sarah Jenkins",
                    specialty = "Cardiologist",
                    date = now.addingDays(-30),
                    notes = "Continue 5mg Lisinopril daily; follow-up in 3 months.",
                    linkedMedications = emptyList()
                )
            ),
            vitals = listOf(
                Vital(
                    id = SeedID.vital1,
                    type = VitalType.WEIGHT,
                    value = 75.5,
                    value2 = null,
                    date = now.addingDays(-1),
                    notes = "Morning weigh-in"
                )
            ),
            logs = listOf(
                Log(
                    id = SeedID.log1,
                    type = "Medication Taken",
                    description = "Vitamin D3 Dose Taken",
                    date = startOfToday.addingSeconds(8 * 3_600.0),
                    source = "Manual Log",
                    medicationId = SeedID.medication1
                )
            ),
            notificationSettings = NotificationSettings.default()
        )
    }
}
