import Core
import SwiftUI

struct AppointmentsScreen: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }
    private var data: AppointmentsData { app.appointments }

    var body: some View {
        VStack(spacing: 0) {
            headerView
            calendarStrip

            ScrollView {
                VStack(spacing: 20) {
                    HStack {
                        Text(data.headerText)
                            .font(.headline)
                            .foregroundColor(Theme.heading(isDark))
                        Spacer()
                    }
                    .padding(.horizontal, 16)

                    if data.visible.isEmpty {
                        Text("No appointments for \(DateFormatter.localizedString(from: data.emptyMessageDate.asDate, dateStyle: .medium, timeStyle: .none))")
                            .font(.subheadline)
                            .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
                            .padding(.top, 40)
                    } else {
                        ForEach(data.visible) { appointment in
                            appointmentCard(appointment)
                        }
                    }
                }
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
        }
        .background(Theme.background(isDark))
    }

    private var headerView: some View {
        HStack {
            Text("Appointments")
                .font(.largeTitle)
                .fontWeight(.semibold)
                .foregroundColor(Theme.heading(isDark))
            Spacer()
            IconCircleButton(
                icon: .actionAdd,
                tint: Theme.primary,
                label: "Add appointment"
            ) {
                app.send(.appointments(.openAdd))
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 16)
        .background(Theme.background(isDark).opacity(0.95))
    }

    private var calendarStrip: some View {
        VStack(spacing: 12) {
            HStack {
                Text(DateFormatting.monthYear(data.stripCenter))
                    .font(.headline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Spacer()
                HStack(spacing: 16) {
                    Button {
                        app.send(.appointments(.previousWeek))
                    } label: {
                        AppIcon(.actionBack, size: 20, tint: Theme.caption(isDark))
                            .frame(width: 44, height: 44)
                    }
                    Button {
                        app.send(.appointments(.nextWeek))
                    } label: {
                        AppIcon(.actionForward, size: 20, tint: Theme.caption(isDark))
                            .frame(width: 44, height: 44)
                    }
                }
            }
            .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(data.stripDates, id: \.self) { date in
                        dayCell(date)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.bottom, 16)
        .background(Theme.background(isDark))
    }

    private func dayCell(_ date: Instant) -> some View {
        let isSelected = data.isSelected(date)
        return Button {
            app.send(.appointments(.selectDay(date)))
        } label: {
            VStack(spacing: 4) {
                Text(DateFormatting.weekday(date).uppercased())
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(isSelected ? .white.opacity(0.8) : (isDark ? Theme.slate500 : Theme.slate400))
                Text(DateFormatting.dayNumber(date))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(isSelected ? .white : Theme.heading(isDark))
            }
            .frame(width: 48, height: 64)
            .background(isSelected ? Theme.primary : Theme.surface(isDark))
            .cornerRadius(12)
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.clear : Theme.border(isDark), lineWidth: 1)
            }
        }
    }

    private func appointmentCard(_ appointment: Appointment) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(spacing: 4) {
                Text(DateFormatter.localizedString(from: appointment.date.asDate, dateStyle: .none, timeStyle: .short))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.primary)
                    .lineLimit(1)
                Rectangle()
                    .fill(Theme.border(isDark))
                    .frame(width: 2)
                    .frame(maxHeight: .infinity)
            }
            .frame(width: 56)

            VStack(alignment: .leading, spacing: 6) {
                Text(appointment.doctorName)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.heading(isDark))
                Text(appointment.specialty)
                    .font(.subheadline)
                    .foregroundColor(Theme.caption(isDark))
                HStack(spacing: 4) {
                    AppIcon(.contentLocation, size: 14)
                    Text(appointment.location)
                        .font(.caption)
                        .lineLimit(1)
                }
                .foregroundColor(isDark ? Theme.slate500 : Theme.slate400)
            }

            Spacer()

            VStack(spacing: 8) {
                IconCircleButton(icon: .actionEdit, tint: Theme.primary, label: "Edit appointment") {
                    app.send(.appointments(.openEdit(appointment.id)))
                }
                IconCircleButton(icon: .actionDelete, tint: Theme.error, label: "Delete appointment") {
                    app.send(.appointments(.delete(appointment.id)))
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
        .padding(.horizontal, 16)
    }
}