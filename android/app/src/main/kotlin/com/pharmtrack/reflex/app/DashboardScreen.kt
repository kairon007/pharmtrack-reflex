package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Screens.AppointmentsAction
import com.pharmtrack.reflex.core.Screens.ConsultationsAction
import com.pharmtrack.reflex.core.Screens.DashboardAction
import com.pharmtrack.reflex.core.Screens.DashboardSheet
import com.pharmtrack.reflex.core.Screens.HeroBranch
import com.pharmtrack.reflex.core.Screens.MedicationTrackerAction
import com.pharmtrack.reflex.core.Screens.QuickActionKind
import com.pharmtrack.reflex.core.Screens.VitalsAction

// DashboardScreen.swift, ported 1:1. The view renders the machine's emit output and
// sends actions; it never derives a value the machine already owns.
@Composable
fun DashboardScreen(app: ReflexApp, screen: ScreenData, tourActive: Boolean) {
    val isDark = isSystemInDarkTheme()
    val data = screen.dashboard
    val listState = rememberLazyListState()

    OnboardingAutoScroll(active = tourActive, listState = listState)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark)),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item { DashboardHeader(app, data, isDark) }
        item { TodaySection(app, data, isDark) }
        item { QuickActionsSection(app, isDark) }
        item { UpcomingSection(app, data, isDark) }
        item { HistorySection(app, data, isDark) }
    }
}

@Composable
private fun DashboardHeader(app: ReflexApp, data: DashboardData, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = data.greeting,
                fontSize = 15.sp,
                color = Theme.caption(isDark)
            )
            Text(
                text = "Today",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Theme.surface(isDark))
                .clickable {
                    app.send(Action.ToDashboard(DashboardAction.PresentSheet(DashboardSheet.SETTINGS)))
                }
                .semantics { contentDescription = "Notification settings" },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                icon = SemanticIcon.ACTION_SETTINGS,
                size = 20.dp,
                tint = if (isDark) Theme.slate300 else Theme.slate600
            )
        }
    }
}

@Composable
private fun TodaySection(app: ReflexApp, data: DashboardData, isDark: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        when (data.heroBranch) {
            HeroBranch.MEDS -> MedsCard(app, data, isDark)
            HeroBranch.APPOINTMENT -> {
                val appointment = data.upcoming.firstOrNull()
                if (appointment != null) AppointmentCard(app, appointment, isDark)
            }
            HeroBranch.EMPTY -> EmptyStateCard(
                icon = SemanticIcon.ACTION_ADD_CIRCLE,
                title = "Start your health log",
                message = "Add a medication or an appointment and your day shows up here.",
                isDark = isDark
            )
        }
    }
}

@Composable
private fun MedsCard(app: ReflexApp, data: DashboardData, isDark: Boolean) {
    CardSurface(isDark = isDark) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Today's plan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = data.takenLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.primary
                )
            }

            ProgressBar(fraction = data.takenFraction, isDark = isDark)

            val medication = data.nextMedication
            if (medication != null) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Next dose",
                        fontSize = 12.sp,
                        color = Theme.caption(isDark)
                    )
                    Text(
                        text = "${medication.name} · ${medication.dosage}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.heading(isDark)
                    )
                    Text(
                        text = medication.instructions,
                        fontSize = 12.sp,
                        color = Theme.caption(isDark)
                    )
                }

                // D6. The reducer guards a repeat tap; the view only reflects the flag.
                PrimaryButton(
                    title = "Mark taken",
                    icon = SemanticIcon.ACTION_CONFIRM,
                    enabled = !data.nextMedIsTaken
                ) {
                    app.send(Action.ToDashboard(DashboardAction.MarkNextTaken))
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(app: ReflexApp, appointment: Appointment, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                app.send(Action.ToDashboard(DashboardAction.TapHeroAppointment(appointment.id)))
            }
    ) {
        CardSurface(isDark = isDark) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Next appointment",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                Text(
                    text = appointment.doctorName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.heading(isDark)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            icon = SemanticIcon.CONTENT_TIME,
                            size = 16.dp,
                            tint = Theme.caption(isDark)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = DateFormatting.logTimestamp(appointment.date),
                            fontSize = 12.sp,
                            color = Theme.caption(isDark)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            icon = SemanticIcon.CONTENT_LOCATION,
                            size = 16.dp,
                            tint = Theme.caption(isDark)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = appointment.location,
                            fontSize = 12.sp,
                            color = Theme.caption(isDark)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsSection(app: ReflexApp, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionTile(
            app = app,
            title = "Doctor",
            icon = SemanticIcon.TAB_CONSULTS,
            kind = QuickActionKind.DOCTOR,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        ) {
            app.send(Action.ToConsultations(ConsultationsAction.OpenAdd))
        }
        QuickActionTile(
            app = app,
            title = "Meds",
            icon = SemanticIcon.TAB_MEDS,
            kind = QuickActionKind.MEDS,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        ) {
            app.send(Action.ToMedicationTracker(MedicationTrackerAction.OpenAdd))
        }
        QuickActionTile(
            app = app,
            title = "Visit",
            icon = SemanticIcon.TAB_CALENDAR,
            kind = QuickActionKind.VISIT,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        ) {
            app.send(Action.ToAppointments(AppointmentsAction.OpenAdd))
        }
        QuickActionTile(
            app = app,
            title = "Vitals",
            icon = SemanticIcon.TAB_VITALS,
            kind = QuickActionKind.VITALS,
            isDark = isDark,
            modifier = Modifier.weight(1f)
        ) {
            app.send(Action.ToVitals(VitalsAction.OpenAdd))
        }
    }
}

