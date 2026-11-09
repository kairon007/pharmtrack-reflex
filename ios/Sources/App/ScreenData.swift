import Foundation
import SwiftUI

// Typed extraction of each machine's canonical `emit` output. This is glue, not logic:
// every derived value (filters, sorts, counts, labels, trends) is computed by the Core
// machine and merely unpacked here so views never re-implement a selector.

extension ReflexValue {
    subscript(key: String) -> ReflexValue { objectValue?[key] ?? .null }
    var uuidValue: UUID? { stringValue.flatMap(UUID.init(uuidString:)) }
    var uuidArray: [UUID] { arrayValue?.compactMap(\.uuidValue) ?? [] }
    var instantValue: Instant? { stringValue.flatMap(Instant.parse) }
    var instantArray: [Instant] { arrayValue?.compactMap(\.instantValue) ?? [] }
    var text: String { stringValue ?? "" }
    var integer: Int { intValue ?? 0 }
    var decimal: Double { doubleValue ?? 0 }
    var flag: Bool { boolValue ?? false }
}

/// Core's Trend has an internal memberwise init; the UI unpacks the emitted object instead.
public struct TrendInfo {
    public let direction: String
    public let sentiment: String
    public let magnitude: String

    public var icon: SemanticIcon { SemanticIcon(trendDirection: direction) }

    /// Q17 palette: down=good=emerald for every metric.
    public var color: Color {
        switch sentiment {
        case "good": return Theme.emerald500
        case "bad": return Theme.rose500
        default: return Theme.slate500
        }
    }
}

extension VitalType {
    /// Legacy chip/tab label.
    public var title: String {
        switch self {
        case .weight: return "Weight"
        case .bloodPressure: return "Pressure"
        case .bloodSugar: return "Sugar"
        }
    }

    /// Legacy detail-chart headline label.
    public var detailLabel: String {
        switch self {
        case .weight: return "Weight"
        case .bloodPressure: return "Blood Pressure"
        case .bloodSugar: return "Blood Sugar"
        }
    }

    public var unit: String { ValueFormat.unit(for: self) }
}

extension VitalRange {
    public var chipLabel: String {
        switch self {
        case .week: return "7D"
        case .month: return "30D"
        case .quarter: return "90D"
        case .all: return "All"
        }
    }

    public var label: String {
        switch self {
        case .week: return "Last 7 Days"
        case .month: return "Last 30 Days"
        case .quarter: return "Last 90 Days"
        case .all: return "All Time"
        }
    }
}

private func trendInfo(from value: ReflexValue) -> TrendInfo? {
    guard !value.isNull else { return nil }
    return TrendInfo(
        direction: value["direction"].text,
        sentiment: value["sentiment"].text,
        magnitude: value["magnitude"].text
    )
}

// MARK: - app_root

public struct AppRootData {
    public let loadPhase: LoadPhase
    public let loadError: String?
    public let selectedTab: Int
    public let isOnboarding: Bool
    public let settingsPresented: Bool

    init(_ emit: ReflexValue) {
        loadPhase = LoadPhase(rawValue: emit["loadPhase"].text) ?? .idle
        loadError = emit["loadError"].stringValue
        selectedTab = emit["selectedTab"].integer
        isOnboarding = emit["isOnboarding"].flag
        settingsPresented = emit["settingsPresented"].flag
    }
}

// MARK: - onboarding

public struct OnboardingData {
    public let visible: Bool
    public let step: Int
    public let clearing: Bool
    public let promptText: String
    public let buttonText: String
    public let subtitle: String

    init(_ emit: ReflexValue) {
        visible = emit["visible"].flag
        step = emit["step"].integer
        clearing = emit["clearing"].flag
        promptText = emit["promptText"].text
        buttonText = emit["buttonText"].text
        subtitle = emit["subtitle"].text
    }
}

// MARK: - dashboard

public struct DashboardData {
    public let greeting: String
    public let todaysMedsCount: Int
    public let takenTodayCount: Int
    public let takenFraction: Double
    public let takenLabel: String
    public let heroBranch: HeroBranch
    public let nextMedication: Medication?
    public let nextMedIsTaken: Bool
    public let upcoming: [Appointment]
    public let recentLogs: [Log]
    public let presentedSheet: DashboardSheet

