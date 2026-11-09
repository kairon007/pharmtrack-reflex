import Core
import SwiftUI

struct VitalsScreen: View {
    @ObservedObject var app: ReflexApp
    var tourActive: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: VitalsData { app.vitals }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                headerView
                    .id("top")

                ScrollView {
                    VStack(spacing: 24) {
                        metricCards
                        trendCard
                        recentEntriesSection

                        if tourActive {
                            Color.clear.frame(height: 1).id("bottom")
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 24)
                }
            }
            .background(Theme.background(isDark))
            .modifier(OnboardingAutoScroll(active: tourActive, proxy: proxy, topAnchor: "top"))
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { data.detailPresented },
                set: { presented in
                    if !presented { app.send(.vitals(.closeDetail)) }
                }
            )
        ) {
            VitalsDetailScreen(app: app)
        }
    }

    private var headerView: some View {
        HStack {
            Text("Vitals")
                .font(.largeTitle)
                .fontWeight(.semibold)
                .foregroundColor(Theme.heading(isDark))
            Spacer()
            IconCircleButton(icon: .actionAdd, tint: Theme.primary, label: "Add reading") {
                app.send(.vitals(.openAdd))
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 16)
        .background(Theme.background(isDark))
    }

    // MARK: - Metric cards

    private var metricCards: some View {
        HStack(spacing: 12) {
            metricCard(.weight)
            metricCard(.bloodPressure)
            metricCard(.bloodSugar)
        }
    }

    private func metricCard(_ type: VitalType) -> some View {
        let isSelected = data.selectedMetric == type
        return Button {
            app.send(.vitals(.selectMetric(type)))
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(type.title)
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
                Text(data.cardValue(type))
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(type.unit)
                    .font(.caption)
                    .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Theme.surface(isDark))
            .cornerRadius(16)
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Theme.primary : Theme.border(isDark), lineWidth: isSelected ? 2 : 1)
            }
        }
    }

    // MARK: - Trend

    private var trendCard: some View {
        CardSurface(isDark: isDark) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(data.selectedMetric.title) trend")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(Theme.caption(isDark))
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(data.formattedLatestValue)
                                .font(.title)
                                .fontWeight(.semibold)
                                .foregroundColor(Theme.heading(isDark))
                            Text(data.selectedMetric.unit)
                                .font(.subheadline)
                                .foregroundColor(Theme.caption(isDark))
                        }
                    }

                    Spacer()

                    if let trend = data.trend {
                        HStack(spacing: 4) {
                            AppIcon(trend.icon, size: 14, tint: trend.color)
                            Text(trend.magnitude)
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(trend.color)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(trend.color.opacity(0.1))
                        .cornerRadius(8)
                    }
                }

                if data.ascending.count > 1 {
                    TrendChartShape(data: data.ascending.map(\.value))
                        .stroke(Theme.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                        .frame(height: 120)
                } else {
                    Text(data.ascending.isEmpty ? "Add a reading to see your trend" : "Add another reading to see your trend")
                        .font(.subheadline)
                        .foregroundColor(Theme.caption(isDark))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: 120)
                }
            }
        }
    }

    // MARK: - Recent entries

    private var recentEntriesSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionHeader(
                title: "Recent readings",
                actionTitle: "View all",
                action: { app.send(.vitals(.openDetail)) },
                isDark: isDark
            )

            if data.ascending.isEmpty {
                EmptyStateCard(
                    icon: .tabVitals,
                    title: "No readings yet",
                    message: "Log your first \(data.selectedMetric.title.lowercased()) reading to start tracking.",
                    isDark: isDark
                )
            } else {
                VStack(spacing: 12) {
                    ForEach(data.recent) { vital in
                        vitalRow(vital)
                    }
                }
            }
        }
    }

    private func vitalRow(_ vital: Vital) -> some View {
        HStack(spacing: 16) {
            // D4. Per-type semantic icon from the machine's rowIcon map.
            AppIcon(SemanticIcon(vitalName: data.iconName(vital.id)), size: 20, tint: Theme.primary)
                .frame(width: 44, height: 44)
                .background(isDark ? Theme.backgroundDark : Theme.slate50)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(ValueFormat.reading(vital))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Text(DateFormatter.localizedString(from: vital.date.asDate, dateStyle: .short, timeStyle: .short))
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
            }

            Spacer()

            if let notes = vital.notes, !notes.isEmpty {
                AppIcon(.contentInfo, size: 16, tint: isDark ? Theme.slate500 : Theme.slate400)
            }

            IconCircleButton(icon: .actionDelete, tint: Theme.error, label: "Delete reading") {
                app.send(.vitals(.delete(vital.id)))
            }
        }
        .padding(16)
        .background(Theme.surface(isDark))
        .cornerRadius(16)
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border(isDark), lineWidth: 1)
        }
    }
}