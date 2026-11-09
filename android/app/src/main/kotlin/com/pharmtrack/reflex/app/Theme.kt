package com.pharmtrack.reflex.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Support.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The v0.0.19 palette, ported verbatim (SharedUI/Theme.swift). No platform branches.
object Theme {
    val primary = Color(0xFF0F766E)
    val secondary = Color(0xFF115E59)
    val backgroundLight = Color(0xFFF5F8F7)
    val backgroundDark = Color(0xFF101817)
    val surfaceLight = Color(0xFFFFFFFF.toInt())
    val surfaceDark = Color(0xFF1B2725)
    val borderLight = Color(0xFFE2EAE8)
    val borderDark = Color(0xFF2B3936)

    val slate50 = Color(0xFFF4F8F7)
    val slate100 = Color(0xFFEAF1EF)
    val slate200 = Color(0xFFDCE6E3)
    val slate300 = Color(0xFFC5D2CE)
    val slate400 = Color(0xFF82938E)
    val slate500 = Color(0xFF667873)
    val slate600 = Color(0xFF4B5D58)
    val slate700 = Color(0xFF374844)
    val slate800 = Color(0xFF263633)
    val slate900 = Color(0xFF1B2725)

    val amber500 = Color(0xFFF59E0B)
    val emerald500 = Color(0xFF10B981)
    val purple500 = Color(0xFFA855F7)
    val rose500 = Color(0xFFF43F5E)
    val blue500 = Color(0xFF3B82F6)

    val success = emerald500
    val warning = amber500
    val error = rose500
    val info = blue500

    fun background(isDark: Boolean): Color = if (isDark) backgroundDark else backgroundLight
    fun surface(isDark: Boolean): Color = if (isDark) surfaceDark else surfaceLight
    fun border(isDark: Boolean): Color = if (isDark) borderDark else borderLight
    fun heading(isDark: Boolean): Color = if (isDark) slate100 else slate900
    fun body(isDark: Boolean): Color = if (isDark) slate300 else slate600
    fun caption(isDark: Boolean): Color = if (isDark) slate400 else slate500
}

/**
 * The semantic icon contract (specs/contracts/icons.yaml). Exhaustive by construction:
 * there is no fallback drawable, so an unmapped name cannot silently render as a plus.
 */
enum class SemanticIcon(val drawable: Int) {
    TAB_HOME(R.drawable.ic_home),
    TAB_CONSULTS(R.drawable.ic_medical_services),
    TAB_MEDS(R.drawable.ic_pills),
    TAB_CALENDAR(R.drawable.ic_calendar),
    TAB_VITALS(R.drawable.ic_favorite),

    VITAL_WEIGHT(R.drawable.ic_monitor_weight),
    VITAL_BLOOD_PRESSURE(R.drawable.ic_favorite_border),
    VITAL_BLOOD_SUGAR(R.drawable.ic_water_drop),

    TREND_DOWN(R.drawable.ic_trending_down),
    TREND_UP(R.drawable.ic_trending_up),
    TREND_FLAT(R.drawable.ic_trending_flat),

    ACTION_ADD(R.drawable.ic_add),
    ACTION_ADD_CIRCLE(R.drawable.ic_add_circle),
    ACTION_REMOVE(R.drawable.ic_remove_circle),
    ACTION_DELETE(R.drawable.ic_delete),
    ACTION_EDIT(R.drawable.ic_edit),
    ACTION_CONFIRM(R.drawable.ic_check),
    ACTION_DONE(R.drawable.ic_check_circle),
    ACTION_SEARCH(R.drawable.ic_search),
    ACTION_SETTINGS(R.drawable.ic_notifications),
    ACTION_BACK(R.drawable.ic_arrow_back),
    ACTION_FORWARD(R.drawable.ic_arrow_forward),

    CONTENT_TIME(R.drawable.ic_schedule),
    CONTENT_LOCATION(R.drawable.ic_location_on),
    CONTENT_HISTORY(R.drawable.ic_bar_chart),
    CONTENT_INFO(R.drawable.ic_info),
    CONTENT_LOG_EDITED(R.drawable.ic_edit),

    ONBOARDING_SPARKLE(R.drawable.ic_auto_awesome);

    /** D4. Resolves the semantic names VitalsMachine.emit puts in `rowIcon`. */
    companion object {
        fun fromVitalName(name: String): SemanticIcon = when (name) {
            "weight" -> VITAL_WEIGHT
            "blood_pressure" -> VITAL_BLOOD_PRESSURE
            "blood_sugar" -> VITAL_BLOOD_SUGAR
            else -> throw IllegalArgumentException("icons.yaml contract violation: unknown vital icon '$name'")
        }

        fun fromVitalType(type: VitalType): SemanticIcon = when (type) {
            VitalType.WEIGHT -> VITAL_WEIGHT
            VitalType.BLOOD_PRESSURE -> VITAL_BLOOD_PRESSURE
            VitalType.BLOOD_SUGAR -> VITAL_BLOOD_SUGAR
        }

        fun fromTrendDirection(direction: String): SemanticIcon = when (direction) {
            "up" -> TREND_UP
            "down" -> TREND_DOWN
            "flat" -> TREND_FLAT
            else -> throw IllegalArgumentException("icons.yaml contract violation: unknown trend '$direction'")
        }
    }
}

@Composable
fun AppIcon(icon: SemanticIcon, size: Dp = 12.dp, tint: Color? = null) {
    Icon(
        painter = painterResource(icon.drawable),
        contentDescription = null,
        tint = tint ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size)
    )
}

// D15: rendered date strings are NOT contract — locale rendering is verified in the device
// pass. State stays in Instants; these formatters are presentation only.
object DateFormatting {

    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.getDefault())

    fun date(instant: Instant): Date = Date((instant.secondsSince1970 * 1000.0).toLong())

    fun time(instant: Instant): String = formatter("h:mm a").format(date(instant))

    fun weekday(instant: Instant): String = formatter("EEE").format(date(instant))

    fun dayNumber(instant: Instant): String = formatter("d").format(date(instant))

    fun monthYear(instant: Instant): String = formatter("MMMM yyyy").format(date(instant))

    fun fullDate(instant: Instant): String = formatter("EEEE, MMMM d").format(date(instant))

    fun mediumDate(instant: Instant): String = formatter("MMM d, yyyy").format(date(instant))

    fun logTimestamp(instant: Instant): String = formatter("MMM d, h:mm a").format(date(instant))
}

@Composable
fun ReflexTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Theme.primary,
            secondary = Theme.secondary,
            background = Theme.backgroundDark,
            surface = Theme.surfaceDark,
            onBackground = Theme.heading(true),
            onSurface = Theme.heading(true)
        )
    } else {
        lightColorScheme(
            primary = Theme.primary,
            secondary = Theme.secondary,
            background = Theme.backgroundLight,
            surface = Theme.surfaceLight,
            onBackground = Theme.heading(false),
            onSurface = Theme.heading(false)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