    init(_ emit: ReflexValue, data: MedicalData) {
        greeting = emit["greeting"].text
        todaysMedsCount = emit["todaysMedsCount"].integer
        takenTodayCount = emit["takenTodayCount"].integer
        takenFraction = emit["takenFraction"].decimal
        takenLabel = emit["takenLabel"].text
        heroBranch = HeroBranch(rawValue: emit["heroBranch"].text) ?? .empty
        nextMedication = emit["nextMedicationId"].uuidValue.flatMap(data.medication)
        nextMedIsTaken = emit["nextMedIsTaken"].flag
        upcoming = emit["upcomingAppointmentIds"].uuidArray.compactMap(data.appointment)
        recentLogs = emit["recentLogIds"].uuidArray.compactMap { id in data.logs.first { $0.id == id } }
        presentedSheet = DashboardSheet(rawValue: emit["presentedSheet"].text) ?? .none
    }
}

// MARK: - consultations

public struct ConsultationsData {
    public let searchQuery: String
    public let presentedForm: UUID?
    public let formMode: FormMode
    public let rows: [Consultation]
    public let totalCount: Int
    private let initials: [String: ReflexValue]

    init(_ emit: ReflexValue, data: MedicalData) {
        searchQuery = emit["searchQuery"].text
        presentedForm = emit["presentedForm"].uuidValue
        formMode = FormMode(rawValue: emit["formMode"].text) ?? .closed
        rows = emit["filteredIds"].uuidArray.compactMap(data.consultation)
        totalCount = emit["totalCount"].integer
        initials = emit["initials"].objectValue ?? [:]
    }

    public func initials(_ id: UUID) -> String { initials[id.uuidString]?.text ?? "?" }
}

// MARK: - medication_tracker

public struct MedicationTrackerData {
    public let segment: MedicationSegment
    public let searchQuery: String
    public let formMode: FormMode
    public let editingId: UUID?
    public let filtered: [Medication]
    public let takenTodayCount: Int
    public let totalCount: Int
    public let takenLabel: String
    public let takenFraction: Double
    public let allTaken: Bool
    public let historyLogs: [Log]
    private let groups: [String: ReflexValue]
    private let isTakenMap: [String: ReflexValue]
    private let isLowStockMap: [String: ReflexValue]
    private let data: MedicalData

    init(_ emit: ReflexValue, data: MedicalData) {
        segment = MedicationSegment(rawValue: emit["segment"].text) ?? .today
        searchQuery = emit["searchQuery"].text
        formMode = FormMode(rawValue: emit["formMode"].text) ?? .closed
        editingId = emit["editingId"].uuidValue
        filtered = emit["filteredMedications"].uuidArray.compactMap(data.medication)
        takenTodayCount = emit["takenTodayCount"].integer
        totalCount = emit["totalCount"].integer
        takenLabel = emit["takenLabel"].text
        takenFraction = emit["takenFraction"].decimal
        allTaken = emit["allTaken"].flag
        historyLogs = emit["historyLogIds"].uuidArray.compactMap { id in data.logs.first { $0.id == id } }
        groups = emit["todayGroups"].objectValue ?? [:]
        isTakenMap = emit["isTaken"].objectValue ?? [:]
        isLowStockMap = emit["isLowStock"].objectValue ?? [:]
        self.data = data
    }

    public func group(_ schedule: MedicationSchedule) -> [Medication] {
        (groups[schedule.rawValue]?.uuidArray ?? []).compactMap(data.medication)
    }

    public func isTaken(_ id: UUID) -> Bool { isTakenMap[id.uuidString]?.flag ?? false }
    public func isLowStock(_ id: UUID) -> Bool { isLowStockMap[id.uuidString]?.flag ?? false }
}

// MARK: - appointments

public struct AppointmentsData {
    public let selectedDate: Instant
    public let weekOffset: Int
    public let formMode: FormMode
    public let editingId: UUID?
    public let stripCenter: Instant
    public let stripDates: [Instant]
    public let visible: [Appointment]
    public let headerKind: String
    public let headerDayCount: Int
    public let emptyMessageDate: Instant
    private let zone: Instant.Zone

