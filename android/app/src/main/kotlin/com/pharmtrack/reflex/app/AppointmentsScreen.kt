package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.Appointment
import com.pharmtrack.reflex.core.Screens.AppointmentsAction
import com.pharmtrack.reflex.core.Support.Instant

/**
 * The appointments tab: a week strip over the day's visits. The header wording,
 * the visible list and the selected day all come from the appointments machine.
 */
@Composable
fun AppointmentsScreen(app: ReflexApp, screen: ScreenData) {
    val data = screen.appointments
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        AppointmentsHeader(app = app, isDark = isDark)
        CalendarStrip(data = data, app = app, isDark = isDark)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = data.headerText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.heading(isDark)
                    )
                }
            }

            if (data.visible.isEmpty()) {
                item {
                    Text(
                        text = "No appointments for ${DateFormatting.mediumDate(data.emptyMessageDate)}",
                        fontSize = 15.sp,
                        color = if (isDark) Theme.slate500 else Theme.slate400,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                data.visible.forEach { appointment ->
                    item(key = appointment.id) {
                        AppointmentCard(appointment = appointment, app = app, isDark = isDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentsHeader(app: ReflexApp, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.background(isDark).copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Appointments",
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.heading(isDark)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconCircleButton(
            icon = SemanticIcon.ACTION_ADD,
            tint = Theme.primary,
            label = "Add appointment"
        ) {
            app.send(Action.ToAppointments(AppointmentsAction.OpenAdd))
        }
    }
}

@Composable
private fun CalendarStrip(data: AppointmentsData, app: ReflexApp, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.background(isDark))
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateFormatting.monthYear(data.stripCenter),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            app.send(Action.ToAppointments(AppointmentsAction.PreviousWeek))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(SemanticIcon.ACTION_BACK, size = 20.dp, tint = Theme.caption(isDark))
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            app.send(Action.ToAppointments(AppointmentsAction.NextWeek))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(SemanticIcon.ACTION_FORWARD, size = 20.dp, tint = Theme.caption(isDark))
                }
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            data.stripDates.forEach { date ->
                DayCell(date = date, data = data, app = app, isDark = isDark)
            }
        }
    }
}

@Composable
private fun DayCell(date: Instant, data: AppointmentsData, app: ReflexApp, isDark: Boolean) {
    val isSelected = data.isSelected(date)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Theme.primary else Theme.surface(isDark))
            .then(
                if (isSelected) Modifier
                else Modifier.border(1.dp, Theme.border(isDark), RoundedCornerShape(12.dp))
            )
            .clickable {
                app.send(Action.ToAppointments(AppointmentsAction.SelectDay(date)))
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = DateFormatting.weekday(date).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White.copy(alpha = 0.8f)
                else if (isDark) Theme.slate500 else Theme.slate400
            )
            Text(
                text = DateFormatting.dayNumber(date),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Theme.heading(isDark)
            )
        }
    }
}

@Composable
private fun AppointmentCard(appointment: Appointment, app: ReflexApp, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.surface(isDark))
                .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
                .padding(16.dp)
                // Intrinsic height lets the timeline rule reach the bottom of the card.
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = DateFormatting.time(appointment.date),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.primary,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Theme.border(isDark))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = appointment.doctorName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.heading(isDark)
                )
                Text(
                    text = appointment.specialty,
                    fontSize = 15.sp,
                    color = Theme.caption(isDark)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppIcon(
                        SemanticIcon.CONTENT_LOCATION,
                        size = 14.dp,
                        tint = if (isDark) Theme.slate500 else Theme.slate400
                    )
                    Text(
                        text = appointment.location,
                        fontSize = 12.sp,
                        color = if (isDark) Theme.slate500 else Theme.slate400,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconCircleButton(
                    icon = SemanticIcon.ACTION_EDIT,
                    tint = Theme.primary,
                    label = "Edit appointment"
                ) {
                    app.send(Action.ToAppointments(AppointmentsAction.OpenEdit(appointment.id)))
                }
                IconCircleButton(
                    icon = SemanticIcon.ACTION_DELETE,
                    tint = Theme.error,
                    label = "Delete appointment"
                ) {
                    app.send(Action.ToAppointments(AppointmentsAction.Delete(appointment.id)))
                }
            }
        }
    }
}
