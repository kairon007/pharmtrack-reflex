import Foundation

public struct AddVitalState: Equatable {
    public var mode: EditMode
    public var editingId: UUID?
    public var type: VitalType
    public var valueText: String
    public var value2Text: String
    public var date: Instant
    public var notesText: String

    public init(now: Instant) {
        mode = .add
        editingId = nil
        type = .weight
        valueText = ""
        value2Text = ""
        date = now
        notesText = ""
    }
}

public enum AddVitalAction {
    case setMode(EditMode, UUID?)
    case setType(VitalType)
    case setValueText(String)
    case setValue2Text(String)
    case setDate(Instant)
    case setNotes(String)
    case save
    case cancel

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AddVitalAction? {
        switch name {
        case "setMode":
            return .setMode(try args.enumeration("mode", EditMode.self), try args.optionalUUID("vitalId"))
        case "setType":
            return .setType(try args.enumeration("type", VitalType.self))
        case "setValueText":
            return .setValueText(try args.string("text"))
        case "setValue2Text":
            return .setValue2Text(try args.string("text"))
        case "setDate":
            return .setDate(try args.instant("date"))
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

public enum AddVitalMachine {
    public static func reduce(
        _ state: AddVitalState,
        _ action: AddVitalAction,
        _ context: MachineContext
    ) -> (AddVitalState, [Effect]) {
        var state = state
        switch action {
        case let .setMode(mode, id):
            state = AddVitalState(now: context.now)
            state.mode = mode
            state.editingId = id
            if mode == .edit, let id, let vital = context.data.vital(id) {
                state.type = vital.type
                state.valueText = ValueFormat.shortestDecimal(vital.value)
                state.value2Text = vital.value2.map(ValueFormat.shortestDecimal) ?? ""
                state.date = vital.date
                state.notesText = vital.notes ?? ""
            }
            return (state, [])

        case let .setType(type):
        // Q: switching type does NOT clear the diastolic buffer, it is merely ignored.
            state.type = type
            return (state, [])

        case let .setValueText(text):
            state.valueText = text
            return (state, [])

        case let .setValue2Text(text):
            state.value2Text = text
            return (state, [])

        case let .setDate(date):
            state.date = date
            return (state, [])

        case let .setNotes(text):
            state.notesText = text
            return (state, [])

        case .save:
        // Q7. The gate is isEmpty WITHOUT trimming; an unparseable value is a SILENT
        // no-op that leaves the modal open and surfaces no error.
            guard !state.valueText.isEmpty, let value = ValueFormat.parseDouble(state.valueText) else {
                return (state, [])
            }
            let value2 = state.type == .bloodPressure ? ValueFormat.parseDouble(state.value2Text) : nil
            let notes = state.notesText.isEmpty ? nil : state.notesText
            var data = context.data

            if state.mode == .add {
                let vital = Vital(
                    id: context.ids.next(),
                    type: state.type,
                    value: value,
                    value2: value2,
                    date: state.date,
                    notes: notes
                )
                // Q16. Vitals insert at the front of storage; display order is by date.
                data.vitals.insert(vital, at: 0)
                return (state, [
                    .dispatch(.appRoot(.commitData(data, .add))),
                    .dispatch(.vitals(.closeForm)),
                ])
            }

            guard let id = state.editingId,
                  let index = data.vitals.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
        // D10. Replace by id, storage position preserved, no re-sort, no id consumed.
            data.vitals[index] = Vital(
                id: id,
                type: state.type,
                value: value,
                value2: value2,
                date: state.date,
                notes: notes
            )
            return (state, [
                .dispatch(.appRoot(.commitData(data, .update))),
                .dispatch(.vitals(.closeForm)),
            ])

        case .cancel:
            return (state, [.dispatch(.vitals(.closeForm))])
        }
    }

    public static func emit(_ state: AddVitalState, _ context: MachineContext) -> ReflexValue {
        .object([
            "mode": .string(state.mode.rawValue),
            "editingId": .optionalUUID(state.editingId),
            "type": .string(state.type.rawValue),
            "valueText": .string(state.valueText),
            "value2Text": .string(state.value2Text),
            "date": .instant(state.date),
            "notesText": .string(state.notesText),
            "value2Visible": .bool(state.type == .bloodPressure),
            "canSave": .bool(!state.valueText.isEmpty),
            "unit": .string(ValueFormat.unit(for: state.type)),
        ])
    }
}