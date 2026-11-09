import Core
import SwiftUI

// The v0.0.19 palette, ported verbatim (SharedUI/Theme.swift). No Skip branches.
public enum Theme {
    public static let primary = Color.hex("0F766E")
    public static let secondary = Color.hex("115E59")
    public static let backgroundLight = Color.hex("F5F8F7")
    public static let backgroundDark = Color.hex("101817")
    public static let surfaceLight = Color.hex("FFFFFF")
    public static let surfaceDark = Color.hex("1B2725")
    public static let borderLight = Color.hex("E2EAE8")
    public static let borderDark = Color.hex("2B3936")

    public static let slate50 = Color.hex("F4F8F7")
    public static let slate100 = Color.hex("EAF1EF")
    public static let slate200 = Color.hex("DCE6E3")
    public static let slate300 = Color.hex("C5D2CE")
    public static let slate400 = Color.hex("82938E")
    public static let slate500 = Color.hex("667873")
    public static let slate600 = Color.hex("4B5D58")
    public static let slate700 = Color.hex("374844")
    public static let slate800 = Color.hex("263633")
    public static let slate900 = Color.hex("1B2725")

    public static let amber500 = Color.hex("f59e0b")
    public static let emerald500 = Color.hex("10b981")
    public static let purple500 = Color.hex("a855f7")
    public static let rose500 = Color.hex("f43f5e")
    public static let blue500 = Color.hex("3b82f6")

    public static let success = emerald500
    public static let warning = amber500
    public static let error = rose500
    public static let info = blue500

    public static func background(_ isDark: Bool) -> Color { isDark ? backgroundDark : backgroundLight }
    public static func surface(_ isDark: Bool) -> Color { isDark ? surfaceDark : surfaceLight }
    public static func border(_ isDark: Bool) -> Color { isDark ? borderDark : borderLight }
    public static func heading(_ isDark: Bool) -> Color { isDark ? slate100 : slate900 }
    public static func body(_ isDark: Bool) -> Color { isDark ? slate300 : slate600 }
    public static func caption(_ isDark: Bool) -> Color { isDark ? slate400 : slate500 }
}

extension Color {
    static func hex(_ hex: String) -> Color {
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        return Color(
            red: Double((int >> 16) & 0xFF) / 255.0,
            green: Double((int >> 8) & 0xFF) / 255.0,
            blue: Double(int & 0xFF) / 255.0
        )
    }
}

/// The semantic icon contract (specs/contracts/icons.yaml). Exhaustive by construction:
/// there is no fallback symbol, so an unmapped name cannot silently render as a plus.
public enum SemanticIcon: String, CaseIterable {
    case tabHome = "tab.home"
    case tabConsults = "tab.consults"
    case tabMeds = "tab.meds"
    case tabCalendar = "tab.calendar"
    case tabVitals = "tab.vitals"
    case vitalWeight = "vital.weight"
    case vitalBloodPressure = "vital.bloodPressure"
    case vitalBloodSugar = "vital.bloodSugar"
    case trendDown = "trend.down"
    case trendUp = "trend.up"
    case trendFlat = "trend.flat"
    case actionAdd = "action.add"
    case actionAddCircle = "action.addCircle"
    case actionRemove = "action.remove"
    case actionDelete = "action.delete"
    case actionEdit = "action.edit"
    case actionConfirm = "action.confirm"
    case actionDone = "action.done"
    case actionSearch = "action.search"
    case actionSettings = "action.settings"
    case actionBack = "action.back"
    case actionForward = "action.forward"
    case contentTime = "content.time"
    case contentLocation = "content.location"
    case contentHistory = "content.history"
    case contentInfo = "content.info"
    case contentLogEdited = "content.logEdited"
    case onboardingSparkle = "onboarding.sparkle"

