package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.Screens.AppRootAction
import com.pharmtrack.reflex.core.App.RootSession
import com.pharmtrack.reflex.core.Domain.reflexValue
import com.pharmtrack.reflex.core.Introspection.NodeRegistry
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.jsonData
import com.pharmtrack.reflex.core.Support.parseJSON
import java.nio.file.Files

/**
 * Runs one case against Core with the fixture service graph and emits the canonical stream.
 * A case owns its services outright — SCHEMA.md#given: "Cases never share state."
 */
class CaseRunner(
    private val specs: Specs,
    private val journey: Journey,
    private val journeyCase: JourneyCase,
    private val out: NDJSON
) {
    private val services: FixtureServices
    private val session: RootSession
    private val aliases: Aliases
    private val startedAt: Instant

    init {
        val given = journeyCase.given
        startedAt = given.now ?: specs.determinism.now

        val keyValues = specs.determinism.keyValues.toMutableMap()
        keyValues.putAll(given.keyValues)

        val slot = seedSlot(specs, given)
        val mode = when (given.store) {
            "absent" -> FixtureMedicalStore.Mode.ABSENT
            "unreadable" -> FixtureMedicalStore.Mode.UNREADABLE
            else -> FixtureMedicalStore.Mode.NORMAL
        }

        services = FixtureServices(
            now = startedAt,
            idSequence = given.ids ?: specs.determinism.sequence,
            keyValues = keyValues,
            slot = slot,
            mode = mode
        )
        aliases = Aliases(specs, services)
        session = RootSession(startedAt, specs.determinism.zone, services.ports)
    }

    /**
     * The slot is the fixture's RAW bytes with `data` and `settings` merged over them, so a
     * fixture that is deliberately unparseable stays unparseable. Only when an override is
     * present is the tree re-serialized, and `canonicalJSON` preserves explicit nulls, so
     * `partial_settings` still fails to decode after a merge.
     */
    private fun seedSlot(specs: Specs, given: Given): ByteArray? {
        val name = given.fixture ?: return null
        val bytes = specs.fixtureBytes(name)
        if (given.data.isEmpty() && given.settings.isEmpty()) return bytes

        val root = parseJSON(bytes).objectValue?.toMutableMap() ?: mutableMapOf()
        // Top-level keys REPLACE wholesale (SCHEMA.md#given) — supplying `medications` swaps
        // the whole array rather than appending to it.
        root.putAll(given.data)
        if (given.settings.isNotEmpty()) {
            val settings = root["notificationSettings"]?.objectValue?.toMutableMap() ?: mutableMapOf()
            settings.putAll(given.settings)
            root["notificationSettings"] = ReflexValue.of(settings)
        }
        return ReflexValue.of(root).jsonData
    }

    suspend fun run(): Boolean {
        out.emit(
            Record.caseStart(
                journey = journey.id,
                caseID = journeyCase.id,
                fixture = journeyCase.given.fixture,
                now = startedAt,
                decisions = journey.decisions
            )
        )

        if (journeyCase.given.load) {
            session.send(Action.ToAppRoot(AppRootAction.OnAppear))
            flush(FlushKind.ALL)
        }
        // SCHEMA.md#the-counter-baseline: setup activity is never counted. The notification set
        // planned during setup stays visible even though replaceCount reads zero.
        services.resetCounters()
        checkBudget()

        var failures = 0
        for ((index, step) in journeyCase.steps.withIndex()) {
            perform(step.verb, step.args)
            checkBudget()

            val snapshot = makeSnapshot()
            out.emit(Record.step(index = index, verb = step.verb, snapshot = snapshot))

            val resolver = PathResolver(snapshot = snapshot, aliases = aliases)
            for (expectation in step.expectations) {
                val expected = aliases.resolve(expectation.value).canonicalizingUUIDCase()
                val actual = resolver.resolve(expectation.path)
                val passed = Operators.evaluate(expectation.op, actual = actual, expected = expected)
                if (!passed) failures += 1
                out.emit(
                    Record.assertion(
                        step = index,
                        path = expectation.path,
                        op = expectation.op,
                        expected = expected,
                        actual = actual,
                        passed = passed
                    )
                )
            }
        }

        out.emit(
            Record.caseEnd(
                journey = journey.id,
                caseID = journeyCase.id,
                passed = failures == 0,
                failedAssertions = failures
            )
        )
        return failures == 0
    }

    // MARK: - Steps

    private suspend fun perform(verb: StepVerb, args: Map<String, ReflexValue>) {
        when (verb) {
            is StepVerb.Send -> {
                validate(verb.node, verb.action, isMessage = false)
                val resolved = aliases.resolve(ReflexValue.of(args)).objectValue ?: emptyMap()
                val action = Action.decode(ActionArguments(verb.node, verb.action, resolved))
                session.send(action)
            }

            is StepVerb.Deliver -> {
                validate(verb.node, verb.message, isMessage = true)
                val resolved = aliases.resolve(ReflexValue.of(args)).objectValue ?: emptyMap()
                val message = Message.decode(ActionArguments(verb.node, verb.message, resolved))
                session.deliver(message)
            }

            is StepVerb.Flush -> flush(verb.kind)

            is StepVerb.Advance ->
                // Moving the clock does not fire timers (SCHEMA.md#steps).
                services.clock.advance(verb.seconds)

            is StepVerb.Inject -> {
                if (verb.service != "store") {
                    throw RunnerError.Exhausted("the ${verb.service} fixture cannot inject failures")
                }
                services.store.armFailure(verb.failure)
            }

            is StepVerb.Note, StepVerb.ExpectOnly -> Unit
        }
    }

    /**
     * nodes.yaml is the only source of truth for what a journey may address. An internal action
     * is reachable from another reducer's effect list, never from a `send`.
     */
    private fun validate(node: NodeID, name: String, isMessage: Boolean) {
        val schema = NodeRegistry.schema(node) ?: throw RunnerError.Usage("unknown node ${node.rawValue}")
        val allowed = if (isMessage) schema.messages else schema.actions
        if (!allowed.contains(name)) {
            val kind = if (isMessage) "message" else "action"
            throw RunnerError.Usage("unknown $kind ${node.rawValue}.$name")
        }
    }

    private suspend fun flush(kind: FlushKind) {
        when (kind) {
            FlushKind.STORE -> session.flushStore()
            FlushKind.SCHEDULER -> drainScheduler()
            FlushKind.ALL ->
                // Fixed point: a store completion can register a timer, and a fired timer can queue
                // another store effect. Neither side is allowed to leave work behind.
                while (true) {
                    session.flushStore()
                    checkBudget()
                    if (services.scheduler.pendingCount == 0) break
                    drainScheduler()
                    checkBudget()
                }
        }
    }

    private fun drainScheduler() {
        var fired = 0
        while (services.scheduler.pendingCount > 0) {
            val messages = services.scheduler.drain()
            fired += messages.size
            if (fired > RootSession.EFFECT_CAP) {
                throw RunnerError.Exhausted("scheduler exceeded the ${RootSession.EFFECT_CAP}-effect cap")
            }
            for (message in messages) session.deliver(message)
            checkBudget()
        }
    }

    private fun checkBudget() {
        if (session.budgetExceeded) {
            throw RunnerError.Exhausted("reducer exceeded the ${RootSession.EFFECT_CAP}-effect cap")
        }
        if (services.ids.isExhausted) {
            throw RunnerError.Exhausted("the ID sequence is exhausted")
        }
    }

    // MARK: - Snapshot

    private fun makeSnapshot(): Snapshot {
        val context = session.context
        val state = NodeRegistry.emit(context)
        val data = session.state.appRoot.data.reflexValue

        val store = ReflexValue.of(
            mapOf(
                "filePresent" to ReflexValue.of(services.store.filePresent),
                "writeCount" to ReflexValue.of(services.store.writeCount.toLong()),
                "saved" to (services.store.saved?.reflexValue ?: ReflexValue.Null)
            )
        )

        val kv = ReflexValue.of(services.keyValue.values.mapValues { ReflexValue.of(it.value) })

        // Emission order is not contract, so the set is sorted by its stable identifier before
        // it ever reaches the stream (runner.yaml#canonicalization).
        val scheduled = services.notifications.scheduled
            .sortedBy { it.identifier }
            .map { it.reflexValue }

        val snapshot = Snapshot(
            state = state,
            data = data,
            store = store,
            kv = kv,
            notifications = ReflexValue.of(scheduled),
            replaceCount = services.notifications.replaceCount,
            now = services.clock.now(),
            consumed = services.ids.consumed
        )

        if (journey.unpinned.isEmpty()) return snapshot
        return snapshot.copy(
            state = snapshot.state.mapValues { PathResolver.prune(it.value, journey.unpinned) },
            data = PathResolver.prune(snapshot.data, journey.unpinned),
            store = PathResolver.prune(snapshot.store, journey.unpinned),
            kv = PathResolver.prune(snapshot.kv, journey.unpinned),
            notifications = PathResolver.prune(snapshot.notifications, journey.unpinned)
        )
    }
}

/** Drives the suite: locate the specs, parse the journeys, run the cases, report the exit code. */
class Executor(
    private val specs: Specs,
    private val out: NDJSON
) {
    /**
     * Returns the process exit code. A usage or exhaustion error propagates instead, because
     * those abort the whole run rather than failing one case.
     */
    suspend fun run(glob: String, caseFilter: String?): Int {
        val files = specs.journeyFiles(matching = glob)
        var allPassed = true
        var matchedCase = false

        for (file in files) {
            val text = String(Files.readAllBytes(file), Charsets.UTF_8)
            val journey = JourneyParser.parse(text, file.fileName.toString())
            for (journeyCase in journey.cases) {
                if (caseFilter != null && journeyCase.id != caseFilter) continue
                matchedCase = true
                val runner = CaseRunner(specs, journey, journeyCase, out)
                val passed = runner.run()
                allPassed = allPassed && passed
            }
        }

        if (caseFilter != null && !matchedCase) {
            throw RunnerError.Usage("no case '$caseFilter' matches $glob")
        }
        return if (allPassed) 0 else 1
    }
}