    init(_ emit: ReflexValue, data: MedicalData, zone: Instant.Zone) {
        selectedDate = emit["selectedDate"].instantValue ?? .epoch
        weekOffset = emit["weekOffset"].integer
        formMode = FormMode(rawValue: emit["formMode"].text) ?? .closed
        editingId = emit["editingId"].uuidValue
        stripCenter = emit["stripCenter"].instantValue ?? .epoch
        stripDates = emit["stripDates"].instantArray
        visible = emit["visibleIds"].uuidArray.compactMap(data.appointment)
        headerKind = emit["headerKind"].text
        headerDayCount = emit["headerDayCount"].integer
        emptyMessageDate = emit["emptyMessageDate"].instantValue ?? .epoch
        self.zone = zone
    }

    /// Legacy pluralization of the machine's (headerKind, headerDayCount) pair.
    public var headerText: String {
        switch headerKind {
        case "today": return "Today's Appointments"
        case "inNDays": return "In \(headerDayCount) day\(headerDayCount == 1 ? "" : "s")"
        default: return "\(headerDayCount) day\(headerDayCount == 1 ? "" : "s") ago"
        }
    }

    public func isSelected(_ date: Instant) -> Bool {
        date.dayIndex(in: zone) == selectedDate.dayIndex(in: zone)
    }
}

// MARK: - vitals

public struct VitalsData {
    public let selectedMetric: VitalType
    public let formMode: FormMode
    public let detailPresented: Bool
    public let ascending: [Vital]
    public let latest: Vital?
    public let recent: [Vital]
    public let trend: TrendInfo?
    public let formattedValue: String
    private let rowIcon: [String: ReflexValue]
    private let data: MedicalData

    init(_ emit: ReflexValue, data: MedicalData) {
        selectedMetric = VitalType(rawValue: emit["selectedMetric"].text) ?? .weight
        formMode = FormMode(rawValue: emit["formMode"].text) ?? .closed
        detailPresented = emit["detailPresented"].flag
        ascending = emit["filteredVitals"].uuidArray.compactMap(data.vital)
        latest = emit["latestReadingId"].uuidValue.flatMap(data.vital)
        recent = emit["recentIds"].uuidArray.compactMap(data.vital)
        trend = trendInfo(from: emit["trend"])
        formattedValue = emit["formattedValue"].text
        rowIcon = emit["rowIcon"].objectValue ?? [:]
        self.data = data
    }

    public func iconName(_ id: UUID) -> String { rowIcon[id.uuidString]?.text ?? "weight" }

    /// The trend headline shows value and unit as separate Texts; same D14 formatter.
    public var formattedLatestValue: String { latest.map(ValueFormat.vitalValue) ?? "--" }

    /// The three metric cards each show their own latest value through the same selectors.
    public func cardValue(_ type: VitalType) -> String {
        let series = Selectors.sortedByDateAndId(
            data.vitals.filter { $0.type == type }, date: \.date, id: \.id
        )
        return series.last.map(ValueFormat.vitalValue) ?? "--"
    }
}

// MARK: - vitals_detail

public struct VitalsDetailData {
    public let metric: VitalType
    public let range: VitalRange
    public let weekOffset: Int
    public let selectedDate: Instant
    public let highlightedVitalId: UUID?
    public let stripCenter: Instant
    public let stripDates: [Instant]
    public let stripDotDates: [Instant]
    public let selectedDay: [Vital]
    public let allReadings: [Vital]
    public let chartValues: [Double]
    public let chartMin: Double?
    public let chartMax: Double?
    public let trend: TrendInfo?
    public let formattedValue: String
    public let totalCount: Int
    public let selectedDayCount: Int
    private let zone: Instant.Zone

