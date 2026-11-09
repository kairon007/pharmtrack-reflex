import Foundation

/// The single place that knows how to route an Action or Message to its machine. Reducers
/// stay pure and total; this type only swaps one node's state into the AppState.
public enum Reducer {
    public static func reduce(
        _ state: AppState,
        _ action: Action,
        _ context: MachineContext
    ) -> (AppState, [Effect]) {
        var state = state
        switch action {
        case let .appRoot(action):
            let result = AppRootMachine.reduce(state.appRoot, action, context)
            state.appRoot = result.0
            return (state, result.1)
        case let .onboarding(action):
            let result = OnboardingMachine.reduce(state.onboarding, action, context)
            state.onboarding = result.0
            return (state, result.1)
        case let .dashboard(action):
            let result = DashboardMachine.reduce(state.dashboard, action, context)
            state.dashboard = result.0
            return (state, result.1)
        case let .consultations(action):
            let result = ConsultationsMachine.reduce(state.consultations, action, context)
            state.consultations = result.0
            return (state, result.1)
        case let .medicationTracker(action):
            let result = MedicationTrackerMachine.reduce(state.medicationTracker, action, context)
            state.medicationTracker = result.0
            return (state, result.1)
        case let .appointments(action):
            let result = AppointmentsMachine.reduce(state.appointments, action, context)
            state.appointments = result.0
            return (state, result.1)
        case let .vitals(action):
            let result = VitalsMachine.reduce(state.vitals, action, context)
            state.vitals = result.0
            return (state, result.1)
        case let .vitalsDetail(action):
            let result = VitalsDetailMachine.reduce(state.vitalsDetail, action, context)
            state.vitalsDetail = result.0
            return (state, result.1)
        case let .addVital(action):
            let result = AddVitalMachine.reduce(state.addVital, action, context)
            state.addVital = result.0
            return (state, result.1)
        case let .addMedication(action):
            let result = AddMedicationMachine.reduce(state.addMedication, action, context)
            state.addMedication = result.0
            return (state, result.1)
        case let .addAppointment(action):
            let result = AddAppointmentMachine.reduce(state.addAppointment, action, context)
            state.addAppointment = result.0
            return (state, result.1)
        case let .consultationForm(action):
            let result = ConsultationFormMachine.reduce(state.consultationForm, action, context)
            state.consultationForm = result.0
            return (state, result.1)
        case let .medicationPicker(action):
            let result = MedicationPickerMachine.reduce(state.medicationPicker, action, context)
            state.medicationPicker = result.0
            return (state, result.1)
        case let .settings(action):
            let result = SettingsMachine.reduce(state.settings, action, context)
            state.settings = result.0
            return (state, result.1)
        }
    }

    public static func reduce(
        _ state: AppState,
        _ message: Message,
        _ context: MachineContext
    ) -> (AppState, [Effect]) {
        var state = state
        switch message {
        case let .appRoot(message):
            let result = AppRootMachine.reduce(state.appRoot, message, context)
            state.appRoot = result.0
            return (state, result.1)
        case let .onboarding(message):
            let result = OnboardingMachine.reduce(state.onboarding, message, context)
            state.onboarding = result.0
            return (state, result.1)
        case let .consultationForm(message):
            let result = ConsultationFormMachine.reduce(state.consultationForm, message, context)
            state.consultationForm = result.0
            return (state, result.1)
        case let .vitalsDetail(message):
            let result = VitalsDetailMachine.reduce(state.vitalsDetail, message, context)
            state.vitalsDetail = result.0
            return (state, result.1)
        }
    }
}

/// Owns the AppState and the service graph. `send`/`deliver` are synchronous: they run the
/// reducer and every immediate dispatch/deliver chain, register timers, and QUEUE the service
/// effects. Nothing persists and no timer fires until the host flushes, which is what makes
/// the journey stream identical on both platforms (SCHEMA.md#effects-are-never-implicit).
public final class RootSession {
    /// runner.yaml's fixed-point guard. Hitting it is exit 3, never a hang.
    public static let effectCap = 1000

