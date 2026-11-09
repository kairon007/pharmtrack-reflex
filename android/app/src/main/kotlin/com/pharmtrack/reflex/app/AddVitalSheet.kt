package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.EditMode
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Screens.AddVitalAction

/**
 * AddVitalScreen.swift:94-210. The iOS form chrome (cancel/save bar, metric tabs) has no
 * shared Compose equivalent, so it is rebuilt here from the machine's state.
 */
@Composable
fun AddVitalSheet(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val data = screen.addVital
    // The legacy InputWell look: slate wells in dark mode, slate50 in light mode.
    val fieldBackground = if (isDark) Theme.slate800 else Theme.slate50

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
                    .clickable { app.send(Action.ToAddVital(AddVitalAction.Cancel)) }
                    .padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (data.mode == EditMode.EDIT) "Edit vital" else "Add vital",
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
                        app.send(Action.ToAddVital(AddVitalAction.Save))
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
                Text(
                    text = "Vital type",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VitalType.entries.forEach { type ->
                        val selected = data.type == type
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Theme.primary
                                    else if (isDark) Theme.slate800 else Color.White
                                )
                                .then(
                                    if (selected) Modifier
                                    else Modifier.border(
                                        1.dp,
                                        Theme.border(isDark),
                                        RoundedCornerShape(12.dp)
                                    )
                                )
                                .clickable { app.send(Action.ToAddVital(AddVitalAction.SetType(type))) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIcon(
                                SemanticIcon.fromVitalType(type),
                                size = 24.dp,
                                tint = if (selected) Color.White else Theme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = type.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White
                                else if (isDark) Theme.slate300 else Theme.slate700
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Value",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = data.valueText,
                        onValueChange = {
                            app.send(Action.ToAddVital(AddVitalAction.SetValueText(it)))
                        },
                        placeholder = {
                            Text(
                                text = if (data.value2Visible) "120" else "0.0",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Theme.caption(isDark)
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Theme.heading(isDark),
                            unfocusedTextColor = Theme.heading(isDark),
                            focusedContainerColor = fieldBackground,
                            unfocusedContainerColor = fieldBackground,
                            focusedBorderColor = Theme.primary,
                            unfocusedBorderColor = Theme.border(isDark),
                            cursorColor = Theme.primary
                        )
                    )
                    if (data.value2Visible) {
                        Text(
                            text = "/",
                            fontSize = 24.sp,
                            color = Theme.caption(isDark)
                        )
                        OutlinedTextField(
                            value = data.value2Text,
                            onValueChange = {
                                app.send(Action.ToAddVital(AddVitalAction.SetValue2Text(it)))
                            },
                            placeholder = {
                                Text(
                                    text = "80",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Theme.caption(isDark)
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Theme.heading(isDark),
                                unfocusedTextColor = Theme.heading(isDark),
                                focusedContainerColor = fieldBackground,
                                unfocusedContainerColor = fieldBackground,
                                focusedBorderColor = Theme.primary,
                                unfocusedBorderColor = Theme.border(isDark),
                                cursorColor = Theme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = data.unit,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.caption(isDark)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Date and time",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                DateTimeField(
                    label = "Select Date",
                    instant = data.date,
                    zone = screen.context.zone,
                    isDark = isDark
                ) { app.send(Action.ToAddVital(AddVitalAction.SetDate(it))) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Notes (optional)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.caption(isDark)
                )
                OutlinedTextField(
                    value = data.notesText,
                    onValueChange = {
                        app.send(Action.ToAddVital(AddVitalAction.SetNotes(it)))
                    },
                    placeholder = {
                        Text(
                            text = "Any specific conditions?",
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
                        unfocusedBorderColor = Theme.border(isDark),
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
                title = "Save entry",
                icon = SemanticIcon.ACTION_CONFIRM,
                enabled = data.canSave
            ) { app.send(Action.ToAddVital(AddVitalAction.Save)) }
        }
    }
}