    init(_ emit: ReflexValue, data: MedicalData, zone: Instant.Zone) {
        metric = VitalType(rawValue: emit["metric"].text) ?? .weight
        range = VitalRange(rawValue: emit["range"].text) ?? .week
        weekOffset = emit["weekOffset"].integer
        selectedDate = emit["selectedDate"].instantValue ?? .epoch
        highlightedVitalId = emit["highlightedVitalId"].uuidValue
        stripCenter = emit["stripCenter"].instantValue ?? .epoch
        stripDates = emit["stripDates"].instantArray
        stripDotDates = emit["stripDotDates"].instantArray
        selectedDay = emit["selectedDayIds"].uuidArray.compactMap(data.vital)
        allReadings = emit["allReadingIds"].uuidArray.compactMap(data.vital)
        chartValues = emit["chartValues"].arrayValue?.map(\.decimal) ?? []
        chartMin = emit["chartMin"].doubleValue
        chartMax = emit["chartMax"].doubleValue
        trend = trendInfo(from: emit["trend"])
        formattedValue = emit["formattedValue"].text
        totalCount = emit["totalCount"].integer
        selectedDayCount = emit["selectedDayCount"].integer
        self.zone = zone
    }

    public func isSelected(_ date: Instant) -> Bool {
        date.dayIndex(in: zone) == selectedDate.dayIndex(in: zone)
    }

    public func hasDot(_ date: Instant) -> Bool {
        let index = date.dayIndex(in: zone)
        return stripDotDates.contains { $0.dayIndex(in: zone) == index }
    }

    /// The chart headline shows value and unit as separate Texts; same D14 formatter.
    public var formattedLatestValue: String { allReadings.first.map(ValueFormat.vitalValue) ?? "--" }
}

// MARK: - add_vital

public struct AddVitalData {
    public let mode: EditMode
    public let type: VitalType
    public let valueText: String
    public let value2Text: String
    public let date: Instant
    public let notesText: String
    public let value2Visible: Bool
    public let canSave: Bool
    public let unit: String

    init(_ emit: ReflexValue) {
        mode = EditMode(rawValue: emit["mode"].text) ?? .add
        type = VitalType(rawValue: emit["type"].text) ?? .weight
        valueText = emit["valueText"].text
        value2Text = emit["value2Text"].text
        date = emit["date"].instantValue ?? .epoch
        notesText = emit["notesText"].text
        value2Visible = emit["value2Visible"].flag
        canSave = emit["canSave"].flag
        unit = emit["unit"].text
    }
}

// MARK: - add_medication

public struct AddMedicationData {
    public let mode: EditMode
    public let nameText: String
    public let dosageText: String
    public let instructionsText: String
    public let schedule: MedicationSchedule
    public let stockText: String
    public let canSave: Bool

    init(_ emit: ReflexValue) {
        mode = EditMode(rawValue: emit["mode"].text) ?? .add
        nameText = emit["nameText"].text
        dosageText = emit["dosageText"].text
        instructionsText = emit["instructionsText"].text
        schedule = MedicationSchedule(rawValue: emit["schedule"].text) ?? .morning
        stockText = emit["stockText"].text
        canSave = emit["canSave"].flag
    }
}

// MARK: - add_appointment

public struct AddAppointmentData {
    public let mode: EditMode
    public let doctorNameText: String
    public let specialtyText: String
    public let typeText: String
    public let date: Instant
    public let locationText: String
    public let notesText: String
    public let canSave: Bool

    init(_ emit: ReflexValue) {
        mode = EditMode(rawValue: emit["mode"].text) ?? .add
        doctorNameText = emit["doctorNameText"].text
        specialtyText = emit["specialtyText"].text
        typeText = emit["typeText"].text
        date = emit["date"].instantValue ?? .epoch
        locationText = emit["locationText"].text
        notesText = emit["notesText"].text
        canSave = emit["canSave"].flag
    }
}

// MARK: - consultation_form

public struct ConsultationFormData {
    public let mode: EditMode
    public let doctorNameText: String
    public let specialtyText: String
    public let date: Instant
    public let notesText: String
    public let pickerPresented: Bool
    public let canSave: Bool
    public let linkedRows: [Medication]

    init(_ emit: ReflexValue, data: MedicalData) {
        mode = EditMode(rawValue: emit["mode"].text) ?? .add
        doctorNameText = emit["doctorNameText"].text
        specialtyText = emit["specialtyText"].text
        date = emit["date"].instantValue ?? .epoch
        notesText = emit["notesText"].text
        pickerPresented = emit["pickerPresented"].flag
        canSave = emit["canSave"].flag
        linkedRows = emit["linkedRows"].uuidArray.compactMap(data.medication)
    }
}

