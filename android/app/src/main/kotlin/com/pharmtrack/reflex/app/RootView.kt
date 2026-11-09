package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.FormMode
import com.pharmtrack.reflex.core.Screens.AppointmentsAction
import com.pharmtrack.reflex.core.Screens.AppRootAction
import com.pharmtrack.reflex.core.Screens.ConsultationsAction
import com.pharmtrack.reflex.core.Screens.DashboardAction
import com.pharmtrack.reflex.core.Screens.MedicationTrackerAction
import com.pharmtrack.reflex.core.Screens.OnboardingAction
import com.pharmtrack.reflex.core.Screens.OnboardingMachine
import com.pharmtrack.reflex.core.Screens.SettingsAction
import com.pharmtrack.reflex.core.Screens.VitalsAction

/**
 * The shell: five tabs plus every form sheet, each bound directly to its machine's
 * presentation state. Sheets live HERE (not per tab) so the dashboard quick actions and
 * the tab "+" buttons present the same form through the same binding.
 */
@Composable
fun RootView(app: ReflexApp) {
    val state by app.state.collectAsState()
    val screen = remember(state) { ScreenData(state, app.context) }
    val root = screen.root
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Theme.background(isDark)
    ) {
        Scaffold(
            containerColor = Theme.background(isDark),
            bottomBar = {
                NavigationBar(containerColor = Theme.surface(isDark)) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = root.selectedTab == tab.index,
                            onClick = { app.send(Action.ToAppRoot(AppRootAction.SelectTab(tab.index))) },
                            icon = { AppIcon(tab.icon, size = 24.dp, tint = Theme.primary) },
                            label = { Text(tab.title, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Theme.primary,
                                selectedTextColor = Theme.primary,
                                unselectedIconColor = Theme.caption(isDark),
                                unselectedTextColor = Theme.caption(isDark)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (root.selectedTab) {
                    0 -> DashboardScreen(app, screen, root.isOnboarding)
                    1 -> ConsultationsScreen(app, screen, root.isOnboarding)
                    2 -> MedicationTrackerScreen(app, screen, root.isOnboarding)
                    3 -> AppointmentsScreen(app, screen)
                    4 -> VitalsScreen(app, screen, root.isOnboarding)
                }
            }
        }
    }

    if (root.isOnboarding && screen.onboarding.visible) {
        OnboardingOverlay(app, screen.onboarding)
    }

    if (screen.consultations.formMode != FormMode.CLOSED) {
        FormSheet(onDismiss = {
            app.send(Action.ToConsultations(ConsultationsAction.CloseForm))
            app.send(Action.ToDashboard(DashboardAction.DismissSheet))
        }) {
            ConsultationFormSheet(app, screen)
        }
    }

    if (screen.medicationTracker.formMode != FormMode.CLOSED) {
        FormSheet(onDismiss = {
            app.send(Action.ToMedicationTracker(MedicationTrackerAction.CloseForm))
            app.send(Action.ToDashboard(DashboardAction.DismissSheet))
        }) {
            AddMedicationSheet(app, screen)
        }
    }

    if (screen.appointments.formMode != FormMode.CLOSED) {
        FormSheet(onDismiss = {
            app.send(Action.ToAppointments(AppointmentsAction.CloseForm))
            app.send(Action.ToDashboard(DashboardAction.DismissSheet))
        }) {
            AddAppointmentSheet(app, screen)
        }
    }

    if (screen.vitals.formMode != FormMode.CLOSED) {
        FormSheet(onDismiss = {
            app.send(Action.ToVitals(VitalsAction.CloseForm))
            app.send(Action.ToDashboard(DashboardAction.DismissSheet))
        }) {
            AddVitalSheet(app, screen)
        }
    }

    if (root.settingsPresented) {
        FormSheet(onDismiss = {
            app.send(Action.ToSettings(SettingsAction.Cancel))
        }) {
            SettingsSheet(app, screen)
        }
    }
}

enum class AppTab(val index: Int, val title: String, val icon: SemanticIcon) {
    HOME(0, "Home", SemanticIcon.TAB_HOME),
    CONSULTS(1, "Consults", SemanticIcon.TAB_CONSULTS),
    MEDS(2, "Meds", SemanticIcon.TAB_MEDS),
    CALENDAR(3, "Calendar", SemanticIcon.TAB_CALENDAR),
    VITALS(4, "Vitals", SemanticIcon.TAB_VITALS)
}

/** A full-screen form surface — the Android counterpart of the iOS .sheet presentation. */
@Composable
fun FormSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Theme.background(isDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                content()
            }
        }
    }
}

/**
 * MainTabView.swift:122-187, minus the Skip branches. Text and button label come from
 * the onboarding machine; the 0.1s clear-sleep is gone (flag_order: the machine writes
 * the flag when the clear completes).
 */
@Composable
fun OnboardingOverlay(app: ReflexApp, data: OnboardingData) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1F).copy(alpha = 0.98f))
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Theme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(SemanticIcon.ONBOARDING_SPARKLE, size = 32.dp, tint = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = data.promptText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = data.subtitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (data.clearing) Theme.primary.copy(alpha = 0.4f) else Theme.primary)
                        .clickable(enabled = !data.clearing) {
                            if (data.step >= OnboardingMachine.LAST_STEP) {
                                app.send(Action.ToOnboarding(OnboardingAction.GetStarted))
                            } else {
                                app.send(Action.ToOnboarding(OnboardingAction.Next))
                            }
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.buttonText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}