    public var symbol: String {
        switch self {
        case .tabHome: return "house.fill"
        case .tabConsults: return "cross.case.fill"
        case .tabMeds: return "pills.fill"
        case .tabCalendar: return "calendar"
        case .tabVitals: return "heart.text.square.fill"
        case .vitalWeight: return "scalemass"
        case .vitalBloodPressure: return "heart.fill"
        case .vitalBloodSugar: return "drop.fill"
        case .trendDown: return "arrow.down.right"
        case .trendUp: return "arrow.up.right"
        case .trendFlat: return "arrow.right"
        case .actionAdd: return "plus"
        case .actionAddCircle: return "plus.circle"
        case .actionRemove: return "minus.circle.fill"
        case .actionDelete: return "trash"
        case .actionEdit: return "pencil.circle"
        case .actionConfirm: return "checkmark"
        case .actionDone: return "checkmark.circle.fill"
        case .actionSearch: return "magnifyingglass"
        case .actionSettings: return "bell.fill"
        case .actionBack: return "chevron.left"
        case .actionForward: return "chevron.right"
        case .contentTime: return "clock"
        case .contentLocation: return "mappin.and.ellipse"
        case .contentHistory: return "chart.bar.fill"
        case .contentInfo: return "info.circle"
        case .contentLogEdited: return "pencil.circle.fill"
        case .onboardingSparkle: return "sparkles"
        }
    }

    /// D4. Resolves the semantic names VitalsMachine.emit puts in `rowIcon`.
    public init(vitalName: String) {
        switch vitalName {
        case "weight": self = .vitalWeight
        case "blood_pressure": self = .vitalBloodPressure
        case "blood_sugar": self = .vitalBloodSugar
        default: fatalError("icons.yaml contract violation: unknown vital icon '\(vitalName)'")
        }
    }

    public init(type: VitalType) {
        switch type {
        case .weight: self = .vitalWeight
        case .bloodPressure: self = .vitalBloodPressure
        case .bloodSugar: self = .vitalBloodSugar
        }
    }

    public init(trendDirection: String) {
        switch trendDirection {
        case "up": self = .trendUp
        case "down": self = .trendDown
        case "flat": self = .trendFlat
        default: fatalError("icons.yaml contract violation: unknown trend '\(trendDirection)'")
        }
    }
}

public struct AppIcon: View {
    let icon: SemanticIcon
    var size: CGFloat = 12
    var tint: Color?

    public init(_ icon: SemanticIcon, size: CGFloat = 12, tint: Color? = nil) {
        self.icon = icon
        self.size = size
        self.tint = tint
    }

    public var body: some View {
        Image(systemName: icon.symbol)
            .font(.system(size: size))
            .foregroundColor(tint)
    }
}

// D15: rendered date strings are NOT contract — locale rendering is verified in the device
// pass. State stays in Instants; these formatters are presentation only.
enum DateFormatting {
    static func date(_ instant: Instant) -> Date { Date(timeIntervalSince1970: instant.secondsSince1970) }
    static func instant(_ date: Date) -> Instant { Instant(secondsSince1970: date.timeIntervalSince1970) }

    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateFormat = format
        return formatter
    }

    static func time(_ instant: Instant) -> String { formatter("h:mm a").string(from: date(instant)) }
    static func weekday(_ instant: Instant) -> String { formatter("EEE").string(from: date(instant)) }
    static func dayNumber(_ instant: Instant) -> String { formatter("d").string(from: date(instant)) }
    static func monthYear(_ instant: Instant) -> String { formatter("MMMM yyyy").string(from: date(instant)) }
    static func fullDate(_ instant: Instant) -> String { formatter("EEEE, MMMM d").string(from: date(instant)) }
    static func mediumDate(_ instant: Instant) -> String { formatter("MMM d, yyyy").string(from: date(instant)) }
    static func logTimestamp(_ instant: Instant) -> String { formatter("MMM d, h:mm a").string(from: date(instant)) }
}

extension Instant {
    var asDate: Date { Date(timeIntervalSince1970: secondsSince1970) }
}

extension Date {
    var asInstant: Instant { Instant(secondsSince1970: timeIntervalSince1970) }
}