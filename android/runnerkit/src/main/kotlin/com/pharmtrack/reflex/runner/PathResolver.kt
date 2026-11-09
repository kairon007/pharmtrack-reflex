package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue

/**
 * One addressable piece of an assertion path. Aliases are resolved per token rather than by
 * string surgery, because `$fixture:notification_matrix.appointments[0].id` carries its own
 * dots and brackets INSIDE the enclosing `[id=...]`.
 */
sealed class PathToken {
    data class Key(val name: String) : PathToken()

    data class Index(val position: Int) : PathToken()

    /** `[id=<expr>]` — select the array element whose `id` equals the expression. */
    data class Select(val expression: String) : PathToken()

    /** `[<key>]` on a derived map such as `rowIcon`, `isTaken`, `initials`. */
    data class SubscriptKey(val key: String) : PathToken()
}

/**
 * Everything an assertion path can address, frozen at one step. Built once per step so the
 * emitted record and the assertions evaluated against it can never disagree.
 */
data class Snapshot(
    val state: Map<String, ReflexValue>,
    val data: ReflexValue,
    val store: ReflexValue,
    val kv: ReflexValue,
    /** The planned set, already sorted by identifier (runner.yaml#canonicalization). */
    val notifications: ReflexValue,
    val replaceCount: Int,
    val now: Instant,
    val consumed: Int
)

