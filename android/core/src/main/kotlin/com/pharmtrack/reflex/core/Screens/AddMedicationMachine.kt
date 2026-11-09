package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.MedicationSchedule
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class AddMedicationState(
    val mode: EditMode,
    val editingId: UUID?,
    val nameText: String,
    val dosageText: String,
    val instructionsText: String,
    val schedule: MedicationSchedule,
    val stockText: String
) {
    companion object {
        fun initial() = AddMedicationState(
            mode = EditMode.ADD,
            editingId = null,
            nameText = "",
            dosageText = "",
            instructionsText = "",
            schedule = MedicationSchedule.MORNING,
            stockText = ""
        )
    }
}

sealed class AddMedicationAction {
    data class SetMode(val mode: EditMode, val id: UUID?) : AddMedicationAction()

    data class SetName(val text: String) : AddMedicationAction()

    data class SetDosage(val text: String) : AddMedicationAction()

    data class SetInstructions(val text: String) : AddMedicationAction()

    data class SetSchedule(val schedule: MedicationSchedule) : AddMedicationAction()

    data class SetStockText(val text: String) : AddMedicationAction()

    data object Save : AddMedicationAction()

    data object Cancel : AddMedicationAction()

    companion object {
        fun decode(name: String, args: ActionArguments): AddMedicationAction? = when (name) {
            "setMode" -> SetMode(
                EditMode.fromRaw(args.enumeration("mode", "EditMode", EditMode.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable"),
                args.optionalUUID("medicationId")
            )

            "setName" -> SetName(args.string("text"))
            "setDosage" -> SetDosage(args.string("text"))
            "setInstructions" -> SetInstructions(args.string("text"))
            "setSchedule" -> SetSchedule(
                MedicationSchedule.fromRaw(
                    args.enumeration("schedule", "MedicationSchedule", MedicationSchedule.entries.map { it.rawValue })
                ) ?: throw IllegalStateException("unreachable")
            )

            "setStockText" -> SetStockText(args.string("text"))
            "save" -> Save
            "cancel" -> Cancel
            else -> null
        }
    }
}

object AddMedicationMachine {

    fun reduce(
        state: AddMedicationState,
        action: AddMedicationAction,
        context: MachineContext
    ): Transition<AddMedicationState> = when (action) {
        is AddMedicationAction.SetMode -> {
            var next = AddMedicationState.initial().copy(mode = action.mode, editingId = action.id)
            if (action.mode == EditMode.EDIT && action.id != null) {
                val medication = context.data.medication(action.id)
                if (medication != null) {
                    next = next.copy(
                        nameText = medication.name,
                        dosageText = medication.dosage,
                        instructionsText = medication.instructions,
                        schedule = MedicationSchedule.fromRaw(medication.schedule) ?: MedicationSchedule.MORNING,
                        stockText = medication.stock.toString()
                    )
                }
            }
            Transition(next)
        }

        is AddMedicationAction.SetName -> Transition(state.copy(nameText = action.text))

        is AddMedicationAction.SetDosage -> Transition(state.copy(dosageText = action.text))

        is AddMedicationAction.SetInstructions -> Transition(state.copy(instructionsText = action.text))

        is AddMedicationAction.SetSchedule -> Transition(state.copy(schedule = action.schedule))

        is AddMedicationAction.SetStockText -> Transition(state.copy(stockText = action.text))

        is AddMedicationAction.Save -> {
            // D13. isEmpty WITHOUT trimming, so " " passes and persists untrimmed.
            if (state.nameText.isEmpty() || state.dosageText.isEmpty()) {
                Transition(state)
            } else {
                // Q6. Int(text) ?? 0 — no trim, no clamp, negatives accepted.
                val stock = ValueFormat.parseInt(state.stockText) ?: 0L
                val data = context.data

                if (state.mode == EditMode.ADD) {
                    val medication = Medication(
                        id = context.ids.next(),
                        name = state.nameText,
                        dosage = state.dosageText,
                        instructions = state.instructionsText,
                        schedule = state.schedule.rawValue,
                        stock = stock,
                        active = true,
                        // Already past by render time, so the planner's trigger > now guard
                        // skips the reminder. Real behavior, not an off-by-one to fix.
                        nextDose = context.now
                    )
                    val updated = data.copy(medications = data.medications + medication)
                    Transition(
                        state,
                        listOf(
                            Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.ADD))),
                            Effect.Dispatch(Action.ToMedicationTracker(MedicationTrackerAction.CloseForm))
                        )
                    )
                } else {
                    val id = state.editingId
                    val index = if (id == null) -1 else data.medications.indexOfFirst { it.id == id }
                    if (id == null || index < 0) {
                        Transition(state)
                    } else {
                        // Edit overwrites these five ONLY. nextDose, active and id are preserved.
                        val medications = data.medications.toMutableList()
                        medications[index] = medications[index].copy(
                            name = state.nameText,
                            dosage = state.dosageText,
                            instructions = state.instructionsText,
                            schedule = state.schedule.rawValue,
                            stock = stock
                        )
                        val updated = data.copy(medications = medications)
                        Transition(
                            state,
                            listOf(
                                Effect.Dispatch(
                                    Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.UPDATE))
                                ),
                                Effect.Dispatch(Action.ToMedicationTracker(MedicationTrackerAction.CloseForm))
                            )
                        )
                    }
                }
            }
        }

        is AddMedicationAction.Cancel ->
            Transition(state, listOf(Effect.Dispatch(Action.ToMedicationTracker(MedicationTrackerAction.CloseForm))))
    }

    fun emit(state: AddMedicationState, context: MachineContext): ReflexValue = ReflexValue.of(
        mapOf(
            "mode" to ReflexValue.Text(state.mode.rawValue),
            "editingId" to ReflexValue.optionalUUID(state.editingId),
            "nameText" to ReflexValue.Text(state.nameText),
            "dosageText" to ReflexValue.Text(state.dosageText),
            "instructionsText" to ReflexValue.Text(state.instructionsText),
            "schedule" to ReflexValue.Text(state.schedule.rawValue),
            "stockText" to ReflexValue.Text(state.stockText),
            "canSave" to ReflexValue.of(state.nameText.isNotEmpty() && state.dosageText.isNotEmpty())
        )
    )
}
