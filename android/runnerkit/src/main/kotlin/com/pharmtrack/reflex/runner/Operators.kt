package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue

/**
 * SCHEMA.md#operators. Comparison is over the canonical value tree, so the same rules hold on
 * both platforms without either runner knowing how the other represents a number.
 */
object Operators {

    fun evaluate(
        op: AssertionOperator,
        actual: ReflexValue,
        expected: ReflexValue
    ): Boolean = when (op) {
        AssertionOperator.EQUALS -> equal(actual, expected)
        AssertionOperator.NOT_EQUALS -> !equal(actual, expected)
        AssertionOperator.COUNT -> {
            val count = actual.count
            val wanted = expected.intValue
            if (count == null || wanted == null) false else count == wanted.toInt()
        }

        AssertionOperator.CONTAINS -> {
            val array = actual.arrayValue ?: return false
            array.any { equal(it, expected) }
        }

        AssertionOperator.NOT_CONTAINS -> {
            val array = actual.arrayValue ?: return false
            array.none { equal(it, expected) }
        }

        AssertionOperator.UNORDERED_EQUALS -> unorderedEqual(actual, expected)
        AssertionOperator.IS_NULL -> actual.isNull == (expected.boolValue ?: true)
        AssertionOperator.NOT_NULL -> (!actual.isNull) == (expected.boolValue ?: true)
        AssertionOperator.ONE_OF -> {
            val choices = expected.arrayValue ?: return false
            choices.any { equal(it, actual) }
        }

        AssertionOperator.MATCHES -> matches(actual, expected)
        AssertionOperator.GT -> compare(actual, expected) == 1
        AssertionOperator.GTE -> (compare(actual, expected) ?: return false) != -1
        AssertionOperator.LT -> compare(actual, expected) == -1
        AssertionOperator.LTE -> (compare(actual, expected) ?: return false) != 1
        AssertionOperator.CLOSE_TO -> {
            val members = expected.objectValue ?: return false
            val target = members["value"]?.doubleValue ?: return false
            val tolerance = members["tolerance"]?.doubleValue ?: return false
            val value = actual.doubleValue ?: return false
            Math.abs(value - target) <= tolerance
        }
    }

    /**
     * Deep equality with one refinement over `ReflexValue.==`: two instant strings compare as
     * INSTANTS, so a fractional-second spelling in a fixture still equals the canonical
     * seconds-precision form the runner emits (SCHEMA.md#operators).
     */
    fun equal(lhs: ReflexValue, rhs: ReflexValue): Boolean {
        val leftText = lhs.stringValue
        val rightText = rhs.stringValue
        if (leftText != null && rightText != null) {
            val leftInstant = Instant.parse(leftText)
            val rightInstant = Instant.parse(rightText)
            if (leftInstant != null && rightInstant != null) {
                return leftInstant == rightInstant
            }
        }
        val leftArray = lhs.arrayValue
        val rightArray = rhs.arrayValue
        if (leftArray != null && rightArray != null) {
            if (leftArray.size != rightArray.size) return false
            return leftArray.zip(rightArray).all { (a, b) -> equal(a, b) }
        }
        val leftObject = lhs.objectValue
        val rightObject = rhs.objectValue
        if (leftObject != null && rightObject != null) {
            if (leftObject.size != rightObject.size) return false
            return leftObject.all { (key, value) ->
                val other = rightObject[key] ?: return false
                equal(value, other)
            }
        }
        return lhs == rhs
    }

    /**
     * Multiset equality. Notification emission order is not contract, so this is the only
     * operator a journey may use on `notifications`.
     */
    private fun unorderedEqual(lhs: ReflexValue, rhs: ReflexValue): Boolean {
        val left = lhs.arrayValue ?: return false
        val right = rhs.arrayValue ?: return false
        if (left.size != right.size) return false
        val remaining = right.toMutableList()
        for (element in left) {
            val index = remaining.indexOfFirst { equal(it, element) }
            if (index < 0) return false
            remaining.removeAt(index)
        }
        return remaining.isEmpty()
    }

    private fun matches(actual: ReflexValue, expected: ReflexValue): Boolean {
        val text = actual.stringValue ?: return false
        val pattern = expected.stringValue ?: return false
        val regex = try {
            Regex(pattern)
        } catch (error: Exception) {
            return false
        }
        // Anchored: the whole string must match, whether or not the pattern spells ^ and $.
        return regex.matches(text)
    }

    private fun compare(lhs: ReflexValue, rhs: ReflexValue): Int? {
        val leftText = lhs.stringValue
        val rightText = rhs.stringValue
        if (leftText != null && rightText != null) {
            val leftInstant = Instant.parse(leftText)
            val rightInstant = Instant.parse(rightText)
            if (leftInstant != null && rightInstant != null) {
                return order(leftInstant.secondsSince1970, rightInstant.secondsSince1970)
            }
        }
        val left = lhs.doubleValue
        val right = rhs.doubleValue
        if (left != null && right != null) {
            return order(left, right)
        }
        return null
    }

    private fun order(lhs: Double, rhs: Double): Int = when {
        lhs < rhs -> -1
        lhs > rhs -> 1
        else -> 0
    }
}
