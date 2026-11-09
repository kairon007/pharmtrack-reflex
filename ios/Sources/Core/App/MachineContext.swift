import Foundation

/// Everything a reducer may read besides its own state. `now` and `zone` come from the
/// injected Clock, never from the system; `ids` is the only source of new identifiers.
public struct MachineContext {
    public let state: AppState
    public let now: Instant
    public let zone: Instant.Zone
    public let ids: IDGenerator

    public init(state: AppState, now: Instant, zone: Instant.Zone, ids: IDGenerator) {
        self.state = state
        self.now = now
        self.zone = zone
        self.ids = ids
    }

    public var data: MedicalData { state.appRoot.data }
}