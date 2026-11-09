package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.FormMode
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class VitalsState(
    val selectedMetric: VitalType,
    val formMode: FormMode,
    val detailPresented: Boolean
) {
    companion object {
        fun initial() = VitalsState(
            selectedMetric = VitalType.WEIGHT,
            formMode = FormMode.CLOSED,
            detailPresented = false
        )
    }
}

sealed class VitalsAction {
    data class SelectMetric(val metric: VitalType) : VitalsAction()

    data object OpenAdd : VitalsAction()

    data class OpenEdit(val id: UUID) : VitalsAction()

    data class Delete(val id: UUID) : VitalsAction()

    data object OpenDetail : VitalsAction()

    data object CloseDetail : VitalsAction()

    data object CloseForm : VitalsAction()

    companion object {
        fun decode(name: String, args: ActionArguments): VitalsAction? = when (name) {
            "selectMetric" -> SelectMetric(
                VitalType.fromRaw(args.enumeration("metric", "VitalType", VitalType.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "openAdd" -> OpenAdd
            "openEdit" -> OpenEdit(args.uuid("vitalId"))
            "delete" -> Delete(args.uuid("vitalId"))
            "openDetail" -> OpenDetail
            "closeDetail" -> CloseDetail
            "closeForm" -> CloseForm
            else -> null
        }
    }
}

object VitalsMachine {

    fun reduce(
        state: VitalsState,
        action: VitalsAction,
        context: MachineContext
    ): Transition<VitalsState> = when (action) {
        is VitalsAction.SelectMetric -> Transition(state.copy(selectedMetric = action.metric))

        is VitalsAction.OpenAdd -> Transition(
            state.copy(formMode = FormMode.ADD),
            listOf(Effect.Dispatch(Action.ToAddVital(AddVitalAction.SetMode(EditMode.ADD, null))))
        )

        is VitalsAction.OpenEdit -> Transition(
            state.copy(formMode = FormMode.EDIT),
            listOf(Effect.Dispatch(Action.ToAddVital(AddVitalAction.SetMode(EditMode.EDIT, action.id))))
        )

        is VitalsAction.Delete -> {
            // MedicalViewModel.swift:186-191 saves only when the id was found. No Log entry.
            val index = context.data.vitals.indexOfFirst { it.id == action.id }
            if (index < 0) {
                Transition(state)
            } else {
                val data = context.data.copy(
                    vitals = context.data.vitals.toMutableList().also { it.removeAt(index) }
                )
                Transition(
                    state,
                    listOf(Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.DELETE))))
                )
            }
        }

        is VitalsAction.OpenDetail -> Transition(
            state.copy(detailPresented = true),
            listOf(
                Effect.Dispatch(
                    Action.ToVitalsDetail(VitalsDetailAction.Open(state.selectedMetric))
                )
            )
        )

        is VitalsAction.CloseDetail -> Transition(
            state.copy(detailPresented = false),
            listOf(Effect.Dispatch(Action.ToVitalsDetail(VitalsDetailAction.Close)))
        )

        is VitalsAction.CloseForm -> Transition(state.copy(formMode = FormMode.CLOSED))
    }

    /**
     * Q18. No time-range cutoff on this screen — the tab and the detail screen legitimately
     * disagree on series length for the same data and metric.
     */
    fun series(state: VitalsState, context: MachineContext): List<Vital> = Selectors.sortedByDateAndId(
        context.data.vitals.filter { it.type == state.selectedMetric },
        date = { it.date },
        id = { it.id }
    )

    fun emit(state: VitalsState, context: MachineContext): ReflexValue {
        val ascending = series(state, context)
        val rowIcon = context.data.vitals.associate { vital ->
            ReflexValue.uuidString(vital.id) to ReflexValue.Text(iconName(vital.type))
        }
        val trend = Selectors.trend(ascending)
        return ReflexValue.of(
            mapOf(
                "selectedMetric" to ReflexValue.Text(state.selectedMetric.rawValue),
                "formMode" to ReflexValue.Text(state.formMode.rawValue),
                "detailPresented" to ReflexValue.of(state.detailPresented),
                "filteredVitals" to ReflexValue.uuids(ascending.map { it.id }),
                "latestReadingId" to ReflexValue.optionalUUID(ascending.lastOrNull()?.id),
                "recentIds" to ReflexValue.uuids(ascending.asReversed().take(5).map { it.id }),
                "trend" to (trend?.reflexValue ?: ReflexValue.Null),
                "formattedValue" to ReflexValue.Text(ValueFormat.reading(ascending.lastOrNull())),
                "rowIcon" to ReflexValue.of(rowIcon)
            )
        )
    }

    fun iconName(type: VitalType): String = when (type) {
        VitalType.WEIGHT -> "weight"
        VitalType.BLOOD_PRESSURE -> "blood_pressure"
        VitalType.BLOOD_SUGAR -> "blood_sugar"
    }
}
