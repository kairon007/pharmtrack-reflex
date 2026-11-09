package com.pharmtrack.reflex.app

import androidx.compose.ui.graphics.Color
import com.pharmtrack.reflex.core.App.AppState
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.FormMode
import com.pharmtrack.reflex.core.App.LoadPhase
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Consultation
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.MedicationSchedule
import com.pharmtrack.reflex.core.Domain.NotificationSettings
import com.pharmtrack.reflex.core.Domain.ReminderTime
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Screens.AddAppointmentMachine
import com.pharmtrack.reflex.core.Screens.AddMedicationMachine
import com.pharmtrack.reflex.core.Screens.AddVitalMachine
import com.pharmtrack.reflex.core.Screens.AppRootMachine
import com.pharmtrack.reflex.core.Screens.AppointmentsMachine
import com.pharmtrack.reflex.core.Screens.ConsultationFormMachine
import com.pharmtrack.reflex.core.Screens.ConsultationsMachine
import com.pharmtrack.reflex.core.Screens.DashboardMachine
import com.pharmtrack.reflex.core.Screens.DashboardSheet
import com.pharmtrack.reflex.core.Screens.HeroBranch
import com.pharmtrack.reflex.core.Screens.MedicationPickerMachine
import com.pharmtrack.reflex.core.Screens.MedicationSegment
import com.pharmtrack.reflex.core.Screens.MedicationTrackerMachine
import com.pharmtrack.reflex.core.Screens.OnboardingMachine
import com.pharmtrack.reflex.core.Screens.SettingsMachine
import com.pharmtrack.reflex.core.Screens.VitalRange
import com.pharmtrack.reflex.core.Screens.VitalsDetailMachine
import com.pharmtrack.reflex.core.Screens.VitalsMachine
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

// Typed extraction of each machine's canonical `emit` output. This is glue, not logic:
// every derived value (filters, sorts, counts, labels, trends) is computed by the Core
// machine and merely unpacked here so composables never re-implement a selector.

operator fun ReflexValue.get(key: String): ReflexValue = objectValue?.get(key) ?: ReflexValue.Null

val ReflexValue.uuidValue: UUID? get() = stringValue?.let { parseUUID(it) }

val ReflexValue.uuidArray: List<UUID> get() = arrayValue?.mapNotNull { it.uuidValue } ?: emptyList()

val ReflexValue.instantValue: Instant? get() = stringValue?.let { Instant.parse(it) }

val ReflexValue.instantArray: List<Instant> get() = arrayValue?.mapNotNull { it.instantValue } ?: emptyList()

val ReflexValue.text: String get() = stringValue ?: ""

val ReflexValue.integer: Int get() = (intValue ?: 0L).toInt()

val ReflexValue.decimal: Double get() = doubleValue ?: 0.0

val ReflexValue.flag: Boolean get() = boolValue ?: false

/** Core's Trend is unpacked from the emitted object; the UI never recomputes it. */
data class TrendInfo(val direction: String, val sentiment: String, val magnitude: String) {

    val icon: SemanticIcon get() = SemanticIcon.fromTrendDirection(direction)

    /** Q17 palette: down=good=emerald for every metric. */
    val color: Color
        get() = when (sentiment) {
            "good" -> Theme.emerald500
            "bad" -> Theme.rose500
            else -> Theme.slate500
        }
}

val VitalType.title: String
    get() = when (this) {
        VitalType.WEIGHT -> "Weight"
        VitalType.BLOOD_PRESSURE -> "Pressure"
        VitalType.BLOOD_SUGAR -> "Sugar"
    }

/** Legacy detail-chart headline label. */
val VitalType.detailLabel: String
    get() = when (this) {
        VitalType.WEIGHT -> "Weight"
        VitalType.BLOOD_PRESSURE -> "Blood Pressure"
        VitalType.BLOOD_SUGAR -> "Blood Sugar"
    }

val VitalType.unit: String get() = ValueFormat.unit(this)

