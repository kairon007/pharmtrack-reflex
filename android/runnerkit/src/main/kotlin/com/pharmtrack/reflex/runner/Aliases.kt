package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.ReflexValue

/**
 * SCHEMA.md#value-aliases. Resolution is deferred to the moment of use because `$1` depends on
 * how many ids the generator has handed out so far and `$now` on how far the clock has moved.
 * Both runners resolve identically; nothing here is platform-specific.
 */
class Aliases(
    private val specs: Specs,
    private val services: FixtureServices
) {
    private val fixtureCache = mutableMapOf<String, ReflexValue>()

    /**
     * Resolves every alias in a value tree. Used for assertion EXPECTED values and for step
     * `args`, which the journeys write as `args: { vitalId: "$1" }`.
     */
    fun resolve(value: ReflexValue): ReflexValue = when (value) {
        is ReflexValue.Text ->
            if (value.value.startsWith("$")) this.value(value.value) else value

        is ReflexValue.ArrayVal -> ReflexValue.of(value.items.map { resolve(it) })
        is ReflexValue.ObjectVal -> ReflexValue.of(
            value.members.mapValues { resolve(it.value) }
        )

        else -> value
    }

    fun value(text: String): ReflexValue {
        if (text == "\$now") {
            return ReflexValue.instant(services.clock.now())
        }
        if (text.startsWith("\$fixture:")) {
            return fixtureReference(text.removePrefix("\$fixture:"))
        }
        if (text.startsWith("$")) {
            val position = text.removePrefix("$").toIntOrNull()
            if (position != null) {
                val handedOut = services.ids.handedOut
                if (position < 1 || position > handedOut.size) {
                    throw RunnerError.Usage(
                        "alias $text has not been handed out (consumed ${handedOut.size})"
                    )
                }
                return ReflexValue.uuid(handedOut[position - 1])
            }
            val id = specs.determinism.reserved[text.removePrefix("$")]
                ?: throw RunnerError.Usage("unknown alias $text")
            return ReflexValue.uuid(id)
        }
        throw RunnerError.Usage("'$text' is not an alias")
    }

    /**
     * An alias used as a path component must resolve to a string — it is addressing a map key
     * or an entity id, never a whole object.
     */
    fun pathComponent(text: String): String {
        val resolved = value(text)
        return resolved.stringValue
            ?: throw RunnerError.Usage("alias $text does not resolve to a path component")
    }

    /**
     * `$fixture:<name>` is the whole file; `$fixture:<name>.<path>` is one field of it. The
     * remainder is tokenized with the SAME grammar as an assertion path, so `vitals[3].id`
     * works identically in both positions.
     */
    private fun fixtureReference(body: String): ReflexValue {
        val tokens = PathResolver.tokenize(body)
        val first = tokens.firstOrNull() as? PathToken.Key
            ?: throw RunnerError.Usage("\$fixture:$body does not name a fixture")
        val root = fixture(first.name)
        return PathResolver.walk(root, tokens.drop(1))
    }

    private fun fixture(name: String): ReflexValue {
        fixtureCache[name]?.let { return it }
        val value = specs.fixtureValue(name)
        fixtureCache[name] = value
        return value
    }
}
