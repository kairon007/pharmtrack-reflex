import Foundation

public enum ActionError: Error, CustomStringConvertible {
    case unknownAction(node: NodeID, name: String)
    case missingArgument(node: NodeID, action: String, key: String)
    case invalidArgument(node: NodeID, action: String, key: String, detail: String)

    public var description: String {
        switch self {
        case let .unknownAction(node, name):
            return "unknown action \(node.rawValue).\(name)"
        case let .missingArgument(node, action, key):
            return "missing argument '\(key)' for \(node.rawValue).\(action)"
        case let .invalidArgument(node, action, key, detail):
            return "invalid argument '\(key)' for \(node.rawValue).\(action): \(detail)"
        }
    }
}

/// Typed access over the YAML `args` map. A missing or wrongly-typed argument is a malformed
/// journey (exit 2), never a silently defaulted value — the specs are the only source of truth
/// for what an action accepts.
public struct ActionArguments {
    public let node: NodeID
    public let action: String
    public let raw: [String: ReflexValue]

    public init(node: NodeID, action: String, raw: [String: ReflexValue]) {
        self.node = node
        self.action = action
        self.raw = raw
    }

    private func require(_ key: String) throws -> ReflexValue {
        guard let value = raw[key], !value.isNull else {
            throw ActionError.missingArgument(node: node, action: action, key: key)
        }
        return value
    }

    private func invalid(_ key: String, _ detail: String) -> ActionError {
        .invalidArgument(node: node, action: action, key: key, detail: detail)
    }

    public func string(_ key: String) throws -> String {
        let value = try require(key)
        guard let text = value.stringValue else { throw invalid(key, "expected a string") }
        return text
    }

    public func optionalString(_ key: String) throws -> String? {
        guard let value = raw[key], !value.isNull else { return nil }
        guard let text = value.stringValue else { throw invalid(key, "expected a string or null") }
        return text
    }

    public func int(_ key: String) throws -> Int {
        let value = try require(key)
        guard let number = value.intValue else { throw invalid(key, "expected an integer") }
        return number
    }

    public func bool(_ key: String) throws -> Bool {
        let value = try require(key)
        guard let flag = value.boolValue else { throw invalid(key, "expected a boolean") }
        return flag
    }

    public func uuid(_ key: String) throws -> UUID {
        let value = try require(key)
        guard let text = value.stringValue, let id = UUID(uuidString: text) else {
            throw invalid(key, "expected a UUID string")
        }
        return id
    }

    public func optionalUUID(_ key: String) throws -> UUID? {
        guard let value = raw[key], !value.isNull else { return nil }
        guard let text = value.stringValue, let id = UUID(uuidString: text) else {
            throw invalid(key, "expected a UUID string or null")
        }
        return id
    }

    public func instant(_ key: String) throws -> Instant {
        let value = try require(key)
        guard let text = value.stringValue, let instant = Instant.parse(text) else {
            throw invalid(key, "expected an ISO8601 instant")
        }
        return instant
    }

    public func uuidArray(_ key: String) throws -> [UUID] {
        let value = try require(key)
        guard let elements = value.arrayValue else { throw invalid(key, "expected an array of UUIDs") }
        return try elements.enumerated().map { index, element in
            guard let text = element.stringValue, let id = UUID(uuidString: text) else {
                throw invalid(key, "element \(index) is not a UUID string")
            }
            return id
        }
    }

    public func enumeration<T: RawRepresentable>(_ key: String, _ type: T.Type) throws -> T
    where T.RawValue == String {
        let text = try string(key)
        guard let value = T(rawValue: text) else {
            throw invalid(key, "'\(text)' is not a valid \(type)")
        }
        return value
    }
}