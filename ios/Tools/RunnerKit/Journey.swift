import Core
import Foundation

public enum FlushKind: String {
    case all, scheduler, store
}

public enum StepVerb {
    case send(node: NodeID, action: String)
    case deliver(node: NodeID, message: String)
    case flush(FlushKind)
    case advance(seconds: Double)
    case inject(service: String, failure: String)
    case note(String)
    /// A steps entry carrying only `expect`. It changes nothing, and SCHEMA.md requires a
    /// record even for a no-op step, so it is labelled rather than skipped.
    case expectOnly

    /// runner.yaml#step.target. Fixed spellings so the two streams agree byte for byte.
    public var target: String {
        switch self {
        case let .send(node, action): return "\(node.rawValue).\(action)"
        case let .deliver(node, message): return "\(node.rawValue).\(message)"
        case let .flush(kind): return kind.rawValue
        case .advance: return "clock"
        case let .inject(service, failure): return "\(service).\(failure)"
        case .note, .expectOnly: return ""
        }
    }

    public var label: String {
        switch self {
        case .send: return "send"
        case .deliver: return "deliver"
        case .flush: return "flush"
        case .advance: return "advance"
        case .inject: return "inject"
        case .note: return "note"
        case .expectOnly: return "expect"
        }
    }
}

public enum AssertionOperator: String {
    case equals
    case notEquals = "not_equals"
    case count
    case contains
    case notContains = "not_contains"
    case unorderedEquals = "unordered_equals"
    case isNull = "is_null"
    case notNull = "not_null"
    case oneOf = "one_of"
    case matches
    case gt, gte, lt, lte
    case closeTo = "close_to"

    /// Operators whose right-hand side is a flag rather than a compared value.
    public var isPresence: Bool { self == .isNull || self == .notNull }
}

public struct Expectation {
    public let path: String
    public let op: AssertionOperator
    /// Raw YAML value. Aliases are resolved at assertion time, not at parse time, because
    /// `$1` and `$now` depend on how far the case has run.
    public let value: ReflexValue
}

public struct Given {
    public var fixture: String?
    public var store: String?
    public var keyValues: [String: Bool]
    public var now: Instant?
    public var ids: [UUID]?
    public var settings: [String: ReflexValue]
    public var data: [String: ReflexValue]
    public var load: Bool
}

public struct JourneyStep {
    public let verb: StepVerb
    public let args: [String: ReflexValue]
    public let expectations: [Expectation]
}

public struct JourneyCase {
    public let id: String
    public let divergence: String?
    public let given: Given
    public let steps: [JourneyStep]
}

public struct Journey {
    public let id: String
    public let decisions: [String]
    public let unpinned: [String]
    public let cases: [JourneyCase]
}

public enum JourneyParser {
    public static func parse(_ text: String, fileName: String) throws -> Journey {
        let root = try YAML.parse(text)
        guard let object = root.objectValue else {
            throw RunnerError.malformed("\(fileName): journey is not a mapping")
        }
        guard object["schema"]?.stringValue == "reflex/behavior/1" else {
            throw RunnerError.malformed("\(fileName): unsupported schema")
        }
        guard let id = object["id"]?.stringValue else {
            throw RunnerError.malformed("\(fileName): journey has no id")
        }
        let decisions = (object["decisions"]?.arrayValue ?? []).compactMap(\.stringValue)
        let unpinned = (object["unpinned"]?.arrayValue ?? []).compactMap(\.stringValue)
        let defaults = object["defaults"]?.objectValue ?? [:]

        // SCHEMA.md#divergence-markers: a case may only cite a decision the file declares.
        let declared = Set(decisions)
        guard let caseValues = object["cases"]?.arrayValue else {
            throw RunnerError.malformed("\(fileName): journey has no cases")
        }
        let cases = try caseValues.map { value -> JourneyCase in
            guard let caseObject = value.objectValue, let caseId = caseObject["id"]?.stringValue else {
                throw RunnerError.malformed("\(fileName): case has no id")
            }
            let divergence = caseObject["divergence"]?.stringValue
            if let divergence, !declared.contains(divergence) {
                throw RunnerError.malformed(
                    "\(fileName): case '\(caseId)' cites \(divergence), which is not in decisions"
                )
            }
            var merged = defaults
            for (key, value) in caseObject["given"]?.objectValue ?? [:] {
                merged[key] = value
            }
            let given = try parseGiven(merged, context: "\(fileName)#\(caseId)")
            let steps = try (caseObject["steps"]?.arrayValue ?? []).map {
                try parseStep($0, context: "\(fileName)#\(caseId)")
            }
            return JourneyCase(id: caseId, divergence: divergence, given: given, steps: steps)
        }
        return Journey(id: id, decisions: decisions, unpinned: unpinned, cases: cases)
    }

