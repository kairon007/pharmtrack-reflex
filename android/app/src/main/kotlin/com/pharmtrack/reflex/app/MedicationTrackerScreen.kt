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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.Domain.Log
import com.pharmtrack.reflex.core.Domain.Medication
import com.pharmtrack.reflex.core.Domain.MedicationSchedule
import com.pharmtrack.reflex.core.Screens.MedicationSegment
import com.pharmtrack.reflex.core.Screens.MedicationTrackerAction

/**
 * The medication tracker: today / inventory / history segments over one machine.
 * Every value shown comes from the machine's emit output; the view only renders.
 */
@Composable
fun MedicationTrackerScreen(app: ReflexApp, screen: ScreenData, tourActive: Boolean) {
    val data = screen.medicationTracker
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()

    OnboardingAutoScroll(active = tourActive, listState = listState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background(isDark))
    ) {
        TrackerHeader(app = app, data = data, isDark = isDark)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (data.segment) {
                MedicationSegment.TODAY -> {
                    item { PlanSummaryCard(data = data, isDark = isDark) }
                    item {
                        RoutineSection(
                            schedule = MedicationSchedule.MORNING,
                            data = data,
                            app = app,
                            isDark = isDark,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    item {
                        RoutineSection(
                            schedule = MedicationSchedule.AFTERNOON,
                            data = data,
                            app = app,
                            isDark = isDark,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    item {
                        RoutineSection(
                            schedule = MedicationSchedule.EVENING,
                            data = data,
                            app = app,
                            isDark = isDark,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                MedicationSegment.INVENTORY -> {
                    if (data.filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = SemanticIcon.TAB_MEDS,
                                title = "No medications yet",
                                message = "Add a medication to start tracking your supply.",
                                isDark = isDark
                            )
                        }
                    } else {
                        data.filtered.forEach { medication ->
                            item(key = medication.id) {
                                InventoryRow(medication = medication, data = data, app = app, isDark = isDark)
                            }
                        }
                    }
                }

                MedicationSegment.HISTORY -> {
                    if (data.historyLogs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = SemanticIcon.CONTENT_TIME,
                                title = "No history yet",
                                message = "Doses you mark as taken will appear here.",
                                isDark = isDark
                            )
                        }
                    } else {
                        data.historyLogs.forEach { log ->
                            item(key = log.id) {
                                LogRow(log = log, isDark = isDark)
                            }
                        }
                    }
                }
            }

            if (tourActive) {
                item { Spacer(modifier = Modifier.height(1.dp)) }
            }
        }
    }
}

@Composable
private fun TrackerHeader(app: ReflexApp, data: MedicationTrackerData, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.background(isDark))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Medications",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconCircleButton(
                icon = SemanticIcon.ACTION_ADD,
                tint = Theme.primary,
                label = "Add medication"
            ) {
                app.send(Action.ToMedicationTracker(MedicationTrackerAction.OpenAdd))
            }
        }

        OutlinedTextField(
            value = data.searchQuery,
            onValueChange = { text ->
                app.send(Action.ToMedicationTracker(MedicationTrackerAction.SetSearch(text)))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    text = "Search medications",
                    fontSize = 15.sp,
                    color = Theme.caption(isDark)
                )
            },
            leadingIcon = {
                AppIcon(SemanticIcon.ACTION_SEARCH, size = 20.dp, tint = Theme.slate400)
            },
            textStyle = TextStyle(color = Theme.heading(isDark), fontSize = 15.sp),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                title = "Today",
                isSelected = data.segment == MedicationSegment.TODAY,
                isDark = isDark
            ) {
                app.send(
                    Action.ToMedicationTracker(MedicationTrackerAction.SetSegment(MedicationSegment.TODAY))
                )
            }
            FilterChip(
                title = "Inventory",
                isSelected = data.segment == MedicationSegment.INVENTORY,
                isDark = isDark
            ) {
                app.send(
                    Action.ToMedicationTracker(MedicationTrackerAction.SetSegment(MedicationSegment.INVENTORY))
                )
            }
            FilterChip(
                title = "History",
                isSelected = data.segment == MedicationSegment.HISTORY,
                isDark = isDark
            ) {
                app.send(
                    Action.ToMedicationTracker(MedicationTrackerAction.SetSegment(MedicationSegment.HISTORY))
                )
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(data: MedicationTrackerData, isDark: Boolean) {
    CardSurface(isDark = isDark) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${data.takenTodayCount}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.heading(isDark)
                )
                Text(
                    text = "of ${data.totalCount} taken today",
                    fontSize = 15.sp,
                    color = Theme.caption(isDark)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (data.allTaken) {
                    AppIcon(SemanticIcon.ACTION_DONE, size = 20.dp, tint = Theme.success)
                }
            }
            ProgressBar(fraction = data.takenFraction, isDark = isDark)
        }
    }
}

