import SwiftUI

// Form chrome shared by the four add/edit screens: the legacy field look
// (slate50/slate800 input wells, 12pt corners) without any Skip padding hacks.

struct FieldLabel: View {
    let text: String
    let isDark: Bool

    var body: some View {
        Text(text)
            .font(.subheadline)
            .fontWeight(.semibold)
            .foregroundColor(isDark ? Theme.slate400 : Theme.slate500)
    }
}

struct InputWell<Content: View>: View {
    let isDark: Bool
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isDark ? Theme.slate800 : Theme.slate50)
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isDark ? Theme.borderDark : Theme.borderLight, lineWidth: 1)
            }
    }
}

struct FormHeader: View {
    let title: String
    let isDark: Bool
    let canSave: Bool
    let onCancel: () -> Void
    let onSave: () -> Void

    var body: some View {
        HStack {
            Button("Cancel", action: onCancel)
                .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                .foregroundColor(Theme.primary)
            Spacer()
            Text(title)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(Theme.heading(isDark))
            Spacer()
            Button("Done", action: onSave)
                .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
                .fontWeight(.semibold)
                .foregroundColor(Theme.primary)
                .disabled(!canSave)
        }
        .padding(16)
        .background(Theme.background(isDark))
    }
}

struct FormSaveBar: View {
    let title: String
    let isDark: Bool
    let canSave: Bool
    let onSave: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Divider().background(isDark ? Theme.slate800 : Theme.slate200)
            Button(action: onSave) {
                HStack {
                    AppIcon(.actionConfirm, size: 20, tint: .white)
                    Text(title)
                }
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(canSave ? Theme.primary : Theme.primary.opacity(0.4))
                .cornerRadius(16)
            }
            .disabled(!canSave)
            .padding(16)
            .padding(.bottom, 16)
        }
        .background(Theme.background(isDark))
    }
}

struct AddVitalScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: AddVitalData { app.addVital }

    var body: some View {
        VStack(spacing: 0) {
            FormHeader(
                title: data.mode == .edit ? "Edit vital" : "Add vital",
                isDark: isDark,
                canSave: data.canSave,
                onCancel: { app.send(.addVital(.cancel)) },
                onSave: { app.send(.addVital(.save)) }
            )

            ScrollView {
                VStack(spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        FieldLabel(text: "Vital type", isDark: isDark)
                        HStack(spacing: 12) {
                            metricTab("Weight", .weight)
                            metricTab("Pressure", .bloodPressure)
                            metricTab("Sugar", .bloodSugar)
                        }
                    }
                    .padding(.top, 16)
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FieldLabel(text: "Value", isDark: isDark)
                        InputWell(isDark: isDark) {
                            HStack {
                                if data.value2Visible {
                                    TextField("120", text: app.textBinding(data.valueText) { .addVital(.setValueText($0)) })
                                        .font(.system(size: 24, weight: .bold))
                                        .keyboardType(.numbersAndPunctuation)
                                    Text("/")
                                        .font(.system(size: 24, weight: .light))
                                        .foregroundColor(isDark ? Theme.slate400 : Theme.slate500)
                                    TextField("80", text: app.textBinding(data.value2Text) { .addVital(.setValue2Text($0)) })
                                        .font(.system(size: 24, weight: .bold))
                                        .keyboardType(.numbersAndPunctuation)
                                } else {
                                    TextField("0.0", text: app.textBinding(data.valueText) { .addVital(.setValueText($0)) })
                                        .font(.system(size: 24, weight: .bold))
                                        .keyboardType(.decimalPad)
                                }
                                Text(data.unit)
                                    .font(.headline)
                                    .foregroundColor(isDark ? Theme.slate400 : Theme.slate500)
                            }
                        }
                    }
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FieldLabel(text: "Date and time", isDark: isDark)
                        InputWell(isDark: isDark) {
                            DatePicker(
                                "Select Date",
                                selection: Binding(
                                    get: { data.date.asDate },
                                    set: { app.send(.addVital(.setDate($0.asInstant))) }
                                )
                            )
                            .labelsHidden()
                        }
                        .padding(12)
                    }
                    .padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 12) {
                        FieldLabel(text: "Notes (optional)", isDark: isDark)
                        InputWell(isDark: isDark) {
                            TextField("Any specific conditions?", text: app.textBinding(data.notesText) { .addVital(.setNotes($0)) })
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.bottom, 24)
            }

            FormSaveBar(
                title: "Save entry",
                isDark: isDark,
                canSave: data.canSave,
                onSave: { app.send(.addVital(.save)) }
            )
        }
        .background(Theme.background(isDark))
    }

    private func metricTab(_ title: String, _ type: VitalType) -> some View {
        let isSelected = data.type == type
        return Button {
            app.send(.addVital(.setType(type)))
        } label: {
            VStack(spacing: 8) {
                AppIcon(SemanticIcon(type: type), size: 24, tint: isSelected ? .white : Theme.primary)
                Text(title)
                    .font(.caption)
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isSelected ? Theme.primary : (isDark ? Theme.slate800 : Color.white))
            .foregroundColor(isSelected ? .white : (isDark ? Theme.slate300 : Theme.slate700))
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.clear : Theme.border(isDark), lineWidth: 1)
            }
        }
    }
}