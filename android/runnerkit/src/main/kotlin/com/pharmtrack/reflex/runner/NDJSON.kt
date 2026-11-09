package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * runner.yaml#output. One canonical JSON object per line, in step order, on stdout.
 * Diagnostics go to stderr and are never compared.
 */
class NDJSON(
    private val stream: OutputStream,
    private val capture: Boolean = false
) {
    /** Populated only in memory mode, so the CLI never buffers the whole suite. */
    val lines = mutableListOf<String>()

    fun emit(record: ReflexValue) {
        // UUID case is normalized here, once, for the whole record — state, data, services and
        // assertion values alike — so no emitter has to remember (runner.yaml#canonicalization).
        val canonical = record.canonicalizingUUIDCase()
        val line = canonical.canonicalJSON
        if (capture) lines.add(line)
        stream.write((line + "\n").toByteArray(Charsets.UTF_8))
    }

    fun finish() {
        stream.flush()
    }

    companion object {
        fun standardOutput(): NDJSON = NDJSON(System.out)

        /** For BehaviorTests: the same emitter, the same canonicalization, kept in memory. */
        fun inMemory(): NDJSON = NDJSON(java.io.OutputStream.nullOutputStream(), capture = true)

        fun file(path: Path): NDJSON = NDJSON(Files.newOutputStream(path))
    }
}

object Record {

    fun caseStart(
        journey: String,
        caseID: String,
        fixture: String?,
        now: Instant,
        decisions: List<String>
    ): ReflexValue = ReflexValue.of(
        mapOf(
            "type" to ReflexValue.Text("case_start"),
            "journey" to ReflexValue.Text(journey),
            "case" to ReflexValue.Text(caseID),
            "fixture" to ReflexValue.optionalString(fixture),
            "now" to ReflexValue.instant(now),
            "decisions" to ReflexValue.of(decisions.map { ReflexValue.Text(it) })
        )
    )

    fun step(
        index: Int,
        verb: StepVerb,
        snapshot: Snapshot
    ): ReflexValue = ReflexValue.of(
        mapOf(
            "type" to ReflexValue.Text("step"),
            "index" to ReflexValue.of(index.toLong()),
            "verb" to ReflexValue.Text(verb.label),
            "target" to ReflexValue.Text(verb.target),
            "state" to ReflexValue.of(snapshot.state),
            "data" to snapshot.data,
            "services" to ReflexValue.of(
                mapOf(
                    "kv" to snapshot.kv,
                    "store" to snapshot.store,
                    "notifications" to ReflexValue.of(
                        mapOf(
                            "replaceCount" to ReflexValue.of(snapshot.replaceCount.toLong()),
                            "scheduled" to snapshot.notifications
                        )
                    ),
                    "clock" to ReflexValue.of(mapOf("now" to ReflexValue.instant(snapshot.now))),
                    "ids" to ReflexValue.of(mapOf("consumed" to ReflexValue.of(snapshot.consumed.toLong())))
                )
            )
        )
    )

    fun assertion(
        step: Int,
        path: String,
        op: AssertionOperator,
        expected: ReflexValue,
        actual: ReflexValue,
        passed: Boolean
    ): ReflexValue = ReflexValue.of(
        mapOf(
            "type" to ReflexValue.Text("assertion"),
            "step" to ReflexValue.of(step.toLong()),
            "path" to ReflexValue.Text(path),
            "operator" to ReflexValue.Text(op.rawValue),
            "expected" to expected,
            "actual" to actual,
            "passed" to ReflexValue.of(passed)
        )
    )

    fun caseEnd(
        journey: String,
        caseID: String,
        passed: Boolean,
        failedAssertions: Int
    ): ReflexValue = ReflexValue.of(
        mapOf(
            "type" to ReflexValue.Text("case_end"),
            "journey" to ReflexValue.Text(journey),
            "case" to ReflexValue.Text(caseID),
            "passed" to ReflexValue.of(passed),
            "failedAssertions" to ReflexValue.of(failedAssertions.toLong())
        )
    )
}
