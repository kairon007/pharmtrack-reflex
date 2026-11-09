import Foundation

/// Service ports from specs/contracts/services.yaml. Core depends only on these; the live
/// adapters (Phase 4) and the fixture adapters (RunnerKit) live outside Core.
///
/// The impure operations are async because the file and the notification center are async
/// boundaries. The fixture adapters complete without suspending, so the runtime's FIFO drain
/// is fully deterministic and the headless runner never observes wall-clock timing.
public protocol Clock {
    func now() -> Instant
}

public protocol IDGenerator {
    /// Non-throwing so reducers stay total ("reducers do not throw"). The fixture adapter
    /// records exhaustion instead of wrapping, and the runtime turns that into exit code 3.
    func next() -> UUID
}

public struct IDCancellation {
    public let token: Int

    public init(token: Int) {
        self.token = token
    }
}

public protocol Scheduler {
    /// A non-nil key REPLACES any pending entry with that key (the onboarding reveal).
    /// A nil key stacks (the vitals_detail highlight clear, guarded by its carried id).
    @discardableResult
    func after(seconds: Double, key: String?, node: NodeID, message: Message) -> IDCancellation
    func cancel(_ handle: IDCancellation)
}

public protocol KeyValueStore {
    func bool(key: String) -> Bool?
    func setBool(key: String, value: Bool)
}

public protocol MedicalStore {
    /// nil when the file is absent; throws when present but unparseable. Never partial data.
    func load() async throws -> MedicalData?
    func save(_ data: MedicalData) async throws
    /// Idempotent — clearing an absent file is success, not an error.
    func clear() async throws
}

public protocol NotificationScheduler {
    func requestAuthorization() async -> Bool
    /// CANONICAL: cancel every pending notification, then schedule the supplied set.
    func replaceAll(_ notifications: [ScheduledNotification]) async
}

public struct Services {
    public let clock: Clock
    public let ids: IDGenerator
    public let scheduler: Scheduler
    public let keyValue: KeyValueStore
    public let store: MedicalStore
    public let notifications: NotificationScheduler

    public init(
        clock: Clock,
        ids: IDGenerator,
        scheduler: Scheduler,
        keyValue: KeyValueStore,
        store: MedicalStore,
        notifications: NotificationScheduler
    ) {
        self.clock = clock
        self.ids = ids
        self.scheduler = scheduler
        self.keyValue = keyValue
        self.store = store
        self.notifications = notifications
    }
}

public enum ServiceError: Error, Equatable {
    case idsExhausted
    case injected(String)
    case decodeFailed
}

public enum KeyValueKey {
    /// The legacy key. Not renamed or namespaced on upgrade (persistence.yaml#key_value_store).
    public static let hasCompletedOnboarding = "hasCompletedOnboarding"
}