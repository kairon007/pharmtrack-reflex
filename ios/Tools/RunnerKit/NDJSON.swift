import Core
import Foundation

/// runner.yaml#output. One canonical JSON object per line, in step order, on stdout.
/// Diagnostics go to stderr and are never compared.
public final class NDJSON {
    private let handle: FileHandle
    private let owned: Bool
    private let capture: Bool
    /// Populated only in memory mode, so the CLI never buffers the whole suite.
    public private(set) var lines: [String] = []

    private init(handle: FileHandle, owned: Bool, capture: Bool = false) {
        self.handle = handle
        self.owned = owned
        self.capture = capture
    }

    public static func standardOutput() -> NDJSON {
        NDJSON(handle: .standardOutput, owned: false)
    }

    /// For BehaviorTests: the same emitter, the same canonicalization, kept in memory.
    public static func inMemory() -> NDJSON {
        NDJSON(handle: .nullDevice, owned: false, capture: true)
    }

    public static func file(_ url: URL) throws -> NDJSON {
        guard FileManager.default.createFile(atPath: url.path, contents: nil) else {
            throw RunnerError.usage("cannot write to \(url.lastPathComponent)")
        }
        guard let handle = try? FileHandle(forWritingTo: url) else {
            throw RunnerError.usage("cannot open \(url.lastPathComponent) for writing")
        }
        return NDJSON(handle: handle, owned: true)
    }

    public func emit(_ record: ReflexValue) {
        // UUID case is normalized here, once, for the whole record — state, data, services and
        // assertion values alike — so no emitter has to remember (runner.yaml#canonicalization).
        let canonical = record.canonicalizingUUIDCase()
        let line = canonical.canonicalJSON
        if capture { lines.append(line) }
        handle.write(Data((line + "\n").utf8))
    }

    public func finish() {
        if owned { try? handle.close() }
    }
}

public enum Record {
    public static func caseStart(
        journey: String,
        caseID: String,
        fixture: String?,
        now: Instant,
        decisions: [String]
    ) -> ReflexValue {
        .object([
            "type": "case_start",
            "journey": .string(journey),
            "case": .string(caseID),
            "fixture": .optionalString(fixture),
            "now": .instant(now),
            "decisions": .array(decisions.map { .string($0) }),
        ])
    }

    public static func step(
        index: Int,
        verb: StepVerb,
        snapshot: Snapshot
    ) -> ReflexValue {
        .object([
            "type": "step",
            "index": .int(index),
            "verb": .string(verb.label),
            "target": .string(verb.target),
            "state": .object(snapshot.state),
            "data": snapshot.data,
            "services": .object([
                "kv": snapshot.kv,
                "store": snapshot.store,
                "notifications": .object([
                    "replaceCount": .int(snapshot.replaceCount),
                    "scheduled": snapshot.notifications,
                ]),
                "clock": .object(["now": .instant(snapshot.now)]),
                "ids": .object(["consumed": .int(snapshot.consumed)]),
            ]),
        ])
    }

    public static func assertion(
        step: Int,
        path: String,
        op: AssertionOperator,
        expected: ReflexValue,
        actual: ReflexValue,
        passed: Bool
    ) -> ReflexValue {
        .object([
            "type": "assertion",
            "step": .int(step),
            "path": .string(path),
            "operator": .string(op.rawValue),
            "expected": expected,
            "actual": actual,
            "passed": .bool(passed),
        ])
    }

    public static func caseEnd(
        journey: String,
        caseID: String,
        passed: Bool,
        failedAssertions: Int
    ) -> ReflexValue {
        .object([
            "type": "case_end",
            "journey": .string(journey),
            "case": .string(caseID),
            "passed": .bool(passed),
            "failedAssertions": .int(failedAssertions),
        ])
    }
}