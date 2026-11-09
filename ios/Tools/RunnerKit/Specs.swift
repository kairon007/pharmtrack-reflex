import Core
import Foundation

/// Locates reflex/specs and loads the frozen inputs: the determinism manifest, the fixture
/// index and the fixture JSON bytes. Nothing here is emitted into the stream, so absolute
/// paths are fine internally (runner.yaml#determinism forbids them in the OUTPUT only).
public struct Specs {
    public let root: URL
    public let determinism: Determinism
    public let fixtureIndex: [String: ReflexValue]

    public var behaviorDirectory: URL { root.appendingPathComponent("behavior") }
    public var fixturesDirectory: URL { root.appendingPathComponent("fixtures") }

    public static func locate() throws -> Specs {
        if let override = ProcessInfo.processInfo.environment["REFLEX_SPECS_DIR"] {
            return try load(URL(fileURLWithPath: override, isDirectory: true))
        }
        var directory = URL(fileURLWithPath: FileManager.default.currentDirectoryPath, isDirectory: true)
        for _ in 0..<8 {
            for candidate in [
                directory.appendingPathComponent("reflex/specs"),
                directory.appendingPathComponent("specs"),
            ] {
                if FileManager.default.fileExists(atPath: candidate.appendingPathComponent("behavior").path) {
                    return try load(candidate)
                }
            }
            directory = directory.deletingLastPathComponent()
        }
        throw RunnerError.usage("cannot locate reflex/specs — set REFLEX_SPECS_DIR or run from the repository")
    }

    private static func load(_ root: URL) throws -> Specs {
        let fixtures = root.appendingPathComponent("fixtures")
        let manifest = try YAML.parse(file: fixtures.appendingPathComponent("determinism.yaml"))
        let index = try YAML.parse(file: fixtures.appendingPathComponent("index.yaml"))
        return Specs(
            root: root,
            determinism: try Determinism(manifest),
            fixtureIndex: index.objectValue?["fixtures"]?.objectValue ?? [:]
        )
    }

    public func fixtureBytes(_ name: String) throws -> Data {
        guard let entry = fixtureIndex[name] else {
            throw RunnerError.usage("unknown fixture '\(name)'")
        }
        guard let file = entry.objectValue?["file"]?.stringValue else {
            throw RunnerError.usage("fixture '\(name)' has no file")
        }
        let url = fixturesDirectory.appendingPathComponent(file)
        guard let bytes = FileManager.default.contents(atPath: url.path) else {
            throw RunnerError.usage("fixture file '\(file)' is missing")
        }
        return bytes
    }

    /// The fixture as a PARSED value, used for `$fixture:` aliases. Decoding through the real
    /// codec and re-rendering is what makes an explicit null compare equal to an omitted key
    /// (runner.yaml#canonicalization). Fixtures that intentionally fail to decode fall back to
    /// their raw JSON so a path alias into them still resolves.
    public func fixtureValue(_ name: String) throws -> ReflexValue {
        let bytes = try fixtureBytes(name)
        if let decoded = try? MedicalCodec.decode(bytes) {
            return decoded.reflexValue
        }
        return try ReflexValue.parseJSON(bytes)
    }

    /// The behavior directory also holds registries that are not journeys (`decisions.yaml`
    /// declares `schema: reflex/decisions/1`). A glob of `*.yaml` must select the 13 journeys,
    /// not everything with a .yaml suffix, so the schema is the filter.
    public func journeyFiles(matching glob: String) throws -> [URL] {
        let directory = behaviorDirectory
        guard let entries = try? FileManager.default.contentsOfDirectory(atPath: directory.path) else {
            throw RunnerError.usage("cannot read the behavior directory")
        }
        let matched = try entries
            .filter { $0.hasSuffix(".yaml") && Glob.matches(pattern: glob, name: $0) }
            .sorted()
            .filter { name in
                let url = directory.appendingPathComponent(name)
                let root = try YAML.parse(file: url)
                return root.objectValue?["schema"]?.stringValue == "reflex/behavior/1"
            }
        guard !matched.isEmpty else {
            throw RunnerError.usage("no journey matches '\(glob)'")
        }
        return matched.map { directory.appendingPathComponent($0) }
    }
}

public struct Determinism {
    public let now: Instant
    public let zone: Instant.Zone
    public let reserved: [String: UUID]
    public let sequence: [UUID]
    public let keyValues: [String: Bool]

    init(_ manifest: ReflexValue) throws {
        let object = manifest.objectValue ?? [:]
        let clock = object["clock"]?.objectValue ?? [:]
        guard let text = clock["now"]?.stringValue, let now = Instant.parse(text) else {
            throw RunnerError.usage("determinism.yaml#clock.now is missing or unparseable")
        }
        self.now = now
        // The suite pins UTC. Accepting a zone name we cannot resolve would silently diverge
        // on day-boundary logic, so an unknown pin is a usage error rather than a fallback.
        let name = clock["timezone"]?.stringValue ?? "UTC"
        guard name == "UTC" else {
            throw RunnerError.usage("unsupported pinned timezone '\(name)' — only UTC is specified")
        }
        zone = .utc

        let ids = object["ids"]?.objectValue ?? [:]
        var reserved: [String: UUID] = [:]
        for (name, value) in ids["reserved"]?.objectValue ?? [:] {
            guard let text = value.stringValue, let id = UUID(uuidString: text) else {
                throw RunnerError.usage("determinism.yaml#ids.reserved.\(name) is not a UUID")
            }
            reserved[name] = id
        }
        self.reserved = reserved

        guard let sequenceValues = ids["sequence"]?.arrayValue else {
            throw RunnerError.usage("determinism.yaml#ids.sequence is missing")
        }
        sequence = try sequenceValues.map { value in
            guard let text = value.stringValue, let id = UUID(uuidString: text) else {
                throw RunnerError.usage("determinism.yaml#ids.sequence holds a non-UUID")
            }
            return id
        }

        var keyValues: [String: Bool] = [:]
        for (key, value) in object["key_value"]?.objectValue ?? [:] {
            if let flag = value.boolValue { keyValues[key] = flag }
        }
        self.keyValues = keyValues
    }
}

/// The contract's globs are filename patterns like "vital_*.yaml" — `*` and `?` only.
enum Glob {
    static func matches(pattern: String, name: String) -> Bool {
        let patternCharacters = Array(pattern)
        let nameCharacters = Array(name)
        var p = 0
        var n = 0
        var star = -1
        var mark = 0
        while n < nameCharacters.count {
            if p < patternCharacters.count && (patternCharacters[p] == "?" || patternCharacters[p] == nameCharacters[n]) {
                p += 1
                n += 1
            } else if p < patternCharacters.count && patternCharacters[p] == "*" {
                star = p
                mark = n
                p += 1
            } else if star >= 0 {
                p = star + 1
                mark += 1
                n = mark
            } else {
                return false
            }
        }
        while p < patternCharacters.count && patternCharacters[p] == "*" { p += 1 }
        return p == patternCharacters.count
    }
}

public enum RunnerError: Error, CustomStringConvertible {
    case usage(String)
    case malformed(String)
    case exhausted(String)

    public var description: String {
        switch self {
        case let .usage(text): return text
        case let .malformed(text): return text
        case let .exhausted(text): return text
        }
    }

    /// runner.yaml#exit_codes: 2 usage/malformed, 3 exhausted or unsupported fixture use.
    public var exitCode: Int32 {
        switch self {
        case .usage, .malformed: return 2
        case .exhausted: return 3
        }
    }
}