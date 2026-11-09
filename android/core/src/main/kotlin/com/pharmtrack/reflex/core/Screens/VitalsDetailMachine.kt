package com.pharmtrack.reflex.core.Screens

import com.pharmtrack.reflex.core.App.ActionArguments
import com.pharmtrack.reflex.core.App.Effect
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.Transition
import com.pharmtrack.reflex.core.Domain.Selectors
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.ReflexValue
import java.util.UUID

enum class VitalRange(val rawValue: String, val days: Int?) {
    WEEK("week", 7), MONTH("month", 30), QUARTER("quarter", 90), ALL("all", null);

    companion object {
        fun fromRaw(raw: String): VitalRange? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class VitalsDetailState(
    val metric: VitalType,
    val range: VitalRange,
    val weekOffset: Int,
    val selectedDate: Instant,
    val highlightedVitalId: UUID?
) {
    companion object {
        fun initial(metric: VitalType = VitalType.WEIGHT, now: Instant, zone: Instant.Zone) = VitalsDetailState(
            metric = metric,
            range = VitalRange.WEEK,
            weekOffset = 0,
            selectedDate = now.startOfDay(zone),
            highlightedVitalId = null
        )
    }
}

sealed class VitalsDetailAction {
    data class SelectMetric(val metric: VitalType) : VitalsDetailAction()

    data class SelectRange(val range: VitalRange) : VitalsDetailAction()

    data object PreviousWeek : VitalsDetailAction()

    data object NextWeek : VitalsDetailAction()

    data class SelectDay(val date: Instant) : VitalsDetailAction()

    data class TapReading(val id: UUID) : VitalsDetailAction()

    data object Close : VitalsDetailAction()

    /** Internal: the vitals tab opens the detail screen on the metric it had selected. */
    data class Open(val metric: VitalType) : VitalsDetailAction()

    companion object {
        fun decode(name: String, args: ActionArguments): VitalsDetailAction? = when (name) {
            "selectMetric" -> SelectMetric(
                VitalType.fromRaw(args.enumeration("metric", "VitalType", VitalType.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "selectRange" -> SelectRange(
                VitalRange.fromRaw(args.enumeration("range", "VitalRange", VitalRange.entries.map { it.rawValue }))
                    ?: throw IllegalStateException("unreachable")
            )

            "previousWeek" -> PreviousWeek
            "nextWeek" -> NextWeek
            "selectDay" -> SelectDay(args.instant("date"))
            "tapReading" -> TapReading(args.uuid("vitalId"))
            "close" -> Close
            else -> null
        }
    }
}

sealed class VitalsDetailMessage {
    data class HighlightClearFired(val id: UUID) : VitalsDetailMessage()

    companion object {
        fun decode(name: String, args: ActionArguments): VitalsDetailMessage? = when (name) {
            "highlightClearFired" -> HighlightClearFired(args.uuid("vitalId"))
            else -> null
        }
    }
}

object VitalsDetailMachine {

    const val HIGHLIGHT_KEY = "vitals_detail.highlight"
    const val HIGHLIGHT_SECONDS = 0.9

    fun reduce(
        state: VitalsDetailState,
        action: VitalsDetailAction,
        context: MachineContext
    ): Transition<VitalsDetailState> = when (action) {
        is VitalsDetailAction.SelectMetric ->
            // D11. Metric, range, week and selection are independent axes; none resets another.
            Transition(state.copy(metric = action.metric))

        is VitalsDetailAction.SelectRange -> Transition(state.copy(range = action.range))

        is VitalsDetailAction.PreviousWeek -> Transition(state.copy(weekOffset = state.weekOffset - 1))

        is VitalsDetailAction.NextWeek -> Transition(state.copy(weekOffset = state.weekOffset + 1))

        is VitalsDetailAction.SelectDay ->
            Transition(state.copy(selectedDate = action.date.startOfDay(context.zone)))

        is VitalsDetailAction.TapReading -> {
            val vital = context.data.vital(action.id)
            if (vital == null) {
                Transition(state)
            } else {
                val day = vital.date.startOfDay(context.zone)
                Transition(
                    state.copy(
                        highlightedVitalId = action.id,
                        selectedDate = day,
                        weekOffset = ValueFormat.weekOffset(
                            day.dayIndex(context.zone) - context.now.dayIndex(context.zone)
                        )
                    ),
                    listOf(
                        Effect.After(
                            seconds = HIGHLIGHT_SECONDS,
                            key = HIGHLIGHT_KEY,
                            message = Message.ToVitalsDetail(VitalsDetailMessage.HighlightClearFired(action.id))
                        )
                    )
                )
            }
        }

        is VitalsDetailAction.Close -> Transition(state.copy(highlightedVitalId = null))

        is VitalsDetailAction.Open ->
            Transition(VitalsDetailState.initial(action.metric, context.now, context.zone))
    }

    fun reduce(
        state: VitalsDetailState,
        message: VitalsDetailMessage,
        context: MachineContext
    ): Transition<VitalsDetailState> = when (message) {
        is VitalsDetailMessage.HighlightClearFired ->
            // D2. A stale timer must not clear a newer highlight.
            Transition(
                if (state.highlightedVitalId == message.id) state.copy(highlightedVitalId = null) else state
            )
    }

    /** A ROLLING instant (now - days*86400), not startOfDay, compared inclusively. */
    fun cutoff(state: VitalsDetailState, context: MachineContext): Instant? {
        val days = state.range.days ?: return null
        return context.now.addingSeconds(-days.toDouble() * 86_400.0)
    }

    fun series(state: VitalsDetailState, context: MachineContext): List<Vital> {
        val limit = cutoff(state, context)
        return Selectors.sortedByDateAndId(
            context.data.vitals.filter { vital ->
                if (vital.type != state.metric) return@filter false
                if (limit == null) return@filter true
                vital.date >= limit
            },
            date = { it.date },
            id = { it.id }
        )
    }

    fun emit(state: VitalsDetailState, context: MachineContext): ReflexValue {
        val ascending = series(state, context)
        val window = Selectors.dayWindow(context.now, state.weekOffset, context.zone)
        val readingDays = ascending.map { it.date.dayIndex(context.zone) }.toSet()
        val dots = window.filter { readingDays.contains(it.dayIndex(context.zone)) }
        val selectedIndex = state.selectedDate.dayIndex(context.zone)
        val selectedDay = ascending.filter { it.date.dayIndex(context.zone) == selectedIndex }
        // value2 is never charted — `value` IS systolic for blood pressure.
        val chartValues = ascending.map { it.value }
        val trend = Selectors.trend(ascending)
        return ReflexValue.of(
            mapOf(
                "metric" to ReflexValue.Text(state.metric.rawValue),
                "range" to ReflexValue.Text(state.range.rawValue),
                "weekOffset" to ReflexValue.of(state.weekOffset.toLong()),
                "selectedDate" to ReflexValue.instant(state.selectedDate),
                "highlightedVitalId" to ReflexValue.optionalUUID(state.highlightedVitalId),
                "rangeDays" to (state.range.days?.let { ReflexValue.of(it.toLong()) } ?: ReflexValue.Null),
                "cutoff" to ReflexValue.optionalInstant(cutoff(state, context)),
                "filteredVitals" to ReflexValue.uuids(ascending.map { it.id }),
                "stripCenter" to ReflexValue.instant(
                    Selectors.stripCenter(context.now, state.weekOffset, context.zone)
                ),
                "stripDates" to ReflexValue.of(window.map { ReflexValue.instant(it) }),
                "stripDotDates" to ReflexValue.of(dots.map { ReflexValue.instant(it) }),
                "selectedDayIds" to ReflexValue.uuids(selectedDay.map { it.id }),
                "allReadingIds" to ReflexValue.uuids(ascending.asReversed().map { it.id }),
                "chartValues" to ReflexValue.of(chartValues.map { ReflexValue.of(it) }),
                "chartMin" to (chartValues.minOrNull()?.let { ReflexValue.of(it) } ?: ReflexValue.Null),
                "chartMax" to (chartValues.maxOrNull()?.let { ReflexValue.of(it) } ?: ReflexValue.Null),
                "trend" to (trend?.reflexValue ?: ReflexValue.Null),
                "selectedDayCount" to ReflexValue.of(selectedDay.size.toLong()),
                "totalCount" to ReflexValue.of(ascending.size.toLong()),
                "formattedValue" to ReflexValue.Text(ValueFormat.reading(ascending.lastOrNull()))
            )
        )
    }
}
