package com.pharmtrack.reflex.core.Support

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Swift-compatible number and string primitives.
 *
 * The emitted stream is byte-compared between the two platforms, so every spelling rule the
 * Swift standard library applies has to be reproduced exactly here. Kotlin's own
 * `Double.toString`, `toDoubleOrNull` and `String.format` all differ from their Swift
 * counterparts in ways that would show up in the diff.
 */
object SwiftNumber {

    /**
     * Swift's `String(Double)`: the shortest decimal form that round-trips.
     *
     * Swift switches to exponential notation outside the decimal exponent range -4...15 and
     * always writes an explicit sign and at least two exponent digits ("1e+16", "1e-05").
     * `java.lang.Double.toString` uses a different range ("1.0E15") and a different exponent
     * spelling, so the digits are re-laid out here.
     */
    fun doubleString(value: Double): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value < 0) "-inf" else "inf"
        if (value == 0.0) return if (1.0 / value < 0) "-0.0" else "0.0"

        val raw = java.lang.Double.toString(value)
        val negative = raw.startsWith("-")
        val body = if (negative) raw.substring(1) else raw

        val exponentMarker = body.indexOf('E')
        val explicitExponent = if (exponentMarker >= 0) body.substring(exponentMarker + 1).toInt() else 0
        val mantissa = if (exponentMarker >= 0) body.substring(0, exponentMarker) else body

        val dot = mantissa.indexOf('.')
        val integerPart = if (dot >= 0) mantissa.substring(0, dot) else mantissa
        val fractionPart = if (dot >= 0) mantissa.substring(dot + 1) else ""

        var digits = integerPart + fractionPart
        var point = integerPart.length + explicitExponent

        // Leading zeros shrink the value's magnitude, so the point moves with them.
        var leading = 0
        while (leading < digits.length && digits[leading] == '0') leading++
        if (leading > 0) {
            digits = digits.substring(leading)
            point -= leading
        }
        // Trailing zeros are dropped without moving the point.
        var trailing = digits.length
        while (trailing > 1 && digits[trailing - 1] == '0') trailing--
        digits = digits.substring(0, trailing)

        val decimalExponent = point - 1
        val bodyOut = if (decimalExponent in -4..15) {
            if (decimalExponent >= 0) {
                // The integer part is the leading digits, zero-padded when the shortest form
                // has fewer of them ("12" -> "120"); the fraction is whatever is left over.
                val whole = digits.take(decimalExponent + 1).padEnd(decimalExponent + 1, '0')
                val fraction = digits.drop(decimalExponent + 1)
                if (fraction.isEmpty()) "$whole.0" else "$whole.$fraction"
            } else {
                "0." + "0".repeat(-decimalExponent - 1) + digits
            }
        } else {
            val mantissaOut = if (digits.length > 1) digits.substring(0, 1) + "." + digits.substring(1) else digits
            val sign = if (decimalExponent < 0) "-" else "+"
            val magnitude = Math.abs(decimalExponent).toString().padStart(2, '0')
            mantissaOut + "e" + sign + magnitude
        }
        return if (negative) "-" + bodyOut else bodyOut
    }

    private val DECIMAL = Regex("^[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?$")
    private val HEX = Regex("^[+-]?0[xX](?:[0-9a-fA-F]+(?:\\.[0-9a-fA-F]*)?|\\.[0-9a-fA-F]+)(?:[pP][+-]?[0-9]+)?$")

    /**
     * Swift's `Double(String)`: no surrounding whitespace, accepts an exponent, hex-floats,
     * "inf", "infinity" and "nan" — and rejects everything else. `toDoubleOrNull` differs on
     * hex and on whitespace, so neither is used (Q7 / numeric_leniency).
     */
    fun parseDouble(text: String): Double? {
        if (text.isEmpty()) return null
        if (text.any { it.isWhitespace() }) return null

        when (text.lowercase()) {
            "inf", "+inf", "infinity", "+infinity" -> return Double.POSITIVE_INFINITY
            "-inf", "-infinity" -> return Double.NEGATIVE_INFINITY
            "nan", "+nan", "-nan" -> return Double.NaN
        }

        if (!DECIMAL.matches(text) && !HEX.matches(text)) return null
        return try {
            java.lang.Double.parseDouble(text)
        } catch (error: NumberFormatException) {
            null
        }
    }

    /**
     * Swift's `Int(String)`: strict, no whitespace, no decimal point, no exponent, no
     * underscores (Q6). A leading "+" is accepted; a leading zero is decimal, never octal.
     */
    fun parseInt(text: String): Long? {
        if (!INT_PATTERN.matches(text)) return null
        return try {
            java.lang.Long.parseLong(text)
        } catch (error: NumberFormatException) {
            null
        }
    }

    private val INT_PATTERN = Regex("^[+-]?[0-9]+$")

    /**
     * D10 numeric_prefill — the shortest round-trip decimal form, NOT the display format.
     * 78.2 -> "78.2", 120.0 -> "120". Exponent spelling is normalized so Swift's "1e+20"
     * and the raw "1.0E20" both become "1e20".
     */
    fun shortestDecimal(value: Double): String {
        if (value == truncate(value) && Math.abs(value) < 1e15) return value.toLong().toString()
        if (!value.isFinite()) {
            if (value.isNaN()) return "nan"
            return if (value < 0) "-inf" else "inf"
        }
        var text = doubleString(value)
        val index = text.indexOfFirst { it == 'e' || it == 'E' }
        if (index >= 0) {
            var mantissa = text.substring(0, index)
            var exponent = text.substring(index + 1)
            if (mantissa.endsWith(".0")) mantissa = mantissa.dropLast(2)
            var negative = false
            if (exponent.startsWith("-")) {
                negative = true
                exponent = exponent.drop(1)
            }
            if (exponent.startsWith("+")) exponent = exponent.drop(1)
            while (exponent.length > 1 && exponent.startsWith("0")) exponent = exponent.drop(1)
            text = mantissa + "e" + (if (negative) "-" else "") + exponent
        }
        return text
    }

    /**
     * D14 display rounding. Darwin's printf rounds the EXACT binary value half-to-even;
     * `String.format` rounds half-up and would disagree on ties such as 76.25.
     */
    fun fixed(value: Double, decimals: Int): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value < 0) "-inf" else "inf"
        return BigDecimal(value).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString()
    }

    /**
     * `Int(Double)` truncates toward zero (120.7 -> 120, 80.9 -> 80). The legacy initializer
     * traps on nan/infinity/out-of-range; this clamps instead so a leniently-accepted value
     * can never crash a render. No journey reaches the clamp.
     */
    fun truncatingInt(value: Double): Long {
        if (!value.isFinite()) {
            return if (value.isNaN()) 0 else if (value < 0) Long.MIN_VALUE else Long.MAX_VALUE
        }
        val truncated = truncate(value)
        if (truncated >= -9.223372036854776E18 && truncated < 9.223372036854776E18) {
            return truncated.toLong()
        }
        return if (truncated < 0) Long.MIN_VALUE else Long.MAX_VALUE
    }

    /** D11. Nearest week, ties away from zero: -4 days -> -1, -3 days -> 0, 43 days -> 6. */
    fun weekOffset(days: Int): Int = roundHalfAwayFromZero(days.toDouble() / 7.0).toInt()

    /** Truncation toward zero, matching Swift's `.rounded(.towardZero)`. */
    fun truncate(value: Double): Double = if (value < 0) Math.ceil(value) else Math.floor(value)

    /** Rounding to the nearest integer with ties away from zero, matching Swift's `.rounded()`. */
    fun roundHalfAwayFromZero(value: Double): Long = if (value < 0) -Math.round(-value) else Math.round(value)
}
