import Foundation

public enum MedicationSegment: String, CaseIterable {
    case today, inventory, history
}

public struct MedicationTrackerState: Equatable {
    public var segment: MedicationSegment
    public var searchQuery: String
    public var formMode: FormMode
    public var editingId: UUID?

    public init() {
        segment = .today
        searchQuery = ""
        formMode = .closed
        editingId = nil
    }
}

public enum MedicationTrackerAction {
    case setSegment(MedicationSegment)
    case setSearch(String)
    case markTaken(UUID)
    case incrementStock(UUID)
    case decrementStock(UUID)
    case openAdd
    case openEdit(UUID)
    case closeForm
    /// Internal: the dashboard's "View all" jump lands on History (D12).
    case showHistory

    public static func decode(_ name: String, _ args: ActionArguments) throws -> MedicationTrackerAction? {
        switch name {
        case "setSegment":
            return .setSegment(try args.enumeration("segment", MedicationSegment.self))
        case "setSearch":
            return .setSearch(try args.string("text"))
        case "markTaken":
            return .markTaken(try args.uuid("medicationId"))
        case "incrementStock":
            return .incrementStock(try args.uuid("medicationId"))
        case "decrementStock":
            return .decrementStock(try args.uuid("medicationId"))
        case "openAdd":
            return .openAdd
        case "openEdit":
            return .openEdit(try args.uuid("medicationId"))
        case "closeForm":
            return .closeForm
        default:
            return nil
        }
    }
}

public enum MedicationTrackerMachine {
    public static func reduce(
        _ state: MedicationTrackerState,
        _ action: MedicationTrackerAction,
        _ context: MachineContext
    ) -> (MedicationTrackerState, [Effect]) {
        var state = state
        switch action {
        case let .setSegment(segment):
            state.segment = segment
            return (state, [])

        case let .setSearch(text):
            state.searchQuery = text
            return (state, [])

        case .showHistory:
            state.segment = .history
            return (state, [])

        case let .markTaken(id):
        // D6. The guard lives in the REDUCER, so it covers the tracker row AND the
        // dashboard hero button. A second mark on the same day writes nothing.
            guard let index = context.data.medications.firstIndex(where: { $0.id == id }),
                  !Selectors.isTaken(
                      medicationId: id, data: context.data, now: context.now, zone: context.zone
                  ) else {
                return (state, [])
            }
            var data = context.data
            let medication = data.medications[index]
            data.medications[index].stock = max(0, medication.stock - 1)
        // Q5. now + 86400s regardless of the schedule string.
            data.medications[index].nextDose = context.now.addingSeconds(86_400)
            data.logs.insert(
                Log(
                    id: context.ids.next(),
                    type: "Medication Taken",
                    description: "\(medication.name) Dose Taken",
                    date: context.now,
                    source: "Manual Log",
                    medicationId: id
                ),
                at: 0
            )
            return (state, [.dispatch(.appRoot(.commitData(data, .markTaken)))])

        case let .incrementStock(id):
            guard let index = context.data.medications.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
            var data = context.data
        // No ceiling.
            data.medications[index].stock += 1
            return (state, [.dispatch(.appRoot(.commitData(data, .update)))])

        case let .decrementStock(id):
            guard let index = context.data.medications.firstIndex(where: { $0.id == id }),
                  context.data.medications[index].stock > 0 else {
                return (state, [])
            }
            var data = context.data
            data.medications[index].stock -= 1
            return (state, [.dispatch(.appRoot(.commitData(data, .update)))])

        case .openAdd:
            state.formMode = .add
            state.editingId = nil
            return (state, [.dispatch(.addMedication(.setMode(.add, nil)))])

        case let .openEdit(id):
            state.formMode = .edit
            state.editingId = id
            return (state, [.dispatch(.addMedication(.setMode(.edit, id)))])

        case .closeForm:
            state.formMode = .closed
            state.editingId = nil
            return (state, [])
        }
    }

    public static func emit(_ state: MedicationTrackerState, _ context: MachineContext) -> ReflexValue {
        let data = context.data
        let all = data.medications
        // No active filter, no sort, no limit. Search is name OR instructions, not trimmed.
        let filtered = all.filter {
            Selectors.matches($0.name, query: state.searchQuery)
                || Selectors.matches($0.instructions, query: state.searchQuery)
        }

        var groups: [String: [UUID]] = [
            MedicationSchedule.morning.rawValue: [],
            MedicationSchedule.afternoon.rawValue: [],
            MedicationSchedule.evening.rawValue: [],
        ]
        for medication in filtered {
        // Q3. Case-sensitive string equality. Any other schedule is in NO group but is
        // still counted in the total.
            if groups[medication.schedule] != nil {
                groups[medication.schedule, default: []].append(medication.id)
            }
        }

        let taken = filtered.filter {
            Selectors.isTaken(medicationId: $0.id, data: data, now: context.now, zone: context.zone)
        }.count
        let total = filtered.count

        var isTakenMap: [String: ReflexValue] = [:]
        var isLowStockMap: [String: ReflexValue] = [:]
        for medication in all {
            isTakenMap[medication.id.uuidString] = .bool(
                Selectors.isTaken(
                    medicationId: medication.id, data: data, now: context.now, zone: context.zone
                )
            )
            isLowStockMap[medication.id.uuidString] = .bool(
                Selectors.isLowStock(medication: medication, settings: data.notificationSettings)
            )
        }

        var fields: [String: ReflexValue] = [:]
        fields["segment"] = .string(state.segment.rawValue)
        fields["searchQuery"] = .string(state.searchQuery)
        fields["formMode"] = .string(state.formMode.rawValue)
        fields["editingId"] = .optionalUUID(state.editingId)
        fields["filteredMedications"] = .uuids(filtered.map(\.id))
        fields["todayGroups"] = .object(groups.mapValues { .uuids($0) })
        fields["takenTodayCount"] = .int(taken)
        fields["totalCount"] = .int(total)
        fields["takenFraction"] = .double(total == 0 ? 0.0 : Double(taken) / Double(total))
        fields["takenLabel"] = .string("\(taken) of \(total) taken today")
        fields["allTaken"] = .bool(taken == total && total > 0)
        fields["isTaken"] = .object(isTakenMap)
        fields["isLowStock"] = .object(isLowStockMap)
        // Q4. History reads data.logs directly: storage order, no limit, no date filter,
        // and the search query is IGNORED.
        fields["historyLogIds"] = .uuids(data.logs.filter { $0.type == "Medication Taken" }.map(\.id))
        return .object(fields)
    }
}