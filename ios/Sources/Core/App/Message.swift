import Foundation

/// An asynchronous result. Only four nodes receive messages; the other ten are action-only.
public enum Message {
    case appRoot(AppRootMessage)
    case onboarding(OnboardingMessage)
    case consultationForm(ConsultationFormMessage)
    case vitalsDetail(VitalsDetailMessage)

    public var node: NodeID {
        switch self {
        case .appRoot: return .appRoot
        case .onboarding: return .onboarding
        case .consultationForm: return .consultationForm
        case .vitalsDetail: return .vitalsDetail
        }
    }

    public static func decode(_ args: ActionArguments) throws -> Message {
        let name = args.action
        switch args.node {
        case .appRoot:
            guard let decoded = try AppRootMessage.decode(name, args) else {
                throw ActionError.unknownAction(node: args.node, name: name)
            }
            return .appRoot(decoded)
        case .onboarding:
            guard let decoded = try OnboardingMessage.decode(name, args) else {
                throw ActionError.unknownAction(node: args.node, name: name)
            }
            return .onboarding(decoded)
        case .consultationForm:
            guard let decoded = try ConsultationFormMessage.decode(name, args) else {
                throw ActionError.unknownAction(node: args.node, name: name)
            }
            return .consultationForm(decoded)
        case .vitalsDetail:
            guard let decoded = try VitalsDetailMessage.decode(name, args) else {
                throw ActionError.unknownAction(node: args.node, name: name)
            }
            return .vitalsDetail(decoded)
        default:
            throw ActionError.unknownAction(node: args.node, name: name)
        }
    }
}