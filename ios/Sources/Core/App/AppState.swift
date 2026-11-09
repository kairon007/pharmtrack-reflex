import Foundation

/// The whole application state: one immutable value per node. Reducers receive the full
/// AppState through MachineContext so a tab can read the root's data and the root can read
/// a tab's selection without either owning the other.
public struct AppState: Equatable {
    public var appRoot: AppRootState
    public var onboarding: OnboardingState
    public var dashboard: DashboardState
    public var consultations: ConsultationsState
    public var medicationTracker: MedicationTrackerState
    public var appointments: AppointmentsState
    public var vitals: VitalsState
    public var vitalsDetail: VitalsDetailState
    public var addVital: AddVitalState
    public var addMedication: AddMedicationState
    public var addAppointment: AddAppointmentState
    public var consultationForm: ConsultationFormState
    public var medicationPicker: MedicationPickerState
    public var settings: SettingsState

    public init(now: Instant, zone: Instant.Zone = .utc) {
        appRoot = AppRootState(now: now)
        onboarding = OnboardingState()
        dashboard = DashboardState()
        consultations = ConsultationsState()
        medicationTracker = MedicationTrackerState()
        appointments = AppointmentsState(selectedDate: now, zone: zone)
        vitals = VitalsState()
        vitalsDetail = VitalsDetailState(now: now, zone: zone)
        addVital = AddVitalState(now: now)
        addMedication = AddMedicationState()
        addAppointment = AddAppointmentState(date: now)
        consultationForm = ConsultationFormState(now: now)
        medicationPicker = MedicationPickerState()
        settings = SettingsState()
    }
}