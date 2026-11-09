package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Screens.AppRootMessage
import com.pharmtrack.reflex.core.Screens.ConsultationFormMessage
import com.pharmtrack.reflex.core.Screens.OnboardingMessage
import com.pharmtrack.reflex.core.Screens.VitalsDetailMessage

/** An asynchronous result. Only four nodes receive messages; the other ten are action-only. */
sealed class Message {

    data class ToAppRoot(val message: AppRootMessage) : Message()

    data class ToOnboarding(val message: OnboardingMessage) : Message()

    data class ToConsultationForm(val message: ConsultationFormMessage) : Message()

    data class ToVitalsDetail(val message: VitalsDetailMessage) : Message()

    val node: NodeID
        get() = when (this) {
            is ToAppRoot -> NodeID.APP_ROOT
            is ToOnboarding -> NodeID.ONBOARDING
            is ToConsultationForm -> NodeID.CONSULTATION_FORM
            is ToVitalsDetail -> NodeID.VITALS_DETAIL
        }

    companion object {
        fun decode(args: ActionArguments): Message {
            val name = args.action
            return when (args.node) {
                NodeID.APP_ROOT -> ToAppRoot(
                    AppRootMessage.decode(name, args) ?: throw ActionError.unknownAction(args.node, name)
                )

                NodeID.ONBOARDING -> ToOnboarding(
                    OnboardingMessage.decode(name, args) ?: throw ActionError.unknownAction(args.node, name)
                )

                NodeID.CONSULTATION_FORM -> ToConsultationForm(
                    ConsultationFormMessage.decode(name, args) ?: throw ActionError.unknownAction(args.node, name)
                )

                NodeID.VITALS_DETAIL -> ToVitalsDetail(
                    VitalsDetailMessage.decode(name, args) ?: throw ActionError.unknownAction(args.node, name)
                )

                else -> throw ActionError.unknownAction(args.node, name)
            }
        }
    }
}
