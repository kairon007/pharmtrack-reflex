import Core
import Foundation

/// SCHEMA.md#value-aliases. Resolution is deferred to the moment of use because `$1` depends on
/// how many ids the generator has handed out so far and `$now` on how far the clock has moved.
/// Both runners resolve identically; nothing here is platform-specific.
public final class Aliases {
    private let specs: Specs
    private let services: FixtureServices
    private var fixtureCache: [String: ReflexValue] = [:]

    public init(specs: Specs, services: FixtureServices) {
        self.specs = specs
        self.services = services
    }

    /// Resolves every alias in a value tree. Used for assertion EXPECTED values and for step
    /// `args`, which the journeys write as `args: { vitalId: "$1" }`.
    public func resolve(_ value: ReflexValue) throws -> ReflexValue {
        switch value {
        case let .string(text) where text.hasPrefix("$"):
            return try self.value(text)
        case let .array(elements):
            return .array(try elements.map { try resolve($0) })
        case let .object(members):
            return .object(try members.mapValues { try resolve($0) })
        default:
            return value
        }
    }

    public func value(_ text: String) throws -> ReflexValue {
        if text == "$now" {
            return .instant(services.clock.now())
        }
        if text.hasPrefix("$fixture:") {
            return try fixtureReference(String(text.dropFirst("$fixture:".count)))
        }
        if text.hasPrefix("$"), let position = Int(text.dropFirst()) {
            let handedOut = services.ids.handedOut
            guard position >= 1, position <= handedOut.count else {
                throw RunnerError.usage("alias \(text) has not been handed out (consumed \(handedOut.count))")
            }
            return .uuid(handedOut[position - 1])
        }
        if text.hasPrefix("$") {
            let name = String(text.dropFirst())
            guard let id = specs.determinism.reserved[name] else {
                throw RunnerError.usage("unknown alias \(text)")
            }
            return .uuid(id)
        }
        throw RunnerError.usage("'\(text)' is not an alias")
    }

    /// An alias used as a path component must resolve to a string — it is addressing a map key
    /// or an entity id, never a whole object.
    public func pathComponent(_ text: String) throws -> String {
        let resolved = try value(text)
        guard let component = resolved.stringValue else {
            throw RunnerError.usage("alias \(text) does not resolve to a path component")
        }
        return component
    }

    /// `$fixture:<name>` is the whole file; `$fixture:<name>.<path>` is one field of it. The
    /// remainder is tokenized with the SAME grammar as an assertion path, so `vitals[3].id`
    /// works identically in both positions.
    private func fixtureReference(_ body: String) throws -> ReflexValue {
        let tokens = PathResolver.tokenize(body)
        guard case let .key(name)? = tokens.first else {
            throw RunnerError.usage("$fixture:\(body) does not name a fixture")
        }
        let root = try fixture(name)
        return PathResolver.walk(root, Array(tokens.dropFirst()))
    }

    private func fixture(_ name: String) throws -> ReflexValue {
        if let cached = fixtureCache[name] { return cached }
        let value = try specs.fixtureValue(name)
        fixtureCache[name] = value
        return value
    }
}