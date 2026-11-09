package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.CommitReason
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.NotificationSettings
import com.pharmtrack.reflex.core.Domain.ReminderTime
import com.pharmtrack.reflex.core.Domain.reflexValue
import com.pharmtrack.reflex.core.Support.ReflexValue

data class SettingsState(val draft: NotificationSettings) {
    companion object {
        fun initial() = SettingsState(draft = NotificationSettings.default())
    }
}

sealed class SettingsAction {
    data class SetAllEnabled(val value: Boolean) : SettingsAction()

    data class SetMedicationsEnabled(val value: Boolean) : SettingsAction()

    data class SetAppointmentsEnabled(val value: Boolean) : SettingsAction()

    data class SetConsultationsEnabled(val value: Boolean) : SettingsAction()

    data class SetLowStockEnabled(val value: Boolean) : SettingsAction()

    data class SetLowStockThreshold(val value: Long) : SettingsAction()

    data class SetMedicationReminderTime(val value: ReminderTime) : SettingsAction()

    data class SetAppointmentReminderTime(val value: ReminderTime) : SettingsAction()

    data object Save : SettingsAction()

    data object Cancel : SettingsAction()

    /** Internal: app_root.presentSettings reseeds the draft from the committed data. */
    data class SeedDraft(val settings: NotificationSettings) : SettingsAction()

    companion object {
        fun decode(name: String, args: ActionArguments): SettingsAction? = when (name) {
            "setAllEnabled" -> SetAllEnabled(args.bool("value"))
            "setMedicationsEnabled" -> SetMedicationsEnabled(args.bool("value"))
            "setAppointmentsEnabled" -> SetAppointmentsEnabled(args.bool("value"))
            "setConsultationsEnabled" -> SetConsultationsEnabled(args.bool("value"))
            "setLowStockEnabled" -> SetLowStockEnabled(args.bool("value"))
            "setLowStockThreshold" -> SetLowStockThreshold(args.int("value"))
            "setMedicationReminderTime" -> SetMedicationReminderTime(
                ReminderTime.fromRaw(
                    args.enumeration("value", "ReminderTime", ReminderTime.entries.map { it.rawValue })
                ) ?: throw IllegalStateException("unreachable")
            )

            "setAppointmentReminderTime" -> SetAppointmentReminderTime(
                ReminderTime.fromRaw(
                    args.enumeration("value", "ReminderTime", ReminderTime.entries.map { it.rawValue })
                ) ?: throw IllegalStateException("unreachable")
            )

            "save" -> Save
            "cancel" -> Cancel
            else -> null
        }
    }
}

object SettingsMachine {

    val THRESHOLD_OPTIONS = listOf(3L, 5L, 10L)

    fun reduce(
        state: SettingsState,
        action: SettingsAction,
        context: MachineContext
    ): Transition<SettingsState> = when (action) {
        is SettingsAction.SetAllEnabled ->
            Transition(state.copy(draft = state.draft.copy(allNotificationsEnabled = action.value)))

        is SettingsAction.SetMedicationsEnabled ->
            Transition(state.copy(draft = state.draft.copy(medicationsEnabled = action.value)))

        is SettingsAction.SetAppointmentsEnabled ->
            Transition(state.copy(draft = state.draft.copy(appointmentsEnabled = action.value)))

        is SettingsAction.SetConsultationsEnabled ->
            Transition(state.copy(draft = state.draft.copy(consultationsEnabled = action.value)))

        is SettingsAction.SetLowStockEnabled ->
            Transition(state.copy(draft = state.draft.copy(lowStockEnabled = action.value)))

        is SettingsAction.SetLowStockThreshold ->
            Transition(state.copy(draft = state.draft.copy(lowStockThreshold = action.value)))

        is SettingsAction.SetMedicationReminderTime ->
            Transition(state.copy(draft = state.draft.copy(medicationReminderTime = action.value)))

        is SettingsAction.SetAppointmentReminderTime ->
            Transition(state.copy(draft = state.draft.copy(appointmentReminderTime = action.value)))

        is SettingsAction.SeedDraft -> Transition(state.copy(draft = action.settings))

        is SettingsAction.Save -> {
            // Q14. No dirty check — saving unchanged settings still writes and reschedules.
            // Hiding a section never reset its values, so the whole draft commits verbatim.
            val data = context.data.copy(notificationSettings = state.draft)
            Transition(
                state,
                listOf(
                    Effect.Dispatch(
                        Action.ToAppRoot(AppRootAction.CommitData(data, CommitReason.SETTINGS))
                    ),
                    Effect.Dispatch(Action.ToAppRoot(AppRootAction.DismissSettings))
                )
            )
        }

        is SettingsAction.Cancel ->
            Transition(state, listOf(Effect.Dispatch(Action.ToAppRoot(AppRootAction.DismissSettings))))
    }

    fun emit(state: SettingsState, context: MachineContext): ReflexValue {
        val draft = state.draft
        return ReflexValue.of(
            mapOf(
                "draft" to draft.reflexValue,
                "sectionsVisible" to ReflexValue.of(draft.allNotificationsEnabled),
                "medicationTimeVisible" to ReflexValue.of(
                    draft.allNotificationsEnabled && draft.medicationsEnabled
                ),
                "thresholdVisible" to ReflexValue.of(draft.allNotificationsEnabled && draft.lowStockEnabled),
                "appointmentTimeVisible" to ReflexValue.of(
                    draft.allNotificationsEnabled && draft.appointmentsEnabled
                ),
                "thresholdOptions" to ReflexValue.of(THRESHOLD_OPTIONS.map { ReflexValue.of(it) }),
                "reminderOptions" to ReflexValue.of(
                    ReminderTime.entries.map { ReflexValue.Text(it.rawValue) }
                )
            )
        )
    }
}
