package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.FormMode
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Domain.MedicationSchedule
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

enum class MedicationSegment(val rawValue: String) {
    TODAY("today"), INVENTORY("inventory"), HISTORY("history");

    companion object {
        fun fromRaw(raw: String): MedicationSegment? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class MedicationTrackerState(
    val segment: MedicationSegment,
    val searchQuery: String,
    val formMode: FormMode,
    val editingId: UUID?
) {
    companion object {
        fun initial() = MedicationTrackerState(
            segment = MedicationSegment.TODAY,
            searchQuery = "",
            formMode = FormMode.CLOSED,
            editingId = null
        )
    }
}

sealed class MedicationTrackerAction {
    data class SetSegment(val segment: MedicationSegment) : MedicationTrackerAction()

    data class SetSearch(val text: String) : MedicationTrackerAction()

    data class MarkTaken(val id: UUID) : MedicationTrackerAction()

    data class IncrementStock(val id: UUID) : MedicationTrackerAction()

    data class DecrementStock(val id: UUID) : MedicationTrackerAction()

    data object OpenAdd : MedicationTrackerAction()

    data class OpenEdit(val id: UUID) : MedicationTrackerAction()

    data object CloseForm : MedicationTrackerAction()

    /** Internal: the dashboard's "View all" jump lands on History (D12). */
    data object ShowHistory : MedicationTrackerAction()

    companion object {
        fun decode(name: String, args: ActionArguments): MedicationTrackerAction? = when (name) {
            "setSegment" -> SetSegment(
                MedicationSegment.fromRaw(
                    args.enumeration("segment", "MedicationSegment", MedicationSegment.entries.map { it.rawValue })
                ) ?: throw IllegalStateException("unreachable")
            )

            "setSearch" -> SetSearch(args.string("text"))
            "markTaken" -> MarkTaken(args.uuid("medicationId"))
            "incrementStock" -> IncrementStock(args.uuid("medicationId"))
            "decrementStock" -> DecrementStock(args.uuid("medicationId"))
            "openAdd" -> OpenAdd
            "openEdit" -> OpenEdit(args.uuid("medicationId"))
            "closeForm" -> CloseForm
            else -> null
        }
    }
}

object MedicationTrackerMachine {

    fun reduce(
        state: MedicationTrackerState,
        action: MedicationTrackerAction,
        context: MachineContext
    ): Transition<MedicationTrackerState> = when (action) {
        is MedicationTrackerAction.SetSegment -> Transition(state.copy(segment = action.segment))

        is MedicationTrackerAction.SetSearch -> Transition(state.copy(searchQuery = action.text))

        is MedicationTrackerAction.ShowHistory -> Transition(state.copy(segment = MedicationSegment.HISTORY))

        is MedicationTrackerAction.MarkTaken -> {
            // D6. The guard lives in the REDUCER, so it covers the tracker row AND the
            // dashboard hero button. A second mark on the same day writes nothing.
            val index = context.data.medications.indexOfFirst { it.id == action.id }
            if (index < 0 || Selectors.isTaken(action.id, context.data, context.now, context.zone)) {
                Transition(state)
            } else {
                val medication = context.data.medications[index]
                val medications = context.data.medications.toMutableList()
                medications[index] = medication.copy(
                    stock = maxOf(0, medication.stock - 1),
                    // Q5. now + 86400s regardless of the schedule string.
                    nextDose = context.now.addingSeconds(86_400.0)
                )
                val log = Log(
                    id = context.ids.next(),
                    type = "Medication Taken",
                    description = "${medication.name} Dose Taken",
                    date = context.now,
                    source = "Manual Log",
                    medicationId = action.id
                )
                val data = context.data.copy(
                    medications = medications,
                    logs = listOf(log) + context.data.logs
                )
                Transition(
                    state,
                    listOf(
                        Effect.Dispatch(
                            Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.MARK_TAKEN))
                        )
                    )
                )
            }
        }

        is MedicationTrackerAction.IncrementStock -> {
            val index = context.data.medications.indexOfFirst { it.id == action.id }
            if (index < 0) {
                Transition(state)
            } else {
                val medications = context.data.medications.toMutableList()
                // No ceiling.
                medications[index] = medications[index].copy(stock = medications[index].stock + 1)
                val data = context.data.copy(medications = medications)
                Transition(
                    state,
                    listOf(Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.UPDATE))))
                )
            }
        }

        is MedicationTrackerAction.DecrementStock -> {
            val index = context.data.medications.indexOfFirst { it.id == action.id }
            if (index < 0 || context.data.medications[index].stock <= 0) {
                Transition(state)
            } else {
                val medications = context.data.medications.toMutableList()
                medications[index] = medications[index].copy(stock = medications[index].stock - 1)
                val data = context.data.copy(medications = medications)
                Transition(
                    state,
                    listOf(Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.UPDATE))))
                )
            }
        }

        is MedicationTrackerAction.OpenAdd -> Transition(
            state.copy(formMode = FormMode.ADD, editingId = null),
            listOf(
                Effect.Dispatch(
                    Action.ToAddMedication(AddMedicationAction.SetMode(EditMode.ADD, null))
                )
            )
        )

        is MedicationTrackerAction.OpenEdit -> Transition(
            state.copy(formMode = FormMode.EDIT, editingId = action.id),
            listOf(
                Effect.Dispatch(
                    Action.ToAddMedication(AddMedicationAction.SetMode(EditMode.EDIT, action.id))
                )
            )
        )

        is MedicationTrackerAction.CloseForm ->
            Transition(state.copy(formMode = FormMode.CLOSED, editingId = null))
    }

    fun emit(state: MedicationTrackerState, context: MachineContext): ReflexValue {
        val data = context.data
        val all = data.medications
        // No active filter, no sort, no limit. Search is name OR instructions, not trimmed.
        val filtered = all.filter {
            Selectors.matches(it.name, state.searchQuery) ||
                Selectors.matches(it.instructions, state.searchQuery)
        }

        val groups = linkedMapOf(
            MedicationSchedule.MORNING.rawValue to mutableListOf<UUID>(),
            MedicationSchedule.AFTERNOON.rawValue to mutableListOf<UUID>(),
            MedicationSchedule.EVENING.rawValue to mutableListOf<UUID>()
        )
        for (medication in filtered) {
            // Q3. Case-sensitive string equality. Any other schedule is in NO group but is
            // still counted in the total.
            groups[medication.schedule]?.add(medication.id)
        }

        val taken = filtered.count {
            Selectors.isTaken(it.id, data, context.now, context.zone)
        }
        val total = filtered.size

        val isTakenMap = all.associate { medication ->
            ReflexValue.uuidString(medication.id) to ReflexValue.of(
                Selectors.isTaken(medication.id, data, context.now, context.zone)
            )
        }
        val isLowStockMap = all.associate { medication ->
            ReflexValue.uuidString(medication.id) to ReflexValue.of(
                Selectors.isLowStock(medication, data.notificationSettings)
            )
        }

        return ReflexValue.of(
            mapOf(
                "segment" to ReflexValue.Text(state.segment.rawValue),
                "searchQuery" to ReflexValue.Text(state.searchQuery),
                "formMode" to ReflexValue.Text(state.formMode.rawValue),
                "editingId" to ReflexValue.optionalUUID(state.editingId),
                "filteredMedications" to ReflexValue.uuids(filtered.map { it.id }),
                "todayGroups" to ReflexValue.of(groups.mapValues { ReflexValue.uuids(it.value) }),
                "takenTodayCount" to ReflexValue.of(taken.toLong()),
                "totalCount" to ReflexValue.of(total.toLong()),
                "takenFraction" to ReflexValue.of(if (total == 0) 0.0 else taken.toDouble() / total.toDouble()),
                "takenLabel" to ReflexValue.Text("$taken of $total taken today"),
                "allTaken" to ReflexValue.of(taken == total && total > 0),
                "isTaken" to ReflexValue.of(isTakenMap),
                "isLowStock" to ReflexValue.of(isLowStockMap),
                // Q4. History reads data.logs directly: storage order, no limit, no date filter,
                // and the search query is IGNORED.
                "historyLogIds" to ReflexValue.uuids(data.logs.filter { it.type == "Medication Taken" }.map { it.id })
            )
        )
    }
}
