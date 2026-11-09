import Foundation

public struct AddAppointmentState: Equatable {
    public var mode: EditMode
    public var editingId: UUID?
    public var doctorNameText: String
    public var specialtyText: String
    public var typeText: String
    public var date: Instant
    public var locationText: String
    public var notesText: String

    public init(date: Instant) {
        mode = .add
        editingId = nil
        doctorNameText = ""
        specialtyText = ""
        typeText = ""
        self.date = date
        locationText = ""
        notesText = ""
    }

    public var canSave: Bool {
        !doctorNameText.isEmpty && !specialtyText.isEmpty && !typeText.isEmpty && !locationText.isEmpty
    }
}

public enum AddAppointmentAction {
    case setMode(EditMode, UUID?)
    case setDoctorName(String)
    case setSpecialty(String)
    case setType(String)
    case setDate(Instant)
    case setLocation(String)
    case setNotes(String)
    case save
    case cancel

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AddAppointmentAction? {
        switch name {
        case "setMode":
            return .setMode(
                try args.enumeration("mode", EditMode.self),
                try args.optionalUUID("appointmentId")
            )
        case "setDoctorName":
            return .setDoctorName(try args.string("text"))
        case "setSpecialty":
            return .setSpecialty(try args.string("text"))
        case "setType":
            return .setType(try args.string("text"))
        case "setDate":
            return .setDate(try args.instant("date"))
        case "setLocation":
            return .setLocation(try args.string("text"))
        case "setNotes":
            return .setNotes(try args.string("text"))
        case "save":
            return .save
        case "cancel":
            return .cancel
        default:
            return nil
        }
    }
}

public enum AddAppointmentMachine {
    public static func reduce(
        _ state: AddAppointmentState,
        _ action: AddAppointmentAction,
        _ context: MachineContext
    ) -> (AddAppointmentState, [Effect]) {
        var state = state
        switch action {
        case let .setMode(mode, id):
        // The add form opens on the day the calendar is showing, not on "now".
            state = AddAppointmentState(date: context.state.appointments.selectedDate)
            state.mode = mode
            state.editingId = id
            if mode == .edit, let id, let appointment = context.data.appointment(id) {
                state.doctorNameText = appointment.doctorName
                state.specialtyText = appointment.specialty
                state.typeText = appointment.type
                state.date = appointment.date
                state.locationText = appointment.location
                state.notesText = appointment.notes ?? ""
            }
            return (state, [])

        case let .setDoctorName(text):
            state.doctorNameText = text
            return (state, [])

        case let .setSpecialty(text):
            state.specialtyText = text
            return (state, [])

        case let .setType(text):
            state.typeText = text
            return (state, [])

        case let .setDate(date):
            state.date = date
            return (state, [])

        case let .setLocation(text):
            state.locationText = text
            return (state, [])

        case let .setNotes(text):
            state.notesText = text
            return (state, [])

        case .save:
            guard state.canSave else { return (state, []) }
            let notes = state.notesText.isEmpty ? nil : state.notesText
            var data = context.data

            if state.mode == .add {
                data.appointments.append(
                    Appointment(
                        id: context.ids.next(),
                        doctorName: state.doctorNameText,
                        specialty: state.specialtyText,
                        type: state.typeText,
                        date: state.date,
                        location: state.locationText,
                        notes: notes,
                        // Q8. No toggle exists. true on create, preserved on edit.
                        notify: true
                    )
                )
            } else {
                guard let id = state.editingId,
                      let index = data.appointments.firstIndex(where: { $0.id == id }) else {
                    return (state, [])
                }
                let notify = data.appointments[index].notify
                data.appointments[index] = Appointment(
                    id: id,
                    doctorName: state.doctorNameText,
                    specialty: state.specialtyText,
                    type: state.typeText,
                    date: state.date,
                    location: state.locationText,
                    notes: notes,
                    notify: notify
                )
            }

        // BOTH paths re-sort the whole array, so editing a date can move the row off
        // the currently selected day (MedicalViewModel.swift:169-171, :228-234).
            data.appointments = Selectors.sortedByDateAndId(data.appointments, date: \.date, id: \.id)
            let reason: CommitReason = state.mode == .add ? .add : .update
            return (state, [
                .dispatch(.appRoot(.commitData(data, reason))),
                .dispatch(.appointments(.closeForm)),
            ])

        case .cancel:
            return (state, [.dispatch(.appointments(.closeForm))])
        }
    }

    public static func emit(_ state: AddAppointmentState, _ context: MachineContext) -> ReflexValue {
        .object([
            "mode": .string(state.mode.rawValue),
            "editingId": .optionalUUID(state.editingId),
            "doctorNameText": .string(state.doctorNameText),
            "specialtyText": .string(state.specialtyText),
            "typeText": .string(state.typeText),
            "date": .instant(state.date),
            "locationText": .string(state.locationText),
            "notesText": .string(state.notesText),
            "canSave": .bool(state.canSave),
        ])
    }
}