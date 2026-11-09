package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.FormMode
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

data class ConsultationsState(
    val searchQuery: String,
    val presentedForm: UUID?,
    val formMode: FormMode
) {
    companion object {
        fun initial() = ConsultationsState(searchQuery = "", presentedForm = null, formMode = FormMode.CLOSED)
    }
}

sealed class ConsultationsAction {
    data class SetSearch(val text: String) : ConsultationsAction()

    data object OpenAdd : ConsultationsAction()

    data class OpenEdit(val id: UUID) : ConsultationsAction()

    data class Delete(val id: UUID) : ConsultationsAction()

    data object CloseForm : ConsultationsAction()

    companion object {
        fun decode(name: String, args: ActionArguments): ConsultationsAction? = when (name) {
            "setSearch" -> SetSearch(args.string("text"))
            "openAdd" -> OpenAdd
            "openEdit" -> OpenEdit(args.uuid("consultationId"))
            "delete" -> Delete(args.uuid("consultationId"))
            "closeForm" -> CloseForm
            else -> null
        }
    }
}

object ConsultationsMachine {

    /**
     * nodes.yaml describes presentedForm as "null = closed; a sentinel NEW id = add mode".
     * The sentinel is a fixed literal, NOT a generator draw — opening a form consumes zero
     * ids (consultation_crud asserts ids.consumed == 0 after a failed add).
     */
    val ADD_SENTINEL: UUID = parseUUID("00000000-0000-0000-0000-000000000000")!!

    fun reduce(
        state: ConsultationsState,
        action: ConsultationsAction,
        context: MachineContext
    ): Transition<ConsultationsState> = when (action) {
        is ConsultationsAction.SetSearch -> Transition(state.copy(searchQuery = action.text))

        is ConsultationsAction.OpenAdd -> Transition(
            state.copy(formMode = FormMode.ADD, presentedForm = ADD_SENTINEL),
            listOf(
                Effect.Dispatch(
                    Action.ToConsultationForm(
                        ConsultationFormAction.SetMode(com.pharmtrack.reflex.core.App.EditMode.ADD, null)
                    )
                )
            )
        )

        is ConsultationsAction.OpenEdit -> Transition(
            state.copy(formMode = FormMode.EDIT, presentedForm = action.id),
            listOf(
                Effect.Dispatch(
                    Action.ToConsultationForm(
                        ConsultationFormAction.SetMode(com.pharmtrack.reflex.core.App.EditMode.EDIT, action.id)
                    )
                )
            )
        )

        is ConsultationsAction.Delete -> {
            // D10. Immediate remove-by-id, no confirmation, no undo. An absent id writes nothing.
            val index = context.data.consultations.indexOfFirst { it.id == action.id }
            if (index < 0) {
                Transition(state)
            } else {
                val data = context.data.copy(
                    consultations = context.data.consultations.toMutableList().also { it.removeAt(index) }
                )
                Transition(
                    state,
                    listOf(
                        Effect.Dispatch(
                            Action.ToAppRoot(
                                AppRootAction.CommitData(
                                    data,
                                    com.pharmtrack.reflex.core.App.CommitReason.DELETE
                                )
                            )
                        )
                    )
                )
            }
        }

        is ConsultationsAction.CloseForm ->
            Transition(state.copy(formMode = FormMode.CLOSED, presentedForm = null))
    }

    fun emit(state: ConsultationsState, context: MachineContext): ReflexValue {
        val all = context.data.consultations
        // Storage order is newest-ADDED-first; the list applies NO sort. Notes are not searched.
        val filtered = all.filter {
            Selectors.matches(it.doctorName, state.searchQuery) ||
                Selectors.matches(it.specialty, state.searchQuery)
        }
        val initials = all.associate { ReflexValue.uuidString(it.id) to ReflexValue.Text(ValueFormat.initials(it.doctorName)) }
        return ReflexValue.of(
            mapOf(
                "searchQuery" to ReflexValue.Text(state.searchQuery),
                "presentedForm" to ReflexValue.optionalUUID(state.presentedForm),
                "formMode" to ReflexValue.Text(state.formMode.rawValue),
                "filteredIds" to ReflexValue.uuids(filtered.map { it.id }),
                // Q11. The badge shows TOTAL, not filtered.
                "totalCount" to ReflexValue.of(all.size.toLong()),
                "initials" to ReflexValue.of(initials)
            )
        )
    }
}