val VitalRange.chipLabel: String
    get() = when (this) {
        VitalRange.WEEK -> "7D"
        VitalRange.MONTH -> "30D"
        VitalRange.QUARTER -> "90D"
        VitalRange.ALL -> "All"
    }

val VitalRange.label: String
    get() = when (this) {
        VitalRange.WEEK -> "Last 7 Days"
        VitalRange.MONTH -> "Last 30 Days"
        VitalRange.QUARTER -> "Last 90 Days"
        VitalRange.ALL -> "All Time"
    }

private fun trendInfo(value: ReflexValue): TrendInfo? {
    if (value.isNull) return null
    return TrendInfo(
        direction = value["direction"].text,
        sentiment = value["sentiment"].text,
        magnitude = value["magnitude"].text
    )
}

// MARK: - app_root

data class AppRootData(
    val loadPhase: LoadPhase,
    val loadError: String?,
    val selectedTab: Int,
    val isOnboarding: Boolean,
    val settingsPresented: Boolean
) {
    constructor(emit: ReflexValue) : this(
        loadPhase = LoadPhase.entries.firstOrNull { it.rawValue == emit["loadPhase"].text } ?: LoadPhase.IDLE,
        loadError = emit["loadError"].stringValue,
        selectedTab = emit["selectedTab"].integer,
        isOnboarding = emit["isOnboarding"].flag,
        settingsPresented = emit["settingsPresented"].flag
    )
}

// MARK: - onboarding

data class OnboardingData(
    val visible: Boolean,
    val step: Int,
    val clearing: Boolean,
    val promptText: String,
    val buttonText: String,
    val subtitle: String
) {
    constructor(emit: ReflexValue) : this(
        visible = emit["visible"].flag,
        step = emit["step"].integer,
        clearing = emit["clearing"].flag,
        promptText = emit["promptText"].text,
        buttonText = emit["buttonText"].text,
        subtitle = emit["subtitle"].text
    )
}

// MARK: - dashboard

data class DashboardData(
    val greeting: String,
    val todaysMedsCount: Int,
    val takenTodayCount: Int,
    val takenFraction: Double,
    val takenLabel: String,
    val heroBranch: HeroBranch,
    val nextMedication: Medication?,
    val nextMedIsTaken: Boolean,
    val upcoming: List<Appointment>,
    val recentLogs: List<Log>,
    val presentedSheet: DashboardSheet
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        greeting = emit["greeting"].text,
        todaysMedsCount = emit["todaysMedsCount"].integer,
        takenTodayCount = emit["takenTodayCount"].integer,
        takenFraction = emit["takenFraction"].decimal,
        takenLabel = emit["takenLabel"].text,
        heroBranch = HeroBranch.entries.firstOrNull { it.rawValue == emit["heroBranch"].text } ?: HeroBranch.EMPTY,
        nextMedication = emit["nextMedicationId"].uuidValue?.let { data.medication(it) },
        nextMedIsTaken = emit["nextMedIsTaken"].flag,
        upcoming = emit["upcomingAppointmentIds"].uuidArray.mapNotNull { data.appointment(it) },
        recentLogs = emit["recentLogIds"].uuidArray.mapNotNull { id -> data.logs.firstOrNull { it.id == id } },
        presentedSheet = DashboardSheet.entries.firstOrNull { it.rawValue == emit["presentedSheet"].text } ?: DashboardSheet.NONE
    )
}

// MARK: - consultations

data class ConsultationsData(
    val searchQuery: String,
    val presentedForm: UUID?,
    val formMode: FormMode,
    val rows: List<Consultation>,
    val totalCount: Int,
    private val initials: Map<String, ReflexValue>
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        searchQuery = emit["searchQuery"].text,
        presentedForm = emit["presentedForm"].uuidValue,
        formMode = FormMode.entries.firstOrNull { it.rawValue == emit["formMode"].text } ?: FormMode.CLOSED,
        rows = emit["filteredIds"].uuidArray.mapNotNull { data.consultation(it) },
        totalCount = emit["totalCount"].integer,
        initials = emit["initials"].objectValue ?: emptyMap()
    )

    fun initials(id: UUID): String = initials[ReflexValue.uuidString(id)]?.text ?: "?"
}

