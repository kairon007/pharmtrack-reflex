import Core
import SwiftUI

struct ConsultationFormScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: ConsultationFormData { app.consultationForm }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(
                title: data.mode == .edit ? "Edit Consultation" : "New Consultation",
                isDark: isDark,
                canSave: data.canSave,
                onCancel: { app.send(.consultationForm(.cancel)) },
                onSave: { app.send(.consultationForm(.save)) }
            )

            ScrollView {
                VStack(spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Doctor information", icon: .tabConsults, isDark: isDark)
                        FormTextField(
                            placeholder: "Doctor Name (e.g. Dr. Sarah Johnson)",
                            text: app.textBinding(data.doctorNameText) { .consultationForm(.setDoctorName($0)) },
                            isDark: isDark
                        )
                        FormTextField(
                            placeholder: "Specialty (e.g. Cardiologist)",
                            text: app.textBinding(data.specialtyText) { .consultationForm(.setSpecialty($0)) },
                            isDark: isDark
                        )
                    }
                    .padding(.top, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Consultation schedule", icon: .tabCalendar, isDark: isDark)
                        HStack {
                            DatePicker(
                                "Consultation Date",
                                selection: Binding(
                                    get: { data.date.asDate },
                                    set: { app.send(.consultationForm(.setDate($0.asInstant))) }
                                ),
                                displayedComponents: [.date, .hourAndMinute]
                            )
                            .labelsHidden()
                            .padding(12)
                            .background(isDark ? Theme.slate800 : Color.white)
                            .cornerRadius(12)
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isDark ? Theme.slate700 : Theme.slate200, lineWidth: 1)
                            }
                            Spacer()
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        // icons.yaml drops doc.text.fill; the edit glyph stands in.
                        FormSectionHeader(title: "Consultation notes", icon: .actionEdit, isDark: isDark)
                        FormTextField(
                            placeholder: "Describe symptoms, advice, etc...",
                            text: app.textBinding(data.notesText) { .consultationForm(.setNotes($0)) },
                            isDark: isDark
                        )
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        FormSectionHeader(title: "Prescribed medications", icon: .tabMeds, isDark: isDark)

                        ForEach(data.linkedRows) { medication in
                            HStack {
                                Text(medication.name)
                                Spacer()
                                Button {
                                    app.send(.consultationForm(.unlinkMedication(medication.id)))
                                } label: {
                                    AppIcon(.actionRemove, size: 20, tint: Theme.rose500)
                                }
                            }
                            .padding()
                            .background(isDark ? Theme.slate800 : Color.white)
                            .cornerRadius(12)
                        }

                        Button {
                            app.send(.consultationForm(.presentPicker))
                        } label: {
                            VStack(spacing: 8) {
                                AppIcon(.actionAddCircle, size: 32, tint: Theme.primary.opacity(0.5))
                                Text("Link medication")
                                    .font(.subheadline)
                                    .foregroundColor(isDark ? Theme.slate400 : Theme.slate500)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(24)
                            .background(Theme.primary.opacity(0.05))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(style: StrokeStyle(lineWidth: 1, dash: [5]))
                                    .foregroundColor(Theme.primary.opacity(0.3))
                            }
                        }
                    }
                }
                .padding(16)
                .padding(.bottom, 24)
            }

            FormSaveBar(
                title: data.mode == .edit ? "Update Consultation" : "Save Consultation",
                isDark: isDark,
                canSave: data.canSave,
                onSave: { app.send(.consultationForm(.save)) }
            )
        }
        .background(Theme.background(isDark))
        .sheet(
            isPresented: Binding(
                get: { data.pickerPresented },
                set: { presented in
                    if !presented { app.send(.consultationForm(.dismissPicker)) }
                }
            )
        ) {
            MedicationPickerScreen(app: app)
        }
    }
}

struct MedicationPickerScreen: View {
    @ObservedObject var app: ReflexApp

    private var data: MedicationPickerData { app.medicationPicker }

    var body: some View {
        NavigationStack {
            List {
                ForEach(data.available) { medication in
                    Button {
                        app.send(.medicationPicker(.toggle(medication.id)))
                    } label: {
                        HStack {
                            Text(medication.name)
                            Spacer()
                            if data.isSelected(medication.id) {
                                AppIcon(.actionConfirm, size: 20, tint: Theme.primary)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle("Select Medication")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { app.send(.medicationPicker(.done)) }
                }
            }
        }
    }
}