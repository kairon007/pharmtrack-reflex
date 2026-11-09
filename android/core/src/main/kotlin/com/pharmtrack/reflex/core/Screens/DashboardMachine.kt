package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

enum class DashboardSheet(val rawValue: String) {
    NONE("none"), ADD_CONSULTATION("addConsultation"), ADD_MEDICATION("addMedication"),
    ADD_APPOINTMENT("addAppointment"), ADD_VITAL("addVital"), SETTINGS("settings");

    companion object {
        fun fromRaw(raw: String): DashboardSheet? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class QuickActionKind(val rawValue: String) {
    DOCTOR("doctor"), MEDS("meds"), VISIT("visit"), VITALS("vitals");

    val sheet: DashboardSheet
        get() = when (this) {
            DOCTOR -> DashboardSheet.ADD_CONSULTATION
            MEDS -> DashboardSheet.ADD_MEDICATION
            VISIT -> DashboardSheet.ADD_APPOINTMENT
            VITALS -> DashboardSheet.ADD_VITAL
        }

    companion object {
        fun fromRaw(raw: String): QuickActionKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class HeroBranch(val rawValue: String) {
    MEDS("meds"), APPOINTMENT("appointment"), EMPTY("empty")
}

data class DashboardState(val presentedSheet: DashboardSheet) {
    companion object {
        fun initial() = DashboardState(presentedSheet = DashboardSheet.NONE)
    }
}

sealed class DashboardAction {
    data object MarkNextTaken : DashboardAction()

    data class TapHeroAppointment(val id: UUID) : DashboardAction()

    data class TapUpcomingRow(val id: UUID) : DashboardAction()

    data object TapUpcomingHeader : DashboardAction()

    data object TapRecentActivityViewAll : DashboardAction()

    data class QuickAction(val kind: QuickActionKind) : DashboardAction()

    data class PresentSheet(val sheet: DashboardSheet) : DashboardAction()

    data object DismissSheet : DashboardAction()

    companion object {
        fun decode(name: String, args: ActionArguments): DashboardAction? = when (name) {
            "markNextTaken" -> MarkNextTaken
            "tapHeroAppointment" -> TapHeroAppointment(args.uuid("appointmentId"))
            "tapUpcomingRow" -> TapUpcomingRow(args.uuid("appointmentId"))
            "tapUpcomingHeader" -> TapUpcomingHeader
            "tapRecentActivityViewAll" -> TapRecentActivityViewAll
            "quickAction" -> QuickAction(
                QuickActionKind.fromRaw(args.enumeration("kind", "QuickActionKind", QuickActionKind.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "presentSheet" -> PresentSheet(
                DashboardSheet.fromRaw(args.enumeration("sheet", "DashboardSheet", DashboardSheet.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "dismissSheet" -> DismissSheet
            else -> null
        }
    }
}

object DashboardMachine {

    /**
     * Q1, D16. Sorted by (nextDose ?? distantFuture, id) with NO active filter and NO future
     * filter, so a dose three hours in the past still renders as "Next dose".
     */
    fun nextMedication(context: MachineContext): Medication? =
        context.data.medications.sortedWith(
            Comparator { lhs, rhs ->
                val left = lhs.nextDose ?: com.pharmtrack.reflex.core.Support.Instant.distantFuture
                val right = rhs.nextDose ?: com.pharmtrack.reflex.core.Support.Instant.distantFuture
                when {
                    left < right -> -1
                    left > right -> 1
                    else -> ReflexValue.uuidString(lhs.id).compareTo(ReflexValue.uuidString(rhs.id))
                }
            }
        ).firstOrNull()

    /** Q2. Inclusive >=, no notify check, ascending (date, id), capped at three. */
    fun upcomingAppointments(context: MachineContext): List<Appointment> =
        Selectors.sortedByDateAndId(
            context.data.appointments.filter { it.date >= context.now },
            date = { it.date },
            id = { it.id }
        ).take(3)

    fun reduce(
        state: DashboardState,
        action: DashboardAction,
        context: MachineContext
    ): Transition<DashboardState> = when (action) {
        is DashboardAction.MarkNextTaken -> {
            // D6. The guard lives in medication_tracker's reducer, which both entry points
            // reach, so a repeated hero tap cannot drain stock.
            val medication = nextMedication(context)
            if (medication == null) {
                Transition(state)
            } else {
                Transition(
                    state,
                    listOf(
                        Effect.Dispatch(
                            Action.ToMedicationTracker(MedicationTrackerAction.MarkTaken(medication.id))
                        )
                    )
                )
            }
        }

        is DashboardAction.TapHeroAppointment, is DashboardAction.TapUpcomingRow -> {
            // Both jump sites are fed from the upcoming list, so a past appointment is
            // unreachable and a direct action carrying one is a no-op.
            val id = when (action) {
                is DashboardAction.TapHeroAppointment -> action.id
                else -> (action as DashboardAction.TapUpcomingRow).id
            }
            if (!upcomingAppointments(context).any { it.id == id }) {
                Transition(state)
            } else {
                Transition(
                    state,
                    listOf(
                        Effect.Dispatch(
                            Action.ToAppRoot(AppRootAction.JumpToAppointment(id))
                        )
                    )
                )
            }
        }

        is DashboardAction.TapUpcomingHeader ->
            // DashboardView.swift:286 sets the tab only; the stored date keeps its value.
            Transition(
                state,
                listOf(
                    Effect.Dispatch(
                        Action.ToAppRoot(AppRootAction.SelectTab(3))
                    )
                )
            )

        is DashboardAction.TapRecentActivityViewAll ->
            // D12. One root action sets tab 2 AND the History segment.
            Transition(
                state,
                listOf(
                    Effect.Dispatch(
                        Action.ToAppRoot(AppRootAction.JumpToMedicationHistory)
                    )
                )
            )

        is DashboardAction.QuickAction -> Transition(state.copy(presentedSheet = action.kind.sheet))

        is DashboardAction.PresentSheet -> {
            if (action.sheet == DashboardSheet.SETTINGS) {
                Transition(
                    state.copy(presentedSheet = action.sheet),
                    listOf(
                        Effect.Dispatch(
                            Action.ToAppRoot(AppRootAction.PresentSettings)
                        )
                    )
                )
            } else {
                Transition(state.copy(presentedSheet = action.sheet))
            }
        }

        is DashboardAction.DismissSheet -> Transition(state.copy(presentedSheet = DashboardSheet.NONE))
    }

    fun emit(state: DashboardState, context: MachineContext): ReflexValue {
        val medications = context.data.medications
        val total = medications.size
        val taken = medications.count {
            Selectors.isTaken(it.id, context.data, context.now, context.zone)
        }
        val upcoming = upcomingAppointments(context)
        val next = nextMedication(context)
        val hero = when {
            medications.isNotEmpty() -> HeroBranch.MEDS
            upcoming.isNotEmpty() -> HeroBranch.APPOINTMENT
            else -> HeroBranch.EMPTY
        }
        return ReflexValue.of(
            mapOf(
                "presentedSheet" to ReflexValue.Text(state.presentedSheet.rawValue),
                "greeting" to ReflexValue.Text(ValueFormat.greeting(context.now.hour(context.zone))),
                "todaysMedsCount" to ReflexValue.of(total.toLong()),
                "takenTodayCount" to ReflexValue.of(taken.toLong()),
                "takenFraction" to ReflexValue.of(if (total == 0) 0.0 else taken.toDouble() / total.toDouble()),
                "takenLabel" to ReflexValue.Text("$taken of $total taken"),
                "nextMedicationId" to ReflexValue.optionalUUID(next?.id),
                "nextMedIsTaken" to ReflexValue.of(
                    next != null && Selectors.isTaken(next.id, context.data, context.now, context.zone)
                ),
                "upcomingAppointmentIds" to ReflexValue.uuids(upcoming.map { it.id }),
                "recentLogIds" to ReflexValue.uuids(context.data.logs.take(5).map { it.id }),
                "heroBranch" to ReflexValue.Text(hero.rawValue)
            )
        )
    }
}
