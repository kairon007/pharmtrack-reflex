import Foundation

/// The introspection surface: every node, its kind, and the actions/messages a journey may
/// address. Mirrors contracts/nodes.yaml. `internalActions` are reachable only from another
/// reducer's effect list — a journey that sends one is a usage error (exit 2).
public struct NodeSchema {
    public let id: NodeID
    public let kind: String
    public let actions: [String]
    public let messages: [String]
    public let internalActions: [String]
}

public enum NodeRegistry {
    public static let schemas: [NodeSchema] = [
        NodeSchema(
            id: .appRoot, kind: "root",
            actions: ["onAppear", "selectTab", "jumpToAppointment", "jumpToMedicationHistory", "presentSettings", "dismissSettings"],
            messages: ["storeSaved", "storeCleared", "onboardingFlagRead"],
            internalActions: ["commitData"]
        ),
        NodeSchema(
            id: .onboarding, kind: "overlay",
            actions: ["next", "getStarted", "tabChanged"],
            messages: ["revealFired", "clearCompleted", "clearFailed"],
            internalActions: []
        ),
        NodeSchema(
            id: .dashboard, kind: "tab",
            actions: ["markNextTaken", "tapHeroAppointment", "tapUpcomingRow", "tapUpcomingHeader", "tapRecentActivityViewAll", "quickAction", "presentSheet", "dismissSheet"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .consultations, kind: "tab",
            actions: ["setSearch", "openAdd", "openEdit", "delete", "closeForm"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .medicationTracker, kind: "tab",
            actions: ["setSegment", "setSearch", "markTaken", "incrementStock", "decrementStock", "openAdd", "openEdit", "closeForm"],
            messages: [],
            internalActions: ["showHistory"]
        ),
        NodeSchema(
            id: .appointments, kind: "tab",
            actions: ["selectDay", "previousWeek", "nextWeek", "openAdd", "openEdit", "delete", "closeForm"],
            messages: [],
            internalActions: ["syncFromRoot"]
        ),
        NodeSchema(
            id: .vitals, kind: "tab",
            actions: ["selectMetric", "openAdd", "openEdit", "delete", "openDetail", "closeDetail", "closeForm"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .vitalsDetail, kind: "push",
            actions: ["selectMetric", "selectRange", "previousWeek", "nextWeek", "selectDay", "tapReading", "close"],
            messages: ["highlightClearFired"],
            internalActions: ["open"]
        ),
        NodeSchema(
            id: .addVital, kind: "modal",
            actions: ["setMode", "setType", "setValueText", "setValue2Text", "setDate", "setNotes", "save", "cancel"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .addMedication, kind: "modal",
            actions: ["setMode", "setName", "setDosage", "setInstructions", "setSchedule", "setStockText", "save", "cancel"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .addAppointment, kind: "modal",
            actions: ["setMode", "setDoctorName", "setSpecialty", "setType", "setDate", "setLocation", "setNotes", "save", "cancel"],
            messages: [],
            internalActions: []
        ),
        NodeSchema(
            id: .consultationForm, kind: "modal",
            actions: ["setMode", "setDoctorName", "setSpecialty", "setDate", "setNotes", "unlinkMedication", "presentPicker", "dismissPicker", "save", "cancel"],
            messages: ["pickerSelectionChanged"],
            internalActions: []
        ),
        NodeSchema(
            id: .medicationPicker, kind: "modal_child",
            actions: ["toggle", "done"],
            messages: [],
            internalActions: ["present"]
        ),
        NodeSchema(
            id: .settings, kind: "modal",
            actions: ["setAllEnabled", "setMedicationsEnabled", "setAppointmentsEnabled", "setConsultationsEnabled", "setLowStockEnabled", "setLowStockThreshold", "setMedicationReminderTime", "setAppointmentReminderTime", "save", "cancel"],
            messages: [],
            internalActions: ["seedDraft"]
        ),
    ]

    private static let byID = Dictionary(uniqueKeysWithValues: schemas.map { ($0.id, $0) })

    public static func schema(_ node: NodeID) -> NodeSchema? {
        byID[node]
    }

    /// Every node is emitted on every step, not just the journey's declared `nodes` list.
    /// The list is documentation; emitting the superset keeps assertion paths resolvable and
    /// the two streams identical regardless of what a journey author remembered to declare.
    public static func emit(_ context: MachineContext) -> [String: ReflexValue] {
        let state = context.state
        return [
            NodeID.appRoot.rawValue: AppRootMachine.emit(state.appRoot, context),
            NodeID.onboarding.rawValue: OnboardingMachine.emit(state.onboarding, context),
            NodeID.dashboard.rawValue: DashboardMachine.emit(state.dashboard, context),
            NodeID.consultations.rawValue: ConsultationsMachine.emit(state.consultations, context),
            NodeID.medicationTracker.rawValue: MedicationTrackerMachine.emit(state.medicationTracker, context),
            NodeID.appointments.rawValue: AppointmentsMachine.emit(state.appointments, context),
            NodeID.vitals.rawValue: VitalsMachine.emit(state.vitals, context),
            NodeID.vitalsDetail.rawValue: VitalsDetailMachine.emit(state.vitalsDetail, context),
            NodeID.addVital.rawValue: AddVitalMachine.emit(state.addVital, context),
            NodeID.addMedication.rawValue: AddMedicationMachine.emit(state.addMedication, context),
            NodeID.addAppointment.rawValue: AddAppointmentMachine.emit(state.addAppointment, context),
            NodeID.consultationForm.rawValue: ConsultationFormMachine.emit(state.consultationForm, context),
            NodeID.medicationPicker.rawValue: MedicationPickerMachine.emit(state.medicationPicker, context),
            NodeID.settings.rawValue: SettingsMachine.emit(state.settings, context),
        ]
    }
}