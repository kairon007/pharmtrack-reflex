import SwiftUI

/// The shell: five tabs plus every form sheet, each bound directly to its machine's
/// presentation state. Sheets live HERE (not per tab) so the dashboard quick actions and
/// the tab "+" buttons present the same form through the same binding.
struct RootView: View {
    @ObservedObject var app: ReflexApp
    @Environment(\.colorScheme) private var colorScheme

    private var isDark: Bool { colorScheme == .dark }

    var body: some View {
        ZStack {
            TabView(
                selection: Binding(
                    get: { app.root.selectedTab },
                    set: { app.send(.appRoot(.selectTab($0))) }
                )
            ) {
                DashboardScreen(app: app, tourActive: app.root.isOnboarding && app.root.selectedTab == 0)
                    .tabItem { Label("Home", systemImage: SemanticIcon.tabHome.symbol) }
                    .tag(0)

                ConsultationsScreen(app: app, tourActive: app.root.isOnboarding && app.root.selectedTab == 1)
                    .tabItem { Label("Consults", systemImage: SemanticIcon.tabConsults.symbol) }
                    .tag(1)

                MedicationTrackerScreen(app: app, tourActive: app.root.isOnboarding && app.root.selectedTab == 2)
                    .tabItem { Label("Meds", systemImage: SemanticIcon.tabMeds.symbol) }
                    .tag(2)

                AppointmentsScreen(app: app)
                    .tabItem { Label("Calendar", systemImage: SemanticIcon.tabCalendar.symbol) }
                    .tag(3)

                VitalsScreen(app: app, tourActive: app.root.isOnboarding && app.root.selectedTab == 4)
                    .tabItem { Label("Vitals", systemImage: SemanticIcon.tabVitals.symbol) }
                    .tag(4)
            }
            .tint(Theme.primary)

            if app.root.isOnboarding && app.onboarding.visible {
                OnboardingOverlay(app: app)
            }
        }
        .onAppear { app.start() }
        .sheet(
            isPresented: formBinding(
                isOpen: { app.state.consultations.presentedForm != nil },
                close: { app.send(.consultations(.closeForm)) }
            ),
            onDismiss: { app.send(.dashboard(.dismissSheet)) }
        ) {
            ConsultationFormScreen(app: app)
        }
        .sheet(
            isPresented: formBinding(
                isOpen: { app.state.medicationTracker.formMode != .closed },
                close: { app.send(.medicationTracker(.closeForm)) }
            ),
            onDismiss: { app.send(.dashboard(.dismissSheet)) }
        ) {
            AddMedicationScreen(app: app)
        }
        .sheet(
            isPresented: formBinding(
                isOpen: { app.state.appointments.formMode != .closed },
                close: { app.send(.appointments(.closeForm)) }
            ),
            onDismiss: { app.send(.dashboard(.dismissSheet)) }
        ) {
            AddAppointmentScreen(app: app)
        }
        .sheet(
            isPresented: formBinding(
                isOpen: { app.state.vitals.formMode != .closed },
                close: { app.send(.vitals(.closeForm)) }
            ),
            onDismiss: { app.send(.dashboard(.dismissSheet)) }
        ) {
            AddVitalScreen(app: app)
        }
        .sheet(
            isPresented: formBinding(
                isOpen: { app.state.appRoot.settingsPresented },
                close: { app.send(.settings(.cancel)) }
            )
        ) {
            SettingsScreen(app: app)
        }
    }

    private func formBinding(isOpen: @escaping () -> Bool, close: @escaping () -> Void) -> Binding<Bool> {
        Binding(
            get: isOpen,
            set: { presented in
                if !presented { close() }
            }
        )
    }
}

/// MainTabView.swift:122-187, minus the Skip branches. Text and button label come from
/// the onboarding machine; the 0.1s clear-sleep is gone (flag_order: the machine writes
/// the flag when the clear completes).
struct OnboardingOverlay: View {
    @ObservedObject var app: ReflexApp

    private var data: OnboardingData { app.onboarding }

    var body: some View {
        ZStack {
            Rectangle()
                .fill(Color.black.opacity(0.6))
                .background(.ultraThinMaterial)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 24) {
                    ZStack {
                        Circle()
                            .fill(Theme.primary)
                            .frame(width: 72, height: 72)
                        AppIcon(.onboardingSparkle, size: 32, tint: .white)
                    }
                    .padding(.bottom, 8)

                    Text(data.promptText)
                        .font(.title2)
                        .fontWeight(.heavy)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)

                    Text(data.subtitle)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)

                    Button {
                        if data.step >= OnboardingMachine.lastStep {
                            app.send(.onboarding(.getStarted))
                        } else {
                            app.send(.onboarding(.next))
                        }
                    } label: {
                        Text(data.buttonText)
                            .font(.headline)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(Theme.primary)
                            .foregroundColor(.white)
                            .cornerRadius(16)
                    }
                    .disabled(data.clearing)
                    .padding(.top, 8)
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 32)
                .background(Color(red: 0.1, green: 0.1, blue: 0.12).opacity(0.98))
                .cornerRadius(24)
                .padding(.horizontal, 20)
                .padding(.bottom, 120)
            }
        }
        .transition(.opacity.combined(with: .scale(scale: 0.95)))
    }
}