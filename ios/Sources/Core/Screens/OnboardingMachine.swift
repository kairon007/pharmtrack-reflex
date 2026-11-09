import Foundation

public struct OnboardingState: Equatable {
    public var visible: Bool
    public var step: Int
    public var clearing: Bool

    public init() {
        visible = false
        step = 0
        clearing = false
    }
}

public enum OnboardingAction {
    case next
    case getStarted
    case tabChanged

    public static func decode(_ name: String, _ args: ActionArguments) throws -> OnboardingAction? {
        switch name {
        case "next":
            return .next
        case "getStarted":
            return .getStarted
        case "tabChanged":
            return .tabChanged
        default:
            return nil
        }
    }
}

public enum OnboardingMessage {
    case revealFired
    case clearCompleted
    case clearFailed(String)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> OnboardingMessage? {
        switch name {
        case "revealFired":
            return .revealFired
        case "clearCompleted":
            return .clearCompleted
        case "clearFailed":
            return .clearFailed(try args.string("error"))
        default:
            return nil
        }
    }
}

public enum OnboardingMachine {
    public static let revealKey = "onboarding.reveal"
    public static let revealSeconds = 3.5
    public static let lastStep = 4

    public static func revealEffect() -> Effect {
        .after(seconds: revealSeconds, key: revealKey, message: .onboarding(.revealFired))
    }

    public static func reduce(
        _ state: OnboardingState,
        _ action: OnboardingAction,
        _ context: MachineContext
    ) -> (OnboardingState, [Effect]) {
        var state = state
        switch action {
        case .next:
        // Q15. Five tab positions, two text variants. The last step's button is Get Started.
            state.step = min(lastStep, state.step + 1)
            return (state, [.dispatch(.appRoot(.selectTab(state.step)))])

        case .getStarted:
            guard !state.clearing else { return (state, []) }
            state.clearing = true
            return (state, [.storeClear])

        case .tabChanged:
            state.visible = false
            return (state, [revealEffect()])
        }
    }

    public static func reduce(
        _ state: OnboardingState,
        _ message: OnboardingMessage,
        _ context: MachineContext
    ) -> (OnboardingState, [Effect]) {
        var state = state
        switch message {
        case .revealFired:
        // A reveal queued before the tour was completed must not reopen it.
            if context.state.appRoot.hasCompletedOnboarding { return (state, []) }
            state.visible = true
            return (state, [])

        case .clearCompleted:
            state.clearing = false
            state.visible = false
            return (state, [])

        case .clearFailed:
        // The tour stays open so the clear can be retried; the flag was never written.
            state.clearing = false
            state.visible = true
            return (state, [])
        }
    }

    public static func emit(_ state: OnboardingState, _ context: MachineContext) -> ReflexValue {
        let last = state.step >= lastStep
        var fields: [String: ReflexValue] = [:]
        fields["visible"] = .bool(state.visible)
        fields["step"] = .int(state.step)
        fields["clearing"] = .bool(state.clearing)
        fields["promptText"] = .string(last ? "Ready to use the app?" : "Ready for the next one?")
        fields["buttonText"] = .string(last ? "Get Started" : "Next")
        fields["subtitle"] = .string("Swipe or tap below to explore")
        return .object(fields)
    }
}