    public private(set) var state: AppState
    public let services: Services
    public let zone: Instant.Zone
    public private(set) var pending: [Effect] = []
    public private(set) var budgetExceeded = false

    public init(state: AppState, zone: Instant.Zone, services: Services) {
        self.state = state
        self.zone = zone
        self.services = services
    }

    public convenience init(now: Instant, zone: Instant.Zone, services: Services) {
        self.init(state: AppState(now: now, zone: zone), zone: zone, services: services)
    }

    public var context: MachineContext {
        MachineContext(state: state, now: services.clock.now(), zone: zone, ids: services.ids)
    }

    public func send(_ action: Action) {
        absorb(Reducer.reduce(state, action, context))
    }

    public func deliver(_ message: Message) {
        absorb(Reducer.reduce(state, message, context))
    }

    /// Completes queued reads, writes, clears and notification replacements in FIFO order,
    /// to fixed point. Timers are NOT fired here.
    public func flushStore() async {
        var processed = 0
        while !pending.isEmpty {
            let effect = pending.removeFirst()
            processed += 1
            if processed > Self.effectCap {
                budgetExceeded = true
                return
            }
            await perform(effect)
        }
    }

    public func resetBudget() {
        budgetExceeded = false
    }

    private func absorb(_ result: (AppState, [Effect])) {
        state = result.0
        var queue = result.1
        var processed = 0
        while !queue.isEmpty {
            let effect = queue.removeFirst()
            switch effect {
            case let .dispatch(action):
                processed += 1
                if processed > Self.effectCap { budgetExceeded = true; return }
                let result = Reducer.reduce(state, action, context)
                state = result.0
                queue.append(contentsOf: result.1)
            case let .deliver(message):
                processed += 1
                if processed > Self.effectCap { budgetExceeded = true; return }
                let result = Reducer.reduce(state, message, context)
                state = result.0
                queue.append(contentsOf: result.1)
            case let .after(seconds, key, message):
                // Registering is not firing. The fixture scheduler fires on flush.
                services.scheduler.after(seconds: seconds, key: key, node: message.node, message: message)
            default:
                pending.append(effect)
            }
        }
    }

    private func perform(_ effect: Effect) async {
        switch effect {
        case .storeLoad:
            do {
                let loaded = try await services.store.load()
                deliver(.appRoot(.storeLoaded(loaded)))
            } catch {
                deliver(.appRoot(.storeLoadFailed(Self.token(error))))
            }
        case let .storeSave(data):
            do {
                try await services.store.save(data)
                deliver(.appRoot(.storeSaved))
            } catch {
                deliver(.appRoot(.storeSaveFailed(Self.token(error))))
            }
        case .storeClear:
            do {
                try await services.store.clear()
                deliver(.appRoot(.storeCleared))
            } catch {
                // A failed clear must not write the flag; the tour stays open for a retry.
                deliver(.onboarding(.clearFailed(Self.token(error))))
            }
        case let .kvRead(key):
            let value = services.keyValue.bool(key: key) ?? false
            deliver(.appRoot(.onboardingFlagRead(value)))
        case let .kvWrite(key, value):
            services.keyValue.setBool(key: key, value: value)
        case let .replaceAll(notifications):
            await services.notifications.replaceAll(notifications)
        case .dispatch, .deliver, .after:
            break
        }
    }

    /// Service error text is never compared across platforms — Swift's DecodingError and
    /// Kotlin's SerializationException can never match — so loadError carries a fixed token.
    private static func token(_ error: Error) -> String {
        if let service = error as? ServiceError {
            switch service {
            case let .injected(name): return name
            case .idsExhausted: return "idsExhausted"
            case .decodeFailed: return MedicalCodec.decodeFailureToken
            }
        }
        return MedicalCodec.decodeFailureToken
    }
}