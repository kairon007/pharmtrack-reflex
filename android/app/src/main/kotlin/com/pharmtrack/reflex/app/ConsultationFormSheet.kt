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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.Screens.ConsultationFormAction
import com.pharmtrack.reflex.core.Screens.MedicationPickerAction

/** ConsultationFormScreen.swift + MedicationPickerScreen.swift, ported to Compose. */
@Composable
fun ConsultationFormSheet(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val data = screen.consultationForm
    val isEdit = data.mode == EditMode.EDIT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConsultationFormHeader(
                title = if (isEdit) "Edit Consultation" else "New Consultation",
                canSave = data.canSave,
                isDark = isDark,
                onCancel = { app.send(Action.ToConsultationForm(ConsultationFormAction.Cancel)) },
                onSave = { app.send(Action.ToConsultationForm(ConsultationFormAction.Save)) }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionLabel("Doctor information", SemanticIcon.TAB_CONSULTS, isDark)
                    ConsultationTextField(
                        placeholder = "Doctor Name (e.g. Dr. Sarah Johnson)",
                        value = data.doctorNameText,
                        isDark = isDark
                    ) { app.send(Action.ToConsultationForm(ConsultationFormAction.SetDoctorName(it))) }
                    ConsultationTextField(
                        placeholder = "Specialty (e.g. Cardiologist)",
                        value = data.specialtyText,
                        isDark = isDark
                    ) { app.send(Action.ToConsultationForm(ConsultationFormAction.SetSpecialty(it))) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionLabel("Consultation schedule", SemanticIcon.TAB_CALENDAR, isDark)
                    DateTimeField(
                        label = "Consultation Date",
                        instant = data.date,
                        zone = screen.context.zone,
                        isDark = isDark
                    ) { app.send(Action.ToConsultationForm(ConsultationFormAction.SetDate(it))) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // icons.yaml drops doc.text.fill; the edit glyph stands in.
                    FormSectionLabel("Consultation notes", SemanticIcon.ACTION_EDIT, isDark)
                    ConsultationTextField(
                        placeholder = "Describe symptoms, advice, etc...",
                        value = data.notesText,
                        isDark = isDark,
                        singleLine = false
                    ) { app.send(Action.ToConsultationForm(ConsultationFormAction.SetNotes(it))) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionLabel("Prescribed medications", SemanticIcon.TAB_MEDS, isDark)

                    data.linkedRows.forEach { medication ->
                        CardSurface(isDark = isDark) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = medication.name,
                                    fontSize = 15.sp,
                                    color = Theme.heading(isDark)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconCircleButton(
                                    icon = SemanticIcon.ACTION_REMOVE,
                                    tint = Theme.rose500,
                                    label = "Unlink ${medication.name}"
                                ) {
                                    app.send(
                                        Action.ToConsultationForm(
                                            ConsultationFormAction.UnlinkMedication(medication.id)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    LinkMedicationButton {
                        app.send(Action.ToConsultationForm(ConsultationFormAction.PresentPicker))
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Theme.slate800 else Theme.slate200)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.background(isDark))
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
            ) {
                PrimaryButton(
                    title = if (isEdit) "Update Consultation" else "Save Consultation",
                    icon = SemanticIcon.ACTION_CONFIRM,
                    enabled = data.canSave
                ) { app.send(Action.ToConsultationForm(ConsultationFormAction.Save)) }
            }
        }

        if (data.pickerPresented) {
            MedicationPickerDialog(app, screen)
        }
    }
}

/** FormHeader.swift: cancel / title / done. */
@Composable
private fun ConsultationFormHeader(
    title: String,
    canSave: Boolean,
    isDark: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
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
            text = title,
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
                .clickable(enabled = canSave, onClick = onSave),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Done",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (canSave) Theme.primary else Theme.primary.copy(alpha = 0.4f)
            )
        }
    }
}

/** FormSectionHeader.swift: a semantic icon next to a caption-weight title. */
@Composable
private fun FormSectionLabel(title: String, icon: SemanticIcon, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, size = 16.dp, tint = Theme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.caption(isDark)
        )
    }
}

/** FormTextField.swift: the field's own surface and border, not the Material default. */
@Composable
private fun ConsultationTextField(
    placeholder: String,
    value: String,
    isDark: Boolean,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    // The legacy FormTextField look: white wells in light mode, slate800 in dark mode.
    val fieldBackground = if (isDark) Theme.slate800 else Color.White
    val fieldBorder = if (isDark) Theme.slate700 else Theme.slate200

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        placeholder = {
            Text(text = placeholder, fontSize = 15.sp, color = Theme.caption(isDark))
        },
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

/** The dashed "Link medication" target; SwiftUI draws it with a dashed stroke. */
@Composable
private fun LinkMedicationButton(onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.primary.copy(alpha = 0.05f))
            .dashedBorder(Theme.primary.copy(alpha = 0.3f), 12.dp, 5.dp)
            .clickable(onClick = onClick)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(
            icon = SemanticIcon.ACTION_ADD_CIRCLE,
            size = 32.dp,
            tint = Theme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Link medication",
            fontSize = 15.sp,
            color = Theme.caption(isDark)
        )
    }
}

/** The nested medication picker, shown over the form while the machine presents it. */
@Composable
private fun MedicationPickerDialog(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val picker = screen.medicationPicker

    Dialog(
        onDismissRequest = {
            app.send(Action.ToConsultationForm(ConsultationFormAction.DismissPicker))
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                color = Theme.surface(isDark),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Select Medication",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Theme.heading(isDark)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Done",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.primary,
                            modifier = Modifier
                                .height(44.dp)
                                .clickable {
                                    app.send(Action.ToMedicationPicker(MedicationPickerAction.Done))
                                }
                                .padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        picker.available.forEach { medication ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable {
                                        app.send(
                                            Action.ToMedicationPicker(
                                                MedicationPickerAction.Toggle(medication.id)
                                            )
                                        )
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = medication.name,
                                    fontSize = 15.sp,
                                    color = Theme.heading(isDark)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (picker.isSelected(medication.id)) {
                                    AppIcon(
                                        icon = SemanticIcon.ACTION_CONFIRM,
                                        size = 20.dp,
                                        tint = Theme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp, dashLength: Dp): Modifier =
    this.drawBehind {
        val strokeWidth = 1.dp.toPx()
        val dash = dashLength.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
            )
        )
    }
