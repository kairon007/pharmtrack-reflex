package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class AddAppointmentState(
    val mode: EditMode,
    val editingId: UUID?,
    val doctorNameText: String,
    val specialtyText: String,
    val typeText: String,
    val date: Instant,
    val locationText: String,
    val notesText: String
) {
    val canSave: Boolean
        get() = doctorNameText.isNotEmpty() && specialtyText.isNotEmpty() &&
            typeText.isNotEmpty() && locationText.isNotEmpty()

    companion object {
        fun initial(date: Instant) = AddAppointmentState(
            mode = EditMode.ADD,
            editingId = null,
            doctorNameText = "",
            specialtyText = "",
            typeText = "",
            date = date,
            locationText = "",
            notesText = ""
        )
    }
}

sealed class AddAppointmentAction {
    data class SetMode(val mode: EditMode, val id: UUID?) : AddAppointmentAction()

    data class SetDoctorName(val text: String) : AddAppointmentAction()

    data class SetSpecialty(val text: String) : AddAppointmentAction()

    data class SetType(val text: String) : AddAppointmentAction()

    data class SetDate(val date: Instant) : AddAppointmentAction()

    data class SetLocation(val text: String) : AddAppointmentAction()

    data class SetNotes(val text: String) : AddAppointmentAction()

    data object Save : AddAppointmentAction()

    data object Cancel : AddAppointmentAction()

    companion object {
        fun decode(name: String, args: ActionArguments): AddAppointmentAction? = when (name) {
            "setMode" -> SetMode(
                EditMode.fromRaw(args.enumeration("mode", "EditMode", EditMode.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable"),
                args.optionalUUID("appointmentId")
            )

            "setDoctorName" -> SetDoctorName(args.string("text"))
            "setSpecialty" -> SetSpecialty(args.string("text"))
            "setType" -> SetType(args.string("text"))
            "setDate" -> SetDate(args.instant("date"))
            "setLocation" -> SetLocation(args.string("text"))
            "setNotes" -> SetNotes(args.string("text"))
            "save" -> Save
            "cancel" -> Cancel
            else -> null
        }
    }
}

object AddAppointmentMachine {

    fun reduce(
        state: AddAppointmentState,
        action: AddAppointmentAction,
        context: MachineContext
    ): Transition<AddAppointmentState> = when (action) {
        is AddAppointmentAction.SetMode -> {
            // The add form opens on the day the calendar is showing, not on "now".
            var next = AddAppointmentState.initial(context.state.appointments.selectedDate)
                .copy(mode = action.mode, editingId = action.id)
            if (action.mode == EditMode.EDIT && action.id != null) {
                val appointment = context.data.appointment(action.id)
                if (appointment != null) {
                    next = next.copy(
                        doctorNameText = appointment.doctorName,
                        specialtyText = appointment.specialty,
                        typeText = appointment.type,
                        date = appointment.date,
                        locationText = appointment.location,
                        notesText = appointment.notes ?: ""
                    )
                }
            }
            Transition(next)
        }

        is AddAppointmentAction.SetDoctorName -> Transition(state.copy(doctorNameText = action.text))

        is AddAppointmentAction.SetSpecialty -> Transition(state.copy(specialtyText = action.text))

        is AddAppointmentAction.SetType -> Transition(state.copy(typeText = action.text))

        is AddAppointmentAction.SetDate -> Transition(state.copy(date = action.date))

        is AddAppointmentAction.SetLocation -> Transition(state.copy(locationText = action.text))

        is AddAppointmentAction.SetNotes -> Transition(state.copy(notesText = action.text))

        is AddAppointmentAction.Save -> {
            if (!state.canSave) {
                Transition(state)
            } else {
                val notes = if (state.notesText.isEmpty()) null else state.notesText
                val data = context.data
                var appointments = data.appointments

                if (state.mode == EditMode.ADD) {
                    appointments = appointments + Appointment(
                        id = context.ids.next(),
                        doctorName = state.doctorNameText,
                        specialty = state.specialtyText,
                        type = state.typeText,
                        date = state.date,
                        location = state.locationText,
                        notes = notes,
                        // Q8. No toggle exists. true on create, preserved on edit.
                        notify = true
                    )
                } else {
                    val id = state.editingId
                    val index = if (id == null) -1 else appointments.indexOfFirst { it.id == id }
                    if (id == null || index < 0) {
                        Transition(state)
                    } else {
                        val notify = appointments[index].notify
                        val mutable = appointments.toMutableList()
                        mutable[index] = Appointment(
                            id = id,
                            doctorName = state.doctorNameText,
                            specialty = state.specialtyText,
                            type = state.typeText,
                            date = state.date,
                            location = state.locationText,
                            notes = notes,
                            notify = notify
                        )
                        appointments = mutable
                    }
                }

                // BOTH paths re-sort the whole array, so editing a date can move the row off
                // the currently selected day (MedicalViewModel.swift:169-171, :228-234).
                val sorted = Selectors.sortedByDateAndId(appointments, date = { it.date }, id = { it.id })
                val updated = data.copy(appointments = sorted)
                val reason = if (state.mode == EditMode.ADD) CommitReason.ADD else CommitReason.UPDATE
                Transition(
                    state,
                    listOf(
                        Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(updated, reason))),
                        Effect.Dispatch(Action.ToAppointments(AppointmentsAction.CloseForm))
                    )
                )
            }
        }

        is AddAppointmentAction.Cancel ->
            Transition(state, listOf(Effect.Dispatch(Action.ToAppointments(AppointmentsAction.CloseForm))))
    }

    fun emit(state: AddAppointmentState, context: MachineContext): ReflexValue = ReflexValue.of(
        mapOf(
            "mode" to ReflexValue.Text(state.mode.rawValue),
            "editingId" to ReflexValue.optionalUUID(state.editingId),
            "doctorNameText" to ReflexValue.Text(state.doctorNameText),
            "specialtyText" to ReflexValue.Text(state.specialtyText),
            "typeText" to ReflexValue.Text(state.typeText),
            "date" to ReflexValue.instant(state.date),
            "locationText" to ReflexValue.Text(state.locationText),
            "notesText" to ReflexValue.Text(state.notesText),
            "canSave" to ReflexValue.of(state.canSave)
        )
    )
}