// MARK: - medication_tracker

data class MedicationTrackerData(
    val segment: MedicationSegment,
    val searchQuery: String,
    val formMode: FormMode,
    val editingId: UUID?,
    val filtered: List<Medication>,
    val takenTodayCount: Int,
    val totalCount: Int,
    val takenLabel: String,
    val takenFraction: Double,
    val allTaken: Boolean,
    val historyLogs: List<Log>,
    private val groups: Map<String, ReflexValue>,
    private val isTakenMap: Map<String, ReflexValue>,
    private val isLowStockMap: Map<String, ReflexValue>,
    private val data: MedicalData
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        segment = MedicationSegment.fromRaw(emit["segment"].text) ?: MedicationSegment.TODAY,
        searchQuery = emit["searchQuery"].text,
        formMode = FormMode.entries.firstOrNull { it.rawValue == emit["formMode"].text } ?: FormMode.CLOSED,
        editingId = emit["editingId"].uuidValue,
        filtered = emit["filteredMedications"].uuidArray.mapNotNull { data.medication(it) },
        takenTodayCount = emit["takenTodayCount"].integer,
        totalCount = emit["totalCount"].integer,
        takenLabel = emit["takenLabel"].text,
        takenFraction = emit["takenFraction"].decimal,
        allTaken = emit["allTaken"].flag,
        historyLogs = emit["historyLogIds"].uuidArray.mapNotNull { id -> data.logs.firstOrNull { it.id == id } },
        groups = emit["todayGroups"].objectValue ?: emptyMap(),
        isTakenMap = emit["isTaken"].objectValue ?: emptyMap(),
        isLowStockMap = emit["isLowStock"].objectValue ?: emptyMap(),
        data = data
    )

    fun group(schedule: MedicationSchedule): List<Medication> =
        (groups[schedule.rawValue]?.uuidArray ?: emptyList()).mapNotNull { data.medication(it) }

    fun isTaken(id: UUID): Boolean = isTakenMap[ReflexValue.uuidString(id)]?.flag ?: false

    fun isLowStock(id: UUID): Boolean = isLowStockMap[ReflexValue.uuidString(id)]?.flag ?: false
}

// MARK: - appointments

data class AppointmentsData(
    val selectedDate: Instant,
    val weekOffset: Int,
    val formMode: FormMode,
    val editingId: UUID?,
    val stripCenter: Instant,
    val stripDates: List<Instant>,
    val visible: List<Appointment>,
    val headerKind: String,
    val headerDayCount: Int,
    val emptyMessageDate: Instant,
    private val zone: Instant.Zone
) {
    constructor(emit: ReflexValue, data: MedicalData, zone: Instant.Zone) : this(
        selectedDate = emit["selectedDate"].instantValue ?: Instant.epoch,
        weekOffset = emit["weekOffset"].integer,
        formMode = FormMode.entries.firstOrNull { it.rawValue == emit["formMode"].text } ?: FormMode.CLOSED,
        editingId = emit["editingId"].uuidValue,
        stripCenter = emit["stripCenter"].instantValue ?: Instant.epoch,
        stripDates = emit["stripDates"].instantArray,
        visible = emit["visibleIds"].uuidArray.mapNotNull { data.appointment(it) },
        headerKind = emit["headerKind"].text,
        headerDayCount = emit["headerDayCount"].integer,
        emptyMessageDate = emit["emptyMessageDate"].instantValue ?: Instant.epoch,
        zone = zone
    )

    /** Legacy pluralization of the machine's (headerKind, headerDayCount) pair. */
    val headerText: String
        get() = when (headerKind) {
            "today" -> "Today's Appointments"
            "inNDays" -> "In $headerDayCount day${if (headerDayCount == 1) "" else "s"}"
            else -> "$headerDayCount day${if (headerDayCount == 1) "" else "s"} ago"
        }

    fun isSelected(date: Instant): Boolean =
        date.dayIndex(zone) == selectedDate.dayIndex(zone)
}

