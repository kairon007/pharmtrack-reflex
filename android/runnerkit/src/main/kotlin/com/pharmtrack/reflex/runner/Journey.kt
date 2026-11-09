package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

enum class FlushKind(val rawValue: String) {
    ALL("all"), SCHEDULER("scheduler"), STORE("store");

    companion object {
        fun fromRaw(raw: String): FlushKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

sealed class StepVerb {
    data class Send(val node: NodeID, val action: String) : StepVerb()

    data class Deliver(val node: NodeID, val message: String) : StepVerb()

    data class Flush(val kind: FlushKind) : StepVerb()

    data class Advance(val seconds: Double) : StepVerb()

    data class Inject(val service: String, val failure: String) : StepVerb()

    data class Note(val text: String) : StepVerb()

    /**
     * A steps entry carrying only `expect`. It changes nothing, and SCHEMA.md requires a
     * record even for a no-op step, so it is labelled rather than skipped.
     */
    data object ExpectOnly : StepVerb()

    /** runner.yaml#step.target. Fixed spellings so the two streams agree byte for byte. */
    val target: String
        get() = when (this) {
            is Send -> "${node.rawValue}.$action"
            is Deliver -> "${node.rawValue}.$message"
            is Flush -> kind.rawValue
            is Advance -> "clock"
            is Inject -> "$service.$failure"
            is Note, is ExpectOnly -> ""
        }

    val label: String
        get() = when (this) {
            is Send -> "send"
            is Deliver -> "deliver"
            is Flush -> "flush"
            is Advance -> "advance"
            is Inject -> "inject"
            is Note -> "note"
            is ExpectOnly -> "expect"
        }
}

enum class AssertionOperator(val rawValue: String) {
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    COUNT("count"),
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains"),
    UNORDERED_EQUALS("unordered_equals"),
    IS_NULL("is_null"),
    NOT_NULL("not_null"),
    ONE_OF("one_of"),
    MATCHES("matches"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    CLOSE_TO("close_to");

    /** Operators whose right-hand side is a flag rather than a compared value. */
    val isPresence: Boolean get() = this == IS_NULL || this == NOT_NULL

    companion object {
        fun fromRaw(raw: String): AssertionOperator? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class Expectation(
    val path: String,
    val op: AssertionOperator,
    /**
     * Raw YAML value. Aliases are resolved at assertion time, not at parse time, because
     * `$1` and `$now` depend on how far the case has run.
     */
    val value: ReflexValue
)

data class Given(
    val fixture: String?,
    val store: String?,
    val keyValues: Map<String, Boolean>,
    val now: Instant?,
    val ids: List<UUID>?,
    val settings: Map<String, ReflexValue>,
    val data: Map<String, ReflexValue>,
    val load: Boolean
)

data class JourneyStep(
    val verb: StepVerb,
    val args: Map<String, ReflexValue>,
    val expectations: List<Expectation>
)

data class JourneyCase(
    val id: String,
    val divergence: String?,
    val given: Given,
    val steps: List<JourneyStep>
)

data class Journey(
    val id: String,
    val decisions: List<String>,
    val unpinned: List<String>,
    val cases: List<JourneyCase>
)

object JourneyParser {

    fun parse(text: String, fileName: String): Journey {
        val root = YAML.parse(text)
        val map = root.objectValue ?: throw RunnerError.Malformed("$fileName: journey is not a mapping")
        if (map["schema"]?.stringValue != "reflex/behavior/1") {
            throw RunnerError.Malformed("$fileName: unsupported schema")
        }
        val id = map["id"]?.stringValue ?: throw RunnerError.Malformed("$fileName: journey has no id")
        val decisions = (map["decisions"]?.arrayValue ?: emptyList())
            .mapNotNull { it.stringValue }
        val unpinned = (map["unpinned"]?.arrayValue ?: emptyList())
            .mapNotNull { it.stringValue }
        val defaults = map["defaults"]?.objectValue ?: emptyMap()

        // SCHEMA.md#divergence-markers: a case may only cite a decision the file declares.
        val declared = decisions.toSet()
        val caseValues = map["cases"]?.arrayValue ?: throw RunnerError.Malformed("$fileName: journey has no cases")
        val cases = caseValues.map { value ->
            val caseObject = value.objectValue
                ?: throw RunnerError.Malformed("$fileName: case has no id")
            val caseId = caseObject["id"]?.stringValue
                ?: throw RunnerError.Malformed("$fileName: case has no id")
            val divergence = caseObject["divergence"]?.stringValue
            if (divergence != null && !declared.contains(divergence)) {
                throw RunnerError.Malformed(
                    "$fileName: case '$caseId' cites $divergence, which is not in decisions"
                )
            }
            val merged = defaults.toMutableMap()
            merged.putAll(caseObject["given"]?.objectValue ?: emptyMap())
            val given = parseGiven(merged, context = "$fileName#$caseId")
            val steps = (caseObject["steps"]?.arrayValue ?: emptyList()).map {
                parseStep(it, context = "$fileName#$caseId")
            }
            JourneyCase(id = caseId, divergence = divergence, given = given, steps = steps)
        }
        return Journey(id = id, decisions = decisions, unpinned = unpinned, cases = cases)
    }

    private fun parseGiven(map: Map<String, ReflexValue>, context: String): Given {
        val fixture = map["fixture"]?.stringValue
        val store = map["store"]?.stringValue
        if (fixture != null && store != null) {
            throw RunnerError.Malformed("$context: fixture and store are mutually exclusive")
        }
        if (store != null && store != "absent" && store != "unreadable") {
            throw RunnerError.Malformed("$context: unknown store mode '$store'")
        }

        val keyValues = linkedMapOf<String, Boolean>()
        for ((key, value) in map["key_value"]?.objectValue ?: emptyMap()) {
            val flag = value.boolValue
                ?: throw RunnerError.Malformed("$context: key_value.$key is not a boolean")
            keyValues[key] = flag
        }

        var now: Instant? = null
        val nowValue = map["now"]
        if (nowValue != null && !nowValue.isNull) {
            val text = nowValue.stringValue
            now = text?.let { Instant.parse(it) }
                ?: throw RunnerError.Malformed("$context: now is not an ISO8601 instant")
        }

        var ids: List<UUID>? = null
        val idsValue = map["ids"]
        if (idsValue != null && !idsValue.isNull) {
            val elements = idsValue.arrayValue.orThrow("$context: ids is not a sequence")
            ids = elements.map { element ->
                val text = element.stringValue ?: throw RunnerError.Malformed("$context: ids holds a non-UUID")
                parseUUID(text) ?: throw RunnerError.Malformed("$context: ids holds a non-UUID")
            }
        }

        val loadValue = map["load"]
        val load = if (loadValue != null) {
            loadValue.boolValue ?: throw RunnerError.Malformed("$context: load is not a boolean")
        } else {
            true
        }

        return Given(
            fixture = fixture,
            store = store,
            keyValues = keyValues,
            now = now,
            ids = ids,
            settings = map["settings"]?.objectValue ?: emptyMap(),
            data = map["data"]?.objectValue ?: emptyMap(),
            load = load
        )
    }

    private fun parseStep(value: ReflexValue, context: String): JourneyStep {
        val map = value.objectValue ?: throw RunnerError.Malformed("$context: step is not a mapping")
        val args = map["args"]?.objectValue ?: emptyMap()
        val expectations = (map["expect"]?.arrayValue ?: emptyList()).map {
            parseExpectation(it, context = context)
        }

        val verbs = mutableListOf<StepVerb>()
        map["send"]?.stringValue?.let { target ->
            verbs.add(StepVerb.Send(node(target, context), actionName(target, context)))
        }
        map["deliver"]?.stringValue?.let { target ->
            verbs.add(StepVerb.Deliver(node(target, context), actionName(target, context)))
        }
        map["flush"]?.stringValue?.let { target ->
            val kind = FlushKind.fromRaw(target)
                ?: throw RunnerError.Malformed("$context: unknown flush target '$target'")
            verbs.add(StepVerb.Flush(kind))
        }
        map["advance"]?.let { advance ->
            val seconds = advance.objectValue?.get("seconds")?.doubleValue
                ?: throw RunnerError.Malformed("$context: advance needs a numeric seconds")
            verbs.add(StepVerb.Advance(seconds))
        }
        map["inject"]?.objectValue?.let { inject ->
            val service = inject["service"]?.stringValue
            val failure = inject["fail_next"]?.stringValue
            if (service == null || failure == null) {
                throw RunnerError.Malformed("$context: inject needs service and fail_next")
            }
            verbs.add(StepVerb.Inject(service, failure))
        }
        map["note"]?.stringValue?.let { note ->
            verbs.add(StepVerb.Note(note))
        }
        if (verbs.size > 1) {
            throw RunnerError.Malformed("$context: step carries more than one verb")
        }
        return JourneyStep(
            verb = verbs.firstOrNull() ?: StepVerb.ExpectOnly,
            args = args,
            expectations = expectations
        )
    }

    private fun parseExpectation(value: ReflexValue, context: String): Expectation {
        val map = value.objectValue ?: throw RunnerError.Malformed("$context: expectation has no path")
        val path = map["path"]?.stringValue
            ?: throw RunnerError.Malformed("$context: expectation has no path")
        val operators = map.keys.filter { it != "path" }.sorted()
        if (operators.size != 1) {
            throw RunnerError.Malformed(
                "$context: expectation at '$path' needs exactly one known operator, found $operators"
            )
        }
        val op = AssertionOperator.fromRaw(operators[0])
            ?: throw RunnerError.Malformed(
                "$context: expectation at '$path' needs exactly one known operator, found $operators"
            )
        return Expectation(path = path, op = op, value = map[operators[0]] ?: ReflexValue.Null)
    }

    private fun node(target: String, context: String): NodeID {
        val parts = target.split('.')
        if (parts.size != 2) throw RunnerError.Malformed("$context: '$target' is not <node>.<name>")
        return NodeID.fromRaw(parts[0]) ?: throw RunnerError.Malformed("$context: '$target' is not <node>.<name>")
    }

    private fun actionName(target: String, context: String): String {
        val parts = target.split('.')
        if (parts.size != 2) throw RunnerError.Malformed("$context: '$target' is not <node>.<name>")
        return parts[1]
    }
}
