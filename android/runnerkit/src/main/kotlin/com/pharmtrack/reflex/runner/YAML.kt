package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * YAML is a TOOL concern (runner.yaml#implementation_constraints.android.yaml). Core receives
 * plain ReflexValues and never sees a YAML node, a Date or a number object.
 */
object YAML {

    private val yaml = Yaml()

    fun parse(text: String): ReflexValue = convert(yaml.load<Any?>(text))

    fun parse(file: Path): ReflexValue = parse(Files.readString(file))

    /**
     * Ints and doubles must stay distinct here: ReflexValue compares them numerically but
     * serializes them differently, and the stream is byte-compared.
     */
    fun convert(value: Any?): ReflexValue = when (value) {
        null -> ReflexValue.Null
        is Boolean -> ReflexValue.of(value)
        is Int -> ReflexValue.of(value.toLong())
        is Long -> ReflexValue.of(value)
        is Short -> ReflexValue.of(value.toLong())
        is Byte -> ReflexValue.of(value.toLong())
        is Double -> ReflexValue.of(value)
        is Float -> ReflexValue.of(value.toDouble())
        is String -> ReflexValue.Text(value)
        is java.util.Date ->
            // Defensive: every instant in the suite is quoted, but a YAML timestamp resolver
            // would otherwise smuggle a timestamp into a string field.
            ReflexValue.Text(Instant(value.time / 1000.0).iso8601)

        is List<*> -> ReflexValue.of(value.map { convert(it) })
        is Map<*, *> -> ReflexValue.of(
            value.entries.associate { (key, member) -> key.toString() to convert(member) }
        )

        else -> ReflexValue.Text(value.toString())
    }
}
