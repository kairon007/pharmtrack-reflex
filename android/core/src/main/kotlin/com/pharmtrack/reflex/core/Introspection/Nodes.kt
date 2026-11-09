package com.pharmtrack.reflex.core.Introspection

import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.Screens.AddAppointmentMachine
import com.pharmtrack.reflex.core.Screens.AddMedicationMachine
import com.pharmtrack.reflex.core.Screens.AddVitalMachine
import com.pharmtrack.reflex.core.Screens.AppRootMachine
import com.pharmtrack.reflex.core.Screens.AppointmentsMachine
import com.pharmtrack.reflex.core.Screens.ConsultationFormMachine
import com.pharmtrack.reflex.core.Screens.ConsultationsMachine
import com.pharmtrack.reflex.core.Screens.DashboardMachine
import com.pharmtrack.reflex.core.Screens.MedicationPickerMachine
import com.pharmtrack.reflex.core.Screens.MedicationTrackerMachine
import com.pharmtrack.reflex.core.Screens.OnboardingMachine
import com.pharmtrack.reflex.core.Screens.SettingsMachine
import com.pharmtrack.reflex.core.Screens.VitalsDetailMachine
import com.pharmtrack.reflex.core.Screens.VitalsMachine
import com.pharmtrack.reflex.core.Support.ReflexValue

/**
 * The introspection surface: every node, its kind, and the actions/messages a journey may
 * address. Mirrors contracts/nodes.yaml. `internalActions` are reachable only from another
 * reducer's effect list — a journey that sends one is a usage error (exit 2).
 */
data class NodeSchema(
    val id: NodeID,
    val kind: String,
    val actions: List<String>,
    val messages: List<String>,
    val internalActions: List<String>
)

object NodeRegistry {

    val schemas: List<NodeSchema> = listOf(
        NodeSchema(
            NodeID.APP_ROOT, "root",
            listOf(
                "onAppear", "selectTab", "jumpToAppointment", "jumpToMedicationHistory",
                "presentSettings", "dismissSettings"
            ),
            listOf("storeSaved", "storeCleared", "onboardingFlagRead"),
            listOf("commitData")
        ),
        NodeSchema(
            NodeID.ONBOARDING, "overlay",
            listOf("next", "getStarted", "tabChanged"),
            listOf("revealFired", "clearCompleted", "clearFailed"),
            emptyList()
        ),
        NodeSchema(
            NodeID.DASHBOARD, "tab",
            listOf(
                "markNextTaken", "tapHeroAppointment", "tapUpcomingRow", "tapUpcomingHeader",
                "tapRecentActivityViewAll", "quickAction", "presentSheet", "dismissSheet"
            ),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.CONSULTATIONS, "tab",
            listOf("setSearch", "openAdd", "openEdit", "delete", "closeForm"),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.MEDICATION_TRACKER, "tab",
            listOf(
                "setSegment", "setSearch", "markTaken", "incrementStock", "decrementStock",
                "openAdd", "openEdit", "closeForm"
            ),
            emptyList(),
            listOf("showHistory")
        ),
        NodeSchema(
            NodeID.APPOINTMENTS, "tab",
            listOf("selectDay", "previousWeek", "nextWeek", "openAdd", "openEdit", "delete", "closeForm"),
            emptyList(),
            listOf("syncFromRoot")
        ),
        NodeSchema(
            NodeID.VITALS, "tab",
            listOf("selectMetric", "openAdd", "openEdit", "delete", "openDetail", "closeDetail", "closeForm"),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.VITALS_DETAIL, "push",
            listOf("selectMetric", "selectRange", "previousWeek", "nextWeek", "selectDay", "tapReading", "close"),
            listOf("highlightClearFired"),
            listOf("open")
        ),
        NodeSchema(
            NodeID.ADD_VITAL, "modal",
            listOf("setMode", "setType", "setValueText", "setValue2Text", "setDate", "setNotes", "save", "cancel"),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.ADD_MEDICATION, "modal",
            listOf(
                "setMode", "setName", "setDosage", "setInstructions", "setSchedule",
                "setStockText", "save", "cancel"
            ),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.ADD_APPOINTMENT, "modal",
            listOf(
                "setMode", "setDoctorName", "setSpecialty", "setType", "setDate", "setLocation",
                "setNotes", "save", "cancel"
            ),
            emptyList(),
            emptyList()
        ),
        NodeSchema(
            NodeID.CONSULTATION_FORM, "modal",
            listOf(
                "setMode", "setDoctorName", "setSpecialty", "setDate", "setNotes", "unlinkMedication",
                "presentPicker", "dismissPicker", "save", "cancel"
            ),
            listOf("pickerSelectionChanged"),
            emptyList()
        ),
        NodeSchema(
            NodeID.MEDICATION_PICKER, "modal_child",
            listOf("toggle", "done"),
            emptyList(),
            listOf("present")
        ),
        NodeSchema(
            NodeID.SETTINGS, "modal",
            listOf(
                "setAllEnabled", "setMedicationsEnabled", "setAppointmentsEnabled",
                "setConsultationsEnabled", "setLowStockEnabled", "setLowStockThreshold",
                "setMedicationReminderTime", "setAppointmentReminderTime", "save", "cancel"
            ),
            emptyList(),
            listOf("seedDraft")
        )
    )

    private val byID: Map<NodeID, NodeSchema> = schemas.associateBy { it.id }

    fun schema(node: NodeID): NodeSchema? = byID[node]

    /**
     * Every node is emitted on every step, not just the journey's declared `nodes` list.
     * The list is documentation; emitting the superset keeps assertion paths resolvable and
     * the two streams identical regardless of what a journey author remembered to declare.
     */
    fun emit(context: MachineContext): Map<String, ReflexValue> {
        val state = context.state
        return linkedMapOf(
            NodeID.APP_ROOT.rawValue to AppRootMachine.emit(state.appRoot, context),
            NodeID.ONBOARDING.rawValue to OnboardingMachine.emit(state.onboarding, context),
            NodeID.DASHBOARD.rawValue to DashboardMachine.emit(state.dashboard, context),
            NodeID.CONSULTATIONS.rawValue to ConsultationsMachine.emit(state.consultations, context),
            NodeID.MEDICATION_TRACKER.rawValue to MedicationTrackerMachine.emit(state.medicationTracker, context),
            NodeID.APPOINTMENTS.rawValue to AppointmentsMachine.emit(state.appointments, context),
            NodeID.VITALS.rawValue to VitalsMachine.emit(state.vitals, context),
            NodeID.VITALS_DETAIL.rawValue to VitalsDetailMachine.emit(state.vitalsDetail, context),
            NodeID.ADD_VITAL.rawValue to AddVitalMachine.emit(state.addVital, context),
            NodeID.ADD_MEDICATION.rawValue to AddMedicationMachine.emit(state.addMedication, context),
            NodeID.ADD_APPOINTMENT.rawValue to AddAppointmentMachine.emit(state.addAppointment, context),
            NodeID.CONSULTATION_FORM.rawValue to ConsultationFormMachine.emit(state.consultationForm, context),
            NodeID.MEDICATION_PICKER.rawValue to MedicationPickerMachine.emit(state.medicationPicker, context),
            NodeID.SETTINGS.rawValue to SettingsMachine.emit(state.settings, context)
        )
    }
}
