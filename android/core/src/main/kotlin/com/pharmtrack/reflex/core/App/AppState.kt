package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Screens.AddAppointmentState
import com.pharmtrack.reflex.core.Screens.AddMedicationState
import com.pharmtrack.reflex.core.Screens.AddVitalState
import com.pharmtrack.reflex.core.Screens.AppRootState
import com.pharmtrack.reflex.core.Screens.AppointmentsState
import com.pharmtrack.reflex.core.Screens.ConsultationFormState
import com.pharmtrack.reflex.core.Screens.ConsultationsState
import com.pharmtrack.reflex.core.Screens.DashboardState
import com.pharmtrack.reflex.core.Screens.MedicationPickerState
import com.pharmtrack.reflex.core.Screens.MedicationTrackerState
import com.pharmtrack.reflex.core.Screens.OnboardingState
import com.pharmtrack.reflex.core.Screens.SettingsState
import com.pharmtrack.reflex.core.Screens.VitalsDetailState
import com.pharmtrack.reflex.core.Screens.VitalsState
import com.pharmtrack.reflex.core.Support.ReflexValue

/** The result of a reduction: the next state and the effects it queued. */
data class Transition<S>(val state: S, val effects: List<Effect> = emptyList())

/**
 * Everything a reducer may read besides its own state. `now` and `zone` come from the
 * injected Clock, never from the system; `ids` is the only source of new identifiers.
 */
class MachineContext(
    val state: AppState,
    val now: Instant,
    val zone: Instant.Zone,
    val ids: IDGenerator
) {
    val data: com.pharmtrack.reflex.core.Domain.MedicalData get() = state.appRoot.data
}

/**
 * The whole application state: one immutable value per node. Reducers receive the full
 * AppState through MachineContext so a tab can read the root's data and the root can read
 * a tab's selection without either owning the other.
 */
data class AppState(
    val appRoot: AppRootState,
    val onboarding: OnboardingState,
    val dashboard: DashboardState,
    val consultations: ConsultationsState,
    val medicationTracker: MedicationTrackerState,
    val appointments: AppointmentsState,
    val vitals: VitalsState,
    val vitalsDetail: VitalsDetailState,
    val addVital: AddVitalState,
    val addMedication: AddMedicationState,
    val addAppointment: AddAppointmentState,
    val consultationForm: ConsultationFormState,
    val medicationPicker: MedicationPickerState,
    val settings: SettingsState
) {
    companion object {
        fun initial(now: Instant, zone: Instant.Zone = Instant.Zone.utc): AppState = AppState(
            appRoot = AppRootState.initial(now),
            onboarding = OnboardingState.initial(),
            dashboard = DashboardState.initial(),
            consultations = ConsultationsState.initial(),
            medicationTracker = MedicationTrackerState.initial(),
            appointments = AppointmentsState.initial(now, zone),
            vitals = VitalsState.initial(),
            vitalsDetail = VitalsDetailState.initial(VitalType.WEIGHT, now, zone),
            addVital = AddVitalState.initial(now),
            addMedication = AddMedicationState.initial(),
            addAppointment = AddAppointmentState.initial(now),
            consultationForm = ConsultationFormState.initial(now),
            medicationPicker = MedicationPickerState.initial(),
            settings = SettingsState.initial()
        )
    }
}
