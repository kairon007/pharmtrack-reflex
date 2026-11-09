import Foundation

public struct MedicationPickerState: Equatable {
    public var selectedIds: [UUID]

    public init() {
        selectedIds = []
    }
}

public enum MedicationPickerAction {
    case toggle(UUID)
    case done
    /// Internal: the parent seeds the selection when it presents the picker. Not reachable
    /// from a journey — the schema exposes only toggle and done.
    case present([UUID])

    public static func decode(_ name: String, _ args: ActionArguments) throws -> MedicationPickerAction? {
        switch name {
        case "toggle":
            return .toggle(try args.uuid("medicationId"))
        case "done":
            return .done
        default:
            return nil
        }
    }
}

public enum MedicationPickerMachine {
    public static func reduce(
        _ state: MedicationPickerState,
        _ action: MedicationPickerAction,
        _ context: MachineContext
    ) -> (MedicationPickerState, [Effect]) {
        var state = state
        switch action {
        case let .toggle(id):
        // Deselect removes ALL matching entries; select appends, so order is tap order.
            if state.selectedIds.contains(id) {
                state.selectedIds.removeAll { $0 == id }
            } else {
                state.selectedIds.append(id)
            }
            return (state, [.deliver(.consultationForm(.pickerSelectionChanged(state.selectedIds)))])

        case .done:
        // Q9. done emits no commit; the parent already has the ids.
            return (state, [.dispatch(.consultationForm(.dismissPicker))])

        case let .present(ids):
            state.selectedIds = ids
            return (state, [])
        }
    }

    public static func emit(_ state: MedicationPickerState, _ context: MachineContext) -> ReflexValue {
        .object([
            "selectedIds": .uuids(state.selectedIds),
            "availableIds": .uuids(context.data.medications.map(\.id)),
        ])
    }
}