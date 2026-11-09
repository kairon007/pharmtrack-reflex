import Foundation

/// The canonical value tree every machine emits and every assertion compares.
///
/// `int` and `double` stay distinct so serialization can honour runner.yaml#canonicalization
/// ("integers without a decimal point; doubles shortest round-trip form"), but `==` compares
/// them numerically so a YAML `120` matches a Swift `Double` 120.0.
public enum ReflexValue {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([ReflexValue])
    case object([String: ReflexValue])
}

extension ReflexValue: Equatable {
    public static func == (lhs: ReflexValue, rhs: ReflexValue) -> Bool {
        switch (lhs, rhs) {
        case (.null, .null): return true
        case let (.bool(a), .bool(b)): return a == b
        case let (.int(a), .int(b)): return a == b
        case let (.double(a), .double(b)): return a == b
        case let (.int(a), .double(b)): return Double(a) == b
        case let (.double(a), .int(b)): return a == Double(b)
        case let (.string(a), .string(b)): return a == b
        case let (.array(a), .array(b)): return a == b
        case let (.object(a), .object(b)):
            guard a.count == b.count else { return false }
            for (key, value) in a {
                guard let other = b[key], other == value else { return false }
            }
            return true
        default: return false
        }
    }
}

extension ReflexValue {
    public static func uuid(_ id: UUID) -> ReflexValue { .string(id.uuidString) }
    public static func uuids(_ ids: [UUID]) -> ReflexValue { .array(ids.map { .string($0.uuidString) }) }
    public static func instant(_ value: Instant) -> ReflexValue { .string(value.iso8601) }
    public static func optionalInstant(_ value: Instant?) -> ReflexValue {
        value.map { .string($0.iso8601) } ?? .null
    }
    public static func optionalUUID(_ value: UUID?) -> ReflexValue {
        value.map { .string($0.uuidString) } ?? .null
    }
    public static func optionalDouble(_ value: Double?) -> ReflexValue {
        value.map { .double($0) } ?? .null
    }
    public static func optionalString(_ value: String?) -> ReflexValue {
        value.map { .string($0) } ?? .null
    }

    public var isNull: Bool { if case .null = self { return true }; return false }

    public var stringValue: String? { if case let .string(s) = self { return s }; return nil }

    public var intValue: Int? {
        switch self {
        case let .int(i): return i
        case let .double(d) where d == d.rounded() && abs(d) < 9.2e18: return Int(d)
        default: return nil
        }
    }

    public var doubleValue: Double? {
        switch self {
        case let .int(i): return Double(i)
        case let .double(d): return d
        default: return nil
        }
    }

    public var boolValue: Bool? { if case let .bool(b) = self { return b }; return nil }

    public var arrayValue: [ReflexValue]? { if case let .array(a) = self { return a }; return nil }

    public var objectValue: [String: ReflexValue]? { if case let .object(o) = self { return o }; return nil }

    /// Element count for arrays and objects; nil for scalars.
    public var count: Int? {
        switch self {
        case let .array(a): return a.count
        case let .object(o): return o.count
        default: return nil
        }
    }

    /// Uppercases every UUID-shaped string, per runner.yaml#canonicalization.
    public func canonicalizingUUIDCase() -> ReflexValue {
        switch self {
        case let .string(s): return .string(ReflexValue.isUUIDShape(s) ? s.uppercased() : s)
        case let .array(a): return .array(a.map { $0.canonicalizingUUIDCase() })
        case let .object(o): return .object(o.mapValues { $0.canonicalizingUUIDCase() })
        default: return self
        }
    }

    public static func isUUIDShape(_ s: String) -> Bool {
        let parts = s.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 5 else { return false }
        let lengths = [8, 4, 4, 4, 12]
        for (part, length) in zip(parts, lengths) {
            guard part.count == length else { return false }
            for character in part where !character.isHexDigit { return false }
        }
        return true
    }

    /// Sorted keys, no whitespace, instants already ISO8601 UTC, UUIDs already uppercase.
    public var canonicalJSON: String {
        switch self {
        case .null: return "null"
        case let .bool(b): return b ? "true" : "false"
        case let .int(i): return String(i)
        case let .double(d): return String(d)
        case let .string(s): return ReflexValue.quote(s)
        case let .array(a): return "[" + a.map { $0.canonicalJSON }.joined(separator: ",") + "]"
        case let .object(o):
            let body = o.keys.sorted().map { key in
                ReflexValue.quote(key) + ":" + o[key]!.canonicalJSON
            }
            return "{" + body.joined(separator: ",") + "}"
        }
    }

    static func quote(_ s: String) -> String {
        var out = "\""
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            case "\u{08}": out += "\\b"
            case "\u{0C}": out += "\\f"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        return out + "\""
    }
}

extension ReflexValue: ExpressibleByStringLiteral {
    public init(stringLiteral value: String) { self = .string(value) }
}

extension ReflexValue: ExpressibleByIntegerLiteral {
    public init(integerLiteral value: Int) { self = .int(value) }
}

extension ReflexValue: ExpressibleByBooleanLiteral {
    public init(booleanLiteral value: Bool) { self = .bool(value) }
}

extension ReflexValue: ExpressibleByArrayLiteral {
    public init(arrayLiteral elements: ReflexValue...) { self = .array(elements) }
}

extension ReflexValue: ExpressibleByDictionaryLiteral {
    public init(dictionaryLiteral elements: (String, ReflexValue)...) {
        self = .object(Dictionary(elements, uniquingKeysWith: { _, last in last }))
    }
}