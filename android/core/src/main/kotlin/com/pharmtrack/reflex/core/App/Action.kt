package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Screens.AddAppointmentAction
import com.pharmtrack.reflex.core.Screens.AddMedicationAction
import com.pharmtrack.reflex.core.Screens.AddVitalAction
import com.pharmtrack.reflex.core.Screens.AppRootAction
import com.pharmtrack.reflex.core.Screens.AppointmentsAction
import com.pharmtrack.reflex.core.Screens.ConsultationFormAction
import com.pharmtrack.reflex.core.Screens.ConsultationsAction
import com.pharmtrack.reflex.core.Screens.DashboardAction
import com.pharmtrack.reflex.core.Screens.MedicationPickerAction
import com.pharmtrack.reflex.core.Screens.MedicationTrackerAction
import com.pharmtrack.reflex.core.Screens.OnboardingAction
import com.pharmtrack.reflex.core.Screens.SettingsAction
import com.pharmtrack.reflex.core.Screens.VitalsAction
import com.pharmtrack.reflex.core.Screens.VitalsDetailAction

/**
 * One synchronous intent, addressed to exactly one node. The wrapper exists so the runtime
 * can queue a heterogeneous effect list without losing the destination.
 */
sealed class Action {

    data class ToAppRoot(val action: AppRootAction) : Action()

    data class ToOnboarding(val action: OnboardingAction) : Action()

    data class ToDashboard(val action: DashboardAction) : Action()

    data class ToConsultations(val action: ConsultationsAction) : Action()

    data class ToMedicationTracker(val action: MedicationTrackerAction) : Action()

    data class ToAppointments(val action: AppointmentsAction) : Action()

    data class ToVitals(val action: VitalsAction) : Action()

    data class ToVitalsDetail(val action: VitalsDetailAction) : Action()

    data class ToAddVital(val action: AddVitalAction) : Action()

    data class ToAddMedication(val action: AddMedicationAction) : Action()

    data class ToAddAppointment(val action: AddAppointmentAction) : Action()

    data class ToConsultationForm(val action: ConsultationFormAction) : Action()

    data class ToMedicationPicker(val action: MedicationPickerAction) : Action()

    data class ToSettings(val action: SettingsAction) : Action()

    val node: NodeID
        get() = when (this) {
            is ToAppRoot -> NodeID.APP_ROOT
            is ToOnboarding -> NodeID.ONBOARDING
            is ToDashboard -> NodeID.DASHBOARD
            is ToConsultations -> NodeID.CONSULTATIONS
            is ToMedicationTracker -> NodeID.MEDICATION_TRACKER
            is ToAppointments -> NodeID.APPOINTMENTS
            is ToVitals -> NodeID.VITALS
            is ToVitalsDetail -> NodeID.VITALS_DETAIL
            is ToAddVital -> NodeID.ADD_VITAL
            is ToAddMedication -> NodeID.ADD_MEDICATION
            is ToAddAppointment -> NodeID.ADD_APPOINTMENT
            is ToConsultationForm -> NodeID.CONSULTATION_FORM
            is ToMedicationPicker -> NodeID.MEDICATION_PICKER
            is ToSettings -> NodeID.SETTINGS
        }

    companion object {
        /**
         * Internal cases (showHistory, syncFromRoot, present, seedDraft, open) are deliberately
         * absent from every node's decoder: a journey may not reach them, so they exit 2.
         */
        fun decode(args: ActionArguments): Action {
            val name = args.action
            return when (args.node) {
                NodeID.APP_ROOT -> ToAppRoot(require(AppRootAction.decode(name, args), args))
                NodeID.ONBOARDING -> ToOnboarding(require(OnboardingAction.decode(name, args), args))
                NodeID.DASHBOARD -> ToDashboard(require(DashboardAction.decode(name, args), args))
                NodeID.CONSULTATIONS -> ToConsultations(require(ConsultationsAction.decode(name, args), args))
                NodeID.MEDICATION_TRACKER ->
                    ToMedicationTracker(require(MedicationTrackerAction.decode(name, args), args))

                NodeID.APPOINTMENTS -> ToAppointments(require(AppointmentsAction.decode(name, args), args))
                NodeID.VITALS -> ToVitals(require(VitalsAction.decode(name, args), args))
                NodeID.VITALS_DETAIL -> ToVitalsDetail(require(VitalsDetailAction.decode(name, args), args))
                NodeID.ADD_VITAL -> ToAddVital(require(AddVitalAction.decode(name, args), args))
                NodeID.ADD_MEDICATION -> ToAddMedication(require(AddMedicationAction.decode(name, args), args))
                NodeID.ADD_APPOINTMENT -> ToAddAppointment(require(AddAppointmentAction.decode(name, args), args))
                NodeID.CONSULTATION_FORM ->
                    ToConsultationForm(require(ConsultationFormAction.decode(name, args), args))

                NodeID.MEDICATION_PICKER -> ToMedicationPicker(require(MedicationPickerAction.decode(name, args), args))
                NodeID.SETTINGS -> ToSettings(require(SettingsAction.decode(name, args), args))
            }
        }

        private fun <T> require(decoded: T?, args: ActionArguments): T =
            decoded ?: throw ActionError.unknownAction(args.node, args.action)
    }
}
