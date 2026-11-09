package com.pharmtrack.reflex.core.Domain

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.SwiftNumber
import java.util.UUID

/**
 * Pure text/number helpers shared by the machines. No locale, no formatter objects —
 * every function here must produce the identical string on iOS and Android.
 */
object ValueFormat {

    /**
     * Swift's `Double(String)`: no surrounding whitespace, accepts exponent, hex-float,
     * "inf" and "nan" (Q7 / numeric_leniency). `toDoubleOrNull` differs on hex and on
     * whitespace, so the acceptance set is reproduced explicitly.
     */
    fun parseDouble(text: String): Double? = SwiftNumber.parseDouble(text)

    /** Swift's `Int(String)`: strict, no whitespace, no decimal point, no exponent (Q6). */
    fun parseInt(text: String): Long? = SwiftNumber.parseInt(text)

    /** D10 numeric_prefill — the shortest round-trip decimal form, NOT the display format. */
    fun shortestDecimal(value: Double): String = SwiftNumber.shortestDecimal(value)

    /** D14 display rounding. Darwin's printf rounds the EXACT binary value half-to-even. */
    fun fixed(value: Double, decimals: Int): String = SwiftNumber.fixed(value, decimals)

    /** `Int(Double)` truncates toward zero (120.7 -> 120, 80.9 -> 80), clamped, never trapping. */
    fun truncatingInt(value: Double): Long = SwiftNumber.truncatingInt(value)

    /** D14. No unit inside — the caller appends the unit exactly once. */
    fun vitalValue(vital: Vital): String = when (vital.type) {
        VitalType.WEIGHT -> fixed(vital.value, decimals = 1)
        VitalType.BLOOD_SUGAR -> fixed(vital.value, decimals = 0)
        VitalType.BLOOD_PRESSURE -> "${truncatingInt(vital.value)}/${truncatingInt(vital.value2 ?: 0.0)}"
    }

    fun unit(type: VitalType): String = when (type) {
        VitalType.WEIGHT -> "kg"
        VitalType.BLOOD_PRESSURE -> "mmHg"
        VitalType.BLOOD_SUGAR -> "mg/dL"
    }

    /** The Vitals tab and Vitals Detail headline: latest reading plus unit, "--" when empty. */
    fun reading(vital: Vital?): String {
        if (vital == null) return "--"
        return vitalValue(vital) + " " + unit(vital.type)
    }

    /**
     * Q12. First character of the first two space-separated tokens, uppercased. Repeated
     * and leading spaces collapse because the split drops empty subsequences.
     * "Dr. Sarah Jenkins" -> "DS", "Madonna" -> "M", "   " -> "?".
     */
    fun initials(name: String): String {
        val tokens = name.split(" ").filter { it.isNotEmpty() }
        val letters = tokens.take(2).map { it.substring(0, 1).uppercase() }
        val joined = letters.joinToString("")
        return if (joined.isEmpty()) "?" else joined
    }

    /** No night branch and no name — hours 0-11 are morning (DashboardView.swift:49-54). */
    fun greeting(hour: Int): String = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    /** D11. Nearest week, ties away from zero: -4 days -> -1, -3 days -> 0, 43 days -> 6. */
    fun weekOffset(days: Int): Int = SwiftNumber.weekOffset(days)
}

/**
 * The shared pure helpers from nodes.yaml#selectors, written once here and again in Swift.
 * Nothing in this file reads a clock, a service or a platform type — `now` and `zone` are
 * always arguments.
 */
object Selectors {

    /**
     * D16. Every ordering in the app is (date ascending, id ascending). "id ascending" is
     * lexicographic on the uppercase UUID string, which is also unsigned bit order.
     */
    fun <T> sortedByDateAndId(
        items: List<T>,
        date: (T) -> Instant,
        id: (T) -> UUID
    ): List<T> = items.sortedWith(
        Comparator { lhs, rhs ->
            val left = date(lhs)
            val right = date(rhs)
            when {
                left < right -> -1
                left > right -> 1
                else -> ReflexValue.uuidString(id(lhs)).compareTo(ReflexValue.uuidString(id(rhs)))
            }
        }
    )

