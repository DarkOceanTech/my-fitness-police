package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LaunchpadCalendarSection(report: DashboardReport, onDaySelected: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("launchpad-section"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DashboardSectionHeading("Launchpad", "Swipe through your patrol · 10 days back or ahead", "dashboard-launchpad")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth - 24.dp) / 5
            val selectedIndex = report.calendar.indexOfFirst { it.date == report.selectedDay }
            val scroll = rememberLazyListState((selectedIndex - 2).coerceIn(0, 16))
            LazyRow(state = scroll, flingBehavior = rememberSnapFlingBehavior(scroll), modifier = Modifier.fillMaxWidth().testTag("launchpad-calendar"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(report.calendar, key = { it.date.toString() }) { day ->
                    val isSelected = day.date == report.selectedDay
                    Surface(onClick = { onDaySelected(day.date) }, modifier = Modifier.width(dayWidth).heightIn(min = 96.dp)
                        .testTag("calendar-${day.date}").semantics {
                            selected = isSelected
                            contentDescription = day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) +
                                if (day.recorded) ", recorded workout" else if (day.scheduled) ", scheduled routine" else ", no activity"
                        }, shape = RoundedCornerShape(24.dp), color = if (isSelected) PoliceColors.Raised else androidx.compose.ui.graphics.Color.Transparent,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) PoliceColors.Red else PoliceColors.Border)) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelMedium)
                            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                            Text(if (day.recorded && day.scheduled) "✓ •" else if (day.recorded) "✓" else if (day.scheduled) "•" else "—",
                                color = if (day.recorded || day.scheduled) PoliceColors.LightBlue else PoliceColors.Muted, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Text(report.selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")), style = MaterialTheme.typography.titleMedium)
    }
}

/** Separate LazyColumn items keep a long day's routine list virtualized. */
@Composable
internal fun LaunchpadEmptyCard() {
    Surface(Modifier.fillMaxWidth().testTag("launchpad-empty"), shape = PoliceCardShape,
            color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No routines or logs for this day.", style = MaterialTheme.typography.titleMedium)
                Text("Schedule training from a plan in Academy, or return after completing a training plan.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
    }
}

@Composable
internal fun LaunchpadRoutineCard(item: DayLaunchItem, onPlan: (String) -> Unit, onLog: (String) -> Unit) {
    Surface(onClick = { if (item.recorded) onLog(item.id) else onPlan(item.id) }, modifier = Modifier.fillMaxWidth().testTag("launch-${item.id}"),
                shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.recorded) "✓" else "•", color = PoliceColors.LightBlue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                    }
                    Text("→", color = PoliceColors.LightBlue)
                }
            }
}