// MARK: - medication_picker

public struct MedicationPickerData {
    public let selectedIds: [UUID]
    public let available: [Medication]

    init(_ emit: ReflexValue, data: MedicalData) {
        selectedIds = emit["selectedIds"].uuidArray
        available = emit["availableIds"].uuidArray.compactMap(data.medication)
    }

    public func isSelected(_ id: UUID) -> Bool { selectedIds.contains(id) }
}

// MARK: - settings

public struct SettingsData {
    public let draft: NotificationSettings
    public let sectionsVisible: Bool
    public let medicationTimeVisible: Bool
    public let thresholdVisible: Bool
    public let appointmentTimeVisible: Bool
    public let thresholdOptions: [Int]
    public let reminderOptions: [ReminderTime]

    init(_ emit: ReflexValue) {
        let draft = emit["draft"]
        self.draft = NotificationSettings(
            allNotificationsEnabled: draft["allNotificationsEnabled"].flag,
            medicationsEnabled: draft["medicationsEnabled"].flag,
            appointmentsEnabled: draft["appointmentsEnabled"].flag,
            consultationsEnabled: draft["consultationsEnabled"].flag,
            lowStockEnabled: draft["lowStockEnabled"].flag,
            lowStockThreshold: draft["lowStockThreshold"].integer,
            medicationReminderTime: ReminderTime(rawValue: draft["medicationReminderTime"].text) ?? .atTimeOfEvent,
            appointmentReminderTime: ReminderTime(rawValue: draft["appointmentReminderTime"].text) ?? .oneDayBefore
        )
        sectionsVisible = emit["sectionsVisible"].flag
        medicationTimeVisible = emit["medicationTimeVisible"].flag
        thresholdVisible = emit["thresholdVisible"].flag
        appointmentTimeVisible = emit["appointmentTimeVisible"].flag
        thresholdOptions = emit["thresholdOptions"].arrayValue?.map(\.integer) ?? []
        reminderOptions = emit["reminderOptions"].arrayValue?.compactMap {
            ReminderTime(rawValue: $0.text)
        } ?? []
    }
}

// MARK: - Host accessors

extension ReflexApp {
    public var root: AppRootData { AppRootData(AppRootMachine.emit(state.appRoot, context)) }
    public var onboarding: OnboardingData { OnboardingData(OnboardingMachine.emit(state.onboarding, context)) }
    public var dashboard: DashboardData { DashboardData(DashboardMachine.emit(state.dashboard, context), data: data) }
    public var consultations: ConsultationsData { ConsultationsData(ConsultationsMachine.emit(state.consultations, context), data: data) }
    public var medicationTracker: MedicationTrackerData { MedicationTrackerData(MedicationTrackerMachine.emit(state.medicationTracker, context), data: data) }
    public var appointments: AppointmentsData { AppointmentsData(AppointmentsMachine.emit(state.appointments, context), data: data, zone: context.zone) }
    public var vitals: VitalsData { VitalsData(VitalsMachine.emit(state.vitals, context), data: data) }
    public var vitalsDetail: VitalsDetailData { VitalsDetailData(VitalsDetailMachine.emit(state.vitalsDetail, context), data: data, zone: context.zone) }
    public var addVital: AddVitalData { AddVitalData(AddVitalMachine.emit(state.addVital, context)) }
    public var addMedication: AddMedicationData { AddMedicationData(AddMedicationMachine.emit(state.addMedication, context)) }
    public var addAppointment: AddAppointmentData { AddAppointmentData(AddAppointmentMachine.emit(state.addAppointment, context)) }
    public var consultationForm: ConsultationFormData { ConsultationFormData(ConsultationFormMachine.emit(state.consultationForm, context), data: data) }
    public var medicationPicker: MedicationPickerData { MedicationPickerData(MedicationPickerMachine.emit(state.medicationPicker, context), data: data) }
    public var settings: SettingsData { SettingsData(SettingsMachine.emit(state.settings, context)) }

    /// Two-way text binding that writes through an action. Presentation glue only.
    public func textBinding(
        _ value: String,
        _ action: @escaping (String) -> Action
    ) -> Binding<String> {
        Binding(get: { value }, set: { self.send(action($0)) })
    }
}