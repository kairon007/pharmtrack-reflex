import Foundation

/// The 14 machines. Raw values are the spec's node names and appear verbatim in the stream.
public enum NodeID: String, CaseIterable, Hashable {
    case appRoot = "app_root"
    case onboarding
    case dashboard
    case consultations
    case medicationTracker = "medication_tracker"
    case appointments
    case vitals
    case vitalsDetail = "vitals_detail"
    case addVital = "add_vital"
    case addMedication = "add_medication"
    case addAppointment = "add_appointment"
    case consultationForm = "consultation_form"
    case medicationPicker = "medication_picker"
    case settings

    /// Tab index for the five tab nodes, nil for overlays, pushes and modals.
    public var tabIndex: Int? {
        switch self {
        case .dashboard: return 0
        case .consultations: return 1
        case .medicationTracker: return 2
        case .appointments: return 3
        case .vitals: return 4
        default: return nil
        }
    }
}

public enum CommitReason: String {
    case add, update, delete, markTaken, settings, seed
}

public enum LoadPhase: String {
    case idle, loading, loaded, unreadable
}

public enum FormMode: String {
    case closed, add, edit
}

public enum EditMode: String {
    case add, edit
}