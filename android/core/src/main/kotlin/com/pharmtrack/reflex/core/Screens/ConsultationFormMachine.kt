package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Consultation
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class ConsultationFormState(
    val mode: EditMode,
    val editingId: UUID?,
    val doctorNameText: String,
    val specialtyText: String,
    val date: Instant,
    val notesText: String,
    val linkedMedicationIds: List<UUID>,
    val pickerPresented: Boolean
) {
    companion object {
        fun initial(now: Instant) = ConsultationFormState(
            mode = EditMode.ADD,
            editingId = null,
            doctorNameText = "",
            specialtyText = "",
            date = now,
            notesText = "",
            linkedMedicationIds = emptyList(),
            pickerPresented = false
        )
    }
}

sealed class ConsultationFormAction {
    data class SetMode(val mode: EditMode, val id: UUID?) : ConsultationFormAction()

    data class SetDoctorName(val text: String) : ConsultationFormAction()

    data class SetSpecialty(val text: String) : ConsultationFormAction()

    data class SetDate(val date: Instant) : ConsultationFormAction()

    data class SetNotes(val text: String) : ConsultationFormAction()

    data class UnlinkMedication(val id: UUID) : ConsultationFormAction()

    data object PresentPicker : ConsultationFormAction()

    data object DismissPicker : ConsultationFormAction()

    data object Save : ConsultationFormAction()

    data object Cancel : ConsultationFormAction()

    companion object {
        fun decode(name: String, args: ActionArguments): ConsultationFormAction? = when (name) {
            "setMode" -> SetMode(
                EditMode.fromRaw(args.enumeration("mode", "EditMode", EditMode.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable"),
                args.optionalUUID("consultationId")
            )

            "setDoctorName" -> SetDoctorName(args.string("text"))
            "setSpecialty" -> SetSpecialty(args.string("text"))
            "setDate" -> SetDate(args.instant("date"))
            "setNotes" -> SetNotes(args.string("text"))
            "unlinkMedication" -> UnlinkMedication(args.uuid("medicationId"))
            "presentPicker" -> PresentPicker
            "dismissPicker" -> DismissPicker
            "save" -> Save
            "cancel" -> Cancel
            else -> null
        }
    }
}

sealed class ConsultationFormMessage {
    data class PickerSelectionChanged(val ids: List<UUID>) : ConsultationFormMessage()

    companion object {
        fun decode(name: String, args: ActionArguments): ConsultationFormMessage? = when (name) {
            "pickerSelectionChanged" -> PickerSelectionChanged(args.uuidArray("ids"))
            else -> null
        }
    }
}

object ConsultationFormMachine {

    fun reduce(
        state: ConsultationFormState,
        action: ConsultationFormAction,
        context: MachineContext
    ): Transition<ConsultationFormState> = when (action) {
        is ConsultationFormAction.SetMode -> {
            var next = ConsultationFormState.initial(context.now).copy(mode = action.mode, editingId = action.id)
            if (action.mode == EditMode.EDIT && action.id != null) {
                val consultation = context.data.consultation(action.id)
                if (consultation != null) {
                    next = next.copy(
                        doctorNameText = consultation.doctorName,
                        specialtyText = consultation.specialty,
                        date = consultation.date,
                        notesText = consultation.notes,
                        linkedMedicationIds = consultation.linkedMedications
                    )
                }
            }
            Transition(next)
        }

        is ConsultationFormAction.SetDoctorName -> Transition(state.copy(doctorNameText = action.text))

        is ConsultationFormAction.SetSpecialty -> Transition(state.copy(specialtyText = action.text))

        is ConsultationFormAction.SetDate -> Transition(state.copy(date = action.date))

        is ConsultationFormAction.SetNotes -> Transition(state.copy(notesText = action.text))

        is ConsultationFormAction.UnlinkMedication ->
            Transition(state.copy(linkedMedicationIds = state.linkedMedicationIds.filter { it != action.id }))

        is ConsultationFormAction.PresentPicker -> Transition(
            state.copy(pickerPresented = true),
            listOf(
                Effect.Dispatch(
                    Action.ToMedicationPicker(MedicationPickerAction.Present(state.linkedMedicationIds))
                )
            )
        )

        is ConsultationFormAction.DismissPicker ->
            // Q9. Dismissing never reverts — the parent already owns the ids.
            Transition(state.copy(pickerPresented = false))

        is ConsultationFormAction.Save -> {
            // D13. The gate TRIMS; the writer does not. " Dr. Alice Rao " persists untrimmed.
            if (state.doctorNameText.trim().isEmpty()) {
                Transition(state)
            } else {
                val data = context.data

                if (state.mode == EditMode.ADD) {
                    val consultation = Consultation(
                        id = context.ids.next(),
                        doctorName = state.doctorNameText,
                        specialty = state.specialtyText,
                        date = state.date,
                        notes = state.notesText,
                        linkedMedications = state.linkedMedicationIds
                    )
                    val updated = data.copy(consultations = listOf(consultation) + data.consultations)
                    Transition(
                        state,
                        listOf(
                            Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.ADD))),
                            Effect.Dispatch(Action.ToConsultations(ConsultationsAction.CloseForm))
                        )
                    )
                } else {
                    val id = state.editingId
                    val index = if (id == null) -1 else data.consultations.indexOfFirst { it.id == id }
                    if (id == null || index < 0) {
                        Transition(state)
                    } else {
                        // Q10. Dangling ids are written straight back — no cascade cleanup is invented.
                        val consultations = data.consultations.toMutableList()
                        consultations[index] = Consultation(
                            id = id,
                            doctorName = state.doctorNameText,
                            specialty = state.specialtyText,
                            date = state.date,
                            notes = state.notesText,
                            linkedMedications = state.linkedMedicationIds
                        )
                        val updated = data.copy(consultations = consultations)
                        Transition(
                            state,
                            listOf(
                                Effect.Dispatch(
                                    Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.UPDATE))
                                ),
                                Effect.Dispatch(Action.ToConsultations(ConsultationsAction.CloseForm))
                            )
                        )
                    }
                }
            }
        }

        is ConsultationFormAction.Cancel ->
            Transition(state, listOf(Effect.Dispatch(Action.ToConsultations(ConsultationsAction.CloseForm))))
    }

    fun reduce(
        state: ConsultationFormState,
        message: ConsultationFormMessage,
        context: MachineContext
    ): Transition<ConsultationFormState> = when (message) {
        is ConsultationFormMessage.PickerSelectionChanged ->
            Transition(state.copy(linkedMedicationIds = message.ids))
    }

    fun emit(state: ConsultationFormState, context: MachineContext): ReflexValue {
        val linkedRows = state.linkedMedicationIds.filter { context.data.medication(it) != null }
        return ReflexValue.of(
            mapOf(
                "mode" to ReflexValue.Text(state.mode.rawValue),
                "editingId" to ReflexValue.optionalUUID(state.editingId),
                "doctorNameText" to ReflexValue.Text(state.doctorNameText),
                "specialtyText" to ReflexValue.Text(state.specialtyText),
                "date" to ReflexValue.instant(state.date),
                "notesText" to ReflexValue.Text(state.notesText),
                "linkedMedicationIds" to ReflexValue.uuids(state.linkedMedicationIds),
                "pickerPresented" to ReflexValue.of(state.pickerPresented),
                "canSave" to ReflexValue.of(state.doctorNameText.trim().isNotEmpty()),
                "linkedRows" to ReflexValue.uuids(linkedRows)
            )
        )
    }
}
