import Foundation

public enum DashboardSheet: String, CaseIterable {
    case none, addConsultation, addMedication, addAppointment, addVital, settings
}

public enum QuickActionKind: String, CaseIterable {
    case doctor, meds, visit, vitals

    var sheet: DashboardSheet {
        switch self {
        case .doctor: return .addConsultation
        case .meds: return .addMedication
        case .visit: return .addAppointment
        case .vitals: return .addVital
        }
    }
}

public enum HeroBranch: String { case meds, appointment, empty }

public struct DashboardState: Equatable {
    public var presentedSheet: DashboardSheet

    public init() {
        presentedSheet = .none
    }
}

public enum DashboardAction {
    case markNextTaken
    case tapHeroAppointment(UUID)
    case tapUpcomingRow(UUID)
    case tapUpcomingHeader
    case tapRecentActivityViewAll
    case quickAction(QuickActionKind)
    case presentSheet(DashboardSheet)
    case dismissSheet

    public static func decode(_ name: String, _ args: ActionArguments) throws -> DashboardAction? {
        switch name {
        case "markNextTaken":
            return .markNextTaken
        case "tapHeroAppointment":
            return .tapHeroAppointment(try args.uuid("appointmentId"))
        case "tapUpcomingRow":
            return .tapUpcomingRow(try args.uuid("appointmentId"))
        case "tapUpcomingHeader":
            return .tapUpcomingHeader
        case "tapRecentActivityViewAll":
            return .tapRecentActivityViewAll
        case "quickAction":
            return .quickAction(try args.enumeration("kind", QuickActionKind.self))
        case "presentSheet":
            return .presentSheet(try args.enumeration("sheet", DashboardSheet.self))
        case "dismissSheet":
            return .dismissSheet
        default:
            return nil
        }
    }
}

public enum DashboardMachine {
    /// Q1, D16. Sorted by (nextDose ?? distantFuture, id) with NO active filter and NO future
    /// filter, so a dose three hours in the past still renders as "Next dose".
    public static func nextMedication(_ context: MachineContext) -> Medication? {
        context.data.medications.sorted { lhs, rhs in
            let lhsDose = lhs.nextDose ?? .distantFuture
            let rhsDose = rhs.nextDose ?? .distantFuture
            if lhsDose != rhsDose { return lhsDose < rhsDose }
            return lhs.id.uuidString < rhs.id.uuidString
        }.first
    }

    /// Q2. Inclusive >=, no notify check, ascending (date, id), capped at three.
    public static func upcomingAppointments(_ context: MachineContext) -> [Appointment] {
        Array(Selectors.sortedByDateAndId(
            context.data.appointments.filter { $0.date >= context.now },
            date: \.date,
            id: \.id
        ).prefix(3))
    }

    public static func reduce(
        _ state: DashboardState,
        _ action: DashboardAction,
        _ context: MachineContext
    ) -> (DashboardState, [Effect]) {
        var state = state
        switch action {
        case .markNextTaken:
        // D6. The guard lives in medication_tracker's reducer, which both entry points
        // reach, so a repeated hero tap cannot drain stock.
            guard let medication = nextMedication(context) else { return (state, []) }
            return (state, [.dispatch(.medicationTracker(.markTaken(medication.id)))])

        case let .tapHeroAppointment(id), let .tapUpcomingRow(id):
        // Both jump sites are fed from the upcoming list, so a past appointment is
        // unreachable and a direct action carrying one is a no-op.
            guard upcomingAppointments(context).contains(where: { $0.id == id }) else {
                return (state, [])
            }
            return (state, [.dispatch(.appRoot(.jumpToAppointment(id)))])

        case .tapUpcomingHeader:
        // DashboardView.swift:286 sets the tab only; the stored date keeps its value.
            return (state, [.dispatch(.appRoot(.selectTab(3)))])

        case .tapRecentActivityViewAll:
        // D12. One root action sets tab 2 AND the History segment.
            return (state, [.dispatch(.appRoot(.jumpToMedicationHistory))])

        case let .quickAction(kind):
            state.presentedSheet = kind.sheet
            return (state, [])

        case let .presentSheet(sheet):
            state.presentedSheet = sheet
            if sheet == .settings {
                return (state, [.dispatch(.appRoot(.presentSettings))])
            }
            return (state, [])

        case .dismissSheet:
            state.presentedSheet = .none
            return (state, [])
        }
    }

    public static func emit(_ state: DashboardState, _ context: MachineContext) -> ReflexValue {
        let medications = context.data.medications
        let total = medications.count
        let taken = medications.filter {
            Selectors.isTaken(medicationId: $0.id, data: context.data, now: context.now, zone: context.zone)
        }.count
        let upcoming = upcomingAppointments(context)
        let next = nextMedication(context)
        let hero: HeroBranch
        if !medications.isEmpty {
            hero = .meds
        } else if upcoming.first != nil {
            hero = .appointment
        } else {
            hero = .empty
        }
        var fields: [String: ReflexValue] = [:]
        fields["presentedSheet"] = .string(state.presentedSheet.rawValue)
        fields["greeting"] = .string(ValueFormat.greeting(hour: context.now.hour(in: context.zone)))
        fields["todaysMedsCount"] = .int(total)
        fields["takenTodayCount"] = .int(taken)
        fields["takenFraction"] = .double(total == 0 ? 0.0 : Double(taken) / Double(total))
        fields["takenLabel"] = .string("\(taken) of \(total) taken")
        fields["nextMedicationId"] = .optionalUUID(next?.id)
        fields["nextMedIsTaken"] = .bool(next.map {
            Selectors.isTaken(medicationId: $0.id, data: context.data, now: context.now, zone: context.zone)
        } ?? false)
        fields["upcomingAppointmentIds"] = .uuids(upcoming.map(\.id))
        fields["recentLogIds"] = .uuids(Array(context.data.logs.prefix(5)).map(\.id))
        fields["heroBranch"] = .string(hero.rawValue)
        return .object(fields)
    }
}