// MARK: - vitals

data class VitalsData(
    val selectedMetric: VitalType,
    val formMode: FormMode,
    val detailPresented: Boolean,
    val ascending: List<Vital>,
    val latest: Vital?,
    val recent: List<Vital>,
    val trend: TrendInfo?,
    val formattedValue: String,
    private val rowIcon: Map<String, ReflexValue>,
    private val data: MedicalData
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        selectedMetric = VitalType.fromRaw(emit["selectedMetric"].text) ?: VitalType.WEIGHT,
        formMode = FormMode.entries.firstOrNull { it.rawValue == emit["formMode"].text } ?: FormMode.CLOSED,
        detailPresented = emit["detailPresented"].flag,
        ascending = emit["filteredVitals"].uuidArray.mapNotNull { data.vital(it) },
        latest = emit["latestReadingId"].uuidValue?.let { data.vital(it) },
        recent = emit["recentIds"].uuidArray.mapNotNull { data.vital(it) },
        trend = trendInfo(emit["trend"]),
        formattedValue = emit["formattedValue"].text,
        rowIcon = emit["rowIcon"].objectValue ?: emptyMap(),
        data = data
    )

    fun iconName(id: UUID): String = rowIcon[ReflexValue.uuidString(id)]?.text ?: "weight"

    /** The trend headline shows value and unit as separate texts; same D14 formatter. */
    val formattedLatestValue: String get() = latest?.let { ValueFormat.vitalValue(it) } ?: "--"

    /** The three metric cards each show their own latest value through the same selectors. */
    fun cardValue(type: VitalType): String {
        val series = Selectors.sortedByDateAndId(
            data.vitals.filter { it.type == type },
            date = { it.date },
            id = { it.id }
        )
        return series.lastOrNull()?.let { ValueFormat.vitalValue(it) } ?: "--"
    }
}

// MARK: - vitals_detail

data class VitalsDetailData(
    val metric: VitalType,
    val range: VitalRange,
    val weekOffset: Int,
    val selectedDate: Instant,
    val highlightedVitalId: UUID?,
    val stripCenter: Instant,
    val stripDates: List<Instant>,
    val stripDotDates: List<Instant>,
    val selectedDay: List<Vital>,
    val allReadings: List<Vital>,
    val chartValues: List<Double>,
    val chartMin: Double?,
    val chartMax: Double?,
    val trend: TrendInfo?,
    val formattedValue: String,
    val totalCount: Int,
    val selectedDayCount: Int,
    private val zone: Instant.Zone
) {
    constructor(emit: ReflexValue, data: MedicalData, zone: Instant.Zone) : this(
        metric = VitalType.fromRaw(emit["metric"].text) ?: VitalType.WEIGHT,
        range = VitalRange.fromRaw(emit["range"].text) ?: VitalRange.WEEK,
        weekOffset = emit["weekOffset"].integer,
        selectedDate = emit["selectedDate"].instantValue ?: Instant.epoch,
        highlightedVitalId = emit["highlightedVitalId"].uuidValue,
        stripCenter = emit["stripCenter"].instantValue ?: Instant.epoch,
        stripDates = emit["stripDates"].instantArray,
        stripDotDates = emit["stripDotDates"].instantArray,
        selectedDay = emit["selectedDayIds"].uuidArray.mapNotNull { data.vital(it) },
        allReadings = emit["allReadingIds"].uuidArray.mapNotNull { data.vital(it) },
        chartValues = emit["chartValues"].arrayValue?.map { it.decimal } ?: emptyList(),
        chartMin = emit["chartMin"].doubleValue,
        chartMax = emit["chartMax"].doubleValue,
        trend = trendInfo(emit["trend"]),
        formattedValue = emit["formattedValue"].text,
        totalCount = emit["totalCount"].integer,
        selectedDayCount = emit["selectedDayCount"].integer,
        zone = zone
    )

    fun isSelected(date: Instant): Boolean =
        date.dayIndex(zone) == selectedDate.dayIndex(zone)

    fun hasDot(date: Instant): Boolean {
        val index = date.dayIndex(zone)
        return stripDotDates.any { it.dayIndex(zone) == index }
    }

    /** The chart headline shows value and unit as separate texts; same D14 formatter. */
    val formattedLatestValue: String get() = allReadings.firstOrNull()?.let { ValueFormat.vitalValue(it) } ?: "--"
}

