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
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class AppointmentsState(
    val selectedDate: Instant,
    val weekOffset: Int,
    val formMode: FormMode,
    val editingId: UUID?
) {
    companion object {
        fun initial(selectedDate: Instant, zone: Instant.Zone) = AppointmentsState(
            selectedDate = selectedDate.startOfDay(zone),
            weekOffset = 0,
            formMode = FormMode.CLOSED,
            editingId = null
        )
    }
}

sealed class AppointmentsAction {
    data class SelectDay(val date: Instant) : AppointmentsAction()

    data object PreviousWeek : AppointmentsAction()

    data object NextWeek : AppointmentsAction()

    data object OpenAdd : AppointmentsAction()

    data class OpenEdit(val id: UUID) : AppointmentsAction()

    data class Delete(val id: UUID) : AppointmentsAction()

    data object CloseForm : AppointmentsAction()

    /** Internal: the root hands the appointments screen the date a jump selected. */
    data class SyncFromRoot(val date: Instant) : AppointmentsAction()

    companion object {
        fun decode(name: String, args: ActionArguments): AppointmentsAction? = when (name) {
            "selectDay" -> SelectDay(args.instant("date"))
            "previousWeek" -> PreviousWeek
            "nextWeek" -> NextWeek
            "openAdd" -> OpenAdd
            "openEdit" -> OpenEdit(args.uuid("appointmentId"))
            "delete" -> Delete(args.uuid("appointmentId"))
            "closeForm" -> CloseForm
            else -> null
        }
    }
}

object AppointmentsMachine {

    fun reduce(
        state: AppointmentsState,
        action: AppointmentsAction,
        context: MachineContext
    ): Transition<AppointmentsState> = when (action) {
        is AppointmentsAction.SelectDay ->
            Transition(state.copy(selectedDate = action.date.startOfDay(context.zone)))

        is AppointmentsAction.PreviousWeek -> Transition(state.copy(weekOffset = state.weekOffset - 1))

        is AppointmentsAction.NextWeek -> Transition(state.copy(weekOffset = state.weekOffset + 1))

        is AppointmentsAction.OpenAdd -> Transition(
            state.copy(formMode = FormMode.ADD, editingId = null),
            listOf(Effect.Dispatch(Action.ToAddAppointment(AddAppointmentAction.SetMode(EditMode.ADD, null))))
        )

        is AppointmentsAction.OpenEdit -> Transition(
            state.copy(formMode = FormMode.EDIT, editingId = action.id),
            listOf(Effect.Dispatch(Action.ToAddAppointment(AddAppointmentAction.SetMode(EditMode.EDIT, action.id))))
        )

        is AppointmentsAction.Delete -> {
            val index = context.data.appointments.indexOfFirst { it.id == action.id }
            if (index < 0) {
                Transition(state)
            } else {
                val data = context.data.copy(
                    appointments = context.data.appointments.toMutableList().also { it.removeAt(index) }
                )
                Transition(
                    state,
                    listOf(Effect.Dispatch(Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.DELETE))))
                )
            }
        }

        is AppointmentsAction.CloseForm ->
            Transition(state.copy(formMode = FormMode.CLOSED, editingId = null))

        is AppointmentsAction.SyncFromRoot -> {
            // plan_hard_problem 5. The root owns the date; paging the strip to the new day
            // uses the same rounding as vitals_detail so the selection stays on screen.
            val day = action.date.startOfDay(context.zone)
            Transition(
                state.copy(
                    selectedDate = day,
                    weekOffset = ValueFormat.weekOffset(
                        day.dayIndex(context.zone) - context.now.dayIndex(context.zone)
                    )
                )
            )
        }
    }

    fun emit(state: AppointmentsState, context: MachineContext): ReflexValue {
        val center = Selectors.stripCenter(context.now, state.weekOffset, context.zone)
        val todayIndex = context.now.dayIndex(context.zone)
        val selectedIndex = state.selectedDate.dayIndex(context.zone)
        val dayDifference = selectedIndex - todayIndex
        // Q2. Same-day predicate only — no date >= now filter, so a past day shows its visits.
        val visible = context.data.appointments.filter {
            it.date.dayIndex(context.zone) == selectedIndex
        }
        val headerKind = when {
            dayDifference == 0 -> "today"
            dayDifference > 0 -> "inNDays"
            else -> "nDaysAgo"
        }
        return ReflexValue.of(
            mapOf(
                "selectedDate" to ReflexValue.instant(state.selectedDate),
                "weekOffset" to ReflexValue.of(state.weekOffset.toLong()),
                "formMode" to ReflexValue.Text(state.formMode.rawValue),
                "editingId" to ReflexValue.optionalUUID(state.editingId),
                "stripCenter" to ReflexValue.instant(center),
                "stripDates" to ReflexValue.of(
                    Selectors.dayWindow(context.now, state.weekOffset, context.zone).map { ReflexValue.instant(it) }
                ),
                "visibleIds" to ReflexValue.uuids(visible.map { it.id }),
                "headerKind" to ReflexValue.Text(headerKind),
                "headerDayCount" to ReflexValue.of(Math.abs(dayDifference).toLong()),
                "emptyMessageDate" to ReflexValue.instant(state.selectedDate)
            )
        )
    }
}
