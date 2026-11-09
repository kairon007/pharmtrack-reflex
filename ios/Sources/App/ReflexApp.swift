import Combine
import Foundation

/// The live host: owns one RootSession, republishes its AppState after every reduction and
/// every flushed service effect, and fires live scheduler messages back into the session.
/// Views only read `state` / the typed screen data below and call `send(_:)`.
@MainActor
public final class ReflexApp: ObservableObject {
    @Published public private(set) var state: AppState
    public let session: RootSession
    private let scheduler = LiveScheduler()
    private var draining = false

    public init() {
        // Instant.Zone models a fixed offset; the live host pins the device's current one.
        let zone = Instant.Zone(offsetSeconds: TimeZone.current.secondsFromGMT())
        let services = Services(
            clock: LiveClock(),
            ids: LiveIDGenerator(),
            scheduler: scheduler,
            keyValue: DefaultsKeyValueStore(),
            store: FileMedicalStore(),
            notifications: LiveNotificationScheduler()
        )
        session = RootSession(now: services.clock.now(), zone: zone, services: services)
        state = session.state
        scheduler.onFire = { [weak self] message in
            Task { @MainActor in self?.receive(message) }
        }
    }

    public var context: MachineContext { session.context }
    public var data: MedicalData { state.appRoot.data }

    /// Entry point from the app shell's onAppear.
    public func start() {
        send(.appRoot(.onAppear))
        Task { _ = await session.services.notifications.requestAuthorization() }
    }

    public func send(_ action: Action) {
        session.send(action)
        publish()
        drain()
    }

    private func receive(_ message: Message) {
        session.deliver(message)
        publish()
        drain()
    }

    private func publish() { state = session.state }

    /// One drain at a time; the session's FIFO loop picks up effects queued mid-flush.
    private func drain() {
        guard !draining else { return }
        draining = true
        Task { @MainActor in
            await session.flushStore()
            draining = false
            publish()
        }
    }
}