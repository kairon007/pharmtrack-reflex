package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Screens.SettingsAction

/** SettingsScreen.swift: the notification settings form with its Cancel / Save toolbar. */
@Composable
fun SettingsSheet(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val data = screen.settings
    val draft = data.draft

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        SettingsHeader(
            isDark = isDark,
            onCancel = { app.send(Action.ToSettings(SettingsAction.Cancel)) },
            onSave = { app.send(Action.ToSettings(SettingsAction.Save)) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Global Notifications", isDark = isDark) {
                SettingsToggle(
                    title = "Enable All Notifications",
                    checked = draft.allNotificationsEnabled,
                    isDark = isDark
                ) { app.send(Action.ToSettings(SettingsAction.SetAllEnabled(it))) }
            }

            if (data.sectionsVisible) {
                SettingsSection(title = "Medications", isDark = isDark) {
                    SettingsToggle(
                        title = "Medication Reminders",
                        checked = draft.medicationsEnabled,
                        isDark = isDark
                    ) { app.send(Action.ToSettings(SettingsAction.SetMedicationsEnabled(it))) }

                    if (data.medicationTimeVisible) {
                        SettingsChoiceRow(title = "Reminder Time", isDark = isDark) {
                            data.reminderOptions.forEach { option ->
                                FilterChip(
                                    title = option.rawValue,
                                    isSelected = draft.medicationReminderTime == option,
                                    isDark = isDark
                                ) {
                                    app.send(
                                        Action.ToSettings(
                                            SettingsAction.SetMedicationReminderTime(option)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SettingsToggle(
                        title = "Low Stock Alerts",
                        checked = draft.lowStockEnabled,
                        isDark = isDark
                    ) { app.send(Action.ToSettings(SettingsAction.SetLowStockEnabled(it))) }

                    if (data.thresholdVisible) {
                        SettingsChoiceRow(title = "Alert Threshold", isDark = isDark) {
                            data.thresholdOptions.forEach { option ->
                                FilterChip(
                                    title = "$option doses left",
                                    isSelected = draft.lowStockThreshold == option.toLong(),
                                    isDark = isDark
                                ) {
                                    app.send(
                                        Action.ToSettings(
                                            SettingsAction.SetLowStockThreshold(option.toLong())
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection(title = "Appointments", isDark = isDark) {
                    SettingsToggle(
                        title = "Appointment Alerts",
                        checked = draft.appointmentsEnabled,
                        isDark = isDark
                    ) { app.send(Action.ToSettings(SettingsAction.SetAppointmentsEnabled(it))) }

                    if (data.appointmentTimeVisible) {
                        SettingsChoiceRow(title = "Alert Time", isDark = isDark) {
                            data.reminderOptions.forEach { option ->
                                FilterChip(
                                    title = option.rawValue,
                                    isSelected = draft.appointmentReminderTime == option,
                                    isDark = isDark
                                ) {
                                    app.send(
                                        Action.ToSettings(
                                            SettingsAction.SetAppointmentReminderTime(option)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection(title = "Consultations", isDark = isDark) {
                    SettingsToggle(
                        title = "Consultation Alerts",
                        checked = draft.consultationsEnabled,
                        isDark = isDark
                    ) { app.send(Action.ToSettings(SettingsAction.SetConsultationsEnabled(it))) }
                }
            }
        }
    }
}

/** The navigation bar: cancellation action, title, confirmation action. */
@Composable
private fun SettingsHeader(isDark: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(44.dp)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = "Cancel", fontSize = 17.sp, color = Theme.primary)
        }
        Text(
            text = "Notification Settings",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Theme.heading(isDark),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(44.dp)
                .clickable(onClick = onSave),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Save",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Theme.primary
            )
        }
    }
}

/** One Form section: a header plus the grouped card that holds its rows. */
@Composable
private fun SettingsSection(
    title: String,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title, isDark = isDark)
        CardSurface(isDark = isDark) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

/** A Form toggle row; the tint is the theme's primary colour. */
@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    isDark: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = Theme.body(isDark),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Theme.primary,
                checkedBorderColor = Theme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (isDark) Theme.slate700 else Theme.slate300,
                uncheckedBorderColor = if (isDark) Theme.slate600 else Theme.slate400
            )
        )
    }
}

/** A picker row: the machine's own options rendered as selectable chips. */
@Composable
private fun SettingsChoiceRow(
    title: String,
    isDark: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Theme.caption(isDark)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}
