package com.pharmtrack.reflex.core.Support

import java.util.UUID

/**
 * The canonical value tree every machine emits and every assertion compares.
 *
 * `int` and `double` stay distinct so serialization can honour runner.yaml#canonicalization
 * ("integers without a decimal point; doubles shortest round-trip form"), but equality
 * compares them numerically so a YAML `120` matches a Kotlin `Double` 120.0.
 */
sealed class ReflexValue {

    object Null : ReflexValue() {
        override fun equals(other: Any?): Boolean = other is Null
        override fun hashCode(): Int = 0
        override fun toString(): String = "null"
    }

    class Bool(val value: Boolean) : ReflexValue() {
        override fun equals(other: Any?): Boolean = other is Bool && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class IntNum(val value: Long) : ReflexValue() {
        override fun equals(other: Any?): Boolean = when (other) {
            is IntNum -> other.value == value
            is DoubleNum -> other.value == value.toDouble()
            else -> false
        }

        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class DoubleNum(val value: Double) : ReflexValue() {
        override fun equals(other: Any?): Boolean = when (other) {
            is DoubleNum -> other.value == value
            is IntNum -> value == other.value.toDouble()
            else -> false
        }

        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class Text(val value: String) : ReflexValue() {
        override fun equals(other: Any?): Boolean = other is Text && other.value == value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value
    }

    class ArrayVal(val items: List<ReflexValue>) : ReflexValue() {
        override fun equals(other: Any?): Boolean = other is ArrayVal && other.items == items
        override fun hashCode(): Int = items.hashCode()
        override fun toString(): String = items.toString()
    }

    class ObjectVal(val members: Map<String, ReflexValue>) : ReflexValue() {
        override fun equals(other: Any?): Boolean = other is ObjectVal && other.members == members
        override fun hashCode(): Int = members.hashCode()
        override fun toString(): String = members.toString()
    }

    // MARK: - Construction

    companion object {

        fun of(value: Boolean) = Bool(value)

        fun of(value: Long) = IntNum(value)

        fun of(value: Int) = IntNum(value.toLong())

        fun of(value: Double) = DoubleNum(value)

        fun of(value: String) = Text(value)

        fun of(items: List<ReflexValue>) = ArrayVal(items)

        fun of(members: Map<String, ReflexValue>) = ObjectVal(members)

        fun uuid(id: UUID): ReflexValue = Text(uuidString(id))

        fun uuids(ids: List<UUID>): ReflexValue = ArrayVal(ids.map { uuid(it) })

        fun instant(value: Instant): ReflexValue = Text(value.iso8601)

        fun optionalInstant(value: Instant?): ReflexValue =
            if (value == null) Null else Text(value.iso8601)

        fun optionalUUID(value: UUID?): ReflexValue =
            if (value == null) Null else uuid(value)

        fun optionalDouble(value: Double?): ReflexValue =
            if (value == null) Null else DoubleNum(value)

        fun optionalString(value: String?): ReflexValue =
            if (value == null) Null else Text(value)

        /**
         * Swift's `UUID.uuidString` is UPPERCASE; `java.util.UUID.toString` is lowercase.
         * Every identifier in the stream goes through here so both platforms spell it the
         * same way (runner.yaml#canonicalization: "UUIDs uppercase").
         */
        fun uuidString(id: UUID): String = id.toString().uppercase()

        fun isUUIDShape(text: String): Boolean {
            val parts = text.split('-')
            if (parts.size != 5) return false
            val lengths = listOf(8, 4, 4, 4, 12)
            for (index in parts.indices) {
                val part = parts[index]
                if (part.length != lengths[index]) return false
                for (character in part) {
                    if (!character.isHexDigit()) return false
                }
            }
            return true
        }

        fun quote(text: String): String {
            val out = StringBuilder("\"")
            for (character in text) {
                when (character) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> {
                        if (character.code < 0x20) {
                            out.append(String.format("\\u%04x", character.code))
                        } else {
                            out.append(character)
                        }
                    }
                }
            }
            out.append('"')
            return out.toString()
        }
    }

    // MARK: - Accessors

    val isNull: Boolean get() = this === Null

    val stringValue: String? get() = (this as? Text)?.value

    val intValue: Long?
        get() = when (this) {
            is IntNum -> value
            is DoubleNum ->
                if (value == Math.floor(value) && Math.abs(value) < 9.2e18) value.toLong() else null

            else -> null
        }

    val doubleValue: Double?
        get() = when (this) {
            is IntNum -> value.toDouble()
            is DoubleNum -> value
            else -> null
        }

    val boolValue: Boolean? get() = (this as? Bool)?.value

    val arrayValue: List<ReflexValue>? get() = (this as? ArrayVal)?.items

    val objectValue: Map<String, ReflexValue>? get() = (this as? ObjectVal)?.members

    /** Element count for arrays and objects; null for scalars. */
    val count: Int?
        get() = when (this) {
            is ArrayVal -> items.size
            is ObjectVal -> members.size
            else -> null
        }

    // MARK: - Canonicalization

    /** Uppercases every UUID-shaped string, per runner.yaml#canonicalization. */
    fun canonicalizingUUIDCase(): ReflexValue = when (this) {
        is Text -> if (isUUIDShape(value)) Text(value.uppercase()) else this
        is ArrayVal -> ArrayVal(items.map { it.canonicalizingUUIDCase() })
        is ObjectVal -> ObjectVal(members.mapValues { it.value.canonicalizingUUIDCase() })
        else -> this
    }

    /** Sorted keys, no whitespace, instants already ISO8601 UTC, UUIDs already uppercase. */
    val canonicalJSON: String
        get() = when (this) {
            is Null -> "null"
            is Bool -> if (value) "true" else "false"
            is IntNum -> value.toString()
            is DoubleNum -> SwiftNumber.doubleString(value)
            is Text -> quote(value)
            is ArrayVal -> items.joinToString(",", "[", "]") { it.canonicalJSON }
            is ObjectVal -> members.keys.sorted().joinToString(",", "{", "}") { key ->
                quote(key) + ":" + members.getValue(key).canonicalJSON
            }
        }
}
