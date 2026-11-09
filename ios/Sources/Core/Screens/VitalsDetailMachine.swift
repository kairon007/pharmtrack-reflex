import Foundation

public enum VitalRange: String, CaseIterable, Codable {
    case week, month, quarter, all

    public var days: Int? {
        switch self {
        case .week: return 7
        case .month: return 30
        case .quarter: return 90
        case .all: return nil
        }
    }
}

public struct VitalsDetailState: Equatable {
    public var metric: VitalType
    public var range: VitalRange
    public var weekOffset: Int
    public var selectedDate: Instant
    public var highlightedVitalId: UUID?

    public init(metric: VitalType = .weight, now: Instant, zone: Instant.Zone) {
        self.metric = metric
        range = .week
        weekOffset = 0
        selectedDate = now.startOfDay(in: zone)
        highlightedVitalId = nil
    }
}

public enum VitalsDetailAction {
    case selectMetric(VitalType)
    case selectRange(VitalRange)
    case previousWeek
    case nextWeek
    case selectDay(Instant)
    case tapReading(UUID)
    case close
    case open(VitalType)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> VitalsDetailAction? {
        switch name {
        case "selectMetric":
            return .selectMetric(try args.enumeration("metric", VitalType.self))
        case "selectRange":
            return .selectRange(try args.enumeration("range", VitalRange.self))
        case "previousWeek":
            return .previousWeek
        case "nextWeek":
            return .nextWeek
        case "selectDay":
            return .selectDay(try args.instant("date"))
        case "tapReading":
            return .tapReading(try args.uuid("vitalId"))
        case "close":
            return .close
        default:
            return nil
        }
    }
}

public enum VitalsDetailMessage {
    case highlightClearFired(UUID)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> VitalsDetailMessage? {
        switch name {
        case "highlightClearFired":
            return .highlightClearFired(try args.uuid("vitalId"))
        default:
            return nil
        }
    }
}

public enum VitalsDetailMachine {
    public static let highlightKey = "vitals_detail.highlight"
    public static let highlightSeconds = 0.9

    public static func reduce(
        _ state: VitalsDetailState,
        _ action: VitalsDetailAction,
        _ context: MachineContext
    ) -> (VitalsDetailState, [Effect]) {
        var state = state
        switch action {
        case let .selectMetric(metric):
        // D11. Metric, range, week and selection are independent axes; none resets another.
            state.metric = metric
            return (state, [])

        case let .selectRange(range):
            state.range = range
            return (state, [])

        case .previousWeek:
            state.weekOffset -= 1
            return (state, [])

        case .nextWeek:
            state.weekOffset += 1
            return (state, [])

        case let .selectDay(date):
            state.selectedDate = date.startOfDay(in: context.zone)
            return (state, [])

        case let .tapReading(id):
            guard let vital = context.data.vital(id) else { return (state, []) }
            state.highlightedVitalId = id
            let day = vital.date.startOfDay(in: context.zone)
            state.selectedDate = day
            state.weekOffset = ValueFormat.weekOffset(
                days: day.dayIndex(in: context.zone) - context.now.dayIndex(in: context.zone)
            )
            return (state, [.after(
                seconds: highlightSeconds,
                key: highlightKey,
                message: .vitalsDetail(.highlightClearFired(id))
            )])

        case .close:
            state.highlightedVitalId = nil
            return (state, [])

        case let .open(metric):
            return (VitalsDetailState(metric: metric, now: context.now, zone: context.zone), [])
        }
    }

    public static func reduce(
        _ state: VitalsDetailState,
        _ message: VitalsDetailMessage,
        _ context: MachineContext
    ) -> (VitalsDetailState, [Effect]) {
        var state = state
        switch message {
        case let .highlightClearFired(id):
        // D2. A stale timer must not clear a newer highlight.
            if state.highlightedVitalId == id { state.highlightedVitalId = nil }
            return (state, [])
        }
    }

    /// A ROLLING instant (now - days*86400), not startOfDay, compared inclusively.
    public static func cutoff(_ state: VitalsDetailState, _ context: MachineContext) -> Instant? {
        guard let days = state.range.days else { return nil }
        return context.now.addingSeconds(-Double(days) * 86_400)
    }

    public static func series(_ state: VitalsDetailState, _ context: MachineContext) -> [Vital] {
        let limit = cutoff(state, context)
        return Selectors.sortedByDateAndId(
            context.data.vitals.filter { vital in
                guard vital.type == state.metric else { return false }
                guard let limit else { return true }
                return vital.date >= limit
            },
            date: \.date,
            id: \.id
        )
    }

    public static func emit(_ state: VitalsDetailState, _ context: MachineContext) -> ReflexValue {
        let ascending = series(state, context)
        let window = Selectors.dayWindow(now: context.now, weekOffset: state.weekOffset, zone: context.zone)
        let readingDays = Set(ascending.map { $0.date.dayIndex(in: context.zone) })
        let dots = window.filter { readingDays.contains($0.dayIndex(in: context.zone)) }
        let selectedIndex = state.selectedDate.dayIndex(in: context.zone)
        let selectedDay = ascending.filter { $0.date.dayIndex(in: context.zone) == selectedIndex }
        // value2 is never charted — `value` IS systolic for blood pressure.
        let chartValues = ascending.map(\.value)
        let trend = Selectors.trend(ascending)
        var fields: [String: ReflexValue] = [:]
        fields["metric"] = .string(state.metric.rawValue)
        fields["range"] = .string(state.range.rawValue)
        fields["weekOffset"] = .int(state.weekOffset)
        fields["selectedDate"] = .instant(state.selectedDate)
        fields["highlightedVitalId"] = .optionalUUID(state.highlightedVitalId)
        fields["rangeDays"] = state.range.days.map { ReflexValue.int($0) } ?? .null
        fields["cutoff"] = .optionalInstant(cutoff(state, context))
        fields["filteredVitals"] = .uuids(ascending.map(\.id))
        fields["stripCenter"] = .instant(Selectors.stripCenter(now: context.now, weekOffset: state.weekOffset, zone: context.zone))
        fields["stripDates"] = .array(window.map { .instant($0) })
        fields["stripDotDates"] = .array(dots.map { .instant($0) })
        fields["selectedDayIds"] = .uuids(selectedDay.map(\.id))
        fields["allReadingIds"] = .uuids(ascending.reversed().map(\.id))
        fields["chartValues"] = .array(chartValues.map { .double($0) })
        fields["chartMin"] = chartValues.min().map { ReflexValue.double($0) } ?? .null
        fields["chartMax"] = chartValues.max().map { ReflexValue.double($0) } ?? .null
        fields["trend"] = trend?.reflexValue ?? .null
        fields["selectedDayCount"] = .int(selectedDay.count)
        fields["totalCount"] = .int(ascending.count)
        fields["formattedValue"] = .string(ValueFormat.reading(ascending.last))
        return .object(fields)
    }
}