package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.ActionError
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.KeyValueKey
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.LoadPhase
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.DemoSeed
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.NotificationPlanner
import com.pharmtrack.reflex.core.Domain.reflexValue
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalCodec
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

data class AppRootState(
    val loadPhase: LoadPhase,
    val loadError: String?,
    val data: MedicalData,
    val selectedTab: Int,
    val selectedAppointmentDate: Instant,
    val hasCompletedOnboarding: Boolean,
    val settingsPresented: Boolean
) {
    val isOnboarding: Boolean get() = !hasCompletedOnboarding

    companion object {
        fun initial(now: Instant): AppRootState = AppRootState(
            loadPhase = LoadPhase.IDLE,
            loadError = null,
            data = MedicalData(),
            selectedTab = 0,
            selectedAppointmentDate = now,
            hasCompletedOnboarding = false,
            settingsPresented = false
        )
    }
}

sealed class AppRootAction {
    data object OnAppear : AppRootAction()

    data class SelectTab(val index: Int) : AppRootAction()

    data class JumpToAppointment(val id: UUID) : AppRootAction()

    data object JumpToMedicationHistory : AppRootAction()

    data object PresentSettings : AppRootAction()

    data object DismissSettings : AppRootAction()

    /** Internal: another reducer commits data through the root. Not reachable from a journey. */
    data class CommitData(val data: MedicalData, val reason: CommitReason) : AppRootAction()

    companion object {
        fun decode(name: String, args: ActionArguments): AppRootAction? = when (name) {
            "onAppear" -> OnAppear
            "selectTab" -> {
                val index = args.int("index").toInt()
                if (index !in 0..4) {
                    throw ActionError.invalidArgument(args.node, args.action, "index", "expected 0...4")
                }
                SelectTab(index)
            }

            "jumpToAppointment" -> JumpToAppointment(args.uuid("appointmentId"))
            "jumpToMedicationHistory" -> JumpToMedicationHistory
            "presentSettings" -> PresentSettings
            "dismissSettings" -> DismissSettings
            else -> null
        }
    }
}

sealed class AppRootMessage {
    data class StoreLoaded(val data: MedicalData?) : AppRootMessage()

    data class StoreLoadFailed(val error: String) : AppRootMessage()

    data object StoreSaved : AppRootMessage()

    data class StoreSaveFailed(val error: String) : AppRootMessage()

    data object StoreCleared : AppRootMessage()

    data class OnboardingFlagRead(val value: Boolean) : AppRootMessage()

    companion object {
        fun decode(name: String, args: ActionArguments): AppRootMessage? = when (name) {
            "storeSaved" -> StoreSaved
            "storeCleared" -> StoreCleared
            "onboardingFlagRead" -> OnboardingFlagRead(args.bool("value"))
            else -> null
        }
    }
}

object AppRootMachine {

    fun reduce(
        state: AppRootState,
        action: AppRootAction,
        context: MachineContext
    ): Transition<AppRootState> = when (action) {
        is AppRootAction.OnAppear -> {
            if (state.loadPhase != LoadPhase.IDLE) {
                Transition(state)
            } else {
                // FIFO: the flag must be known before the load result picks a branch.
                Transition(
                    state.copy(loadPhase = LoadPhase.LOADING),
                    listOf(
                        Effect.KvRead(KeyValueKey.HAS_COMPLETED_ONBOARDING),
                        Effect.StoreLoad
                    )
                )
            }
        }

        is AppRootAction.SelectTab -> {
            val (next, effects) = selectTab(action.index, state, context)
            Transition(next, effects)
        }

        is AppRootAction.JumpToAppointment -> {
            val appointment = state.data.appointment(action.id)
            if (appointment == null) {
                Transition(state)
            } else {
                val (next, effects) = selectTab(3, state, context)
                Transition(
                    next.copy(selectedAppointmentDate = appointment.date),
                    effects + Effect.Dispatch(
                        Action.ToAppointments(AppointmentsAction.SyncFromRoot(appointment.date))
                    )
                )
            }
        }

        is AppRootAction.JumpToMedicationHistory -> {
            // D12. Tab 2 AND the History segment, so a second jump cannot land on Inventory.
            val (next, effects) = selectTab(2, state, context)
            Transition(
                next,
                effects + Effect.Dispatch(
                    Action.ToMedicationTracker(MedicationTrackerAction.ShowHistory)
                )
            )
        }

        is AppRootAction.PresentSettings -> Transition(
            state.copy(settingsPresented = true),
            listOf(
                Effect.Dispatch(
                    Action.ToSettings(SettingsAction.SeedDraft(state.data.notificationSettings))
                )
            )
        )

        is AppRootAction.DismissSettings -> Transition(
            state.copy(settingsPresented = false),
            listOf(
                Effect.Dispatch(
                    Action.ToDashboard(DashboardAction.DismissSheet)
                )
            )
        )

        is AppRootAction.CommitData -> {
            // D1. Save first, then hand the planner's COMPLETE desired set to replaceAll.
            Transition(
                state.copy(data = action.data),
                listOf(
                    Effect.StoreSave(action.data),
                    Effect.ReplaceAll(NotificationPlanner.plan(action.data, context.now))
                )
            )
        }
    }

