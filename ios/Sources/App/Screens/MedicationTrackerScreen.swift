import SwiftUI

struct MedicationTrackerScreen: View {
    @ObservedObject var app: ReflexApp
    var tourActive: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: MedicationTrackerData { app.medicationTracker }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                headerView

                ScrollView {
                    VStack(spacing: 24) {
                        switch data.segment {
                        case .today:
                            planSummaryCard
                            routineSection(.morning)
                            routineSection(.afternoon)
                            routineSection(.evening)
                        case .inventory:
                            inventorySection
                        case .history:
                            historySection
                        }

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
            .modifier(OnboardingAutoScroll(active: tourActive, proxy: proxy, topAnchor: "Morning"))
        }
    }

    // MARK: - Header

    private var headerView: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Medications")
                    .font(.largeTitle)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Spacer()
                IconCircleButton(
                    icon: .actionAdd,
                    tint: Theme.primary,
                    label: "Add medication"
                ) {
                    app.send(.medicationTracker(.openAdd))
                }
            }

            HStack {
                AppIcon(.actionSearch, size: 20, tint: Theme.slate400)
                TextField(
                    "Search medications",
                    text: app.textBinding(data.searchQuery) { .medicationTracker(.setSearch($0)) }
                )
                .foregroundColor(Theme.heading(isDark))
            }
            .padding(12)
            .background(Theme.surface(isDark))
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Theme.border(isDark), lineWidth: 1)
            }

            HStack(spacing: 12) {
                segmentChip("Today", .today)
                segmentChip("Inventory", .inventory)
                segmentChip("History", .history)
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 16)
        .background(Theme.background(isDark))
    }

    private func segmentChip(_ title: String, _ segment: MedicationSegment) -> some View {
        FilterChip(title: title, isSelected: data.segment == segment, isDark: isDark) {
            app.send(.medicationTracker(.setSegment(segment)))
        }
    }

    // MARK: - Today

    private var planSummaryCard: some View {
        CardSurface(isDark: isDark) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(data.takenTodayCount)")
                        .font(.title)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.heading(isDark))
                    Text("of \(data.totalCount) taken today")
                        .font(.subheadline)
                        .foregroundColor(Theme.caption(isDark))
                    Spacer()
                    if data.allTaken {
                        AppIcon(.actionDone, size: 20, tint: Theme.success)
                    }
                }
                ProgressBar(fraction: data.takenFraction, isDark: isDark)
            }
        }
    }

    private func routineSection(_ schedule: MedicationSchedule) -> some View {
        let medications = data.group(schedule)
        return VStack(alignment: .leading, spacing: 12) {
            Text(schedule.rawValue)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(Theme.caption(isDark))
                .padding(.horizontal, 4)

            if medications.isEmpty {
                Text("Nothing scheduled")
                    .font(.caption)
                    .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                    .padding(.horizontal, 4)
            } else {
                VStack(spacing: 12) {
                    ForEach(medications) { medication in
                        medicationRow(medication)
                    }
                }
            }
        }
        .id(schedule.rawValue)
    }

    // D6. The whole row sends markTaken; the reducer no-ops a repeat tap.
    private func medicationRow(_ medication: Medication) -> some View {
        let isTaken = data.isTaken(medication.id)
        return Button {
            app.send(.medicationTracker(.markTaken(medication.id)))
        } label: {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .stroke(isTaken ? Theme.primary : Theme.border(isDark), lineWidth: 2)
                        .frame(width: 28, height: 28)
                    if isTaken {
                        AppIcon(.actionConfirm, size: 14, tint: Theme.primary)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(medication.name)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.heading(isDark))
                    Text("\(medication.dosage) · \(medication.instructions)")
                        .font(.caption)
                        .foregroundColor(Theme.caption(isDark))
                }

                Spacer()

                if data.isLowStock(medication.id) {
                    Text("Low stock")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(Theme.amber500)
                }

                IconCircleButton(icon: .actionEdit, tint: Theme.primary, label: "Edit \(medication.name)") {
                    app.send(.medicationTracker(.openEdit(medication.id)))
                }
            }
            .padding(16)
            .background(Theme.surface(isDark))
            .cornerRadius(16)
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isTaken ? Theme.primary.opacity(0.4) : Theme.border(isDark), lineWidth: 1)
            }
        }
    }

    // MARK: - Inventory

    private var inventorySection: some View {
        VStack(spacing: 12) {
            if data.filtered.isEmpty {
                EmptyStateCard(
                    icon: .tabMeds,
                    title: "No medications yet",
                    message: "Add a medication to start tracking your supply.",
                    isDark: isDark
                )
            } else {
                ForEach(data.filtered) { medication in
                    inventoryRow(medication)
                }
            }
        }
    }

    private func inventoryRow(_ medication: Medication) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(medication.name)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Text(medication.dosage)
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
            }

            Spacer()

            HStack(spacing: 8) {
                // icons.yaml has no bare minus glyph; action.remove stands in.
                IconCircleButton(icon: .actionRemove, tint: Theme.slate500, label: "Reduce stock of \(medication.name)") {
                    app.send(.medicationTracker(.decrementStock(medication.id)))
                }

                Text("\(medication.stock)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(data.isLowStock(medication.id) ? Theme.amber500 : Theme.heading(isDark))
                    .frame(minWidth: 32)

                IconCircleButton(icon: .actionAdd, tint: Theme.primary, label: "Add stock of \(medication.name)") {
                    app.send(.medicationTracker(.incrementStock(medication.id)))
                }
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

    // MARK: - History

    private var historySection: some View {
        VStack(spacing: 12) {
            if data.historyLogs.isEmpty {
                EmptyStateCard(
                    icon: .contentTime,
                    title: "No history yet",
                    message: "Doses you mark as taken will appear here.",
                    isDark: isDark
                )
            } else {
                ForEach(data.historyLogs) { log in
                    logRow(log)
                }
            }
        }
    }

    private func logRow(_ log: Log) -> some View {
        HStack(spacing: 16) {
            AppIcon(.actionDone, size: 22, tint: Theme.success)

            VStack(alignment: .leading, spacing: 2) {
                Text(log.description)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(Theme.heading(isDark))
                    .lineLimit(1)
                Text(DateFormatter.localizedString(from: log.date.asDate, dateStyle: .none, timeStyle: .short))
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
            }

            Spacer()
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