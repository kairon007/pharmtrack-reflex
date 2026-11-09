import SwiftUI

// Dumb building blocks ported from SharedUI/Medical/Components.swift. Zero state,
// every interaction is a callback the screen wires to an Action.

public struct SectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?
    var isDark: Bool

    public init(title: String, actionTitle: String? = nil, action: (() -> Void)? = nil, isDark: Bool) {
        self.title = title
        self.actionTitle = actionTitle
        self.action = action
        self.isDark = isDark
    }

    public var body: some View {
        HStack {
            Text(title)
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundColor(Theme.heading(isDark))
            Spacer()
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.primary)
                }
                .frame(minHeight: 44)
            }
        }
    }
}

public struct CardSurface<Content: View>: View {
    let isDark: Bool
    let content: Content

    public init(isDark: Bool, @ViewBuilder content: () -> Content) {
        self.isDark = isDark
        self.content = content()
    }

    public var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(isDark))
            .cornerRadius(20)
            .overlay {
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Theme.border(isDark), lineWidth: 1)
            }
    }
}

public struct PrimaryButton: View {
    let title: String
    var icon: SemanticIcon?
    var enabled: Bool = true
    let action: () -> Void

    public init(title: String, icon: SemanticIcon? = nil, enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.enabled = enabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { AppIcon(icon, size: 20, tint: .white) }
                Text(title)
                    .font(.headline)
                    .fontWeight(.semibold)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(enabled ? Theme.primary : Theme.primary.opacity(0.4))
            .cornerRadius(16)
        }
        .disabled(!enabled)
    }
}

public struct EmptyStateCard: View {
    let icon: SemanticIcon
    let title: String
    let message: String
    let isDark: Bool

    public init(icon: SemanticIcon, title: String, message: String, isDark: Bool) {
        self.icon = icon
        self.title = title
        self.message = message
        self.isDark = isDark
    }

    public var body: some View {
        VStack(spacing: 12) {
            AppIcon(icon, size: 32, tint: isDark ? Theme.slate400 : Theme.slate300)
            VStack(spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Text(message)
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(32)
        .background(Theme.surface(isDark))
        .cornerRadius(20)
        .overlay {
            RoundedRectangle(cornerRadius: 20)
                .stroke(Theme.border(isDark), lineWidth: 1)
        }
    }
}

public struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let isDark: Bool
    let action: () -> Void

    public init(title: String, isSelected: Bool, isDark: Bool, action: @escaping () -> Void) {
        self.title = title
        self.isSelected = isSelected
        self.isDark = isDark
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .semibold : .regular)
                .foregroundColor(isSelected ? .white : Theme.body(isDark))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(isSelected ? Theme.primary : Theme.surface(isDark))
                .cornerRadius(20)
                .overlay {
                    Capsule()
                        .stroke(isSelected ? Color.clear : Theme.border(isDark), lineWidth: 1)
                }
        }
        .frame(minHeight: 44)
    }
}

public struct ProgressBar: View {
    let fraction: Double
    let isDark: Bool

    public init(fraction: Double, isDark: Bool) {
        self.fraction = fraction
        self.isDark = isDark
    }

    public var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(isDark ? Theme.borderDark : Theme.slate100)
                Capsule()
                    .fill(Theme.primary)
                    .frame(width: max(0, min(1, fraction)) * geo.size.width)
            }
        }
        .frame(height: 6)
    }
}

public struct IconCircleButton: View {
    let icon: SemanticIcon
    let tint: Color
    let label: String
    let action: () -> Void

    public init(icon: SemanticIcon, tint: Color, label: String, action: @escaping () -> Void) {
        self.icon = icon
        self.tint = tint
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            AppIcon(icon, size: 18, tint: tint)
                .frame(width: 44, height: 44)
                .background(tint.opacity(0.1))
                .clipShape(Circle())
        }
        .accessibilityLabel(label)
    }
}

/// The onboarding demo scroll (0.5s to bottom, 2.5s back to top) is presentation
/// choreography, not behavior — Core has no action for it (services.yaml#Scheduler).
struct OnboardingAutoScroll: ViewModifier {
    let active: Bool
    let proxy: ScrollViewProxy
    let topAnchor: String

    func body(content: Content) -> some View {
        content.task(id: active) {
            guard active else { return }
            do {
                try await Task.sleep(nanoseconds: 500_000_000)
                withAnimation(.easeInOut(duration: 0.8)) { proxy.scrollTo("bottom", anchor: .bottom) }
                try await Task.sleep(nanoseconds: 2_500_000_000)
                withAnimation(.easeInOut(duration: 0.8)) { proxy.scrollTo(topAnchor, anchor: .top) }
            } catch {}
        }
    }
}