@Composable
private fun RoutineSection(
    schedule: MedicationSchedule,
    data: MedicationTrackerData,
    app: ReflexApp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val medications = data.group(schedule)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = schedule.rawValue,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.caption(isDark),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (medications.isEmpty()) {
            Text(
                text = "Nothing scheduled",
                fontSize = 12.sp,
                color = if (isDark) Theme.slate500 else Theme.slate400,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                medications.forEach { medication ->
                    MedicationRow(medication = medication, data = data, app = app, isDark = isDark)
                }
            }
        }
    }
}

// The whole row sends markTaken; the reducer no-ops a repeat tap.
@Composable
private fun MedicationRow(
    medication: Medication,
    data: MedicationTrackerData,
    app: ReflexApp,
    isDark: Boolean
) {
    val isTaken = data.isTaken(medication.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(
                1.dp,
                if (isTaken) Theme.primary.copy(alpha = 0.4f) else Theme.border(isDark),
                RoundedCornerShape(16.dp)
            )
            .clickable {
                app.send(Action.ToMedicationTracker(MedicationTrackerAction.MarkTaken(medication.id)))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (isTaken) Theme.primary else Theme.border(isDark),
                        CircleShape
                    )
            )
            if (isTaken) {
                AppIcon(SemanticIcon.ACTION_CONFIRM, size = 14.dp, tint = Theme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = medication.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Text(
                text = "${medication.dosage} · ${medication.instructions}",
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
        if (data.isLowStock(medication.id)) {
            Text(
                text = "Low stock",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.amber500
            )
        }
        IconCircleButton(
            icon = SemanticIcon.ACTION_EDIT,
            tint = Theme.primary,
            label = "Edit ${medication.name}"
        ) {
            app.send(Action.ToMedicationTracker(MedicationTrackerAction.OpenEdit(medication.id)))
        }
    }
}

@Composable
private fun InventoryRow(
    medication: Medication,
    data: MedicationTrackerData,
    app: ReflexApp,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = medication.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.heading(isDark)
            )
            Text(
                text = medication.dosage,
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // icons.yaml has no bare minus glyph; action.remove stands in.
            IconCircleButton(
                icon = SemanticIcon.ACTION_REMOVE,
                tint = Theme.slate500,
                label = "Reduce stock of ${medication.name}"
            ) {
                app.send(Action.ToMedicationTracker(MedicationTrackerAction.DecrementStock(medication.id)))
            }
            Text(
                text = "${medication.stock}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (data.isLowStock(medication.id)) Theme.amber500 else Theme.heading(isDark),
                modifier = Modifier.widthIn(min = 32.dp)
            )
            IconCircleButton(
                icon = SemanticIcon.ACTION_ADD,
                tint = Theme.primary,
                label = "Add stock of ${medication.name}"
            ) {
                app.send(Action.ToMedicationTracker(MedicationTrackerAction.IncrementStock(medication.id)))
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
        AppIcon(SemanticIcon.ACTION_DONE, size = 22.dp, tint = Theme.success)
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = log.description,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.heading(isDark),
                maxLines = 1
            )
            Text(
                text = DateFormatting.time(log.date),
                fontSize = 12.sp,
                color = Theme.caption(isDark)
            )
        }
    }
}
