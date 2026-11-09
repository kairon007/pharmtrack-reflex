package com.pharmtrack.reflex.core.Support

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * A wall-clock instant, stored as seconds since the Unix epoch.
 *
 * Formatting is implemented arithmetically (Howard Hinnant's civil-date algorithms) rather
 * than through a formatter, so output is byte-identical on every platform and in every locale
 * — runner.yaml#canonicalization requires exactly `yyyy-MM-ddTHH:mm:ssZ` with no fractional
 * part. `java.time` is deliberately not used: its formatter and its instant arithmetic would
 * introduce precision and rounding the Swift Core does not have.
 */
@Serializable(with = InstantSerializer::class)
class Instant(val secondsSince1970: Double) : Comparable<Instant> {

    constructor(secondsSince1970: Long) : this(secondsSince1970.toDouble())

    fun addingSeconds(seconds: Double): Instant = Instant(secondsSince1970 + seconds)

    fun addingDays(days: Int): Instant = addingSeconds(days.toDouble() * 86_400.0)

    fun timeIntervalSince(other: Instant): Double = secondsSince1970 - other.secondsSince1970

    /**
     * The pinned zone for the suite (fixtures/determinism.yaml). Only a fixed offset is
     * modelled — no DST rules are needed, and none may leak into a reducer.
     */
    class Zone(val offsetSeconds: Int) {
        companion object {
            val utc = Zone(0)
        }
    }

    fun startOfDay(zone: Zone): Instant {
        val shifted = secondsSince1970 + zone.offsetSeconds.toDouble()
        val dayStart = Math.floor(shifted / 86_400.0) * 86_400.0
        return Instant(dayStart - zone.offsetSeconds.toDouble())
    }

    /** Days since the epoch in the given zone — never consults the host timezone. */
    fun dayIndex(zone: Zone): Int = Math.floor((secondsSince1970 + zone.offsetSeconds.toDouble()) / 86_400.0).toInt()

    fun hour(zone: Zone): Int {
        val shifted = Math.floor(secondsSince1970).toInt() + zone.offsetSeconds
        val secondOfDay = ((shifted % 86_400) + 86_400) % 86_400
        return secondOfDay / 3_600
    }

    val iso8601: String
        get() {
            val whole = Math.floor(secondsSince1970)
            val days = Math.floor(whole / 86_400.0).toInt()
            val secondOfDay = whole.toInt() - days * 86_400

            val (year, month, day) = civilFromDays(days)
            val hour = secondOfDay / 3_600
            val minute = (secondOfDay % 3_600) / 60
            val second = secondOfDay % 60

            val yearText = if (year < 0) "-" + pad(-year, 4) else pad(year, 4)
            return yearText + "-" + pad(month, 2) + "-" + pad(day, 2) + "T" +
                pad(hour, 2) + ":" + pad(minute, 2) + ":" + pad(second, 2) + "Z"
        }

    override fun compareTo(other: Instant): Int = secondsSince1970.compareTo(other.secondsSince1970)

    override fun equals(other: Any?): Boolean = other is Instant && other.secondsSince1970 == secondsSince1970

    override fun hashCode(): Int = secondsSince1970.hashCode()

    companion object {
        val epoch = Instant(0.0)
        val distantFuture = Instant(253_402_300_799.0)
        val distantPast = Instant(-62_135_596_800.0)

        fun parse(text: String): Instant? {
            val scalars = text.map { it.code }
            if (scalars.size < 20) return null

            fun digits(range: IntRange): Int? {
                var value = 0
                for (index in range) {
                    if (index >= scalars.size) return null
                    val scalar = scalars[index]
                    if (scalar < 48 || scalar > 57) return null
                    value = value * 10 + (scalar - 48)
                }
                return value
            }

            var yearNegative = false
            var cursor = 0
            if (scalars[0] == '-'.code) {
                yearNegative = true
                cursor = 1
            }
            val yearRaw = digits(cursor until cursor + 4) ?: return null
            cursor += 4
            if (scalars[cursor] != '-'.code) return null
            cursor += 1
            val month = digits(cursor until cursor + 2) ?: return null
            if (month < 1 || month > 12) return null
            cursor += 2
            if (scalars[cursor] != '-'.code) return null
            cursor += 1
            val day = digits(cursor until cursor + 2) ?: return null
            if (day < 1 || day > 31) return null
            cursor += 2
            if (cursor >= scalars.size) return null
            val separator = scalars[cursor]
            if (separator != 'T'.code && separator != 't'.code && separator != ' '.code) return null
            cursor += 1
            val hour = digits(cursor until cursor + 2) ?: return null
            if (hour > 23) return null
            cursor += 2
            if (cursor >= scalars.size || scalars[cursor] != ':'.code) return null
            cursor += 1
            val minute = digits(cursor until cursor + 2) ?: return null
            if (minute > 59) return null
            cursor += 2
            if (cursor >= scalars.size || scalars[cursor] != ':'.code) return null
            cursor += 1
            val second = digits(cursor until cursor + 2) ?: return null
            if (second > 60) return null
            cursor += 2

            // Fractional seconds are accepted on read and dropped on write.
            if (cursor < scalars.size && scalars[cursor] == '.'.code) {
                cursor += 1
                val start = cursor
                while (cursor < scalars.size && scalars[cursor] >= 48 && scalars[cursor] <= 57) {
                    cursor += 1
                }
                if (cursor <= start) return null
            }

            var offsetSeconds = 0
            if (cursor < scalars.size) {
                val marker = scalars[cursor]
                if (marker == 'Z'.code || marker == 'z'.code) {
                    cursor += 1
                } else if (marker == '+'.code || marker == '-'.code) {
                    val sign = if (marker == '-'.code) -1 else 1
                    cursor += 1
                    val offsetHour = digits(cursor until cursor + 2) ?: return null
                    cursor += 2
                    var offsetMinute = 0
                    if (cursor < scalars.size) {
                        if (scalars[cursor] == ':'.code) cursor += 1
                        val parsed = digits(cursor until cursor + 2) ?: return null
                        offsetMinute = parsed
                        cursor += 2
                    }
                    offsetSeconds = sign * (offsetHour * 3_600 + offsetMinute * 60)
                } else {
                    return null
                }
            }
            if (cursor != scalars.size) return null

            val year = if (yearNegative) -yearRaw else yearRaw
            val days = daysFromCivil(year, month, day)
            val seconds = days * 86_400 + hour * 3_600 + minute * 60 + second - offsetSeconds
            return Instant(seconds.toDouble())
        }

        fun pad(value: Int, width: Int): String {
            var text = value.toString()
            while (text.length < width) text = "0" + text
            return text
        }

        /** Howard Hinnant, "chrono-Compatible Low-Level Date Algorithms". */
        fun civilFromDays(z0: Int): Triple<Int, Int, Int> {
            val z = z0 + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + (if (mp < 10) 3 else -9)
            return Triple(y + (if (m <= 2) 1 else 0), m, d)
        }

        fun daysFromCivil(year: Int, month: Int, day: Int): Int {
            val y = year - (if (month <= 2) 1 else 0)
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = month + (if (month > 2) -3 else 9)
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.pharmtrack.reflex.core.Support.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.iso8601)

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return Instant.parse(text)
            ?: throw SerializationException("Expected ISO8601 UTC instant, got $text")
    }
}
