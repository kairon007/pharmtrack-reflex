import Core
import SwiftUI

struct ConsultationsScreen: View {
    @ObservedObject var app: ReflexApp
    var tourActive: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: ConsultationsData { app.consultations }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                headerView
                    .id("top")

                ScrollView {
                    VStack(spacing: 16) {
                        HStack {
                            Text("Active treatments")
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(Theme.caption(isDark))
                            Spacer()
                            Text("\(data.totalCount) active")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundColor(Theme.primary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)

                        ForEach(data.rows) { consultation in
                            consultationCard(consultation)
                        }

                        if data.rows.isEmpty {
                            EmptyStateCard(
                                icon: .tabConsults,
                                title: "No consultations yet",
                                message: "Add a doctor to keep your treatments in one place.",
                                isDark: isDark
                            )
                            .padding(.horizontal, 16)
                        }

                        if tourActive {
                            Color.clear.frame(height: 1).id("bottom")
                        }
                    }
                    .padding(.bottom, 24)
                }
            }
            .background(Theme.background(isDark))
            .modifier(OnboardingAutoScroll(active: tourActive, proxy: proxy, topAnchor: "top"))
        }
    }

    private var headerView: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Consultations")
                    .font(.largeTitle)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Spacer()
                IconCircleButton(
                    icon: .actionAdd,
                    tint: Theme.primary,
                    label: "Add consultation"
                ) {
                    app.send(.consultations(.openAdd))
                }
            }

            HStack {
                AppIcon(.actionSearch, size: 20, tint: Theme.slate400)
                TextField(
                    "Search by doctor or specialty...",
                    text: app.textBinding(data.searchQuery) { .consultations(.setSearch($0)) }
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
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 16)
        .background(Theme.background(isDark))
    }

    private func consultationCard(_ consultation: Consultation) -> some View {
        Button {
            app.send(.consultations(.openEdit(consultation.id)))
        } label: {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Theme.primary.opacity(0.1))
                        .frame(width: 52, height: 52)
                    Text(data.initials(consultation.id))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.primary)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(consultation.doctorName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.heading(isDark))
                        .lineLimit(1)
                    Text(consultation.specialty)
                        .font(.caption)
                        .foregroundColor(Theme.primary)
                        .lineLimit(1)
                    Text("Last visit: \(DateFormatter.localizedString(from: consultation.date.asDate, dateStyle: .medium, timeStyle: .none))")
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
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}