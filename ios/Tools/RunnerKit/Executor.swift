import Core
import Foundation

/// Runs one case against Core with the fixture service graph and emits the canonical stream.
/// A case owns its services outright — SCHEMA.md#given: "Cases never share state."
final class CaseRunner {
    private let specs: Specs
    private let journey: Journey
    private let journeyCase: JourneyCase
    private let out: NDJSON

    private let services: FixtureServices
    private let session: RootSession
    private let aliases: Aliases
    private let startedAt: Instant

    init(specs: Specs, journey: Journey, journeyCase: JourneyCase, out: NDJSON) throws {
        self.specs = specs
        self.journey = journey
        self.journeyCase = journeyCase
        self.out = out

        let given = journeyCase.given
        startedAt = given.now ?? specs.determinism.now

        var keyValues = specs.determinism.keyValues
        for (key, value) in given.keyValues { keyValues[key] = value }

        let slot = try CaseRunner.seedSlot(specs: specs, given: given)
        let mode: FixtureMedicalStore.Mode
        switch given.store {
        case "absent": mode = .absent
        case "unreadable": mode = .unreadable
        default: mode = .normal
        }

        services = FixtureServices(
            now: startedAt,
            idSequence: given.ids ?? specs.determinism.sequence,
            keyValues: keyValues,
            slot: slot,
            mode: mode
        )
        aliases = Aliases(specs: specs, services: services)
        session = RootSession(now: startedAt, zone: specs.determinism.zone, services: services.ports)
    }

    /// The slot is the fixture's RAW bytes with `data` and `settings` merged over them, so a
    /// fixture that is deliberately unparseable stays unparseable. Only when an override is
    /// present is the tree re-serialized, and `canonicalJSON` preserves explicit nulls, so
    /// `partial_settings` still fails to decode after a merge.
    private static func seedSlot(specs: Specs, given: Given) throws -> Data? {
        guard let name = given.fixture else { return nil }
        let bytes = try specs.fixtureBytes(name)
        guard !given.data.isEmpty || !given.settings.isEmpty else { return bytes }

        var root = try ReflexValue.parseJSON(bytes).objectValue ?? [:]
        // Top-level keys REPLACE wholesale (SCHEMA.md#given) — supplying `medications` swaps
        // the whole array rather than appending to it.
        for (key, value) in given.data { root[key] = value }
        if !given.settings.isEmpty {
            var settings = root["notificationSettings"]?.objectValue ?? [:]
            for (key, value) in given.settings { settings[key] = value }
            root["notificationSettings"] = .object(settings)
        }
        return ReflexValue.object(root).jsonData
    }

    func run() async throws -> Bool {
        out.emit(Record.caseStart(
            journey: journey.id,
            caseID: journeyCase.id,
            fixture: journeyCase.given.fixture,
            now: startedAt,
            decisions: journey.decisions
        ))

        if journeyCase.given.load {
            session.send(.appRoot(.onAppear))
            try await flush(.all)
        }
        // SCHEMA.md#the-counter-baseline: setup activity is never counted. The notification set
        // planned during setup stays visible even though replaceCount reads zero.
        services.resetCounters()
        try checkBudget()

        var failures = 0
        for (index, step) in journeyCase.steps.enumerated() {
            try await perform(step.verb, args: step.args)
            try checkBudget()

            let snapshot = makeSnapshot()
            out.emit(Record.step(index: index, verb: step.verb, snapshot: snapshot))

            let resolver = PathResolver(snapshot: snapshot, aliases: aliases)
            for expectation in step.expectations {
                let expected = try aliases.resolve(expectation.value).canonicalizingUUIDCase()
                let actual = try resolver.resolve(expectation.path)
                let passed = Operators.evaluate(expectation.op, actual: actual, expected: expected)
                if !passed { failures += 1 }
                out.emit(Record.assertion(
                    step: index,
                    path: expectation.path,
                    op: expectation.op,
                    expected: expected,
                    actual: actual,
                    passed: passed
                ))
            }
        }

        out.emit(Record.caseEnd(
            journey: journey.id,
            caseID: journeyCase.id,
            passed: failures == 0,
            failedAssertions: failures
        ))
        return failures == 0
    }

    // MARK: - Steps

    private func perform(_ verb: StepVerb, args: [String: ReflexValue]) async throws {
        switch verb {
        case let .send(node, name):
            try validate(node: node, name: name, isMessage: false)
            let resolved = try aliases.resolve(.object(args)).objectValue ?? [:]
            let action = try Action.decode(ActionArguments(node: node, action: name, raw: resolved))
            session.send(action)
        case let .deliver(node, name):
            try validate(node: node, name: name, isMessage: true)
            let resolved = try aliases.resolve(.object(args)).objectValue ?? [:]
            let message = try Message.decode(ActionArguments(node: node, action: name, raw: resolved))
            session.deliver(message)
        case let .flush(kind):
            try await flush(kind)
        case let .advance(seconds):
            // Moving the clock does not fire timers (SCHEMA.md#steps).
            services.clock.advance(seconds: seconds)
        case let .inject(service, failure):
            guard service == "store" else {
                throw RunnerError.exhausted("the \(service) fixture cannot inject failures")
            }
            services.store.armFailure(failure)
        case .note, .expectOnly:
            break
        }
    }

