import Foundation

public struct AppointmentsState: Equatable {
    public var selectedDate: Instant
    public var weekOffset: Int
    public var formMode: FormMode
    public var editingId: UUID?

    public init(selectedDate: Instant, zone: Instant.Zone) {
        self.selectedDate = selectedDate.startOfDay(in: zone)
        weekOffset = 0
        formMode = .closed
        editingId = nil
    }
}

public enum AppointmentsAction {
    case selectDay(Instant)
    case previousWeek
    case nextWeek
    case openAdd
    case openEdit(UUID)
    case delete(UUID)
    case closeForm
    case syncFromRoot(Instant)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AppointmentsAction? {
        switch name {
        case "selectDay":
            return .selectDay(try args.instant("date"))
        case "previousWeek":
            return .previousWeek
        case "nextWeek":
            return .nextWeek
        case "openAdd":
            return .openAdd
        case "openEdit":
            return .openEdit(try args.uuid("appointmentId"))
        case "delete":
            return .delete(try args.uuid("appointmentId"))
        case "closeForm":
            return .closeForm
        default:
            return nil
        }
    }
}

public enum AppointmentsMachine {
    public static func reduce(
        _ state: AppointmentsState,
        _ action: AppointmentsAction,
        _ context: MachineContext
    ) -> (AppointmentsState, [Effect]) {
        var state = state
        switch action {
        case let .selectDay(date):
            state.selectedDate = date.startOfDay(in: context.zone)
            return (state, [])

        case .previousWeek:
            state.weekOffset -= 1
            return (state, [])

        case .nextWeek:
            state.weekOffset += 1
            return (state, [])

        case .openAdd:
            state.formMode = .add
            state.editingId = nil
            return (state, [.dispatch(.addAppointment(.setMode(.add, nil)))])

        case let .openEdit(id):
            state.formMode = .edit
            state.editingId = id
            return (state, [.dispatch(.addAppointment(.setMode(.edit, id)))])

        case let .delete(id):
            guard let index = context.data.appointments.firstIndex(where: { $0.id == id }) else {
                return (state, [])
            }
            var data = context.data
            data.appointments.remove(at: index)
            return (state, [.dispatch(.appRoot(.commitData(data, .delete)))])

        case .closeForm:
            state.formMode = .closed
            state.editingId = nil
            return (state, [])

        case let .syncFromRoot(date):
        // plan_hard_problem 5. The root owns the date; paging the strip to the new day
        // uses the same rounding as vitals_detail so the selection stays on screen.
            let day = date.startOfDay(in: context.zone)
            state.selectedDate = day
            state.weekOffset = ValueFormat.weekOffset(days: day.dayIndex(in: context.zone) - context.now.dayIndex(in: context.zone))
            return (state, [])
        }
    }

    public static func emit(_ state: AppointmentsState, _ context: MachineContext) -> ReflexValue {
        let center = Selectors.stripCenter(now: context.now, weekOffset: state.weekOffset, zone: context.zone)
        let todayIndex = context.now.dayIndex(in: context.zone)
        let selectedIndex = state.selectedDate.dayIndex(in: context.zone)
        let dayDifference = selectedIndex - todayIndex
        // Q2. Same-day predicate only — no date >= now filter, so a past day shows its visits.
        let visible = context.data.appointments.filter {
            $0.date.dayIndex(in: context.zone) == selectedIndex
        }
        let headerKind: String
        if dayDifference == 0 {
            headerKind = "today"
        } else if dayDifference > 0 {
            headerKind = "inNDays"
        } else {
            headerKind = "nDaysAgo"
        }
        var fields: [String: ReflexValue] = [:]
        fields["selectedDate"] = .instant(state.selectedDate)
        fields["weekOffset"] = .int(state.weekOffset)
        fields["formMode"] = .string(state.formMode.rawValue)
        fields["editingId"] = .optionalUUID(state.editingId)
        fields["stripCenter"] = .instant(center)
        fields["stripDates"] = .array(Selectors.dayWindow(now: context.now, weekOffset: state.weekOffset, zone: context.zone).map { .instant($0) })
        fields["visibleIds"] = .uuids(visible.map(\.id))
        fields["headerKind"] = .string(headerKind)
        fields["headerDayCount"] = .int(abs(dayDifference))
        fields["emptyMessageDate"] = .instant(state.selectedDate)
        return .object(fields)
    }
}