// MARK: - add_vital

data class AddVitalData(
    val mode: EditMode,
    val type: VitalType,
    val valueText: String,
    val value2Text: String,
    val date: Instant,
    val notesText: String,
    val value2Visible: Boolean,
    val canSave: Boolean,
    val unit: String
) {
    constructor(emit: ReflexValue) : this(
        mode = EditMode.fromRaw(emit["mode"].text) ?: EditMode.ADD,
        type = VitalType.fromRaw(emit["type"].text) ?: VitalType.WEIGHT,
        valueText = emit["valueText"].text,
        value2Text = emit["value2Text"].text,
        date = emit["date"].instantValue ?: Instant.epoch,
        notesText = emit["notesText"].text,
        value2Visible = emit["value2Visible"].flag,
        canSave = emit["canSave"].flag,
        unit = emit["unit"].text
    )
}

// MARK: - add_medication

data class AddMedicationData(
    val mode: EditMode,
    val nameText: String,
    val dosageText: String,
    val instructionsText: String,
    val schedule: MedicationSchedule,
    val stockText: String,
    val canSave: Boolean
) {
    constructor(emit: ReflexValue) : this(
        mode = EditMode.fromRaw(emit["mode"].text) ?: EditMode.ADD,
        nameText = emit["nameText"].text,
        dosageText = emit["dosageText"].text,
        instructionsText = emit["instructionsText"].text,
        schedule = MedicationSchedule.fromRaw(emit["schedule"].text) ?: MedicationSchedule.MORNING,
        stockText = emit["stockText"].text,
        canSave = emit["canSave"].flag
    )
}

// MARK: - add_appointment

data class AddAppointmentData(
    val mode: EditMode,
    val doctorNameText: String,
    val specialtyText: String,
    val typeText: String,
    val date: Instant,
    val locationText: String,
    val notesText: String,
    val canSave: Boolean
) {
    constructor(emit: ReflexValue) : this(
        mode = EditMode.fromRaw(emit["mode"].text) ?: EditMode.ADD,
        doctorNameText = emit["doctorNameText"].text,
        specialtyText = emit["specialtyText"].text,
        typeText = emit["typeText"].text,
        date = emit["date"].instantValue ?: Instant.epoch,
        locationText = emit["locationText"].text,
        notesText = emit["notesText"].text,
        canSave = emit["canSave"].flag
    )
}

// MARK: - consultation_form

data class ConsultationFormData(
    val mode: EditMode,
    val doctorNameText: String,
    val specialtyText: String,
    val date: Instant,
    val notesText: String,
    val pickerPresented: Boolean,
    val canSave: Boolean,
    val linkedRows: List<Medication>
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        mode = EditMode.fromRaw(emit["mode"].text) ?: EditMode.ADD,
        doctorNameText = emit["doctorNameText"].text,
        specialtyText = emit["specialtyText"].text,
        date = emit["date"].instantValue ?: Instant.epoch,
        notesText = emit["notesText"].text,
        pickerPresented = emit["pickerPresented"].flag,
        canSave = emit["canSave"].flag,
        linkedRows = emit["linkedRows"].uuidArray.mapNotNull { data.medication(it) }
    )
}

// MARK: - medication_picker

data class MedicationPickerData(
    val selectedIds: List<UUID>,
    val available: List<Medication>
) {
    constructor(emit: ReflexValue, data: MedicalData) : this(
        selectedIds = emit["selectedIds"].uuidArray,
        available = emit["availableIds"].uuidArray.mapNotNull { data.medication(it) }
    )

    fun isSelected(id: UUID): Boolean = selectedIds.contains(id)
}

