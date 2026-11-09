import SwiftUI

struct FormSectionHeader: View {
    let title: String
    let icon: SemanticIcon
    let isDark: Bool

    var body: some View {
        HStack(spacing: 8) {
            AppIcon(icon, size: 16, tint: Theme.primary)
            Text(title)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(isDark ? Theme.slate400 : Theme.slate500)
            Spacer()
        }
    }
}

struct FormTextField: View {
    let placeholder: String
    let text: Binding<String>
    let isDark: Bool
    var keyboard: UIKeyboardType = .default

    var body: some View {
        TextField(placeholder, text: text)
            .keyboardType(keyboard)
            .padding(16)
            .background(isDark ? Theme.slate800 : Color.white)
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isDark ? Theme.slate700 : Theme.slate200, lineWidth: 1)
            }
    }
}

struct AddMedicationScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: AddMedicationData { app.addMedication }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(
                title: data.mode == .edit ? "Edit Medication" : "Add Medication",
                isDark: isDark,
                canSave: data.canSave,
                onCancel: { app.send(.addMedication(.cancel)) },
                onSave: { app.send(.addMedication(.save)) }
            )

            ScrollView {
                VStack(spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Medication details", icon: .tabMeds, isDark: isDark)
                        FormTextField(placeholder: "Name", text: app.textBinding(data.nameText) { .addMedication(.setName($0)) }, isDark: isDark)
                        FormTextField(placeholder: "Dosage (e.g., 10mg)", text: app.textBinding(data.dosageText) { .addMedication(.setDosage($0)) }, isDark: isDark)
                        FormTextField(placeholder: "Instructions (e.g., With Food)", text: app.textBinding(data.instructionsText) { .addMedication(.setInstructions($0)) }, isDark: isDark)
                    }
                    .padding(.top, 16)
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Schedule", icon: .contentTime, isDark: isDark)
                        Picker(
                            "Time of Day",
                            selection: Binding(
                                get: { data.schedule },
                                set: { app.send(.addMedication(.setSchedule($0))) }
                            )
                        ) {
                            ForEach(MedicationSchedule.allCases, id: \.self) { schedule in
                                Text(schedule.rawValue).tag(schedule)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(4)
                        .background(isDark ? Theme.slate800 : Theme.slate100)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        // icons.yaml drops shippingbox.fill; the meds tab glyph stands in.
                        FormSectionHeader(title: "Inventory", icon: .tabMeds, isDark: isDark)
                        FormTextField(placeholder: "Current Stock (pill count)", text: app.textBinding(data.stockText) { .addMedication(.setStockText($0)) }, isDark: isDark, keyboard: .numberPad)
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.bottom, 24)
            }

            FormSaveBar(
                title: data.mode == .edit ? "Update Medication" : "Save Medication",
                isDark: isDark,
                canSave: data.canSave,
                onSave: { app.send(.addMedication(.save)) }
            )
        }
        .background(Theme.background(isDark))
    }
}

struct AddAppointmentScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: AddAppointmentData { app.addAppointment }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(
                title: data.mode == .edit ? "Edit Appointment" : "New Appointment",
                isDark: isDark,
                canSave: data.canSave,
                onCancel: { app.send(.addAppointment(.cancel)) },
                onSave: { app.send(.addAppointment(.save)) }
            )

            ScrollView {
                VStack(spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        // icons.yaml drops person.fill; the consults tab glyph stands in.
                        FormSectionHeader(title: "Doctor & Specialty", icon: .tabConsults, isDark: isDark)
                        FormTextField(placeholder: "Doctor's Name", text: app.textBinding(data.doctorNameText) { .addAppointment(.setDoctorName($0)) }, isDark: isDark)
                        FormTextField(placeholder: "Specialty", text: app.textBinding(data.specialtyText) { .addAppointment(.setSpecialty($0)) }, isDark: isDark)
                    }
                    .padding(.top, 16)
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Appointment details", icon: .tabCalendar, isDark: isDark)
                        FormTextField(placeholder: "Type (e.g., General Checkup)", text: app.textBinding(data.typeText) { .addAppointment(.setType($0)) }, isDark: isDark)
                        DatePicker(
                            "Date & Time",
                            selection: Binding(
                                get: { data.date.asDate },
                                set: { app.send(.addAppointment(.setDate($0.asInstant))) }
                            )
                        )
                        .labelsHidden()
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(isDark ? Theme.slate800 : Color.white)
                        .cornerRadius(12)
                        .overlay {
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(isDark ? Theme.slate700 : Theme.slate200, lineWidth: 1)
                        }
                        FormTextField(placeholder: "Location", text: app.textBinding(data.locationText) { .addAppointment(.setLocation($0)) }, isDark: isDark)
                    }
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        // icons.yaml drops doc.text.fill; the edit glyph stands in.
                        FormSectionHeader(title: "Notes (optional)", icon: .actionEdit, isDark: isDark)
                        FormTextField(placeholder: "Bring previous reports...", text: app.textBinding(data.notesText) { .addAppointment(.setNotes($0)) }, isDark: isDark)
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.bottom, 24)
            }

            FormSaveBar(
                title: "Save appointment",
                isDark: isDark,
                canSave: data.canSave,
                onSave: { app.send(.addAppointment(.save)) }
            )
        }
        .background(Theme.background(isDark))
    }
}