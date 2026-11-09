import Core
import SwiftUI

struct SettingsScreen: View {
    @ObservedObject var app: ReflexApp

    private var data: SettingsData { app.settings }

    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Global Notifications")) {
                    Toggle(
                        "Enable All Notifications",
                        isOn: Binding(
                            get: { data.draft.allNotificationsEnabled },
                            set: { app.send(.settings(.setAllEnabled($0))) }
                        )
                    )
                    .tint(Theme.primary)
                }

                if data.sectionsVisible {
                    Section(header: Text("Medications")) {
                        Toggle(
                            "Medication Reminders",
                            isOn: Binding(
                                get: { data.draft.medicationsEnabled },
                                set: { app.send(.settings(.setMedicationsEnabled($0))) }
                            )
                        )
                        .tint(Theme.primary)

                        if data.medicationTimeVisible {
                            Picker(
                                "Reminder Time",
                                selection: Binding(
                                    get: { data.draft.medicationReminderTime },
                                    set: { app.send(.settings(.setMedicationReminderTime($0))) }
                                )
                            ) {
                                ForEach(data.reminderOptions, id: \.self) { time in
                                    Text(time.rawValue).tag(time)
                                }
                            }
                        }

                        Toggle(
                            "Low Stock Alerts",
                            isOn: Binding(
                                get: { data.draft.lowStockEnabled },
                                set: { app.send(.settings(.setLowStockEnabled($0))) }
                            )
                        )
                        .tint(Theme.primary)

                        if data.thresholdVisible {
                            Picker(
                                "Alert Threshold",
                                selection: Binding(
                                    get: { data.draft.lowStockThreshold },
                                    set: { app.send(.settings(.setLowStockThreshold($0))) }
                                )
                            ) {
                                ForEach(data.thresholdOptions, id: \.self) { threshold in
                                    Text("\(threshold) doses left").tag(threshold)
                                }
                            }
                        }
                    }

                    Section(header: Text("Appointments")) {
                        Toggle(
                            "Appointment Alerts",
                            isOn: Binding(
                                get: { data.draft.appointmentsEnabled },
                                set: { app.send(.settings(.setAppointmentsEnabled($0))) }
                            )
                        )
                        .tint(Theme.primary)

                        if data.appointmentTimeVisible {
                            Picker(
                                "Alert Time",
                                selection: Binding(
                                    get: { data.draft.appointmentReminderTime },
                                    set: { app.send(.settings(.setAppointmentReminderTime($0))) }
                                )
                            ) {
                                ForEach(data.reminderOptions, id: \.self) { time in
                                    Text(time.rawValue).tag(time)
                                }
                            }
                        }
                    }

                    Section(header: Text("Consultations")) {
                        Toggle(
                            "Consultation Alerts",
                            isOn: Binding(
                                get: { data.draft.consultationsEnabled },
                                set: { app.send(.settings(.setConsultationsEnabled($0))) }
                            )
                        )
                        .tint(Theme.primary)
                    }
                }
            }
            .navigationTitle("Notification Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { app.send(.settings(.cancel)) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { app.send(.settings(.save)) }
                        .fontWeight(.bold)
                }
            }
        }
    }
}