// The sheet lives at RootView, bound to the form machine; quickAction records the
// dashboard contract state and openAdd opens the owning form in one tap.
@Composable
private fun QuickActionTile(
    app: ReflexApp,
    title: String,
    icon: SemanticIcon,
    kind: QuickActionKind,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    openForm: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
            .clickable {
                app.send(Action.ToDashboard(DashboardAction.QuickAction(kind)))
                openForm()
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(icon = icon, size = 20.dp, tint = Theme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Theme.slate200 else Theme.slate700
        )
    }
}

@Composable
private fun UpcomingSection(app: ReflexApp, data: DashboardData, isDark: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(
            title = "Upcoming",
            actionTitle = "Calendar",
            action = { app.send(Action.ToDashboard(DashboardAction.TapUpcomingHeader)) },
            isDark = isDark
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (data.upcoming.isEmpty()) {
            EmptyStateCard(
                icon = SemanticIcon.TAB_CALENDAR,
                title = "No upcoming appointments",
                message = "Add a visit and it will appear here before the day comes.",
                isDark = isDark
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.upcoming.forEach { appointment ->
                    AppointmentRow(app, appointment, isDark)
                }
            }
        }
    }
}

@Composable
private fun AppointmentRow(app: ReflexApp, appointment: Appointment, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
            .clickable {
                app.send(Action.ToDashboard(DashboardAction.TapUpcomingRow(appointment.id)))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(72.dp)) {
            Text(
                text = DateFormatting.time(appointment.date),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary
            )
            Text(
                text = DateFormatting.mediumDate(appointment.date),
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Theme.border(isDark))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appointment.doctorName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${appointment.type} · ${appointment.location}",
                fontSize = 12.sp,
                color = Theme.caption(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIcon(
            icon = SemanticIcon.ACTION_FORWARD,
            size = 14.dp,
            tint = if (isDark) Theme.slate500 else Theme.slate400
        )
    }
}

@Composable
private fun HistorySection(app: ReflexApp, data: DashboardData, isDark: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(
            title = "Recent activity",
            actionTitle = "View all",
            action = { app.send(Action.ToDashboard(DashboardAction.TapRecentActivityViewAll)) },
            isDark = isDark
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (data.recentLogs.isEmpty()) {
            EmptyStateCard(
                icon = SemanticIcon.CONTENT_TIME,
                title = "Nothing logged yet",
                message = "Taking a medication or adding a reading will show up here.",
                isDark = isDark
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.recentLogs.forEach { log ->
                    LogRow(log, isDark)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: Log, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            icon = if (log.type == "Medication Taken") {
                SemanticIcon.ACTION_DONE
            } else {
                SemanticIcon.CONTENT_LOG_EDITED
            },
            size = 22.dp,
            tint = Theme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.description,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.heading(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = DateFormatting.time(log.date),
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
        Text(
            text = log.source,
            fontSize = 12.sp,
            color = Theme.caption(isDark)
        )
    }
}
