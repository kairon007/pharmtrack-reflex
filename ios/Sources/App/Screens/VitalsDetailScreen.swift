import SwiftUI

struct VitalsDetailScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: VitalsDetailData { app.vitalsDetail }

    var body: some View {
        VStack(spacing: 0) {
            headerView
            calendarStrip

            ScrollView {
                VStack(spacing: 20) {
                    metricFilterSection
                    timeRangeSection
                    chartSection
                    selectedDateSection
                    allReadingsSection
                }
                .padding(.bottom, 16)
            }
            .frame(maxHeight: .infinity)
        }
        .frame(maxHeight: .infinity)
        .background(Theme.background(isDark))
    }

    // MARK: - Header

    private var headerView: some View {
        HStack {
            Button {
                app.send(.vitals(.closeDetail))
            } label: {
                HStack(spacing: 4) {
                    AppIcon(.actionBack, size: 18, tint: Theme.primary)
                    Text("Back")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
                .foregroundColor(Theme.primary)
                .frame(minHeight: 44)
            }

            Spacer()

            Text("All Vitals")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(Theme.heading(isDark))

            Spacer()

            AppIcon(.actionBack, size: 18, tint: .clear)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .background(Theme.background(isDark).opacity(0.95))
    }

    // MARK: - Filters

    private var metricFilterSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Metric")
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundColor(Theme.caption(isDark))
                .tracking(1.2)
                .padding(.horizontal, 16)

            HStack(spacing: 0) {
                metricChip(.weight)
                metricChip(.bloodPressure)
                metricChip(.bloodSugar)
            }
            .padding(4)
            .background(isDark ? Theme.slate800 : Theme.slate200)
            .cornerRadius(12)
            .padding(.horizontal, 16)
        }
    }

    private func metricChip(_ type: VitalType) -> some View {
        let isSelected = data.metric == type
        return Button {
            app.send(.vitalsDetail(.selectMetric(type)))
        } label: {
            Text(type.title)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(isSelected ? (isDark ? .white : Theme.primary) : Theme.caption(isDark))
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(isSelected ? (isDark ? Theme.slate700 : Color.white) : Color.clear)
                .cornerRadius(8)
        }
    }

    private var timeRangeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Time range")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Theme.caption(isDark))
                    .tracking(1.2)
                Spacer()
                Text(data.range.label)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.primary)
            }
            .padding(.horizontal, 16)

            HStack(spacing: 8) {
                ForEach(VitalRange.allCases, id: \.self) { range in
                    rangeChip(range)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func rangeChip(_ range: VitalRange) -> some View {
        let isSelected = data.range == range
        return Button {
            app.send(.vitalsDetail(.selectRange(range)))
        } label: {
            Text(range.chipLabel)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(isSelected ? .white : (isDark ? Theme.slate300 : Theme.slate600))
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(isSelected ? Theme.primary : (isDark ? Theme.slate800 : Theme.slate100))
                .cornerRadius(8)
        }
    }

    // MARK: - Calendar strip

    private var calendarStrip: some View {
        VStack(spacing: 12) {
            HStack {
                Text(DateFormatting.monthYear(data.stripCenter))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Spacer()
                HStack(spacing: 12) {
                    Button {
                        app.send(.vitalsDetail(.previousWeek))
                    } label: {
                        AppIcon(.actionBack, size: 18, tint: Theme.caption(isDark))
                            .frame(width: 44, height: 44)
                    }
                    Button {
                        app.send(.vitalsDetail(.nextWeek))
                    } label: {
                        AppIcon(.actionForward, size: 18, tint: Theme.caption(isDark))
                            .frame(width: 44, height: 44)
                    }
                }
            }
            .padding(.horizontal, 16)

            HStack(spacing: 8) {
                ForEach(data.stripDates, id: \.self) { date in
                    dayCell(date)
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
        .background(isDark ? Theme.slate900 : Color.white)
        .cornerRadius(16)
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(isDark ? Theme.slate800 : Theme.slate100, lineWidth: 1)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func dayCell(_ date: Instant) -> some View {
        let isSelected = data.isSelected(date)
        let hasReading = data.hasDot(date)
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                app.send(.vitalsDetail(.selectDay(date)))
            }
        } label: {
            VStack(spacing: 4) {
                Text(DateFormatting.weekday(date).uppercased())
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(isSelected ? .white.opacity(0.85) : (isDark ? Theme.slate500 : Theme.slate400))
                Text(DateFormatting.dayNumber(date))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(isSelected ? .white : Theme.heading(isDark))
                Circle()
                    .fill(hasReading ? Theme.primary : Color.clear)
                    .frame(width: 5, height: 5)
            }
            .frame(width: 44, height: 64)
            .background(isSelected ? Theme.primary : Color.clear)
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.clear : (isDark ? Theme.slate800 : Theme.slate200), lineWidth: 1)
            }
            .shadow(color: isSelected ? Theme.primary.opacity(0.3) : Color.clear, radius: 5, x: 0, y: 5)
        }
    }

    // MARK: - Chart

    private var chartSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(data.metric.detailLabel.uppercased())
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(Theme.caption(isDark))
                        .tracking(1.2)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(data.formattedLatestValue)
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(Theme.heading(isDark))
                        Text(data.metric.unit)
                            .font(.subheadline)
                            .foregroundColor(Theme.caption(isDark))
                    }
                }

                Spacer()

                if let trend = data.trend {
                    HStack(spacing: 4) {
                        AppIcon(trend.icon, size: 12)
                        Text(trend.magnitude)
                    }
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(trend.color)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(trend.color.opacity(0.1))
                    .cornerRadius(4)
                }
            }

            if data.chartValues.isEmpty {
                VStack(spacing: 8) {
                    AppIcon(.contentHistory, size: 32, tint: isDark ? Theme.slate700 : Theme.slate300)
                    Text("No readings yet for \(data.metric.detailLabel)")
                        .font(.caption)
                        .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 140)
            } else {
                ZStack(alignment: .topLeading) {
                    TrendChartShape(data: data.chartValues)
                        .stroke(Theme.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                        .frame(height: 140)
                        .background(
                            LinearGradient(
                                gradient: Gradient(colors: [Theme.primary.opacity(0.2), Theme.primary.opacity(0.0)]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .mask { TrendChartShape(closed: true, data: data.chartValues).fill(Color.black) }
                        )

                    if let maxValue = data.chartMax, let minValue = data.chartMin {
                        HStack {
                            Text(ValueFormat.fixed(minValue, decimals: 0))
                                .font(.caption2)
                                .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                            Spacer()
                            Text(ValueFormat.fixed(maxValue, decimals: 0))
                                .font(.caption2)
                                .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                        }
                        .padding(.horizontal, 4)
                        .offset(y: 145)
                    }
                }
                .frame(height: 170)
            }
        }
        .padding(20)
        .background(Theme.surface(isDark))
        .cornerRadius(20)
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(isDark ? Theme.slate800 : Theme.slate100, lineWidth: 1)
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Selected day

    private var selectedDateSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Readings for")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(Theme.caption(isDark))
                        .tracking(1.2)
                    Text(DateFormatter.localizedString(from: data.selectedDate.asDate, dateStyle: .full, timeStyle: .none))
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(Theme.heading(isDark))
                }
                Spacer()
                Text("\(data.selectedDayCount) entries")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.primary)
            }
            .padding(.horizontal, 16)

            if data.selectedDay.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 6) {
                        AppIcon(.tabCalendar, size: 24, tint: isDark ? Theme.slate600 : Theme.slate400)
                        Text("No readings on this day")
                            .font(.caption)
                            .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                    }
                    .padding(.vertical, 24)
                    Spacer()
                }
                .background(isDark ? Theme.slate900 : Color.white)
                .cornerRadius(12)
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isDark ? Theme.slate800 : Theme.slate100, lineWidth: 1)
                }
                .padding(.horizontal, 16)
            } else {
                VStack(spacing: 10) {
                    ForEach(data.selectedDay) { vital in
                        readingButton(vital)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // MARK: - All readings

    private var allReadingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("All readings")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Theme.caption(isDark))
                    .tracking(1.2)
                Spacer()
                Text("\(data.totalCount) total")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.primary)
            }
            .padding(.horizontal, 16)

            if data.allReadings.isEmpty {
                HStack {
                    Spacer()
                    Text("Tap + to add your first reading")
                        .font(.caption)
                        .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                        .padding(.vertical, 24)
                    Spacer()
                }
                .background(isDark ? Theme.slate900 : Color.white)
                .cornerRadius(12)
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isDark ? Theme.slate800 : Theme.slate100, lineWidth: 1)
                }
                .padding(.horizontal, 16)
            } else {
                VStack(spacing: 10) {
                    ForEach(data.allReadings) { vital in
                        readingButton(vital)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // D2. Highlight + auto-clear live in the machine's timer; the row only renders the id.
    private func readingButton(_ vital: Vital) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.3)) {
                app.send(.vitalsDetail(.tapReading(vital.id)))
            }
        } label: {
            vitalRow(vital)
        }
        .buttonStyle(.plain)
    }

    private func vitalRow(_ vital: Vital) -> some View {
        let isHighlighted = data.highlightedVitalId == vital.id
        return HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(isDark ? Theme.slate800 : Theme.slate50)
                    .frame(width: 44, height: 44)
                AppIcon(SemanticIcon(type: vital.type), size: 22, tint: Theme.primary)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(ValueFormat.reading(vital))
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(Theme.heading(isDark))
                Text(DateFormatter.localizedString(from: vital.date.asDate, dateStyle: .short, timeStyle: .short))
                    .font(.caption2)
                    .foregroundColor(Theme.caption(isDark))
            }

            Spacer()

            if let notes = vital.notes, !notes.isEmpty {
                AppIcon(.contentInfo, size: 16, tint: isDark ? Theme.slate500 : Theme.slate400)
            }

            AppIcon(.actionForward, size: 14, tint: isDark ? Theme.slate500 : Theme.slate400)
        }
        .padding(14)
        .background(isHighlighted ? Theme.primary.opacity(0.15) : (isDark ? Theme.slate900 : Color.white))
        .cornerRadius(12)
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    isHighlighted ? Theme.primary : (isDark ? Theme.slate800 : Theme.slate100),
                    lineWidth: isHighlighted ? 1.5 : 1
                )
        }
    }
}