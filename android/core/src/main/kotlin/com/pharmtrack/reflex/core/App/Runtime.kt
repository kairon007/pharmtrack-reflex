package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Screens.AddAppointmentMachine
import com.pharmtrack.reflex.core.Screens.AddMedicationMachine
import com.pharmtrack.reflex.core.Screens.AddVitalMachine
import com.pharmtrack.reflex.core.Screens.AppRootMachine
import com.pharmtrack.reflex.core.Screens.AppointmentsMachine
import com.pharmtrack.reflex.core.Screens.ConsultationFormMachine
import com.pharmtrack.reflex.core.Screens.ConsultationsMachine
import com.pharmtrack.reflex.core.Screens.DashboardMachine
import com.pharmtrack.reflex.core.Screens.MedicationPickerMachine
import com.pharmtrack.reflex.core.Screens.MedicationTrackerMachine
import com.pharmtrack.reflex.core.Screens.OnboardingMachine
import com.pharmtrack.reflex.core.Screens.SettingsMachine
import com.pharmtrack.reflex.core.Screens.VitalsDetailMachine
import com.pharmtrack.reflex.core.Screens.VitalsMachine
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalCodec

/**
 * The single place that knows how to route an Action or Message to its machine. Reducers
 * stay pure and total; this type only swaps one node's state into the AppState.
 */
object Reducer {

    fun reduce(state: AppState, action: Action, context: MachineContext): Transition<AppState> =
        when (action) {
            is Action.ToAppRoot -> {
                val result = AppRootMachine.reduce(state.appRoot, action.action, context)
                Transition(state.copy(appRoot = result.state), result.effects)
            }

            is Action.ToOnboarding -> {
                val result = OnboardingMachine.reduce(state.onboarding, action.action, context)
                Transition(state.copy(onboarding = result.state), result.effects)
            }

            is Action.ToDashboard -> {
                val result = DashboardMachine.reduce(state.dashboard, action.action, context)
                Transition(state.copy(dashboard = result.state), result.effects)
            }

            is Action.ToConsultations -> {
                val result = ConsultationsMachine.reduce(state.consultations, action.action, context)
                Transition(state.copy(consultations = result.state), result.effects)
            }

            is Action.ToMedicationTracker -> {
                val result = MedicationTrackerMachine.reduce(state.medicationTracker, action.action, context)
                Transition(state.copy(medicationTracker = result.state), result.effects)
            }

            is Action.ToAppointments -> {
                val result = AppointmentsMachine.reduce(state.appointments, action.action, context)
                Transition(state.copy(appointments = result.state), result.effects)
            }

            is Action.ToVitals -> {
                val result = VitalsMachine.reduce(state.vitals, action.action, context)
                Transition(state.copy(vitals = result.state), result.effects)
            }

            is Action.ToVitalsDetail -> {
                val result = VitalsDetailMachine.reduce(state.vitalsDetail, action.action, context)
                Transition(state.copy(vitalsDetail = result.state), result.effects)
            }

            is Action.ToAddVital -> {
                val result = AddVitalMachine.reduce(state.addVital, action.action, context)
                Transition(state.copy(addVital = result.state), result.effects)
            }

            is Action.ToAddMedication -> {
                val result = AddMedicationMachine.reduce(state.addMedication, action.action, context)
                Transition(state.copy(addMedication = result.state), result.effects)
            }

            is Action.ToAddAppointment -> {
                val result = AddAppointmentMachine.reduce(state.addAppointment, action.action, context)
                Transition(state.copy(addAppointment = result.state), result.effects)
            }

            is Action.ToConsultationForm -> {
                val result = ConsultationFormMachine.reduce(state.consultationForm, action.action, context)
                Transition(state.copy(consultationForm = result.state), result.effects)
            }

            is Action.ToMedicationPicker -> {
                val result = MedicationPickerMachine.reduce(state.medicationPicker, action.action, context)
                Transition(state.copy(medicationPicker = result.state), result.effects)
            }

            is Action.ToSettings -> {
                val result = SettingsMachine.reduce(state.settings, action.action, context)
                Transition(state.copy(settings = result.state), result.effects)
            }
        }

    fun reduce(state: AppState, message: Message, context: MachineContext): Transition<AppState> =
        when (message) {
            is Message.ToAppRoot -> {
                val result = AppRootMachine.reduce(state.appRoot, message.message, context)
                Transition(state.copy(appRoot = result.state), result.effects)
            }

            is Message.ToOnboarding -> {
                val result = OnboardingMachine.reduce(state.onboarding, message.message, context)
                Transition(state.copy(onboarding = result.state), result.effects)
            }

            is Message.ToConsultationForm -> {
                val result = ConsultationFormMachine.reduce(state.consultationForm, message.message, context)
                Transition(state.copy(consultationForm = result.state), result.effects)
            }

            is Message.ToVitalsDetail -> {
                val result = VitalsDetailMachine.reduce(state.vitalsDetail, message.message, context)
                Transition(state.copy(vitalsDetail = result.state), result.effects)
            }
        }
}

