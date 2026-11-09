package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

class ActionError(message: String) : Exception(message) {
    companion object {
        fun unknownAction(node: NodeID, name: String): ActionError =
            ActionError("unknown action ${node.rawValue}.$name")

        fun missingArgument(node: NodeID, action: String, key: String): ActionError =
            ActionError("missing argument '$key' for ${node.rawValue}.$action")

        fun invalidArgument(node: NodeID, action: String, key: String, detail: String): ActionError =
            ActionError("invalid argument '$key' for ${node.rawValue}.$action: $detail")
    }
}

/**
 * Typed access over the YAML `args` map. A missing or wrongly-typed argument is a malformed
 * journey (exit 2), never a silently defaulted value — the specs are the only source of truth
 * for what an action accepts.
 */
class ActionArguments(
    val node: NodeID,
    val action: String,
    val raw: Map<String, ReflexValue>
) {
    private fun require(key: String): ReflexValue {
        val value = raw[key] ?: throw ActionError.missingArgument(node, action, key)
        if (value.isNull) throw ActionError.missingArgument(node, action, key)
        return value
    }

    private fun invalid(key: String, detail: String): ActionError =
        ActionError.invalidArgument(node, action, key, detail)

    fun string(key: String): String {
        val value = require(key)
        return value.stringValue ?: throw invalid(key, "expected a string")
    }

    fun optionalString(key: String): String? {
        val value = raw[key] ?: return null
        if (value.isNull) return null
        return value.stringValue ?: throw invalid(key, "expected a string or null")
    }

    fun int(key: String): Long {
        val value = require(key)
        return value.intValue ?: throw invalid(key, "expected an integer")
    }

    fun bool(key: String): Boolean {
        val value = require(key)
        return value.boolValue ?: throw invalid(key, "expected a boolean")
    }

    fun uuid(key: String): UUID {
        val value = require(key)
        val text = value.stringValue ?: throw invalid(key, "expected a UUID string")
        return parseUUID(text) ?: throw invalid(key, "expected a UUID string")
    }

    fun optionalUUID(key: String): UUID? {
        val value = raw[key] ?: return null
        if (value.isNull) return null
        val text = value.stringValue ?: throw invalid(key, "expected a UUID string or null")
        return parseUUID(text) ?: throw invalid(key, "expected a UUID string or null")
    }

    fun instant(key: String): Instant {
        val value = require(key)
        val text = value.stringValue ?: throw invalid(key, "expected an ISO8601 instant")
        return Instant.parse(text) ?: throw invalid(key, "expected an ISO8601 instant")
    }

    fun uuidArray(key: String): List<UUID> {
        val value = require(key)
        val elements = value.arrayValue ?: throw invalid(key, "expected an array of UUIDs")
        return elements.mapIndexed { index, element ->
            val text = element.stringValue ?: throw invalid(key, "element $index is not a UUID string")
            parseUUID(text) ?: throw invalid(key, "element $index is not a UUID string")
        }
    }

    /** The RawRepresentable enumeration: an exact, case-sensitive match on one of the raw values. */
    fun enumeration(key: String, typeName: String, allowed: List<String>): String {
        val text = string(key)
        if (!allowed.contains(text)) throw invalid(key, "'$text' is not a valid $typeName")
        return text
    }
}
