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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.Consultation
import com.pharmtrack.reflex.core.Screens.ConsultationsAction

// ConsultationsScreen.swift, ported 1:1. The search text, the filtered rows and the
// count all come from the machine; the view only renders them and sends actions.
@Composable
fun ConsultationsScreen(app: ReflexApp, screen: ScreenData, tourActive: Boolean) {
    val isDark = isSystemInDarkTheme()
    val data = screen.consultations
    val listState = rememberLazyListState()

    OnboardingAutoScroll(active = tourActive, listState = listState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        ConsultationsHeader(app, data, isDark)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ActiveTreatmentsRow(data, isDark) }
            items(data.rows, key = { it.id }) { consultation ->
                ConsultationCard(app, data, consultation, isDark)
            }
            if (data.rows.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EmptyStateCard(
                            icon = SemanticIcon.TAB_CONSULTS,
                            title = "No consultations yet",
                            message = "Add a doctor to keep your treatments in one place.",
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsultationsHeader(app: ReflexApp, data: ConsultationsData, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.background(isDark))
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Consultations",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconCircleButton(
                icon = SemanticIcon.ACTION_ADD,
                tint = Theme.primary,
                label = "Add consultation"
            ) {
                app.send(Action.ToConsultations(ConsultationsAction.OpenAdd))
            }
        }

        OutlinedTextField(
            value = data.searchQuery,
            onValueChange = {
                app.send(Action.ToConsultations(ConsultationsAction.SetSearch(it)))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search by doctor or specialty...",
                    fontSize = 15.sp,
                    color = Theme.caption(isDark)
                )
            },
            leadingIcon = {
                AppIcon(
                    icon = SemanticIcon.ACTION_SEARCH,
                    size = 20.dp,
                    tint = Theme.slate400
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Theme.heading(isDark),
                unfocusedTextColor = Theme.heading(isDark),
                focusedContainerColor = Theme.surface(isDark),
                unfocusedContainerColor = Theme.surface(isDark),
                focusedBorderColor = Theme.primary,
                unfocusedBorderColor = Theme.border(isDark),
                focusedPlaceholderColor = Theme.caption(isDark),
                unfocusedPlaceholderColor = Theme.caption(isDark),
                focusedLeadingIconColor = Theme.slate400,
                unfocusedLeadingIconColor = Theme.slate400,
                cursorColor = Theme.primary
            )
        )
    }
}

@Composable
private fun ActiveTreatmentsRow(data: ConsultationsData, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Active treatments",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.caption(isDark)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${data.totalCount} active",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Theme.primary
        )
    }
}

@Composable
private fun ConsultationCard(
    app: ReflexApp,
    data: ConsultationsData,
    consultation: Consultation,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.surface(isDark))
                .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
                .clickable {
                    app.send(Action.ToConsultations(ConsultationsAction.OpenEdit(consultation.id)))
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Theme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.initials(consultation.id),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = consultation.doctorName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.heading(isDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = consultation.specialty,
                    fontSize = 12.sp,
                    color = Theme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Last visit: ${DateFormatting.mediumDate(consultation.date)}",
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
}