    /// nodes.yaml is the only source of truth for what a journey may address. An internal action
    /// is reachable from another reducer's effect list, never from a `send`.
    private func validate(node: NodeID, name: String, isMessage: Bool) throws {
        guard let schema = NodeRegistry.schema(node) else {
            throw RunnerError.usage("unknown node \(node.rawValue)")
        }
        let allowed = isMessage ? schema.messages : schema.actions
        guard allowed.contains(name) else {
            throw RunnerError.usage("unknown \(isMessage ? "message" : "action") \(node.rawValue).\(name)")
        }
    }

    private func flush(_ kind: FlushKind) async throws {
        switch kind {
        case .store:
            await session.flushStore()
        case .scheduler:
            try drainScheduler()
        case .all:
            // Fixed point: a store completion can register a timer, and a fired timer can queue
            // another store effect. Neither side is allowed to leave work behind.
            while true {
                await session.flushStore()
                try checkBudget()
                if services.scheduler.pendingCount == 0 { break }
                try drainScheduler()
                try checkBudget()
            }
        }
    }

    private func drainScheduler() throws {
        var fired = 0
        while services.scheduler.pendingCount > 0 {
            let messages = services.scheduler.drain()
            fired += messages.count
            if fired > RootSession.effectCap {
                throw RunnerError.exhausted("scheduler exceeded the \(RootSession.effectCap)-effect cap")
            }
            for message in messages { session.deliver(message) }
            try checkBudget()
        }
    }

    private func checkBudget() throws {
        if session.budgetExceeded {
            throw RunnerError.exhausted("reducer exceeded the \(RootSession.effectCap)-effect cap")
        }
        if services.ids.isExhausted {
            throw RunnerError.exhausted("the ID sequence is exhausted")
        }
    }

    // MARK: - Snapshot

    private func makeSnapshot() -> Snapshot {
        let context = session.context
        let state = NodeRegistry.emit(context)
        let data = session.state.appRoot.data.reflexValue

        let store = ReflexValue.object([
            "filePresent": .bool(services.store.filePresent),
            "writeCount": .int(services.store.writeCount),
            "saved": services.store.saved?.reflexValue ?? .null,
        ])

        let kv = ReflexValue.object(services.keyValue.values.mapValues { ReflexValue.bool($0) })

        // Emission order is not contract, so the set is sorted by its stable identifier before
        // it ever reaches the stream (runner.yaml#canonicalization).
        let scheduled = services.notifications.scheduled
            .sorted { $0.identifier < $1.identifier }
            .map(\.reflexValue)

        let snapshot = Snapshot(
            state: state,
            data: data,
            store: store,
            kv: kv,
            notifications: .array(scheduled),
            replaceCount: services.notifications.replaceCount,
            now: services.clock.now(),
            consumed: services.ids.consumed
        )

        guard !journey.unpinned.isEmpty else { return snapshot }
        return Snapshot(
            state: state.mapValues { PathResolver.prune($0, unpinned: journey.unpinned) },
            data: PathResolver.prune(data, unpinned: journey.unpinned),
            store: PathResolver.prune(store, unpinned: journey.unpinned),
            kv: PathResolver.prune(kv, unpinned: journey.unpinned),
            notifications: PathResolver.prune(.array(scheduled), unpinned: journey.unpinned),
            replaceCount: snapshot.replaceCount,
            now: snapshot.now,
            consumed: snapshot.consumed
        )
    }
}

/// Drives the suite: locate the specs, parse the journeys, run the cases, report the exit code.
public struct Executor {
    private let specs: Specs
    private let out: NDJSON

    public init(specs: Specs, out: NDJSON) {
        self.specs = specs
        self.out = out
    }

    /// Returns the process exit code. A usage or exhaustion error propagates instead, because
    /// those abort the whole run rather than failing one case.
    public func run(glob: String, caseFilter: String?) async throws -> Int32 {
        let files = try specs.journeyFiles(matching: glob)
        var allPassed = true
        var matchedCase = false

        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            let journey = try JourneyParser.parse(text, fileName: file.lastPathComponent)
            for journeyCase in journey.cases {
                if let caseFilter, journeyCase.id != caseFilter { continue }
                matchedCase = true
                let runner = try CaseRunner(
                    specs: specs, journey: journey, journeyCase: journeyCase, out: out
                )
                let passed = try await runner.run()
                allPassed = allPassed && passed
            }
        }

        if let caseFilter, !matchedCase {
            throw RunnerError.usage("no case '\(caseFilter)' matches \(glob)")
        }
        return allPassed ? 0 : 1
    }
}