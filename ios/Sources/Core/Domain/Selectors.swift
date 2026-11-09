import Foundation

/// The shared pure helpers from nodes.yaml#selectors, written once here and again in Kotlin.
/// Nothing in this file reads a clock, a service or a platform type — `now` and `zone` are
/// always arguments.
public enum Selectors {
    /// D16. Every ordering in the app is (date ascending, id ascending). "id ascending" is
    /// lexicographic on the uppercase UUID string, which is also unsigned bit order.
    public static func sortedByDateAndId<T>(
        _ items: [T],
        date: KeyPath<T, Instant>,
        id: KeyPath<T, UUID>
    ) -> [T] {
        items.sorted { lhs, rhs in
            let lhsDate = lhs[keyPath: date]
            let rhsDate = rhs[keyPath: date]
            if lhsDate != rhsDate { return lhsDate < rhsDate }
            return lhs[keyPath: id].uuidString < rhs[keyPath: id].uuidString
        }
    }

    /// D6. A dose counts as taken when a "Medication Taken" log exists for today AND either
    /// carries this medicationId, or — for pre-1.0 logs with no id — mentions the medication's
    /// CURRENT name as a case-sensitive substring. The substring collision between "Vitamin D"
    /// and "Vitamin D3" is preserved behavior, not a bug to fix.
    public static func isTaken(
        medicationId: UUID,
        data: MedicalData,
        now: Instant,
        zone: Instant.Zone
    ) -> Bool {
        guard let medication = data.medication(medicationId) else { return false }
        let today = now.dayIndex(in: zone)
        return data.logs.contains { log in
            guard log.type == "Medication Taken", log.date.dayIndex(in: zone) == today else {
                return false
            }
            if let linked = log.medicationId { return linked == medicationId }
            return log.description.contains(medication.name)
        }
    }

    /// D8. One predicate for the Today-row badge, the Inventory tint and the planner, so the
    /// badge and the notification can no longer disagree. Inclusive <=.
    public static func isLowStock(medication: Medication, settings: NotificationSettings) -> Bool {
        medication.active && medication.stock <= settings.lowStockThreshold
    }

    /// D11. Seven cells centered on startOfDay(now) + weekOffset*7 days, no weekday anchor.
    /// Shared by appointments and vitals_detail so the two strips cannot drift apart.
    public static func dayWindow(now: Instant, weekOffset: Int, zone: Instant.Zone) -> [Instant] {
        let center = now.startOfDay(in: zone).addingDays(weekOffset * 7)
        return (-3...3).map { center.addingDays($0) }
    }

    public static func stripCenter(now: Instant, weekOffset: Int, zone: Instant.Zone) -> Instant {
        now.startOfDay(in: zone).addingDays(weekOffset * 7)
    }

    /// Case-insensitive substring, NOT trimmed. An empty query matches everything.
    public static func matches(_ text: String, query: String) -> Bool {
        query.isEmpty || text.lowercased().contains(query.lowercased())
    }

    /// Q17 / VitalsView.swift:51-72. Compares the LAST TWO of an ascending series.
    /// nil when fewer than two readings, nil when the previous value is zero (guarded
    /// division, never infinity). Blood pressure compares systolic only — `value` IS
    /// systolic, so no special case is needed. Sentiment is down=good for EVERY metric.
    public static func trend(_ ascendingSeries: [Vital]) -> Trend? {
        guard ascendingSeries.count >= 2 else { return nil }
        let previous = ascendingSeries[ascendingSeries.count - 2].value
        let current = ascendingSeries[ascendingSeries.count - 1].value
        guard previous != 0 else { return nil }

        let difference = current - previous
        let percentage = difference / previous * 100
        let direction: Trend.Direction = difference < 0 ? .down : (difference > 0 ? .up : .flat)
        return Trend(
            direction: direction,
            sentiment: direction.sentiment,
            magnitude: ValueFormat.fixed(abs(percentage), decimals: 1) + "%"
        )
    }
}

public struct Trend: Equatable {
    public enum Direction: String {
        case up, down, flat

        /// Q17. Down is good for every metric, including blood pressure and weight.
        public var sentiment: Sentiment {
            switch self {
            case .down: return .good
            case .up: return .bad
            case .flat: return .neutral
            }
        }
    }

    public enum Sentiment: String { case good, bad, neutral }

    public let direction: Direction
    public let sentiment: Sentiment
    public let magnitude: String

    public var reflexValue: ReflexValue {
        .object([
            "direction": .string(direction.rawValue),
            "sentiment": .string(sentiment.rawValue),
            "magnitude": .string(magnitude),
        ])
    }
}