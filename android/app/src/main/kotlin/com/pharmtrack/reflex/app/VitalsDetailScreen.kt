package com.pharmtrack.reflex.app

// Port of iOS VitalsDetailScreen.swift: one screen, one data source, one Action per interaction.

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.ValueFormat
import com.pharmtrack.reflex.core.Domain.Vital
import com.pharmtrack.reflex.core.Domain.VitalType
import com.pharmtrack.reflex.core.Screens.VitalRange
import com.pharmtrack.reflex.core.Screens.VitalsAction
import com.pharmtrack.reflex.core.Screens.VitalsDetailAction
import com.pharmtrack.reflex.core.Support.Instant

@Composable
fun VitalsDetailScreen(app: ReflexApp, screen: ScreenData) {
    val isDark = isSystemInDarkTheme()
    val data = screen.vitalsDetail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        DetailHeader(app, isDark)
        CalendarStrip(app, data, isDark)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { MetricFilterSection(app, data, isDark) }
            item { TimeRangeSection(app, data, isDark) }
            item { ChartSection(data, isDark) }
            item { SelectedDateSection(app, data, isDark) }
            item { AllReadingsSection(app, data, isDark) }
        }
    }
}

@Composable
private fun DetailHeader(app: ReflexApp, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.background(isDark).copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable { app.send(Action.ToVitals(VitalsAction.CloseDetail)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(SemanticIcon.ACTION_BACK, size = 18.dp, tint = Theme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Back",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "All Vitals",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Theme.heading(isDark)
        )
        Spacer(modifier = Modifier.weight(1f))
        AppIcon(SemanticIcon.ACTION_BACK, size = 18.dp, tint = Color.Transparent)
    }
}

@Composable
private fun MetricFilterSection(app: ReflexApp, data: VitalsDetailData, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Metric",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Theme.caption(isDark),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Theme.slate800 else Theme.slate200)
                .padding(4.dp)
        ) {
            MetricChip(app, data, VitalType.WEIGHT, isDark)
            MetricChip(app, data, VitalType.BLOOD_PRESSURE, isDark)
            MetricChip(app, data, VitalType.BLOOD_SUGAR, isDark)
        }
    }
}

@Composable
private fun RowScope.MetricChip(
    app: ReflexApp,
    data: VitalsDetailData,
    type: VitalType,
    isDark: Boolean
) {
    val isSelected = data.metric == type
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    if (isDark) Theme.slate700 else Color.White
                } else {
                    Color.Transparent
                }
            )
            .clickable {
                app.send(Action.ToVitalsDetail(VitalsDetailAction.SelectMetric(type)))
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) {
                if (isDark) Color.White else Theme.primary
            } else {
                Theme.caption(isDark)
            }
        )
    }
}

@Composable
private fun TimeRangeSection(app: ReflexApp, data: VitalsDetailData, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time range",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Theme.caption(isDark),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = data.range.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VitalRange.entries.forEach { range ->
                RangeChip(app, data, range, isDark)
            }
        }
    }
}

@Composable
private fun RowScope.RangeChip(
    app: ReflexApp,
    data: VitalsDetailData,
    range: VitalRange,
    isDark: Boolean
) {
    val isSelected = data.range == range
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    Theme.primary
                } else if (isDark) {
                    Theme.slate800
                } else {
                    Theme.slate100
                }
            )
            .clickable {
                app.send(Action.ToVitalsDetail(VitalsDetailAction.SelectRange(range)))
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = range.chipLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) {
                Color.White
            } else if (isDark) {
                Theme.slate300
            } else {
                Theme.slate600
            }
        )
    }
}