    fun reduce(
        state: AppRootState,
        message: AppRootMessage,
        context: MachineContext
    ): Transition<AppRootState> = when (message) {
        is AppRootMessage.OnboardingFlagRead ->
            Transition(state.copy(hasCompletedOnboarding = message.value))

        is AppRootMessage.StoreLoaded -> {
            val loaded = message.data
            if (loaded != null) {
                val next = state.copy(data = loaded, loadPhase = LoadPhase.LOADED, loadError = null)
                // A successful load reschedules but does NOT re-save the file.
                Transition(next, loadedEffects(next, context))
            } else if (state.isOnboarding) {
                seedDemo(state, context)
            } else {
                val next = state.copy(data = MedicalData(), loadPhase = LoadPhase.LOADED, loadError = null)
                Transition(next, loadedEffects(next, context))
            }
        }

        is AppRootMessage.StoreLoadFailed -> {
            if (state.isOnboarding) {
                // A parse error and an absent file are the same branch before onboarding.
                seedDemo(state, context)
            } else {
                // THE DATA-LOSS GUARD. Empty in memory, bad file untouched on disk, no demo seed.
                val next = state.copy(
                    data = MedicalData(),
                    loadPhase = LoadPhase.UNREADABLE,
                    loadError = MedicalCodec.DECODE_FAILURE_TOKEN
                )
                Transition(next, loadedEffects(next, context))
            }
        }

        is AppRootMessage.StoreSaved, is AppRootMessage.StoreSaveFailed ->
            // The replan already happened when the data was committed; saving replans nothing.
            Transition(state)

        is AppRootMessage.StoreCleared -> {
            val next = state.copy(data = MedicalData(), hasCompletedOnboarding = true)
            // flag_order: the clear completed, so the flag is written now — not after a sleep.
            // No save: the file is gone, and replanning the empty set cancels the demo reminders.
            Transition(
                next,
                listOf(
                    Effect.Deliver(
                        Message.ToOnboarding(OnboardingMessage.ClearCompleted)
                    ),
                    Effect.KvWrite(
                        KeyValueKey.HAS_COMPLETED_ONBOARDING,
                        true
                    ),
                    Effect.ReplaceAll(NotificationPlanner.plan(next.data, context.now))
                )
            )
        }
    }

    fun emit(state: AppRootState, context: MachineContext): ReflexValue = ReflexValue.of(
        mapOf(
            "loadPhase" to ReflexValue.Text(state.loadPhase.rawValue),
            "loadError" to ReflexValue.optionalString(state.loadError),
            "data" to state.data.reflexValue,
            "selectedTab" to ReflexValue.of(state.selectedTab.toLong()),
            "selectedAppointmentDate" to ReflexValue.instant(state.selectedAppointmentDate),
            "hasCompletedOnboarding" to ReflexValue.of(state.hasCompletedOnboarding),
            "settingsPresented" to ReflexValue.of(state.settingsPresented),
            "isOnboarding" to ReflexValue.of(state.isOnboarding)
        )
    )

    /**
     * MainTabView.swift:107-118 — every selection change hides the overlay and requeues the
     * reveal, including the tour's own step. A non-nil scheduler key replaces the pending one.
     */
    private fun selectTab(
        index: Int,
        state: AppRootState,
        context: MachineContext
    ): Pair<AppRootState, List<Effect>> {
        val next = state.copy(selectedTab = index)
        if (!next.isOnboarding) return next to emptyList()
        return next to listOf(
            Effect.Dispatch(
                Action.ToOnboarding(OnboardingAction.TabChanged)
            )
        )
    }

    private fun seedDemo(state: AppRootState, context: MachineContext): Transition<AppRootState> {
        // Reserved seed ids, zero draws from the generator sequence.
        val seed = DemoSeed.data(context.now, context.zone)
        val next = state.copy(data = seed, loadPhase = LoadPhase.LOADED, loadError = null)
        // The seed persists, which is why demo reminders are live before the tour ends.
        return Transition(next, listOf(Effect.StoreSave(seed)) + loadedEffects(next, context))
    }

    private fun loadedEffects(state: AppRootState, context: MachineContext): List<Effect> {
        val effects = mutableListOf<Effect>(
            Effect.ReplaceAll(NotificationPlanner.plan(state.data, context.now))
        )
        if (state.loadPhase == LoadPhase.LOADED && state.isOnboarding) {
            effects.add(OnboardingMachine.revealEffect())
        }
        return effects
    }
}
