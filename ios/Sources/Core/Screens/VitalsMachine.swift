import Foundation

public struct VitalsState: Equatable {
    public var selectedMetric: VitalType
    public var formMode: FormMode
    public var detailPresented: Bool

    public init() {
        selectedMetric = .weight
        formMode = .closed
        detailPresented = false
    }
}

public enum VitalsAction {
    case selectMetric(VitalType)
    case openAdd
    case openEdit(UUID)
    case delete(UUID)
    case openDetail
    case closeDetail
    case closeForm

    public static func decode(_ name: String, _ args: ActionArguments) throws -> VitalsAction? {
        switch name {
        case "selectMetric":
            return .selectMetric(try args.enumeration("metric", VitalType.self))
        case "openAdd":
            return .openAdd
        case "openEdit":
            return .openEdit(try args.uuid("vitalId"))
        case "delete":
            return .delete(try args.uuid("vitalId"))
        case "openDetail":
            return .openDetail
        case "closeDetail":
            return .closeDetail
        case "closeForm":
            return .closeForm
        default:
            return nil
        }
    }
}

public enum VitalsMachine {
    public static func reduce(
        _ state: VitalsState,
        _ action: VitalsAction,
        _ context: MachineContext
    ) -> (VitalsState, [Effect]) {
        var state = state
        switch action {
        case let .selectMetric(metric):
            state.selectedMetric = metric
            return (state, [])

        case .openAdd:
            state.formMode = .add
            return (state, [.dispatch(.addVital(.setMode(.add, nil)))])

        case let .openEdit(id):
            state.formMode = .edit
            return (state, [.dispatch(.addVital(.setMode(.edit, id)))])

        case let .delete(id):
        // MedicalViewModel.swift:186-191 saves only when the id was found. No Log entry.
            guard let index = context.data.vitals.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
            var data = context.data
            data.vitals.remove(at: index)
            return (state, [.dispatch(.appRoot(.commitData(data, .delete)))])

        case .openDetail:
            state.detailPresented = true
            return (state, [.dispatch(.vitalsDetail(.open(state.selectedMetric)))])

        case .closeDetail:
            state.detailPresented = false
            return (state, [.dispatch(.vitalsDetail(.close))])

        case .closeForm:
            state.formMode = .closed
            return (state, [])
        }
    }

    /// Q18. No time-range cutoff on this screen — the tab and the detail screen legitimately
    /// disagree on series length for the same data and metric.
    public static func series(_ state: VitalsState, _ context: MachineContext) -> [Vital] {
        Selectors.sortedByDateAndId(
            context.data.vitals.filter { $0.type == state.selectedMetric },
            date: \.date,
            id: \.id
        )
    }

    public static func emit(_ state: VitalsState, _ context: MachineContext) -> ReflexValue {
        let ascending = series(state, context)
        var rowIcon: [String: ReflexValue] = [:]
        for vital in context.data.vitals {
        // D4. Semantic name, resolved through contracts/icons.yaml by each platform's UI.
            rowIcon[vital.id.uuidString] = .string(VitalsMachine.iconName(vital.type))
        }
        let trend = Selectors.trend(ascending)
        var fields: [String: ReflexValue] = [:]
        fields["selectedMetric"] = .string(state.selectedMetric.rawValue)
        fields["formMode"] = .string(state.formMode.rawValue)
        fields["detailPresented"] = .bool(state.detailPresented)
        fields["filteredVitals"] = .uuids(ascending.map(\.id))
        fields["latestReadingId"] = .optionalUUID(ascending.last?.id)
        fields["recentIds"] = .uuids(Array(ascending.reversed().prefix(5)).map(\.id))
        fields["trend"] = trend?.reflexValue ?? .null
        fields["formattedValue"] = .string(ValueFormat.reading(ascending.last))
        fields["rowIcon"] = .object(rowIcon)
        return .object(fields)
    }

    public static func iconName(_ type: VitalType) -> String {
        switch type {
        case .weight: return "weight"
        case .bloodPressure: return "blood_pressure"
        case .bloodSugar: return "blood_sugar"
        }
    }
}