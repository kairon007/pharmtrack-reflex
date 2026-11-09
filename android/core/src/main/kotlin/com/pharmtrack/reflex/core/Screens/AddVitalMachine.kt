package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class AddVitalState(
    val mode: EditMode,
    val editingId: UUID?,
    val type: VitalType,
    val valueText: String,
    val value2Text: String,
    val date: Instant,
    val notesText: String
) {
    companion object {
        fun initial(now: Instant) = AddVitalState(
            mode = EditMode.ADD,
            editingId = null,
            type = VitalType.WEIGHT,
            valueText = "",
            value2Text = "",
            date = now,
            notesText = ""
        )
    }
}

sealed class AddVitalAction {
    data class SetMode(val mode: EditMode, val id: UUID?) : AddVitalAction()

    data class SetType(val type: VitalType) : AddVitalAction()

    data class SetValueText(val text: String) : AddVitalAction()

    data class SetValue2Text(val text: String) : AddVitalAction()

    data class SetDate(val date: Instant) : AddVitalAction()

    data class SetNotes(val text: String) : AddVitalAction()

    data object Save : AddVitalAction()

    data object Cancel : AddVitalAction()

    companion object {
        fun decode(name: String, args: ActionArguments): AddVitalAction? = when (name) {
            "setMode" -> SetMode(
                EditMode.fromRaw(args.enumeration("mode", "EditMode", EditMode.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable"),
                args.optionalUUID("vitalId")
            )

            "setType" -> SetType(
                VitalType.fromRaw(args.enumeration("type", "VitalType", VitalType.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "setValueText" -> SetValueText(args.string("text"))
            "setValue2Text" -> SetValue2Text(args.string("text"))
            "setDate" -> SetDate(args.instant("date"))
            "setNotes" -> SetNotes(args.string("text"))
            "save" -> Save
            "cancel" -> Cancel
            else -> null
        }
    }
}

object AddVitalMachine {

    fun reduce(
        state: AddVitalState,
        action: AddVitalAction,
        context: MachineContext
    ): Transition<AddVitalState> = when (action) {
        is AddVitalAction.SetMode -> {
            var next = AddVitalState.initial(context.now).copy(mode = action.mode, editingId = action.id)
            if (action.mode == EditMode.EDIT && action.id != null) {
                val vital = context.data.vital(action.id)
                if (vital != null) {
                    next = next.copy(
                        type = vital.type,
                        valueText = ValueFormat.shortestDecimal(vital.value),
                        value2Text = vital.value2?.let { ValueFormat.shortestDecimal(it) } ?: "",
                        date = vital.date,
                        notesText = vital.notes ?: ""
                    )
                }
            }
            Transition(next)
        }

        is AddVitalAction.SetType ->
            // Q: switching type does NOT clear the diastolic buffer, it is merely ignored.
            Transition(state.copy(type = action.type))

        is AddVitalAction.SetValueText -> Transition(state.copy(valueText = action.text))

        is AddVitalAction.SetValue2Text -> Transition(state.copy(value2Text = action.text))

        is AddVitalAction.SetDate -> Transition(state.copy(date = action.date))

        is AddVitalAction.SetNotes -> Transition(state.copy(notesText = action.text))

        is AddVitalAction.Save -> {
            // Q7. The gate is isEmpty WITHOUT trimming; an unparseable value is a SILENT
            // no-op that leaves the modal open and surfaces no error.
            if (state.valueText.isEmpty()) {
                Transition(state)
            } else {
                val value = ValueFormat.parseDouble(state.valueText)
                if (value == null) {
                    Transition(state)
                } else {
                    val value2 = if (state.type == VitalType.BLOOD_PRESSURE) {
                        ValueFormat.parseDouble(state.value2Text)
                    } else {
                        null
                    }
                    val notes = if (state.notesText.isEmpty()) null else state.notesText
                    val data = context.data

                    if (state.mode == EditMode.ADD) {
                        val vital = Vital(
                            id = context.ids.next(),
                            type = state.type,
                            value = value,
                            value2 = value2,
                            date = state.date,
                            notes = notes
                        )
                        // Q16. Vitals insert at the front of storage; display order is by date.
                        val updated = data.copy(vitals = listOf(vital) + data.vitals)
                        Transition(
                            state,
                            listOf(
                                Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.ADD))),
                                Effect.Dispatch(Action.ToVitals(VitalsAction.CloseForm))
                            )
                        )
                    } else {
                        val id = state.editingId
                        val index = if (id == null) -1 else data.vitals.indexOfFirst { it.id == id }
                        if (id == null || index < 0) {
                            Transition(state)
                        } else {
                            // D10. Replace by id, storage position preserved, no re-sort, no id consumed.
                            val vitals = data.vitals.toMutableList()
                            vitals[index] = Vital(
                                id = id,
                                type = state.type,
                                value = value,
                                value2 = value2,
                                date = state.date,
                                notes = notes
                            )
                            val updated = data.copy(vitals = vitals)
                            Transition(
                                state,
                                listOf(
                                    Effect.Dispatch(
                                        Action.ToAppRoot(AppRootAction.CommitData(updated, CommitReason.UPDATE))
                                    ),
                                    Effect.Dispatch(Action.ToVitals(VitalsAction.CloseForm))
                                )
                            )
                        }
                    }
                }
            }
        }

        is AddVitalAction.Cancel ->
            Transition(state, listOf(Effect.Dispatch(Action.ToVitals(VitalsAction.CloseForm))))
    }

    fun emit(state: AddVitalState, context: MachineContext): ReflexValue = ReflexValue.of(
        mapOf(
            "mode" to ReflexValue.Text(state.mode.rawValue),
            "editingId" to ReflexValue.optionalUUID(state.editingId),
            "type" to ReflexValue.Text(state.type.rawValue),
            "valueText" to ReflexValue.Text(state.valueText),
            "value2Text" to ReflexValue.Text(state.value2Text),
            "date" to ReflexValue.instant(state.date),
            "notesText" to ReflexValue.Text(state.notesText),
            "value2Visible" to ReflexValue.of(state.type == VitalType.BLOOD_PRESSURE),
            "canSave" to ReflexValue.of(state.valueText.isNotEmpty()),
            "unit" to ReflexValue.Text(ValueFormat.unit(state.type))
        )
    )
}
