import Foundation

public struct AppRootState: Equatable {
    public var loadPhase: LoadPhase
    public var loadError: String?
    public var data: MedicalData
    public var selectedTab: Int
    public var selectedAppointmentDate: Instant
    public var hasCompletedOnboarding: Bool
    public var settingsPresented: Bool

    public init(now: Instant) {
        loadPhase = .idle
        loadError = nil
        data = MedicalData()
        selectedTab = 0
        selectedAppointmentDate = now
        hasCompletedOnboarding = false
        settingsPresented = false
    }

    public var isOnboarding: Bool { !hasCompletedOnboarding }
}

public enum AppRootAction {
    case onAppear
    case selectTab(Int)
    case jumpToAppointment(UUID)
    case jumpToMedicationHistory
    case presentSettings
    case dismissSettings
    case commitData(MedicalData, CommitReason)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AppRootAction? {
        switch name {
        case "onAppear":
            return .onAppear
        case "selectTab":
            let index = try args.int("index")
            guard (0...4).contains(index) else {
                throw ActionError.invalidArgument(
                    node: args.node, action: args.action, key: "index", detail: "expected 0...4"
                )
            }
            return .selectTab(index)
        case "jumpToAppointment":
            return .jumpToAppointment(try args.uuid("appointmentId"))
        case "jumpToMedicationHistory":
            return .jumpToMedicationHistory
        case "presentSettings":
            return .presentSettings
        case "dismissSettings":
            return .dismissSettings
        default:
            return nil
        }
    }
}

public enum AppRootMessage {
    case storeLoaded(MedicalData?)
    case storeLoadFailed(String)
    case storeSaved
    case storeSaveFailed(String)
    case storeCleared
    case onboardingFlagRead(Bool)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> AppRootMessage? {
        switch name {
        case "storeSaved":
            return .storeSaved
        case "storeCleared":
            return .storeCleared
        case "onboardingFlagRead":
            return .onboardingFlagRead(try args.bool("value"))
        default:
            return nil
        }
    }
}

public enum AppRootMachine {
    public static func reduce(
        _ state: AppRootState,
        _ action: AppRootAction,
        _ context: MachineContext
    ) -> (AppRootState, [Effect]) {
        var state = state
        switch action {
        case .onAppear:
            guard state.loadPhase == .idle else { return (state, []) }
            state.loadPhase = .loading
        // FIFO: the flag must be known before the load result picks a branch.
            return (state, [.kvRead(KeyValueKey.hasCompletedOnboarding), .storeLoad])

        case let .selectTab(index):
            let effects = selectTab(index, state: &state, context: context)
            return (state, effects)

        case let .jumpToAppointment(id):
            guard let appointment = state.data.appointment(id) else { return (state, []) }
            let effects = selectTab(3, state: &state, context: context)
            state.selectedAppointmentDate = appointment.date
            return (state, effects + [.dispatch(.appointments(.syncFromRoot(appointment.date)))])

        case .jumpToMedicationHistory:
        // D12. Tab 2 AND the History segment, so a second jump cannot land on Inventory.
            let effects = selectTab(2, state: &state, context: context)
            return (state, effects + [.dispatch(.medicationTracker(.showHistory))])

        case .presentSettings:
            state.settingsPresented = true
            return (state, [.dispatch(.settings(.seedDraft(state.data.notificationSettings)))])

        case .dismissSettings:
            state.settingsPresented = false
            return (state, [.dispatch(.dashboard(.dismissSheet))])

        case let .commitData(data, _):
            state.data = data
        // D1. Save first, then hand the planner's COMPLETE desired set to replaceAll.
            return (state, [.storeSave(data), .replaceAll(NotificationPlanner.plan(data: data, now: context.now))])
        }
    }

    public static func reduce(
        _ state: AppRootState,
        _ message: AppRootMessage,
        _ context: MachineContext
    ) -> (AppRootState, [Effect]) {
        var state = state
        switch message {
        case let .onboardingFlagRead(value):
            state.hasCompletedOnboarding = value
            return (state, [])

        case let .storeLoaded(loaded):
            if let loaded {
                state.data = loaded
                state.loadPhase = .loaded
                state.loadError = nil
                // A successful load reschedules but does NOT re-save the file.
                return (state, loadedEffects(state, context))
            }
            if state.isOnboarding {
                return (seedDemo(state: &state, context: context))
            }
            state.data = MedicalData()
            state.loadPhase = .loaded
            state.loadError = nil
            return (state, loadedEffects(state, context))

        case .storeLoadFailed:
            if state.isOnboarding {
                // A parse error and an absent file are the same branch before onboarding.
                return (seedDemo(state: &state, context: context))
            }
        // THE DATA-LOSS GUARD. Empty in memory, bad file untouched on disk, no demo seed.
            state.data = MedicalData()
            state.loadPhase = .unreadable
            state.loadError = MedicalCodec.decodeFailureToken
            return (state, loadedEffects(state, context))

        case .storeSaved, .storeSaveFailed:
        // The replan already happened when the data was committed; saving replans nothing.
            return (state, [])

        case .storeCleared:
            state.data = MedicalData()
            state.hasCompletedOnboarding = true
        // flag_order: the clear completed, so the flag is written now — not after a sleep.
        // No save: the file is gone, and replanning the empty set cancels the demo reminders.
            return (state, [
                .deliver(.onboarding(.clearCompleted)),
                .kvWrite(KeyValueKey.hasCompletedOnboarding, true),
                .replaceAll(NotificationPlanner.plan(data: state.data, now: context.now)),
            ])
        }
    }

    public static func emit(_ state: AppRootState, _ context: MachineContext) -> ReflexValue {
        .object([
            "loadPhase": .string(state.loadPhase.rawValue),
            "loadError": .optionalString(state.loadError),
            "data": state.data.reflexValue,
            "selectedTab": .int(state.selectedTab),
            "selectedAppointmentDate": .instant(state.selectedAppointmentDate),
            "hasCompletedOnboarding": .bool(state.hasCompletedOnboarding),
            "settingsPresented": .bool(state.settingsPresented),
            "isOnboarding": .bool(state.isOnboarding),
        ])
    }

    /// MainTabView.swift:107-118 — every selection change hides the overlay and requeues the
    /// reveal, including the tour's own step. A non-nil scheduler key replaces the pending one.
    private static func selectTab(
        _ index: Int,
        state: inout AppRootState,
        context: MachineContext
    ) -> [Effect] {
        state.selectedTab = index
        guard state.isOnboarding else { return [] }
        return [.dispatch(.onboarding(.tabChanged))]
    }

    private static func seedDemo(
        state: inout AppRootState,
        context: MachineContext
    ) -> (AppRootState, [Effect]) {
        // Reserved seed ids, zero draws from the generator sequence.
        let seed = DemoSeed.data(now: context.now, zone: context.zone)
        state.data = seed
        state.loadPhase = .loaded
        state.loadError = nil
        // The seed persists, which is why demo reminders are live before the tour ends.
        return (state, [.storeSave(seed)] + loadedEffects(state, context))
    }

    private static func loadedEffects(_ state: AppRootState, _ context: MachineContext) -> [Effect] {
        var effects: [Effect] = [.replaceAll(NotificationPlanner.plan(data: state.data, now: context.now))]
        if state.loadPhase == .loaded && state.isOnboarding {
            effects.append(OnboardingMachine.revealEffect())
        }
        return effects
    }
}