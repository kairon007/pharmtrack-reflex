import Foundation

public struct ConsultationFormState: Equatable {
    public var mode: EditMode
    public var editingId: UUID?
    public var doctorNameText: String
    public var specialtyText: String
    public var date: Instant
    public var notesText: String
    public var linkedMedicationIds: [UUID]
    public var pickerPresented: Bool

    public init(now: Instant) {
        mode = .add
        editingId = nil
        doctorNameText = ""
        specialtyText = ""
        date = now
        notesText = ""
        linkedMedicationIds = []
        pickerPresented = false
    }
}

public enum ConsultationFormAction {
    case setMode(EditMode, UUID?)
    case setDoctorName(String)
    case setSpecialty(String)
    case setDate(Instant)
    case setNotes(String)
    case unlinkMedication(UUID)
    case presentPicker
    case dismissPicker
    case save
    case cancel

    public static func decode(_ name: String, _ args: ActionArguments) throws -> ConsultationFormAction? {
        switch name {
        case "setMode":
            return .setMode(
                try args.enumeration("mode", EditMode.self),
                try args.optionalUUID("consultationId")
            )
        case "setDoctorName":
            return .setDoctorName(try args.string("text"))
        case "setSpecialty":
            return .setSpecialty(try args.string("text"))
        case "setDate":
            return .setDate(try args.instant("date"))
        case "setNotes":
            return .setNotes(try args.string("text"))
        case "unlinkMedication":
            return .unlinkMedication(try args.uuid("medicationId"))
        case "presentPicker":
            return .presentPicker
        case "dismissPicker":
            return .dismissPicker
        case "save":
            return .save
        case "cancel":
            return .cancel
        default:
            return nil
        }
    }
}

public enum ConsultationFormMessage {
    case pickerSelectionChanged([UUID])

    public static func decode(_ name: String, _ args: ActionArguments) throws -> ConsultationFormMessage? {
        switch name {
        case "pickerSelectionChanged":
            return .pickerSelectionChanged(try args.uuidArray("ids"))
        default:
            return nil
        }
    }
}

public enum ConsultationFormMachine {
    public static func reduce(
        _ state: ConsultationFormState,
        _ action: ConsultationFormAction,
        _ context: MachineContext
    ) -> (ConsultationFormState, [Effect]) {
        var state = state
        switch action {
        case let .setMode(mode, id):
            state = ConsultationFormState(now: context.now)
            state.mode = mode
            state.editingId = id
            if mode == .edit, let id, let consultation = context.data.consultation(id) {
                state.doctorNameText = consultation.doctorName
                state.specialtyText = consultation.specialty
                state.date = consultation.date
                state.notesText = consultation.notes
                state.linkedMedicationIds = consultation.linkedMedications
            }
            return (state, [])

        case let .setDoctorName(text):
            state.doctorNameText = text
            return (state, [])

        case let .setSpecialty(text):
            state.specialtyText = text
            return (state, [])

        case let .setDate(date):
            state.date = date
            return (state, [])

        case let .setNotes(text):
            state.notesText = text
            return (state, [])

        case let .unlinkMedication(id):
            state.linkedMedicationIds.removeAll { $0 == id }
            return (state, [])

        case .presentPicker:
            state.pickerPresented = true
            return (state, [.dispatch(.medicationPicker(.present(state.linkedMedicationIds)))])

        case .dismissPicker:
        // Q9. Dismissing never reverts — the parent already owns the ids.
            state.pickerPresented = false
            return (state, [])

        case .save:
        // D13. The gate TRIMS; the writer does not. " Dr. Alice Rao " persists untrimmed.
            guard !state.doctorNameText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return (state, [])
            }
            var data = context.data

            if state.mode == .add {
                data.consultations.insert(
                    Consultation(
                        id: context.ids.next(),
                        doctorName: state.doctorNameText,
                        specialty: state.specialtyText,
                        date: state.date,
                        notes: state.notesText,
                        linkedMedications: state.linkedMedicationIds
                    ),
                    at: 0
                )
                return (state, [
                    .dispatch(.appRoot(.commitData(data, .add))),
                    .dispatch(.consultations(.closeForm)),
                ])
            }

            guard let id = state.editingId,
                  let index = data.consultations.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
        // Q10. Dangling ids are written straight back — no cascade cleanup is invented.
            data.consultations[index] = Consultation(
                id: id,
                doctorName: state.doctorNameText,
                specialty: state.specialtyText,
                date: state.date,
                notes: state.notesText,
                linkedMedications: state.linkedMedicationIds
            )
            return (state, [
                .dispatch(.appRoot(.commitData(data, .update))),
                .dispatch(.consultations(.closeForm)),
            ])

        case .cancel:
            return (state, [.dispatch(.consultations(.closeForm))])
        }
    }

    public static func reduce(
        _ state: ConsultationFormState,
        _ message: ConsultationFormMessage,
        _ context: MachineContext
    ) -> (ConsultationFormState, [Effect]) {
        var state = state
        switch message {
        case let .pickerSelectionChanged(ids):
            state.linkedMedicationIds = ids
            return (state, [])
        }
    }

    public static func emit(_ state: ConsultationFormState, _ context: MachineContext) -> ReflexValue {
        let linkedRows = state.linkedMedicationIds.filter { context.data.medication($0) != nil }
        var fields: [String: ReflexValue] = [:]
        fields["mode"] = .string(state.mode.rawValue)
        fields["editingId"] = .optionalUUID(state.editingId)
        fields["doctorNameText"] = .string(state.doctorNameText)
        fields["specialtyText"] = .string(state.specialtyText)
        fields["date"] = .instant(state.date)
        fields["notesText"] = .string(state.notesText)
        fields["linkedMedicationIds"] = .uuids(state.linkedMedicationIds)
        fields["pickerPresented"] = .bool(state.pickerPresented)
        fields["canSave"] = .bool(
                !state.doctorNameText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            )
        fields["linkedRows"] = .uuids(linkedRows)
        return .object(fields)
    }
}