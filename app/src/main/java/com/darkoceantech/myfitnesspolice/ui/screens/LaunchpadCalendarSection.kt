package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DailyBlotterCalendarSection(report: DashboardReport, onDaySelected: (LocalDate) -> Unit) {
    var instructions by rememberSaveable { mutableStateOf(false) }
    var navigationReset by remember { mutableIntStateOf(0) }
    val selectedDay by rememberUpdatedState(report.selectedDay)
    val selectDay by rememberUpdatedState(onDaySelected)
    val selectExplicitly: (LocalDate) -> Unit = { date ->
        // Also cancel a swipe still settling when Today (or a day) is tapped. If today is
        // already selected the model may not emit a new report, so resetting is explicit.
        navigationReset++
        selectDay(date)
    }
    Column(Modifier.fillMaxWidth().testTag("launchpad-section"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily Blotter", Modifier.weight(1f, fill = false).testTag("dashboard-launchpad").semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, color = PoliceColors.Text,
                    maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 22.sp))
                IconButton(onClick = { instructions = true }, modifier = Modifier.testTag("daily-blotter-info")) {
                    Surface(Modifier.size(23.dp), shape = CircleShape, color = Color.Transparent,
                        border = BorderStroke(1.dp, PoliceColors.LightBlue)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("i", Modifier.semantics { contentDescription = "Daily Blotter instructions" },
                                color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            TextButton(onClick = { selectExplicitly(LocalDate.now()) }, modifier = Modifier.testTag("daily-blotter-today")) {
                Text("Today", color = PoliceColors.LightBlue)
            }
        }
        // Three pages are continuously rebased around the selected week. This permits unlimited
        // week navigation without an arbitrary date window or an enormous saved pager index.
        key(report.weekStart, report.weekStartsOn, navigationReset) {
            val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            val generation = navigationReset
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.filter { it != 1 }.collect { page ->
                    if (generation == navigationReset) selectDay(selectedDay.plusWeeks((page - 1).toLong()))
                }
            }
            Box(Modifier.fillMaxWidth().testTag("launchpad-calendar")) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("daily-blotter-weeks"),
                    beyondViewportPageCount = 1) { page ->
                    val first = report.weekStart.plusWeeks((page - 1).toLong())
                    BlotterWeek(first, report, selectExplicitly)
                }
            }
        }
        Text(report.selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
            Modifier.testTag("daily-blotter-selected-date"), style = MaterialTheme.typography.titleMedium)
    }
    if (instructions) DailyBlotterInstructions { instructions = false }
}

@Composable
private fun BlotterWeek(first: LocalDate, report: DashboardReport, onDaySelected: (LocalDate) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = if (maxWidth < 372.dp) 2.dp else 4.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            (0..6).forEach { offset ->
                val date = first.plusDays(offset.toLong())
                BlotterDay(date, report.calendar.firstOrNull { it.date == date },
                    date == report.selectedDay, date == report.today, Modifier.weight(1f), onDaySelected)
            }
        }
    }
}

@Composable
private fun BlotterDay(date: LocalDate, data: CalendarDay?, isSelected: Boolean, isToday: Boolean,
    modifier: Modifier, onDaySelected: (LocalDate) -> Unit) {
    val status = if (data == null) "Activity loads when this week is selected"
        else if (data.recorded && data.scheduled) "recorded workout and scheduled routine"
        else if (data.recorded) "recorded workout" else if (data.scheduled) "scheduled routine" else "no activity"
    Surface(onClick = { onDaySelected(date) },
        modifier = modifier.heightIn(min = 88.dp).testTag("calendar-$date").semantics {
            selected = isSelected
            stateDescription = when {
                isToday && isSelected -> "Today and selected"
                isToday -> "Today"
                isSelected -> "Selected"
                else -> "Not selected"
            }
            contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) +
                (if (isToday) ", today" else "") + (if (isSelected) ", selected" else "") + ", $status"
        }, shape = RoundedCornerShape(20.dp),
        color = when { isSelected -> PoliceColors.Destructive; isToday -> PoliceColors.ButtonBlueDark; else -> Color.Transparent },
        border = BorderStroke(if (isToday || isSelected) 2.dp else 1.dp,
            when { isToday -> PoliceColors.Blue; isSelected -> PoliceColors.Red; else -> PoliceColors.Border })) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEE")), Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp))
            Text(date.dayOfMonth.toString(), Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp))
            Text(if (data == null) "·" else if (data.recorded && data.scheduled) "✓ •"
                else if (data.recorded) "✓" else if (data.scheduled) "•" else "—",
                color = if (isSelected) PoliceColors.Text else if (data?.recorded == true || data?.scheduled == true)
                    PoliceColors.LightBlue else PoliceColors.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 14.sp))
        }
    }
}

@Composable
private fun DailyBlotterInstructions(onBack: () -> Unit) {
    WorkoutEditorDialog("Daily Blotter instructions", "daily-blotter-instructions", true, onBack,
        fullScreen = true, footer = {
            PoliceOutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("daily-blotter-instructions-back")) {
                Text("Back")
            }
        }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("daily-blotter-instructions-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                "Select a day" to "Tap a date to see scheduled training plans and completed training logs for that day. The daily, weekly and monthly dashboards follow your selected date. Days without activity keep their empty state.",
                "Swipe through weeks" to "Swipe left for the next complete week or right for the previous week. Your selected weekday follows you into that week, even across month and year boundaries. All seven days stay visible in one row.",
                "Date highlights" to "Blue marks today. Red marks your selected date. When today is selected, its red background keeps a blue outline so both meanings remain visible. A dot marks scheduled training and a check marks a recorded training log.",
                "Return to Today" to "Press Today to return to the device’s current local date. The displayed week and all three dashboard periods reset with it.",
                "Week starts on" to "Open HQ settings and choose Monday or Sunday under Week starts on. The calendar immediately changes its day order and week boundaries. Weekly dashboard totals use the same setting; your selected date stays selected."
            ).forEach { (title, body) ->
                item {
                    Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                        border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Separate LazyColumn items keep a long day's routine list virtualized. */
@Composable
internal fun DailyBlotterEmptyCard() {
    Surface(Modifier.fillMaxWidth().testTag("launchpad-empty"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No routines or logs for this day.", style = MaterialTheme.typography.titleMedium)
            Text("Schedule training from a plan in Academy, or return after completing a training plan.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}

@Composable
internal fun DailyBlotterRoutineCard(item: DayLaunchItem, onPlan: (String) -> Unit, onLog: (String) -> Unit) {
    Surface(onClick = { if (item.recorded) onLog(item.id) else onPlan(item.id) },
        modifier = Modifier.fillMaxWidth().testTag("launch-${item.id}"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
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