/**
 * Owns the AppState and the service graph. `send`/`deliver` are synchronous: they run the
 * reducer and every immediate dispatch/deliver chain, register timers, and QUEUE the service
 * effects. Nothing persists and no timer fires until the host flushes, which is what makes
 * the journey stream identical on both platforms (SCHEMA.md#effects-are-never-implicit).
 */
class RootSession(
    state: AppState,
    val zone: Instant.Zone,
    val services: Services
) {
    constructor(now: Instant, zone: Instant.Zone, services: Services) :
        this(AppState.initial(now, zone), zone, services)

    /** runner.yaml's fixed-point guard. Hitting it is exit 3, never a hang. */
    companion object {
        const val EFFECT_CAP = 1000
    }

    var state: AppState = state
        private set

    val pending = mutableListOf<Effect>()

    var budgetExceeded = false
        private set

    val context: MachineContext
        get() = MachineContext(state, services.clock.now(), zone, services.ids)

    fun send(action: Action) {
        absorb(Reducer.reduce(state, action, context))
    }

    fun deliver(message: Message) {
        absorb(Reducer.reduce(state, message, context))
    }

    /**
     * Completes queued reads, writes, clears and notification replacements in FIFO order,
     * to fixed point. Timers are NOT fired here.
     */
    suspend fun flushStore() {
        var processed = 0
        while (pending.isNotEmpty()) {
            val effect = pending.removeAt(0)
            processed += 1
            if (processed > EFFECT_CAP) {
                budgetExceeded = true
                return
            }
            perform(effect)
        }
    }

    fun resetBudget() {
        budgetExceeded = false
    }

    private fun absorb(result: Transition<AppState>) {
        state = result.state
        val queue = ArrayDeque(result.effects)
        var processed = 0
        while (queue.isNotEmpty()) {
            val effect = queue.removeFirst()
            when (effect) {
                is Effect.Dispatch -> {
                    processed += 1
                    if (processed > EFFECT_CAP) {
                        budgetExceeded = true
                        return
                    }
                    val result = Reducer.reduce(state, effect.action, context)
                    state = result.state
                    queue.addAll(result.effects)
                }

                is Effect.Deliver -> {
                    processed += 1
                    if (processed > EFFECT_CAP) {
                        budgetExceeded = true
                        return
                    }
                    val result = Reducer.reduce(state, effect.message, context)
                    state = result.state
                    queue.addAll(result.effects)
                }

                is Effect.After -> {
                    // Registering is not firing. The fixture scheduler fires on flush.
                    services.scheduler.after(effect.seconds, effect.key, effect.message.node, effect.message)
                }

                else -> pending.add(effect)
            }
        }
    }

    private suspend fun perform(effect: Effect) {
        when (effect) {
            is Effect.StoreLoad -> {
                try {
                    val loaded = services.store.load()
                    deliver(Message.ToAppRoot(com.pharmtrack.reflex.core.Screens.AppRootMessage.StoreLoaded(loaded)))
                } catch (error: Exception) {
                    deliver(
                        Message.ToAppRoot(
                            com.pharmtrack.reflex.core.Screens.AppRootMessage.StoreLoadFailed(token(error))
                        )
                    )
                }
            }

            is Effect.StoreSave -> {
                try {
                    services.store.save(effect.data)
                    deliver(Message.ToAppRoot(com.pharmtrack.reflex.core.Screens.AppRootMessage.StoreSaved))
                } catch (error: Exception) {
                    deliver(
                        Message.ToAppRoot(
                            com.pharmtrack.reflex.core.Screens.AppRootMessage.StoreSaveFailed(token(error))
                        )
                    )
                }
            }

            is Effect.StoreClear -> {
                try {
                    services.store.clear()
                    deliver(Message.ToAppRoot(com.pharmtrack.reflex.core.Screens.AppRootMessage.StoreCleared))
                } catch (error: Exception) {
                    // A failed clear must not write the flag; the tour stays open for a retry.
                    deliver(
                        Message.ToOnboarding(
                            com.pharmtrack.reflex.core.Screens.OnboardingMessage.ClearFailed(token(error))
                        )
                    )
                }
            }

            is Effect.KvRead -> {
                val value = services.keyValue.bool(effect.key) ?: false
                deliver(Message.ToAppRoot(com.pharmtrack.reflex.core.Screens.AppRootMessage.OnboardingFlagRead(value)))
            }

            is Effect.KvWrite -> services.keyValue.setBool(effect.key, effect.value)

            is Effect.ReplaceAll -> services.notifications.replaceAll(effect.notifications)

            else -> Unit
        }
    }

    /**
     * Service error text is never compared across platforms — Swift's DecodingError and
     * Kotlin's SerializationException can never match — so loadError carries a fixed token.
     */
    private fun token(error: Throwable): String = when (error) {
        is ServiceError.Injected -> error.name
        is ServiceError.IdsExhausted -> "idsExhausted"
        else -> MedicalCodec.DECODE_FAILURE_TOKEN
    }
}
