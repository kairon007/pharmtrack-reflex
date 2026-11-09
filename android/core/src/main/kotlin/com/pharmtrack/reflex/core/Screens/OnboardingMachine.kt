package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Support.ReflexValue

data class OnboardingState(
    val visible: Boolean,
    val step: Int,
    val clearing: Boolean
) {
    companion object {
        fun initial() = OnboardingState(visible = false, step = 0, clearing = false)
    }
}

sealed class OnboardingAction {
    data object Next : OnboardingAction()

    data object GetStarted : OnboardingAction()

    /** Internal: the root reports a tab change so the tour can hide and requeue its reveal. */
    data object TabChanged : OnboardingAction()

    companion object {
        fun decode(name: String, args: ActionArguments): OnboardingAction? = when (name) {
            "next" -> Next
            "getStarted" -> GetStarted
            "tabChanged" -> TabChanged
            else -> null
        }
    }
}

sealed class OnboardingMessage {
    data object RevealFired : OnboardingMessage()

    data object ClearCompleted : OnboardingMessage()

    data class ClearFailed(val error: String) : OnboardingMessage()

    companion object {
        fun decode(name: String, args: ActionArguments): OnboardingMessage? = when (name) {
            "revealFired" -> RevealFired
            "clearCompleted" -> ClearCompleted
            "clearFailed" -> ClearFailed(args.string("error"))
            else -> null
        }
    }
}

object OnboardingMachine {

    const val REVEAL_KEY = "onboarding.reveal"
    const val REVEAL_SECONDS = 3.5
    const val LAST_STEP = 4

    fun revealEffect(): Effect = Effect.After(
        seconds = REVEAL_SECONDS,
        key = REVEAL_KEY,
        message = Message.ToOnboarding(OnboardingMessage.RevealFired)
    )

    fun reduce(
        state: OnboardingState,
        action: OnboardingAction,
        context: MachineContext
    ): Transition<OnboardingState> = when (action) {
        is OnboardingAction.Next -> {
            // Q15. Five tab positions, two text variants. The last step's button is Get Started.
            val step = minOf(LAST_STEP, state.step + 1)
            Transition(
                state.copy(step = step),
                listOf(
                    com.pharmtrack.reflex.core.App.Action.ToAppRoot(
                        AppRootAction.SelectTab(step)
                    ).let { Effect.Dispatch(it) }
                )
            )
        }

        is OnboardingAction.GetStarted -> {
            if (state.clearing) {
                Transition(state)
            } else {
                Transition(state.copy(clearing = true), listOf(Effect.StoreClear))
            }
        }

        is OnboardingAction.TabChanged ->
            Transition(state.copy(visible = false), listOf(revealEffect()))
    }

    fun reduce(
        state: OnboardingState,
        message: OnboardingMessage,
        context: MachineContext
    ): Transition<OnboardingState> = when (message) {
        is OnboardingMessage.RevealFired -> {
            // A reveal queued before the tour was completed must not reopen it.
            if (context.state.appRoot.hasCompletedOnboarding) {
                Transition(state)
            } else {
                Transition(state.copy(visible = true))
            }
        }

        is OnboardingMessage.ClearCompleted ->
            Transition(state.copy(clearing = false, visible = false))

        is OnboardingMessage.ClearFailed ->
            // The tour stays open so the clear can be retried; the flag was never written.
            Transition(state.copy(clearing = false, visible = true))
    }

    fun emit(state: OnboardingState, context: MachineContext): ReflexValue {
        val last = state.step >= LAST_STEP
        return ReflexValue.of(
            mapOf(
                "visible" to ReflexValue.of(state.visible),
                "step" to ReflexValue.of(state.step.toLong()),
                "clearing" to ReflexValue.of(state.clearing),
                "promptText" to ReflexValue.Text(if (last) "Ready to use the app?" else "Ready for the next one?"),
                "buttonText" to ReflexValue.Text(if (last) "Get Started" else "Next"),
                "subtitle" to ReflexValue.Text("Swipe or tap below to explore")
            )
        )
    }
}