@Composable
private fun CalendarStrip(app: ReflexApp, data: VitalsDetailData, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Theme.slate900 else Color.White)
            .border(
                width = 1.dp,
                color = if (isDark) Theme.slate800 else Theme.slate100,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp),
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
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            app.send(Action.ToVitalsDetail(VitalsDetailAction.PreviousWeek))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        SemanticIcon.ACTION_BACK,
                        size = 18.dp,
                        tint = Theme.caption(isDark)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            app.send(Action.ToVitalsDetail(VitalsDetailAction.NextWeek))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        SemanticIcon.ACTION_FORWARD,
                        size = 18.dp,
                        tint = Theme.caption(isDark)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.stripDates.forEach { date ->
                DayCell(app, data, date, isDark)
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    app: ReflexApp,
    data: VitalsDetailData,
    date: Instant,
    isDark: Boolean
) {
    val isSelected = data.isSelected(date)
    val hasReading = data.hasDot(date)
    val shape = RoundedCornerShape(12.dp)
    val shadowColor = if (isSelected) Theme.primary.copy(alpha = 0.3f) else Color.Transparent
    Column(
        modifier = Modifier
            .weight(1f)
            .shadow(
                elevation = if (isSelected) 5.dp else 0.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(if (isSelected) Theme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    Color.Transparent
                } else if (isDark) {
                    Theme.slate800
                } else {
                    Theme.slate200
                },
                shape = shape
            )
            .height(64.dp)
            .clickable {
                app.send(Action.ToVitalsDetail(VitalsDetailAction.SelectDay(date)))
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = DateFormatting.weekday(date).uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) {
                Color.White.copy(alpha = 0.85f)
            } else if (isDark) {
                Theme.slate500
            } else {
                Theme.slate400
            }
        )
        Text(
            text = DateFormatting.dayNumber(date),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Theme.heading(isDark)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasReading) Theme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun ChartSection(data: VitalsDetailData, isDark: Boolean) {
    val trend = data.trend
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface(isDark))
            .border(
                width = 1.dp,
                color = if (isDark) Theme.slate800 else Theme.slate100,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = data.metric.detailLabel.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Theme.caption(isDark),
                    letterSpacing = 1.2.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = data.formattedLatestValue,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Theme.heading(isDark)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = data.metric.unit,
                        fontSize = 15.sp,
                        color = Theme.caption(isDark)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (trend != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(trend.color.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(trend.icon, size = 12.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = trend.magnitude,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = trend.color
                    )
                }
            }
        }
        if (data.chartValues.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppIcon(
                    SemanticIcon.CONTENT_HISTORY,
                    size = 32.dp,
                    tint = if (isDark) Theme.slate700 else Theme.slate300
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No readings yet for ${data.metric.detailLabel}",
                    fontSize = 12.sp,
                    color = if (isDark) Theme.slate500 else Theme.slate400
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                TrendChart(
                    data = data.chartValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    closed = true,
                    lineColor = Theme.primary,
                    fillBrush = Brush.verticalGradient(
                        listOf(Theme.primary.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                val chartMax = data.chartMax
                val chartMin = data.chartMin
                if (chartMax != null && chartMin != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 4.dp)
                            .offset(y = 145.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ValueFormat.fixed(chartMin, decimals = 0),
                            fontSize = 11.sp,
                            color = if (isDark) Theme.slate500 else Theme.slate400
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = ValueFormat.fixed(chartMax, decimals = 0),
                            fontSize = 11.sp,
                            color = if (isDark) Theme.slate500 else Theme.slate400
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateSection(app: ReflexApp, data: VitalsDetailData, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Readings for",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Theme.caption(isDark),
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = DateFormatting.fullDate(data.selectedDate),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Theme.heading(isDark)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${data.selectedDayCount} entries",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary
            )
        }
        if (data.selectedDay.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Theme.slate900 else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (isDark) Theme.slate800 else Theme.slate100,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcon(
                        SemanticIcon.TAB_CALENDAR,
                        size = 24.dp,
                        tint = if (isDark) Theme.slate600 else Theme.slate400
                    )
                    Text(
                        text = "No readings on this day",
                        fontSize = 12.sp,
                        color = if (isDark) Theme.slate500 else Theme.slate400
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                data.selectedDay.forEach { vital ->
                    ReadingRow(app, data, vital, isDark)
                }
            }
        }
    }
}

@Composable
private fun AllReadingsSection(app: ReflexApp, data: VitalsDetailData, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All readings",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Theme.caption(isDark),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${data.totalCount} total",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary
            )
        }
        if (data.allReadings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Theme.slate900 else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (isDark) Theme.slate800 else Theme.slate100,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap + to add your first reading",
                    fontSize = 12.sp,
                    color = if (isDark) Theme.slate500 else Theme.slate400
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                data.allReadings.forEach { vital ->
                    ReadingRow(app, data, vital, isDark)
                }
            }
        }
    }
}

@Composable
private fun ReadingRow(app: ReflexApp, data: VitalsDetailData, vital: Vital, isDark: Boolean) {
    val isHighlighted = data.highlightedVitalId == vital.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHighlighted) {
                    Theme.primary.copy(alpha = 0.15f)
                } else if (isDark) {
                    Theme.slate900
                } else {
                    Color.White
                }
            )
            .border(
                width = if (isHighlighted) 1.5.dp else 1.dp,
                color = if (isHighlighted) {
                    Theme.primary
                } else if (isDark) {
                    Theme.slate800
                } else {
                    Theme.slate100
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                app.send(Action.ToVitalsDetail(VitalsDetailAction.TapReading(vital.id)))
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) Theme.slate800 else Theme.slate50),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                SemanticIcon.fromVitalType(vital.type),
                size = 22.dp,
                tint = Theme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = ValueFormat.reading(vital),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Theme.heading(isDark)
            )
            Text(
                text = "${DateFormatting.mediumDate(vital.date)} ${DateFormatting.time(vital.date)}",
                fontSize = 11.sp,
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
        AppIcon(
            SemanticIcon.ACTION_FORWARD,
            size = 14.dp,
            tint = if (isDark) Theme.slate500 else Theme.slate400
        )
    }
}
