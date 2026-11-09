import Core
import Foundation

/// SCHEMA.md#operators. Comparison is over the canonical value tree, so the same rules hold on
/// both platforms without either runner knowing how the other represents a number.
public enum Operators {
    public static func evaluate(
        _ op: AssertionOperator,
        actual: ReflexValue,
        expected: ReflexValue
    ) -> Bool {
        switch op {
        case .equals:
            return equal(actual, expected)
        case .notEquals:
            return !equal(actual, expected)
        case .count:
            guard let count = actual.count, let wanted = expected.intValue else { return false }
            return count == wanted
        case .contains:
            guard let array = actual.arrayValue else { return false }
            return array.contains { equal($0, expected) }
        case .notContains:
            guard let array = actual.arrayValue else { return false }
            return !array.contains { equal($0, expected) }
        case .unorderedEquals:
            return unorderedEqual(actual, expected)
        case .isNull:
            return actual.isNull == (expected.boolValue ?? true)
        case .notNull:
            return (!actual.isNull) == (expected.boolValue ?? true)
        case .oneOf:
            guard let choices = expected.arrayValue else { return false }
            return choices.contains { equal($0, actual) }
        case .matches:
            return matches(actual, expected)
        case .gt:
            return compare(actual, expected) == .orderedDescending
        case .gte:
            guard let order = compare(actual, expected) else { return false }
            return order != .orderedAscending
        case .lt:
            return compare(actual, expected) == .orderedAscending
        case .lte:
            guard let order = compare(actual, expected) else { return false }
            return order != .orderedDescending
        case .closeTo:
            guard let target = expected.objectValue?["value"]?.doubleValue,
                  let tolerance = expected.objectValue?["tolerance"]?.doubleValue,
                  let value = actual.doubleValue else { return false }
            return abs(value - target) <= tolerance
        }
    }

    /// Deep equality with one refinement over `ReflexValue.==`: two instant strings compare as
    /// INSTANTS, so a fractional-second spelling in a fixture still equals the canonical
    /// seconds-precision form the runner emits (SCHEMA.md#operators).
    public static func equal(_ lhs: ReflexValue, _ rhs: ReflexValue) -> Bool {
        if let left = lhs.stringValue, let right = rhs.stringValue,
           let leftInstant = Instant.parse(left), let rightInstant = Instant.parse(right) {
            return leftInstant == rightInstant
        }
        if let left = lhs.arrayValue, let right = rhs.arrayValue {
            guard left.count == right.count else { return false }
            return zip(left, right).allSatisfy { equal($0, $1) }
        }
        if let left = lhs.objectValue, let right = rhs.objectValue {
            guard left.count == right.count else { return false }
            return left.allSatisfy { key, value in
                guard let other = right[key] else { return false }
                return equal(value, other)
            }
        }
        return lhs == rhs
    }

    /// Multiset equality. Notification emission order is not contract, so this is the only
    /// operator a journey may use on `notifications`.
    private static func unorderedEqual(_ lhs: ReflexValue, _ rhs: ReflexValue) -> Bool {
        guard let left = lhs.arrayValue, let right = rhs.arrayValue, left.count == right.count else {
            return false
        }
        var remaining = right
        for element in left {
            guard let index = remaining.firstIndex(where: { equal($0, element) }) else { return false }
            remaining.remove(at: index)
        }
        return remaining.isEmpty
    }

    private static func matches(_ actual: ReflexValue, _ expected: ReflexValue) -> Bool {
        guard let text = actual.stringValue, let pattern = expected.stringValue else { return false }
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: range) else { return false }
        // Anchored: the whole string must match, whether or not the pattern spells ^ and $.
        return match.range == range
    }

    private static func compare(_ lhs: ReflexValue, _ rhs: ReflexValue) -> ComparisonResult? {
        if let left = lhs.stringValue, let right = rhs.stringValue,
           let leftInstant = Instant.parse(left), let rightInstant = Instant.parse(right) {
            return order(leftInstant.secondsSince1970, rightInstant.secondsSince1970)
        }
        if let left = lhs.doubleValue, let right = rhs.doubleValue {
            return order(left, right)
        }
        return nil
    }

    private static func order(_ lhs: Double, _ rhs: Double) -> ComparisonResult {
        if lhs < rhs { return .orderedAscending }
        if lhs > rhs { return .orderedDescending }
        return .orderedSame
    }
}