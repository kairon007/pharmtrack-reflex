package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.Screens.AddAppointmentAction

/**
 * AddAppointmentScreen.swift:108-179. The iOS form chrome (cancel/save bar) has no shared
 * Compose equivalent, so it is rebuilt here from the machine's state.
 */
@Composable
fun AddAppointmentSheet(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val data = screen.addAppointment
    // The legacy FormTextField look: white wells in light mode, slate800 in dark mode.
    val fieldBackground = if (isDark) Theme.slate800 else Color.White
    val fieldBorder = if (isDark) Theme.slate700 else Theme.slate200

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cancel",
                fontSize = 15.sp,
                color = Theme.primary,
                modifier = Modifier
                    .height(44.dp)
                    .clickable { app.send(Action.ToAddAppointment(AddAppointmentAction.Cancel)) }
                    .padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (data.mode == EditMode.EDIT) "Edit Appointment" else "New Appointment",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Theme.heading(isDark)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Done",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (data.canSave) Theme.primary else Theme.primary.copy(alpha = 0.4f),
                modifier = Modifier
                    .height(44.dp)
                    .clickable(enabled = data.canSave) {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.Save))
                    }
                    .padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // icons.yaml drops person.fill; the consults tab glyph stands in.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(SemanticIcon.TAB_CONSULTS, size = 16.dp, tint = Theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Doctor & Specialty",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.caption(isDark)
                    )
                }
                OutlinedTextField(
                    value = data.doctorNameText,
                    onValueChange = {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.SetDoctorName(it)))
                    },
                    placeholder = {
                        Text(text = "Doctor's Name", fontSize = 15.sp, color = Theme.caption(isDark))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Theme.heading(isDark),
                        unfocusedTextColor = Theme.heading(isDark),
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        focusedBorderColor = Theme.primary,
                        unfocusedBorderColor = fieldBorder,
                        cursorColor = Theme.primary
                    )
                )
                OutlinedTextField(
                    value = data.specialtyText,
                    onValueChange = {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.SetSpecialty(it)))
                    },
                    placeholder = {
                        Text(text = "Specialty", fontSize = 15.sp, color = Theme.caption(isDark))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Theme.heading(isDark),
                        unfocusedTextColor = Theme.heading(isDark),
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        focusedBorderColor = Theme.primary,
                        unfocusedBorderColor = fieldBorder,
                        cursorColor = Theme.primary
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(SemanticIcon.TAB_CALENDAR, size = 16.dp, tint = Theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Appointment details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.caption(isDark)
                    )
                }
                OutlinedTextField(
                    value = data.typeText,
                    onValueChange = {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.SetType(it)))
                    },
                    placeholder = {
                        Text(
                            text = "Type (e.g., General Checkup)",
                            fontSize = 15.sp,
                            color = Theme.caption(isDark)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Theme.heading(isDark),
                        unfocusedTextColor = Theme.heading(isDark),
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        focusedBorderColor = Theme.primary,
                        unfocusedBorderColor = fieldBorder,
                        cursorColor = Theme.primary
                    )
                )
                DateTimeField(
                    label = "Date & Time",
                    instant = data.date,
                    zone = screen.context.zone,
                    isDark = isDark
                ) { app.send(Action.ToAddAppointment(AddAppointmentAction.SetDate(it))) }
                OutlinedTextField(
                    value = data.locationText,
                    onValueChange = {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.SetLocation(it)))
                    },
                    placeholder = {
                        Text(text = "Location", fontSize = 15.sp, color = Theme.caption(isDark))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Theme.heading(isDark),
                        unfocusedTextColor = Theme.heading(isDark),
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        focusedBorderColor = Theme.primary,
                        unfocusedBorderColor = fieldBorder,
                        cursorColor = Theme.primary
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // icons.yaml drops doc.text.fill; the edit glyph stands in.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(SemanticIcon.ACTION_EDIT, size = 16.dp, tint = Theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notes (optional)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.caption(isDark)
                    )
                }
                OutlinedTextField(
                    value = data.notesText,
                    onValueChange = {
                        app.send(Action.ToAddAppointment(AddAppointmentAction.SetNotes(it)))
                    },
                    placeholder = {
                        Text(
                            text = "Bring previous reports...",
                            fontSize = 15.sp,
                            color = Theme.caption(isDark)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Theme.heading(isDark),
                        unfocusedTextColor = Theme.heading(isDark),
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        focusedBorderColor = Theme.primary,
                        unfocusedBorderColor = fieldBorder,
                        cursorColor = Theme.primary
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Theme.border(isDark))
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                title = "Save appointment",
                icon = SemanticIcon.ACTION_CONFIRM,
                enabled = data.canSave
            ) { app.send(Action.ToAddAppointment(AddAppointmentAction.Save)) }
        }
    }
}
