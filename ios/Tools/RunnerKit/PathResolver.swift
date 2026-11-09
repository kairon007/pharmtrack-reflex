import Core
import Foundation

/// One addressable piece of an assertion path. Aliases are resolved per token rather than by
/// string surgery, because `$fixture:notification_matrix.appointments[0].id` carries its own
/// dots and brackets INSIDE the enclosing `[id=...]`.
public enum PathToken: Equatable {
    case key(String)
    case index(Int)
    /// `[id=<expr>]` — select the array element whose `id` equals the expression.
    case select(String)
    /// `[<key>]` on a derived map such as `rowIcon`, `isTaken`, `initials`.
    case subscriptKey(String)
}

/// Everything an assertion path can address, frozen at one step. Built once per step so the
/// emitted record and the assertions evaluated against it can never disagree.
public struct Snapshot {
    public let state: [String: ReflexValue]
    public let data: ReflexValue
    public let store: ReflexValue
    public let kv: ReflexValue
    /// The planned set, already sorted by identifier (runner.yaml#canonicalization).
    public let notifications: ReflexValue
    public let replaceCount: Int
    public let now: Instant
    public let consumed: Int
}

public struct PathResolver {
    public let snapshot: Snapshot
    public let aliases: Aliases

    public init(snapshot: Snapshot, aliases: Aliases) {
        self.snapshot = snapshot
        self.aliases = aliases
    }

    /// An unresolvable path is `.null`, not an error: `is_null` and `not_null` are how a journey
    /// asserts absence, and a deleted entity's field must read as absent rather than abort the run.
    public func resolve(_ path: String) throws -> ReflexValue {
        var tokens = PathResolver.tokenize(path)
        guard case let .key(namespace)? = tokens.first else { return .null }
        tokens.removeFirst()
        tokens = try resolveAliases(in: tokens)

        switch namespace {
        case "state":
            return PathResolver.walk(.object(snapshot.state), tokens)
        case "data":
            return PathResolver.walk(snapshot.data, tokens)
        case "store":
            return PathResolver.walk(snapshot.store, tokens)
        case "kv":
            return PathResolver.walk(snapshot.kv, tokens)
        case "notifications":
            // The bare namespace is the planned SET; replaceCount is the one scalar beside it.
            if tokens == [.key("replaceCount")] { return .int(snapshot.replaceCount) }
            return PathResolver.walk(snapshot.notifications, tokens)
        case "clock":
            return PathResolver.walk(.object(["now": .instant(snapshot.now)]), tokens)
        case "ids":
            return PathResolver.walk(.object(["consumed": .int(snapshot.consumed)]), tokens)
        default:
            return .null
        }
    }

    private func resolveAliases(in tokens: [PathToken]) throws -> [PathToken] {
        try tokens.map { token in
            switch token {
            case let .key(text) where text.hasPrefix("$"):
                return .key(try aliases.pathComponent(text))
            case let .select(text) where text.hasPrefix("$"):
                return .select(try aliases.pathComponent(text))
            case let .subscriptKey(text) where text.hasPrefix("$"):
                return .subscriptKey(try aliases.pathComponent(text))
            default:
                return token
            }
        }
    }

    public static func tokenize(_ path: String) -> [PathToken] {
        var tokens: [PathToken] = []
        var current = ""
        var index = path.startIndex

        func flush() {
            if !current.isEmpty {
                tokens.append(.key(current))
                current = ""
            }
        }

        while index < path.endIndex {
            let character = path[index]
            if character == "." {
                flush()
                index = path.index(after: index)
            } else if character == "[" {
                flush()
                // Brackets nest: `[id=$fixture:notification_matrix.appointments[2].id]` carries
                // its own index, so the scan has to balance rather than stop at the first "]".
                var depth = 0
                var close = index
                var scan = index
                while scan < path.endIndex {
                    if path[scan] == "[" { depth += 1 }
                    if path[scan] == "]" {
                        depth -= 1
                        if depth == 0 { close = scan; break }
                    }
                    scan = path.index(after: scan)
                }
                guard depth == 0 else {
                    current += String(path[index...])
                    index = path.endIndex
                    continue
                }
                let inner = String(path[path.index(after: index)..<close])
                if inner.hasPrefix("id=") {
                    tokens.append(.select(String(inner.dropFirst(3))))
                } else if let position = Int(inner) {
                    tokens.append(.index(position))
                } else {
                    tokens.append(.subscriptKey(inner))
                }
                index = path.index(after: close)
            } else {
                current.append(character)
                index = path.index(after: index)
            }
        }
        flush()
        return tokens
    }

    public static func walk(_ value: ReflexValue, _ tokens: [PathToken]) -> ReflexValue {
        var current = value
        for token in tokens {
            switch token {
            case let .key(name):
                if let object = current.objectValue, let member = object[name] {
                    current = member
                } else if name == "count", let count = current.count {
                    current = .int(count)
                } else {
                    return .null
                }
            case let .index(position):
                guard let array = current.arrayValue, position >= 0, position < array.count else {
                    return .null
                }
                current = array[position]
            case let .subscriptKey(key):
                guard let object = current.objectValue, let member = object[key] else { return .null }
                current = member
            case let .select(identifier):
                guard let array = current.arrayValue else { return .null }
                guard let match = array.first(where: { $0.objectValue?["id"]?.stringValue == identifier }) else {
                    return .null
                }
                current = match
            }
        }
        return current
    }

    /// runner.yaml#comparison.exclude. Every `unpinned` entry in the frozen suite is prose
    /// ("rendered date strings (D15)"), and Core emits no rendered strings or pixel geometry,
    /// so those entries match nothing. An entry that IS a real path is honoured by removing it,
    /// which keeps the rule identical on both platforms if a future journey pins one.
    public static func prune(_ value: ReflexValue, unpinned: [String]) -> ReflexValue {
        var pruned = value
        for entry in unpinned {
            let tokens = tokenize(entry)
            guard case .key = tokens.first else { continue }
            pruned = remove(tokens, from: pruned)
        }
        return pruned
    }

    private static func remove(_ tokens: [PathToken], from value: ReflexValue) -> ReflexValue {
        guard let head = tokens.first else { return value }
        let rest = Array(tokens.dropFirst())
        switch head {
        case let .key(name):
            guard var object = value.objectValue, let member = object[name] else { return value }
            if rest.isEmpty {
                object.removeValue(forKey: name)
            } else {
                object[name] = remove(rest, from: member)
            }
            return .object(object)
        case let .subscriptKey(key):
            guard var object = value.objectValue, let member = object[key] else { return value }
            if rest.isEmpty {
                object.removeValue(forKey: key)
            } else {
                object[key] = remove(rest, from: member)
            }
            return .object(object)
        case let .index(position):
            guard var array = value.arrayValue, position >= 0, position < array.count else { return value }
            if rest.isEmpty {
                array.remove(at: position)
            } else {
                array[position] = remove(rest, from: array[position])
            }
            return .array(array)
        case .select:
            return value
        }
    }
}