    static func parseGiven(_ object: [String: ReflexValue], context: String) throws -> Given {
        var given = Given(
            fixture: nil, store: nil, keyValues: [:], now: nil, ids: nil,
            settings: [:], data: [:], load: true
        )
        given.fixture = object["fixture"]?.stringValue
        given.store = object["store"]?.stringValue
        if given.fixture != nil && given.store != nil {
            throw RunnerError.malformed("\(context): fixture and store are mutually exclusive")
        }
        if let store = given.store, store != "absent" && store != "unreadable" {
            throw RunnerError.malformed("\(context): unknown store mode '\(store)'")
        }
        if let pairs = object["key_value"]?.objectValue {
            for (key, value) in pairs {
                guard let flag = value.boolValue else {
                    throw RunnerError.malformed("\(context): key_value.\(key) is not a boolean")
                }
                given.keyValues[key] = flag
            }
        }
        if let value = object["now"], !value.isNull {
            guard let text = value.stringValue, let instant = Instant.parse(text) else {
                throw RunnerError.malformed("\(context): now is not an ISO8601 instant")
            }
            given.now = instant
        }
        if let value = object["ids"], !value.isNull {
            given.ids = try value.arrayValue.orThrow("\(context): ids is not a sequence").map { element in
                guard let text = element.stringValue, let id = UUID(uuidString: text) else {
                    throw RunnerError.malformed("\(context): ids holds a non-UUID")
                }
                return id
            }
        }
        given.settings = object["settings"]?.objectValue ?? [:]
        given.data = object["data"]?.objectValue ?? [:]
        if let value = object["load"] {
            guard let flag = value.boolValue else {
                throw RunnerError.malformed("\(context): load is not a boolean")
            }
            given.load = flag
        }
        return given
    }

    static func parseStep(_ value: ReflexValue, context: String) throws -> JourneyStep {
        guard let object = value.objectValue else {
            throw RunnerError.malformed("\(context): step is not a mapping")
        }
        let args = object["args"]?.objectValue ?? [:]
        let expectations = try (object["expect"]?.arrayValue ?? []).map {
            try parseExpectation($0, context: context)
        }

        var verbs: [StepVerb] = []
        if let target = object["send"]?.stringValue {
            verbs.append(.send(node: try node(target, context: context), action: try actionName(target, context: context)))
        }
        if let target = object["deliver"]?.stringValue {
            verbs.append(.deliver(node: try node(target, context: context), message: try actionName(target, context: context)))
        }
        if let target = object["flush"]?.stringValue {
            guard let kind = FlushKind(rawValue: target) else {
                throw RunnerError.malformed("\(context): unknown flush target '\(target)'")
            }
            verbs.append(.flush(kind))
        }
        if let advance = object["advance"] {
            guard let seconds = advance.objectValue?["seconds"]?.doubleValue else {
                throw RunnerError.malformed("\(context): advance needs a numeric seconds")
            }
            verbs.append(.advance(seconds: seconds))
        }
        if let inject = object["inject"]?.objectValue {
            guard let service = inject["service"]?.stringValue,
                  let failure = inject["fail_next"]?.stringValue else {
                throw RunnerError.malformed("\(context): inject needs service and fail_next")
            }
            verbs.append(.inject(service: service, failure: failure))
        }
        if let note = object["note"]?.stringValue {
            verbs.append(.note(note))
        }
        guard verbs.count <= 1 else {
            throw RunnerError.malformed("\(context): step carries more than one verb")
        }
        return JourneyStep(verb: verbs.first ?? .expectOnly, args: args, expectations: expectations)
    }

    static func parseExpectation(_ value: ReflexValue, context: String) throws -> Expectation {
        guard let object = value.objectValue, let path = object["path"]?.stringValue else {
            throw RunnerError.malformed("\(context): expectation has no path")
        }
        let operators = object.keys.filter { $0 != "path" }.sorted()
        guard operators.count == 1, let op = AssertionOperator(rawValue: operators[0]) else {
            throw RunnerError.malformed(
                "\(context): expectation at '\(path)' needs exactly one known operator, found \(operators)"
            )
        }
        return Expectation(path: path, op: op, value: object[operators[0]] ?? .null)
    }

    private static func node(_ target: String, context: String) throws -> NodeID {
        let parts = target.split(separator: ".")
        guard parts.count == 2, let node = NodeID(rawValue: String(parts[0])) else {
            throw RunnerError.malformed("\(context): '\(target)' is not <node>.<name>")
        }
        return node
    }

    private static func actionName(_ target: String, context: String) throws -> String {
        let parts = target.split(separator: ".")
        guard parts.count == 2 else {
            throw RunnerError.malformed("\(context): '\(target)' is not <node>.<name>")
        }
        return String(parts[1])
    }
}

extension Optional {
    func orThrow(_ message: String) throws -> Wrapped {
        guard let self else { throw RunnerError.malformed(message) }
        return self
    }
}