// MARK: - settings

data class SettingsData(
    val draft: NotificationSettings,
    val sectionsVisible: Boolean,
    val medicationTimeVisible: Boolean,
    val thresholdVisible: Boolean,
    val appointmentTimeVisible: Boolean,
    val thresholdOptions: List<Int>,
    val reminderOptions: List<ReminderTime>
) {
    constructor(emit: ReflexValue) : this(
        draft = NotificationSettings(
            allNotificationsEnabled = emit["draft"]["allNotificationsEnabled"].flag,
            medicationsEnabled = emit["draft"]["medicationsEnabled"].flag,
            appointmentsEnabled = emit["draft"]["appointmentsEnabled"].flag,
            consultationsEnabled = emit["draft"]["consultationsEnabled"].flag,
            lowStockEnabled = emit["draft"]["lowStockEnabled"].flag,
            lowStockThreshold = emit["draft"]["lowStockThreshold"].integer.toLong(),
            medicationReminderTime = ReminderTime.fromRaw(emit["draft"]["medicationReminderTime"].text)
                ?: ReminderTime.AT_TIME_OF_EVENT,
            appointmentReminderTime = ReminderTime.fromRaw(emit["draft"]["appointmentReminderTime"].text)
                ?: ReminderTime.ONE_DAY_BEFORE
        ),
        sectionsVisible = emit["sectionsVisible"].flag,
        medicationTimeVisible = emit["medicationTimeVisible"].flag,
        thresholdVisible = emit["thresholdVisible"].flag,
        appointmentTimeVisible = emit["appointmentTimeVisible"].flag,
        thresholdOptions = emit["thresholdOptions"].arrayValue?.map { it.integer } ?: emptyList(),
        reminderOptions = emit["reminderOptions"].arrayValue?.mapNotNull { ReminderTime.fromRaw(it.text) } ?: emptyList()
    )
}

// MARK: - Host accessors

/**
 * The typed view of one AppState snapshot. Composables read these instead of the raw
 * machine states, so the display always comes from the canonical emit output.
 */
class ScreenData(val state: AppState, val context: MachineContext) {

    val data: MedicalData get() = state.appRoot.data

    val root: AppRootData get() = AppRootData(AppRootMachine.emit(state.appRoot, context))

    val onboarding: OnboardingData get() = OnboardingData(OnboardingMachine.emit(state.onboarding, context))

    val dashboard: DashboardData get() = DashboardData(DashboardMachine.emit(state.dashboard, context), data)

    val consultations: ConsultationsData
        get() = ConsultationsData(ConsultationsMachine.emit(state.consultations, context), data)

    val medicationTracker: MedicationTrackerData
        get() = MedicationTrackerData(MedicationTrackerMachine.emit(state.medicationTracker, context), data)

    val appointments: AppointmentsData
        get() = AppointmentsData(AppointmentsMachine.emit(state.appointments, context), data, context.zone)

    val vitals: VitalsData get() = VitalsData(VitalsMachine.emit(state.vitals, context), data)

    val vitalsDetail: VitalsDetailData
        get() = VitalsDetailData(VitalsDetailMachine.emit(state.vitalsDetail, context), data, context.zone)

    val addVital: AddVitalData get() = AddVitalData(AddVitalMachine.emit(state.addVital, context))

    val addMedication: AddMedicationData get() = AddMedicationData(AddMedicationMachine.emit(state.addMedication, context))

    val addAppointment: AddAppointmentData
        get() = AddAppointmentData(AddAppointmentMachine.emit(state.addAppointment, context))

    val consultationForm: ConsultationFormData
        get() = ConsultationFormData(ConsultationFormMachine.emit(state.consultationForm, context), data)

    val medicationPicker: MedicationPickerData
        get() = MedicationPickerData(MedicationPickerMachine.emit(state.medicationPicker, context), data)

    val settings: SettingsData get() = SettingsData(SettingsMachine.emit(state.settings, context))
}
