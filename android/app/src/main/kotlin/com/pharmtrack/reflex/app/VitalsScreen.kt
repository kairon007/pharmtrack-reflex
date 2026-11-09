package com.pharmtrack.reflex.app

// Port of iOS VitalsScreen.swift: every value comes from the machine, every tap is an Action.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Screens.VitalsAction

@Composable
fun VitalsScreen(app: ReflexApp, screen: ScreenData, tourActive: Boolean) {
    val isDark = isSystemInDarkTheme()
    val data = screen.vitals
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        if (data.detailPresented) {
            BackHandler { app.send(Action.ToVitals(VitalsAction.CloseDetail)) }
            VitalsDetailScreen(app, screen)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                VitalsHeader(app, isDark)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item { MetricCards(app, data, isDark) }
                    item { VitalsTrendCard(data, isDark) }
                    item { RecentReadings(app, data, isDark) }
                    if (tourActive) {
                        item { Spacer(modifier = Modifier.height(1.dp)) }
                    }
                }
            }
            OnboardingAutoScroll(active = tourActive, listState = listState)
        }
    }
}

@Composable
private fun VitalsHeader(app: ReflexApp, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Vitals",
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.heading(isDark)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconCircleButton(
            icon = SemanticIcon.ACTION_ADD,
            tint = Theme.primary,
            label = "Add reading",
            action = { app.send(Action.ToVitals(VitalsAction.OpenAdd)) }
        )
    }
}

@Composable
private fun MetricCards(app: ReflexApp, data: VitalsData, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(app, data, VitalType.WEIGHT, isDark)
        MetricCard(app, data, VitalType.BLOOD_PRESSURE, isDark)
        MetricCard(app, data, VitalType.BLOOD_SUGAR, isDark)
    }
}

@Composable
private fun RowScope.MetricCard(
    app: ReflexApp,
    data: VitalsData,
    type: VitalType,
    isDark: Boolean
) {
    val isSelected = data.selectedMetric == type
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Theme.primary else Theme.border(isDark),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { app.send(Action.ToVitals(VitalsAction.SelectMetric(type))) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = type.title,
            fontSize = 12.sp,
            color = Theme.caption(isDark)
        )
        Text(
            text = data.cardValue(type),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.heading(isDark),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = type.unit,
            fontSize = 12.sp,
            color = if (isDark) Theme.slate500 else Theme.slate400
        )
    }
}

@Composable
private fun VitalsTrendCard(data: VitalsData, isDark: Boolean) {
    val trend = data.trend
    CardSurface(isDark = isDark) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${data.selectedMetric.title} trend",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.caption(isDark)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = data.formattedLatestValue,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.heading(isDark)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = data.selectedMetric.unit,
                            fontSize = 15.sp,
                            color = Theme.caption(isDark)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (trend != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(trend.color.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(trend.icon, size = 14.dp, tint = trend.color)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = trend.magnitude,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = trend.color
                        )
                    }
                }
            }
            if (data.ascending.size > 1) {
                TrendChart(
                    data = data.ascending.map { it.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    closed = false,
                    lineColor = Theme.primary
                )
            } else {
                Text(
                    text = if (data.ascending.isEmpty()) {
                        "Add a reading to see your trend"
                    } else {
                        "Add another reading to see your trend"
                    },
                    fontSize = 15.sp,
                    color = Theme.caption(isDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentReadings(app: ReflexApp, data: VitalsData, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = "Recent readings",
            actionTitle = "View all",
            action = { app.send(Action.ToVitals(VitalsAction.OpenDetail)) },
            isDark = isDark
        )
        if (data.ascending.isEmpty()) {
            EmptyStateCard(
                icon = SemanticIcon.TAB_VITALS,
                title = "No readings yet",
                message = "Log your first ${data.selectedMetric.title.lowercase()} reading to start tracking.",
                isDark = isDark
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.recent.forEach { vital ->
                    VitalRow(app, data, vital, isDark)
                }
            }
        }
    }
}

@Composable
private fun VitalRow(app: ReflexApp, data: VitalsData, vital: Vital, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) Theme.backgroundDark else Theme.slate50),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                SemanticIcon.fromVitalName(data.iconName(vital.id)),
                size = 20.dp,
                tint = Theme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = ValueFormat.reading(vital),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Text(
                text = "${DateFormatting.mediumDate(vital.date)} ${DateFormatting.time(vital.date)}",
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!vital.notes.isNullOrEmpty()) {
            AppIcon(
                SemanticIcon.CONTENT_INFO,
                size = 16.dp,
                tint = if (isDark) Theme.slate500 else Theme.slate400
            )
        }
        IconCircleButton(
            icon = SemanticIcon.ACTION_DELETE,
            tint = Theme.error,
            label = "Delete reading",
            action = { app.send(Action.ToVitals(VitalsAction.Delete(vital.id))) }
        )
    }
}
