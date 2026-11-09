import Foundation

public struct AddMedicationState: Equatable {
    public var mode: EditMode
    public var editingId: UUID?
    public var nameText: String
    public var dosageText: String
    public var instructionsText: String
    public var schedule: MedicationSchedule
    public var stockText: String

    public init() {
        mode = .add
        editingId = nil
        nameText = ""
        dosageText = ""
        instructionsText = ""
        schedule = .morning
        stockText = ""
    }
}

public enum AddMedicationAction {
    case setMode(EditMode, UUID?)
    case setName(String)
    case setDosage(String)
    case setInstructions(String)
    case setSchedule(MedicationSchedule)
    case setStockText(String)
    case save
    case cancel

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AddMedicationAction? {
        switch name {
        case "setMode":
            return .setMode(
                try args.enumeration("mode", EditMode.self),
                try args.optionalUUID("medicationId")
            )
        case "setName":
            return .setName(try args.string("text"))
        case "setDosage":
            return .setDosage(try args.string("text"))
        case "setInstructions":
            return .setInstructions(try args.string("text"))
        case "setSchedule":
            return .setSchedule(try args.enumeration("schedule", MedicationSchedule.self))
        case "setStockText":
            return .setStockText(try args.string("text"))
        case "save":
            return .save
        case "cancel":
            return .cancel
        default:
            return nil
        }
    }
}

public enum AddMedicationMachine {
    public static func reduce(
        _ state: AddMedicationState,
        _ action: AddMedicationAction,
        _ context: MachineContext
    ) -> (AddMedicationState, [Effect]) {
        var state = state
        switch action {
        case let .setMode(mode, id):
            state = AddMedicationState()
            state.mode = mode
            state.editingId = id
            if mode == .edit, let id, let medication = context.data.medication(id) {
                state.nameText = medication.name
                state.dosageText = medication.dosage
                state.instructionsText = medication.instructions
                state.schedule = MedicationSchedule(rawValue: medication.schedule) ?? .morning
                state.stockText = String(medication.stock)
            }
            return (state, [])

        case let .setName(text):
            state.nameText = text
            return (state, [])

        case let .setDosage(text):
            state.dosageText = text
            return (state, [])

        case let .setInstructions(text):
            state.instructionsText = text
            return (state, [])

        case let .setSchedule(schedule):
            state.schedule = schedule
            return (state, [])

        case let .setStockText(text):
            state.stockText = text
            return (state, [])

        case .save:
        // D13. isEmpty WITHOUT trimming, so " " passes and persists untrimmed.
            guard !state.nameText.isEmpty, !state.dosageText.isEmpty else { return (state, []) }
        // Q6. Int(text) ?? 0 — no trim, no clamp, negatives accepted.
            let stock = ValueFormat.parseInt(state.stockText) ?? 0
            var data = context.data

            if state.mode == .add {
                let medication = Medication(
                    id: context.ids.next(),
                    name: state.nameText,
                    dosage: state.dosageText,
                    instructions: state.instructionsText,
                    schedule: state.schedule.rawValue,
                    stock: stock,
                    active: true,
                    // Already past by render time, so the planner's trigger > now guard
                    // skips the reminder. Real behavior, not an off-by-one to fix.
                    nextDose: context.now
                )
                data.medications.append(medication)
                return (state, [
                    .dispatch(.appRoot(.commitData(data, .add))),
                    .dispatch(.medicationTracker(.closeForm)),
                ])
            }

            guard let id = state.editingId,
                  let index = data.medications.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
        // Edit overwrites these five ONLY. nextDose, active and id are preserved.
            data.medications[index].name = state.nameText
            data.medications[index].dosage = state.dosageText
            data.medications[index].instructions = state.instructionsText
            data.medications[index].schedule = state.schedule.rawValue
            data.medications[index].stock = stock
            return (state, [
                .dispatch(.appRoot(.commitData(data, .update))),
                .dispatch(.medicationTracker(.closeForm)),
            ])

        case .cancel:
            return (state, [.dispatch(.medicationTracker(.closeForm))])
        }
    }

    public static func emit(_ state: AddMedicationState, _ context: MachineContext) -> ReflexValue {
        .object([
            "mode": .string(state.mode.rawValue),
            "editingId": .optionalUUID(state.editingId),
            "nameText": .string(state.nameText),
            "dosageText": .string(state.dosageText),
            "instructionsText": .string(state.instructionsText),
            "schedule": .string(state.schedule.rawValue),
            "stockText": .string(state.stockText),
            "canSave": .bool(!state.nameText.isEmpty && !state.dosageText.isEmpty),
        ])
    }
}