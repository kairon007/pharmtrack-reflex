import Foundation
import UserNotifications

// Phase 4 live adapters for the six Core ports (contracts/services.yaml).
// Core never learns a file path, a bundle default or a notification center — all of that
// lives here. The fixture adapters in RunnerKit remain the test surface.

public struct LiveClock: Clock {
    public init() {}
    public func now() -> Instant { Instant(secondsSince1970: Date().timeIntervalSince1970) }
}

public struct LiveIDGenerator: IDGenerator {
    public init() {}
    public func next() -> UUID { UUID() }
}

/// Fires delivered messages on the main queue. A non-nil key replaces the pending entry
/// with that key; a nil key stacks (services.yaml#Scheduler).
public final class LiveScheduler: Scheduler {
    public var onFire: ((Message) -> Void)?
    private var nextToken = 0
    private var items: [Int: DispatchWorkItem] = [:]
    private var keyed: [String: Int] = [:]

    public init() {}

    @discardableResult
    public func after(seconds: Double, key: String?, node: NodeID, message: Message) -> IDCancellation {
        let token = nextToken
        nextToken += 1
        if let key {
            if let previous = keyed[key] { items.removeValue(forKey: previous)?.cancel() }
            keyed[key] = token
        }
        let item = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.items.removeValue(forKey: token)
            if let key, self.keyed[key] == token { self.keyed.removeValue(forKey: key) }
            self.onFire?(message)
        }
        items[token] = item
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: item)
        return IDCancellation(token: token)
    }

    public func cancel(_ handle: IDCancellation) {
        items.removeValue(forKey: handle.token)?.cancel()
        if let key = keyed.first(where: { $0.value == handle.token })?.key {
            keyed.removeValue(forKey: key)
        }
    }
}

public struct DefaultsKeyValueStore: KeyValueStore {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    public func bool(key: String) -> Bool? {
        guard defaults.object(forKey: key) != nil else { return nil }
        return defaults.bool(forKey: key)
    }

    public func setBool(key: String, value: Bool) { defaults.set(value, forKey: key) }
}

/// medical_data.json in the app sandbox Documents directory — the exact path v0.0.19
/// writes (persistence.yaml#file). Encoding goes through the same MedicalCodec the
/// fixture adapter round-trips, so leniency and nil-omission are identical.
public struct FileMedicalStore: MedicalStore {
    public let url: URL

    public init(url: URL? = nil) {
        self.url = url ?? FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("medical_data.json")
    }

    public func load() async throws -> MedicalData? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try MedicalCodec.decode(Data(contentsOf: url))
    }

    public func save(_ data: MedicalData) async throws {
        let bytes = try MedicalCodec.encode(data)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        try bytes.write(to: url, options: .atomic)
    }

    public func clear() async throws {
        do {
            try FileManager.default.removeItem(at: url)
        } catch let error as CocoaError where error.code == .fileNoSuchFile {
            // Idempotent: clearing an absent file is success.
        }
    }
}

/// Clear-all-then-reschedule is canonical (D1). The planner's list is the complete desired
/// set. Channels become thread identifiers; a nil triggerAt (low stock) posts immediately.
public final class LiveNotificationScheduler: NotificationScheduler {
    public init() {}

    public func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    public func replaceAll(_ notifications: [ScheduledNotification]) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        for notification in notifications {
            let content = UNMutableNotificationContent()
            content.title = notification.title
            content.body = notification.body
            content.sound = .default
            content.threadIdentifier = notification.channel

            var trigger: UNNotificationTrigger?
            if let triggerAt = notification.triggerAt {
                let date = Date(timeIntervalSince1970: triggerAt.secondsSince1970)
                let components = Calendar.current.dateComponents(
                    [.year, .month, .day, .hour, .minute], from: date
                )
                trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            }
            let request = UNNotificationRequest(
                identifier: notification.identifier, content: content, trigger: trigger
            )
            try? await center.add(request)
        }
    }
}