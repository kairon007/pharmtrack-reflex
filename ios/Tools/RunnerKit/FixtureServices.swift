import Core
import Foundation

/// The pinned clock. `advance` is driven by the spec, never by Core (services.yaml#Clock).
public final class FixtureClock: Clock {
    public private(set) var instant: Instant

    public init(now: Instant) {
        instant = now
    }

    public func now() -> Instant { instant }

    public func advance(seconds: Double) {
        instant = instant.addingSeconds(seconds)
    }

    public func reset(to instant: Instant) {
        self.instant = instant
    }
}

/// Hands out determinism.yaml#ids.sequence in order. Exhaustion is recorded, never wrapped;
/// the executor turns it into exit 3. `next()` stays non-throwing so reducers remain total.
public final class FixtureIDGenerator: IDGenerator {
    public static let exhaustedSentinel = UUID(uuidString: "00000000-0000-4000-8000-00000000DEAD")!

    private let sequence: [UUID]
    private var index = 0
    public private(set) var handedOut: [UUID] = []
    public private(set) var consumed = 0
    public private(set) var isExhausted = false

    public init(sequence: [UUID]) {
        self.sequence = sequence
    }

    public func next() -> UUID {
        guard index < sequence.count else {
            isExhausted = true
            return Self.exhaustedSentinel
        }
        let id = sequence[index]
        index += 1
        consumed += 1
        handedOut.append(id)
        return id
    }

    /// SCHEMA.md#setup: counters are zeroed at the end of setup. The sequence position is NOT
    /// rewound, so a journey never sees an id that setup already handed out.
    public func resetCounters() {
        consumed = 0
        handedOut = []
    }
}

public struct TimerEntry {
    public let token: Int
    public let seconds: Double
    public let key: String?
    public let message: Message
}

/// Queues after() entries; nothing fires until drain(). A non-nil key REPLACES the pending
/// entry with that key, which is how the onboarding reveal cancels its own predecessor
/// instead of stacking uncancelled Tasks the way MainTabView.swift:107-118 did.
public final class FixtureScheduler: Scheduler {
    private var entries: [TimerEntry] = []
    private var nextToken = 0
    public private(set) var registeredCount = 0

    public init() {}

    @discardableResult
    public func after(seconds: Double, key: String?, node: NodeID, message: Message) -> IDCancellation {
        if let key {
            entries.removeAll { $0.key == key }
        }
        nextToken += 1
        registeredCount += 1
        entries.append(TimerEntry(token: nextToken, seconds: seconds, key: key, message: message))
        return IDCancellation(token: nextToken)
    }

    public func cancel(_ handle: IDCancellation) {
        entries.removeAll { $0.token == handle.token }
    }

    /// Fires every pending entry in insertion order and hands the messages back. The
    /// executor delivers them, which may register new entries; it drains to fixed point.
    public func drain() -> [Message] {
        let fired = entries.map(\.message)
        entries.removeAll()
        return fired
    }

    public var pendingCount: Int { entries.count }

    public func reset() {
        entries.removeAll()
        nextToken = 0
        registeredCount = 0
    }
}

public final class FixtureKeyValueStore: KeyValueStore {
    public private(set) var values: [String: Bool]

    public init(values: [String: Bool]) {
        self.values = values
    }

    public func bool(key: String) -> Bool? { values[key] }

    public func setBool(key: String, value: Bool) { values[key] = value }
}

/// The medical_data.json slot. save() runs the platform's REAL encoder and then the REAL
/// decoder, so `saved` is a genuine decode(encode(x)) round-trip (runner.yaml#fixture_adapters).
public final class FixtureMedicalStore: MedicalStore {
    public enum Mode {
        case normal
        case absent
        case unreadable
    }

    public private(set) var slot: Data?
    public private(set) var mode: Mode
    public private(set) var saved: MedicalData?
    public private(set) var writeCount = 0
    private var armedFailure: String?

    public init(slot: Data?, mode: Mode = .normal) {
        self.slot = slot
        self.mode = mode
    }

    public var filePresent: Bool {
        switch mode {
        case .absent: return false
        case .unreadable: return true
        case .normal: return slot != nil
        }
    }

    /// SCHEMA.md's `inject: { service: store, fail_next: <error> }` names no operation, so the
    /// armed failure is consumed by whichever store operation runs next.
    public func armFailure(_ error: String) {
        armedFailure = error
    }

    private func consumeFailure() throws {
        guard let name = armedFailure else { return }
        armedFailure = nil
        throw ServiceError.injected(name)
    }

    public func load() async throws -> MedicalData? {
        try consumeFailure()
        switch mode {
        case .absent:
            return nil
        case .unreadable:
            throw ServiceError.decodeFailed
        case .normal:
            guard let slot else { return nil }
            return try MedicalCodec.decode(slot)
        }
    }

    public func save(_ data: MedicalData) async throws {
        try consumeFailure()
        let bytes = try MedicalCodec.encode(data)
        slot = bytes
        mode = .normal
        saved = try MedicalCodec.decode(bytes)
        writeCount += 1
    }

    /// Idempotent. A removal is not a write, so writeCount is untouched.
    public func clear() async throws {
        try consumeFailure()
        slot = nil
        mode = .normal
    }

    public func resetCounters() {
        writeCount = 0
    }
}

/// Records the last replaceAll argument and counts invocations. Never delivers anything.
public final class FixtureNotificationScheduler: NotificationScheduler {
    public private(set) var scheduled: [ScheduledNotification] = []
    public private(set) var replaceCount = 0
    public var authorized = true

    public init() {}

    public func requestAuthorization() async -> Bool { authorized }

    public func replaceAll(_ notifications: [ScheduledNotification]) async {
        scheduled = notifications
        replaceCount += 1
    }

    public func resetCounters() {
        replaceCount = 0
    }
}

/// The whole fixture graph for one case. Cases never share state (SCHEMA.md#given).
public final class FixtureServices {
    public let clock: FixtureClock
    public let ids: FixtureIDGenerator
    public let scheduler: FixtureScheduler
    public let keyValue: FixtureKeyValueStore
    public let store: FixtureMedicalStore
    public let notifications: FixtureNotificationScheduler

    public init(
        now: Instant,
        idSequence: [UUID],
        keyValues: [String: Bool],
        slot: Data?,
        mode: FixtureMedicalStore.Mode
    ) {
        clock = FixtureClock(now: now)
        ids = FixtureIDGenerator(sequence: idSequence)
        scheduler = FixtureScheduler()
        keyValue = FixtureKeyValueStore(values: keyValues)
        store = FixtureMedicalStore(slot: slot, mode: mode)
        notifications = FixtureNotificationScheduler()
    }

    public var ports: Services {
        Services(
            clock: clock,
            ids: ids,
            scheduler: scheduler,
            keyValue: keyValue,
            store: store,
            notifications: notifications
        )
    }

    public func resetCounters() {
        ids.resetCounters()
        store.resetCounters()
        notifications.resetCounters()
    }
}