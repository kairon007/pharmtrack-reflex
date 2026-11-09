package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class MedicationPickerState(val selectedIds: List<UUID>) {
    companion object {
        fun initial() = MedicationPickerState(selectedIds = emptyList())
    }
}

sealed class MedicationPickerAction {
    data class Toggle(val id: UUID) : MedicationPickerAction()

    data object Done : MedicationPickerAction()

    /**
     * Internal: the parent seeds the selection when it presents the picker. Not reachable
     * from a journey — the schema exposes only toggle and done.
     */
    data class Present(val ids: List<UUID>) : MedicationPickerAction()

    companion object {
        fun decode(name: String, args: ActionArguments): MedicationPickerAction? = when (name) {
            "toggle" -> Toggle(args.uuid("medicationId"))
            "done" -> Done
            else -> null
        }
    }
}

object MedicationPickerMachine {

    fun reduce(
        state: MedicationPickerState,
        action: MedicationPickerAction,
        context: MachineContext
    ): Transition<MedicationPickerState> = when (action) {
        is MedicationPickerAction.Toggle -> {
            // Deselect removes ALL matching entries; select appends, so order is tap order.
            val selected = if (state.selectedIds.contains(action.id)) {
                state.selectedIds.filter { it != action.id }
            } else {
                state.selectedIds + action.id
            }
            Transition(
                state.copy(selectedIds = selected),
                listOf(
                    Effect.Deliver(
                        Message.ToConsultationForm(
                            ConsultationFormMessage.PickerSelectionChanged(selected)
                        )
                    )
                )
            )
        }

        is MedicationPickerAction.Done ->
            // Q9. done emits no commit; the parent already has the ids.
            Transition(
                state,
                listOf(Effect.Dispatch(Action.ToConsultationForm(ConsultationFormAction.DismissPicker)))
            )

        is MedicationPickerAction.Present -> Transition(state.copy(selectedIds = action.ids))
    }

    fun emit(state: MedicationPickerState, context: MachineContext): ReflexValue = ReflexValue.of(
        mapOf(
            "selectedIds" to ReflexValue.uuids(state.selectedIds),
            "availableIds" to ReflexValue.uuids(context.data.medications.map { it.id })
        )
    )
}
