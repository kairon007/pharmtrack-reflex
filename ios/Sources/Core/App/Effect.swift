import Foundation

/// Effects are the only way a reducer reaches the outside world.
///
/// `dispatch` and `deliver` are serviced IMMEDIATELY and synchronously, in order, before the
/// step record is emitted — that is what makes `markTaken` mutate `data` and consume its id
/// before any flush (medication_lifecycle asserts both right after `send`). Everything else
/// is deferred onto one of two FIFO queues and only runs on `flush`.
public enum Effect {
    case dispatch(Action)
    case deliver(Message)

    case storeLoad
    case storeSave(MedicalData)
    case storeClear
    case kvRead(String)
    case kvWrite(String, Bool)
    case replaceAll([ScheduledNotification])

    case after(seconds: Double, key: String?, message: Message)

    public var isImmediate: Bool {
        switch self {
        case .dispatch, .deliver: return true
        default: return false
        }
    }

    /// Timers drain on `flush: scheduler`; service calls drain on `flush: store`.
    public var isTimer: Bool {
        if case .after = self { return true }
        return false
    }
}