    /**
     * D6. A dose counts as taken when a "Medication Taken" log exists for today AND either
     * carries this medicationId, or — for pre-1.0 logs with no id — mentions the medication's
     * CURRENT name as a case-sensitive substring. The substring collision between "Vitamin D"
     * and "Vitamin D3" is preserved behavior, not a bug to fix.
     */
    fun isTaken(
        medicationId: UUID,
        data: MedicalData,
        now: Instant,
        zone: Instant.Zone
    ): Boolean {
        val medication = data.medication(medicationId) ?: return false
        val today = now.dayIndex(zone)
        return data.logs.any { log ->
            if (log.type != "Medication Taken" || log.date.dayIndex(zone) != today) {
                false
            } else {
                val linked = log.medicationId
                if (linked != null) linked == medicationId else log.description.contains(medication.name)
            }
        }
    }

    /**
     * D8. One predicate for the Today-row badge, the Inventory tint and the planner, so the
     * badge and the notification can no longer disagree. Inclusive <=.
     */
    fun isLowStock(medication: Medication, settings: NotificationSettings): Boolean =
        medication.active && medication.stock <= settings.lowStockThreshold

    /**
     * D11. Seven cells centered on startOfDay(now) + weekOffset*7 days, no weekday anchor.
     * Shared by appointments and vitals_detail so the two strips cannot drift apart.
     */
    fun dayWindow(now: Instant, weekOffset: Int, zone: Instant.Zone): List<Instant> {
        val center = now.startOfDay(zone).addingDays(weekOffset * 7)
        return (-3..3).map { center.addingDays(it) }
    }

    fun stripCenter(now: Instant, weekOffset: Int, zone: Instant.Zone): Instant =
        now.startOfDay(zone).addingDays(weekOffset * 7)

    /** Case-insensitive substring, NOT trimmed. An empty query matches everything. */
    fun matches(text: String, query: String): Boolean =
        query.isEmpty() || text.lowercase().contains(query.lowercase())

    /**
     * Q17 / VitalsView.swift:51-72. Compares the LAST TWO of an ascending series.
     * null when fewer than two readings, null when the previous value is zero (guarded
     * division, never infinity). Blood pressure compares systolic only — `value` IS
     * systolic, so no special case is needed. Sentiment is down=good for EVERY metric.
     */
    fun trend(ascendingSeries: List<Vital>): Trend? {
        if (ascendingSeries.size < 2) return null
        val previous = ascendingSeries[ascendingSeries.size - 2].value
        val current = ascendingSeries[ascendingSeries.size - 1].value
        if (previous == 0.0) return null

        val difference = current - previous
        val percentage = difference / previous * 100
        val direction = when {
            difference < 0 -> Trend.Direction.DOWN
            difference > 0 -> Trend.Direction.UP
            else -> Trend.Direction.FLAT
        }
        return Trend(
            direction = direction,
            sentiment = direction.sentiment,
            magnitude = ValueFormat.fixed(Math.abs(percentage), decimals = 1) + "%"
        )
    }
}

data class Trend(
    val direction: Trend.Direction,
    val sentiment: Trend.Sentiment,
    val magnitude: String
) {
    enum class Direction(val rawValue: String) {
        UP("up"), DOWN("down"), FLAT("flat");

        /** Q17. Down is good for every metric, including blood pressure and weight. */
        val sentiment: Sentiment
            get() = when (this) {
                DOWN -> Sentiment.GOOD
                UP -> Sentiment.BAD
                FLAT -> Sentiment.NEUTRAL
            }
    }

    enum class Sentiment(val rawValue: String) {
        GOOD("good"), BAD("bad"), NEUTRAL("neutral")
    }

    val reflexValue: ReflexValue
        get() = ReflexValue.of(
            mapOf(
                "direction" to ReflexValue.Text(direction.rawValue),
                "sentiment" to ReflexValue.Text(sentiment.rawValue),
                "magnitude" to ReflexValue.Text(magnitude)
            )
        )
}
