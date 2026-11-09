import Foundation

public struct ConsultationsState: Equatable {
    public var searchQuery: String
    public var presentedForm: UUID?
    public var formMode: FormMode

    public init() {
        searchQuery = ""
        presentedForm = nil
        formMode = .closed
    }
}

public enum ConsultationsAction {
    case setSearch(String)
    case openAdd
    case openEdit(UUID)
    case delete(UUID)
    case closeForm

    public static func decode(_ name: String, _ args: ActionArguments) throws -> ConsultationsAction? {
        switch name {
        case "setSearch":
            return .setSearch(try args.string("text"))
        case "openAdd":
            return .openAdd
        case "openEdit":
            return .openEdit(try args.uuid("consultationId"))
        case "delete":
            return .delete(try args.uuid("consultationId"))
        case "closeForm":
            return .closeForm
        default:
            return nil
        }
    }
}

public enum ConsultationsMachine {
    /// nodes.yaml describes presentedForm as "null = closed; a sentinel NEW id = add mode".
    /// The sentinel is a fixed literal, NOT a generator draw — opening a form consumes zero
    /// ids (consultation_crud asserts ids.consumed == 0 after a failed add).
    public static let addSentinel = UUID(uuidString: "00000000-0000-0000-0000-000000000000")!

    public static func reduce(
        _ state: ConsultationsState,
        _ action: ConsultationsAction,
        _ context: MachineContext
    ) -> (ConsultationsState, [Effect]) {
        var state = state
        switch action {
        case let .setSearch(text):
            state.searchQuery = text
            return (state, [])

        case .openAdd:
            state.formMode = .add
            state.presentedForm = addSentinel
            return (state, [.dispatch(.consultationForm(.setMode(.add, nil)))])

        case let .openEdit(id):
            state.formMode = .edit
            state.presentedForm = id
            return (state, [.dispatch(.consultationForm(.setMode(.edit, id)))])

        case let .delete(id):
        // D10. Immediate remove-by-id, no confirmation, no undo. An absent id writes nothing.
            guard let index = context.data.consultations.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
            var data = context.data
            data.consultations.remove(at: index)
            return (state, [.dispatch(.appRoot(.commitData(data, .delete)))])

        case .closeForm:
            state.formMode = .closed
            state.presentedForm = nil
            return (state, [])
        }
    }

    public static func emit(_ state: ConsultationsState, _ context: MachineContext) -> ReflexValue {
        let all = context.data.consultations
        // Storage order is newest-ADDED-first; the list applies NO sort. Notes are not searched.
        let filtered = all.filter {
            Selectors.matches($0.doctorName, query: state.searchQuery)
                || Selectors.matches($0.specialty, query: state.searchQuery)
        }
        var initials: [String: ReflexValue] = [:]
        for consultation in all {
            initials[consultation.id.uuidString] = .string(ValueFormat.initials(consultation.doctorName))
        }
        var fields: [String: ReflexValue] = [:]
        fields["searchQuery"] = .string(state.searchQuery)
        fields["presentedForm"] = .optionalUUID(state.presentedForm)
        fields["formMode"] = .string(state.formMode.rawValue)
        fields["filteredIds"] = .uuids(filtered.map(\.id))
        // Q11. The badge shows TOTAL, not filtered.
        fields["totalCount"] = .int(all.count)
        fields["initials"] = .object(initials)
        return .object(fields)
    }
}