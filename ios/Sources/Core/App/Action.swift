import Foundation

/// One synchronous intent, addressed to exactly one node. The wrapper exists so the runtime
/// can queue a heterogeneous effect list without losing the destination.
public enum Action {
    case appRoot(AppRootAction)
    case onboarding(OnboardingAction)
    case dashboard(DashboardAction)
    case consultations(ConsultationsAction)
    case medicationTracker(MedicationTrackerAction)
    case appointments(AppointmentsAction)
    case vitals(VitalsAction)
    case vitalsDetail(VitalsDetailAction)
    case addVital(AddVitalAction)
    case addMedication(AddMedicationAction)
    case addAppointment(AddAppointmentAction)
    case consultationForm(ConsultationFormAction)
    case medicationPicker(MedicationPickerAction)
    case settings(SettingsAction)

    public var node: NodeID {
        switch self {
        case .appRoot: return .appRoot
        case .onboarding: return .onboarding
        case .dashboard: return .dashboard
        case .consultations: return .consultations
        case .medicationTracker: return .medicationTracker
        case .appointments: return .appointments
        case .vitals: return .vitals
        case .vitalsDetail: return .vitalsDetail
        case .addVital: return .addVital
        case .addMedication: return .addMedication
        case .addAppointment: return .addAppointment
        case .consultationForm: return .consultationForm
        case .medicationPicker: return .medicationPicker
        case .settings: return .settings
        }
    }

    /// Internal cases (showHistory, syncFromRoot, present, seedDraft, open) are deliberately
    /// absent from every node's decoder: a journey may not reach them, so they exit 2.
    public static func decode(_ args: ActionArguments) throws -> Action {
        let name = args.action
        switch args.node {
        case .appRoot:
            return .appRoot(try require(AppRootAction.decode(name, args), args))
        case .onboarding:
            return .onboarding(try require(OnboardingAction.decode(name, args), args))
        case .dashboard:
            return .dashboard(try require(DashboardAction.decode(name, args), args))
        case .consultations:
            return .consultations(try require(ConsultationsAction.decode(name, args), args))
        case .medicationTracker:
            return .medicationTracker(try require(MedicationTrackerAction.decode(name, args), args))
        case .appointments:
            return .appointments(try require(AppointmentsAction.decode(name, args), args))
        case .vitals:
            return .vitals(try require(VitalsAction.decode(name, args), args))
        case .vitalsDetail:
            return .vitalsDetail(try require(VitalsDetailAction.decode(name, args), args))
        case .addVital:
            return .addVital(try require(AddVitalAction.decode(name, args), args))
        case .addMedication:
            return .addMedication(try require(AddMedicationAction.decode(name, args), args))
        case .addAppointment:
            return .addAppointment(try require(AddAppointmentAction.decode(name, args), args))
        case .consultationForm:
            return .consultationForm(try require(ConsultationFormAction.decode(name, args), args))
        case .medicationPicker:
            return .medicationPicker(try require(MedicationPickerAction.decode(name, args), args))
        case .settings:
            return .settings(try require(SettingsAction.decode(name, args), args))
        }
    }

    private static func require<T>(_ decoded: T?, _ args: ActionArguments) throws -> T {
        guard let decoded else {
            throw ActionError.unknownAction(node: args.node, name: args.action)
        }
        return decoded
    }
}