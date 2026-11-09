import SwiftUI

struct DashboardScreen: View {
    @ObservedObject var app: ReflexApp
    var tourActive: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: DashboardData { app.dashboard }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 32) {
                    headerView
                        .id("top")
                    todaySection
                    quickActionsSection
                    upcomingSection
                    historySection

                    if tourActive {
                        Color.clear.frame(height: 1).id("bottom")
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 24)
            }
            .background(Theme.background(isDark))
            .modifier(OnboardingAutoScroll(active: tourActive, proxy: proxy, topAnchor: "top"))
        }
    }

    // MARK: - Header

    private var headerView: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(data.greeting)
                    .font(.subheadline)
                    .foregroundColor(Theme.caption(isDark))
                Text("Today")
                    .font(.largeTitle)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
            }

            Spacer()

            Button {
                app.send(.dashboard(.presentSheet(.settings)))
            } label: {
                AppIcon(.actionSettings, size: 20, tint: isDark ? Theme.slate300 : Theme.slate600)
                    .frame(width: 44, height: 44)
                    .background(Theme.surface(isDark))
                    .clipShape(Circle())
            }
            .accessibilityLabel("Notification settings")
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
    }

    // MARK: - Hero

    @ViewBuilder
    private var todaySection: some View {
        VStack(alignment: .leading, spacing: 16) {
            switch data.heroBranch {
            case .meds:
                medsCard
            case .appointment:
                if let appointment = data.upcoming.first {
                    appointmentCard(appointment)
                }
            case .empty:
                EmptyStateCard(
                    icon: .actionAddCircle,
                    title: "Start your health log",
                    message: "Add a medication or an appointment and your day shows up here.",
                    isDark: isDark
                )
            }
        }
        .padding(.horizontal, 16)
    }

    private var medsCard: some View {
        CardSurface(isDark: isDark) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Today's plan")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.caption(isDark))
                    Spacer()
                    Text(data.takenLabel)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.primary)
                }

                ProgressBar(fraction: data.takenFraction, isDark: isDark)

                if let medication = data.nextMedication {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Next dose")
                            .font(.caption)
                            .foregroundColor(Theme.caption(isDark))
                        Text("\(medication.name) · \(medication.dosage)")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .foregroundColor(Theme.heading(isDark))
                        Text(medication.instructions)
                            .font(.caption)
                            .foregroundColor(Theme.caption(isDark))
                    }
                    .padding(.top, 8)

                    // D6. The reducer guards a repeat tap; the view only reflects the flag.
                    PrimaryButton(
                        title: "Mark taken",
                        icon: .actionConfirm,
                        enabled: !data.nextMedIsTaken
                    ) {
                        app.send(.dashboard(.markNextTaken))
                    }
                }
            }
        }
    }

    private func appointmentCard(_ appointment: Appointment) -> some View {
        Button {
            app.send(.dashboard(.tapHeroAppointment(appointment.id)))
        } label: {
            CardSurface(isDark: isDark) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Next appointment")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.caption(isDark))

                    Text(appointment.doctorName)
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.heading(isDark))

                    HStack(spacing: 16) {
                        Label(
                            title: { Text(DateFormatter.localizedString(from: appointment.date.asDate, dateStyle: .short, timeStyle: .short)) },
                            icon: { AppIcon(.contentTime, size: 16) }
                        )
                        Label(
                            title: { Text(appointment.location) },
                            icon: { AppIcon(.contentLocation, size: 16) }
                        )
                    }
                    .font(.caption)
                    .foregroundColor(Theme.caption(isDark))
                }
            }
        }
    }

    // MARK: - Quick actions

    private var quickActionsSection: some View {
        HStack(spacing: 12) {
            quickActionTile("Doctor", .tabConsults, .doctor) { app.send(.consultations(.openAdd)) }
            quickActionTile("Meds", .tabMeds, .meds) { app.send(.medicationTracker(.openAdd)) }
            quickActionTile("Visit", .tabCalendar, .visit) { app.send(.appointments(.openAdd)) }
            quickActionTile("Vitals", .tabVitals, .vitals) { app.send(.vitals(.openAdd)) }
        }
        .padding(.horizontal, 16)
    }

    // The sheet lives at RootView, bound to the form machine; quickAction records the
    // dashboard contract state and openAdd opens the owning form in one tap.
    private func quickActionTile(
        _ title: String,
        _ icon: SemanticIcon,
        _ kind: QuickActionKind,
        openForm: @escaping () -> Void
    ) -> some View {
        Button {
            app.send(.dashboard(.quickAction(kind)))
            openForm()
        } label: {
            VStack(spacing: 8) {
                AppIcon(icon, size: 20, tint: Theme.primary)
                Text(title)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(isDark ? Theme.slate200 : Theme.slate700)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Theme.surface(isDark))
            .cornerRadius(16)
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Theme.border(isDark), lineWidth: 1)
            }
        }
    }

    // MARK: - Upcoming

    private var upcomingSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionHeader(
                title: "Upcoming",
                actionTitle: "Calendar",
                action: { app.send(.dashboard(.tapUpcomingHeader)) },
                isDark: isDark
            )
            .padding(.horizontal, 16)

            if data.upcoming.isEmpty {
                EmptyStateCard(
                    icon: .tabCalendar,
                    title: "No upcoming appointments",
                    message: "Add a visit and it will appear here before the day comes.",
                    isDark: isDark
                )
                .padding(.horizontal, 16)
            } else {
                VStack(spacing: 12) {
                    ForEach(data.upcoming) { appointment in
                        appointmentRow(appointment)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func appointmentRow(_ appointment: Appointment) -> some View {
        Button {
            app.send(.dashboard(.tapUpcomingRow(appointment.id)))
        } label: {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(DateFormatter.localizedString(from: appointment.date.asDate, dateStyle: .none, timeStyle: .short))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.primary)
                    Text(DateFormatter.localizedString(from: appointment.date.asDate, dateStyle: .short, timeStyle: .none))
                        .font(.caption)
                        .foregroundColor(Theme.caption(isDark))
                }
                .frame(width: 72, alignment: .leading)

                RoundedRectangle(cornerRadius: 1)
                    .fill(Theme.border(isDark))
                    .frame(width: 2, height: 44)

                VStack(alignment: .leading, spacing: 2) {
                    Text(appointment.doctorName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.heading(isDark))
                        .lineLimit(1)
                    Text("\(appointment.type) · \(appointment.location)")
                        .font(.caption)
                        .foregroundColor(Theme.caption(isDark))
                        .lineLimit(1)
                }

                Spacer()

                AppIcon(.actionForward, size: 14, tint: isDark ? Theme.slate500 : Theme.slate400)
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

    // MARK: - Recent activity

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionHeader(
                title: "Recent activity",
                actionTitle: "View all",
                action: { app.send(.dashboard(.tapRecentActivityViewAll)) },
                isDark: isDark
            )
            .padding(.horizontal, 16)

            if data.recentLogs.isEmpty {
                EmptyStateCard(
                    icon: .contentTime,
                    title: "Nothing logged yet",
                    message: "Taking a medication or adding a reading will show up here.",
                    isDark: isDark
                )
                .padding(.horizontal, 16)
            } else {
                VStack(spacing: 12) {
                    ForEach(data.recentLogs) { log in
                        logRow(log)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func logRow(_ log: Log) -> some View {
        HStack(spacing: 16) {
            AppIcon(
                log.type == "Medication Taken" ? .actionDone : .contentLogEdited,
                size: 22,
                tint: Theme.primary
            )

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

            Text(log.source)
                .font(.caption)
                .foregroundColor(Theme.caption(isDark))
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