class PathResolver(
    private val snapshot: Snapshot,
    private val aliases: Aliases
) {

    /**
     * An unresolvable path is `.null`, not an error: `is_null` and `not_null` are how a journey
     * asserts absence, and a deleted entity's field must read as absent rather than abort the run.
     */
    fun resolve(path: String): ReflexValue {
        val all = tokenize(path)
        val first = all.firstOrNull() as? PathToken.Key ?: return ReflexValue.Null
        val tokens = resolveAliases(all.drop(1))

        return when (first.name) {
            "state" -> walk(ReflexValue.of(snapshot.state), tokens)
            "data" -> walk(snapshot.data, tokens)
            "store" -> walk(snapshot.store, tokens)
            "kv" -> walk(snapshot.kv, tokens)
            "notifications" ->
                // The bare namespace is the planned SET; replaceCount is the one scalar beside it.
                if (tokens == listOf<PathToken>(PathToken.Key("replaceCount"))) {
                    ReflexValue.of(snapshot.replaceCount.toLong())
                } else {
                    walk(snapshot.notifications, tokens)
                }

            "clock" -> walk(ReflexValue.of(mapOf("now" to ReflexValue.instant(snapshot.now))), tokens)
            "ids" -> walk(ReflexValue.of(mapOf("consumed" to ReflexValue.of(snapshot.consumed.toLong()))), tokens)
            else -> ReflexValue.Null
        }
    }

    private fun resolveAliases(tokens: List<PathToken>): List<PathToken> = tokens.map { token ->
        when (token) {
            is PathToken.Key -> if (token.name.startsWith("$")) {
                PathToken.Key(aliases.pathComponent(token.name))
            } else {
                token
            }

            is PathToken.Select -> if (token.expression.startsWith("$")) {
                PathToken.Select(aliases.pathComponent(token.expression))
            } else {
                token
            }

            is PathToken.SubscriptKey -> if (token.key.startsWith("$")) {
                PathToken.SubscriptKey(aliases.pathComponent(token.key))
            } else {
                token
            }

            is PathToken.Index -> token
        }
    }

    companion object {

        fun tokenize(path: String): List<PathToken> {
            val tokens = mutableListOf<PathToken>()
            val current = StringBuilder()
            var index = 0
            val length = path.length

            fun flush() {
                if (current.isNotEmpty()) {
                    tokens.add(PathToken.Key(current.toString()))
                    current.setLength(0)
                }
            }

            while (index < length) {
                val character = path[index]
                if (character == '.') {
                    flush()
                    index += 1
                } else if (character == '[') {
                    flush()
                    // Brackets nest: `[id=$fixture:notification_matrix.appointments[2].id]` carries
                    // its own index, so the scan has to balance rather than stop at the first "]".
                    var depth = 0
                    var close = index
                    var scan = index
                    while (scan < length) {
                        if (path[scan] == '[') depth += 1
                        if (path[scan] == ']') {
                            depth -= 1
                            if (depth == 0) {
                                close = scan
                                break
                            }
                        }
                        scan += 1
                    }
                    if (depth != 0) {
                        current.append(path.substring(index))
                        index = length
                        continue
                    }
                    val inner = path.substring(index + 1, close)
                    val position = inner.toIntOrNull()
                    when {
                        inner.startsWith("id=") -> tokens.add(PathToken.Select(inner.substring(3)))
                        position != null -> tokens.add(PathToken.Index(position))
                        else -> tokens.add(PathToken.SubscriptKey(inner))
                    }
                    index = close + 1
                } else {
                    current.append(character)
                    index += 1
                }
            }
            flush()
            return tokens
        }

        fun walk(value: ReflexValue, tokens: List<PathToken>): ReflexValue {
            var current = value
            for (token in tokens) {
                when (token) {
                    is PathToken.Key -> {
                        val members = current.objectValue
                        val member = members?.get(token.name)
                        if (member != null) {
                            current = member
                        } else if (token.name == "count") {
                            val count = current.count ?: return ReflexValue.Null
                            current = ReflexValue.of(count.toLong())
                        } else {
                            return ReflexValue.Null
                        }
                    }

                    is PathToken.Index -> {
                        val array = current.arrayValue ?: return ReflexValue.Null
                        if (token.position < 0 || token.position >= array.size) return ReflexValue.Null
                        current = array[token.position]
                    }

                    is PathToken.SubscriptKey -> {
                        val members = current.objectValue ?: return ReflexValue.Null
                        current = members[token.key] ?: return ReflexValue.Null
                    }

                    is PathToken.Select -> {
                        val array = current.arrayValue ?: return ReflexValue.Null
                        val match = array.firstOrNull {
                            it.objectValue?.get("id")?.stringValue == token.expression
                        } ?: return ReflexValue.Null
                        current = match
                    }
                }
            }
            return current
        }

        /**
         * runner.yaml#comparison.exclude. Every `unpinned` entry in the frozen suite is prose
         * ("rendered date strings (D15)"), and Core emits no rendered strings or pixel geometry,
         * so those entries match nothing. An entry that IS a real path is honoured by removing it,
         * which keeps the rule identical on both platforms if a future journey pins one.
         */
        fun prune(value: ReflexValue, unpinned: List<String>): ReflexValue {
            var pruned = value
            for (entry in unpinned) {
                val tokens = tokenize(entry)
                if (tokens.firstOrNull() !is PathToken.Key) continue
                pruned = remove(tokens, pruned)
            }
            return pruned
        }

        private fun remove(tokens: List<PathToken>, value: ReflexValue): ReflexValue {
            val head = tokens.firstOrNull() ?: return value
            val rest = tokens.drop(1)
            return when (head) {
                is PathToken.Key -> {
                    val members = value.objectValue ?: return value
                    val member = members[head.name] ?: return value
                    if (rest.isEmpty()) {
                        val copy = members.toMutableMap()
                        copy.remove(head.name)
                        ReflexValue.of(copy)
                    } else {
                        val copy = members.toMutableMap()
                        copy[head.name] = remove(rest, member)
                        ReflexValue.of(copy)
                    }
                }

                is PathToken.SubscriptKey -> {
                    val members = value.objectValue ?: return value
                    val member = members[head.key] ?: return value
                    if (rest.isEmpty()) {
                        val copy = members.toMutableMap()
                        copy.remove(head.key)
                        ReflexValue.of(copy)
                    } else {
                        val copy = members.toMutableMap()
                        copy[head.key] = remove(rest, member)
                        ReflexValue.of(copy)
                    }
                }

                is PathToken.Index -> {
                    val array = value.arrayValue ?: return value
                    if (head.position < 0 || head.position >= array.size) return value
                    val copy = array.toMutableList()
                    if (rest.isEmpty()) {
                        copy.removeAt(head.position)
                    } else {
                        copy[head.position] = remove(rest, copy[head.position])
                    }
                    ReflexValue.of(copy)
                }

                is PathToken.Select -> value
            }
        }
    }
}
