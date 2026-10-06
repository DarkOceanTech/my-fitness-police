package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch

@Composable
fun DashboardRoute(model: DashboardViewModel, modifier: Modifier = Modifier, onSettings: () -> Unit,
    onPlan: (String) -> Unit, onLog: (String) -> Unit,
    selectedDay: LocalDate = LocalDate.now(), onDayChanged: (LocalDate) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    var dailyLift by rememberSaveable { mutableStateOf<String?>(null) }
    var monthlyLift by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedDay) { model.selectDay(selectedDay) }
    LaunchedEffect(dailyLift) { dailyLift?.let(model::selectDailyLift) }
    LaunchedEffect(monthlyLift) { monthlyLift?.let(model::selectMonthlyLift) }
    DashboardScreen(state, modifier, onSettings, onDaySelected = onDayChanged,
        onDailyLift = { dailyLift = it }, onMonthlyLift = { monthlyLift = it }, onPlan = onPlan, onLog = onLog, onRetry = model::retry)
}

@Composable
fun DashboardScreen(state: DashboardUiState, modifier: Modifier = Modifier, onSettings: () -> Unit = {},
    onDaySelected: (LocalDate) -> Unit = {}, onDailyLift: (String) -> Unit = {}, onMonthlyLift: (String) -> Unit = {},
    onPlan: (String) -> Unit = {}, onLog: (String) -> Unit = {}, onRetry: () -> Unit = {}) {
    var insightId by rememberSaveable { mutableStateOf<String?>(null) }
    val report = state.report
    val allCharts = report?.let { it.weekly + it.daily + it.monthly }.orEmpty()
    LazyColumn(modifier.testTag("dashboard-home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "brand") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShieldMark(Modifier.size(46.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    SirenRule(Modifier.padding(top = 8.dp).width(88.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_settings), "HQ settings", tint = PoliceColors.LightBlue.copy(alpha = .55f), modifier = Modifier.fillMaxSize())
                        Text("HQ", style = MaterialTheme.typography.titleSmall, color = PoliceColors.Text,
                            modifier = Modifier.background(PoliceColors.Background.copy(alpha = .8f)))
                    }
                }
            }
        }
        if (state.loading) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp)); Text("Loading your training report…")
        } }
        else if (state.error) item {
            Text("Could not load your report.", color = PoliceColors.Error)
            PoliceButton(onClick = onRetry) { Text("Retry") }
        }
        if (report != null) {
            item(key = "launchpad") { LaunchpadCalendarSection(report, onDaySelected) }
            if (report.launches.isEmpty()) item(key = "launchpad-empty") { LaunchpadEmptyCard() }
            else items(report.launches, key = { "launch-${it.recorded}-${it.id}" }) { launch ->
                LaunchpadRoutineCard(launch, onPlan, onLog)
            }
            item(key = "weekly-heading") {
                DashboardSectionHeading("Weekly Metrics", "This week · " + report.selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .format(DateTimeFormatter.ofPattern("MMM d")) + " · last 8 weeks", "dashboard-weekly")
            }
            item(key = "weekly-carousel") { DashboardChartCarousel("weekly", report.weekly, listOf("Volume", "Muscles", "Density", "Intensity")) { insightId = it } }
            item(key = "daily-heading") {
                DashboardSectionHeading("Daily Performance", report.selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), "dashboard-daily")
                DashboardLiftSelector("Highlighted lift", report.dailyLifts, report.dailyLiftId, "daily-lift", onDailyLift)
            }
            item(key = "daily-carousel") { DashboardChartCarousel("daily", report.daily, listOf("Fatigue", "Time", "Intensity", "Rest")) { insightId = it } }
            item(key = "monthly-heading") {
                DashboardSectionHeading("Monthly Trends", report.selectedDay.format(DateTimeFormatter.ofPattern("MMMM yyyy")), "dashboard-monthly")
                DashboardLiftSelector("Trend lift", report.monthlyLifts, report.monthlyLiftId, "monthly-lift", onMonthlyLift)
            }
            item(key = "monthly-carousel") { DashboardChartCarousel("monthly", report.monthly, listOf("1RM", "PRs", "Adherence", "Rep ranges")) { insightId = it } }
        }
    }
    allCharts.firstOrNull { it.id == insightId }?.let { chart -> DashboardInsightsDialog(chart) { insightId = null } }
}

@Composable
private fun DashboardChartCarousel(section: String, charts: List<DashboardChart>, labels: List<String>, onInsights: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { charts.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, pageSpacing = 12.dp, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().testTag("$section-chart-carousel")) { index ->
            DashboardChartCard(charts[index], onInsights = { onInsights(charts[index].id) })
        }
        Row(Modifier.fillMaxWidth().testTag("$section-chart-tabs")) {
            charts.forEachIndexed { index, chart ->
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    modifier = Modifier.weight(1f).testTag("${chart.id}-tab").semantics { selected = pager.currentPage == index },
                    contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(labels[index], style = MaterialTheme.typography.labelSmall,
                            color = if (pager.currentPage == index) PoliceColors.Text else PoliceColors.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(.7f).height(2.dp).background(if (pager.currentPage == index) PoliceColors.Red else PoliceColors.Blue))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardSectionHeading(title: String, subtitle: String, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        SirenRule(Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun DashboardLiftSelector(label: String, lifts: List<LiftChoice>, selected: String?, tag: String, onSelect: (String) -> Unit) {
    if (lifts.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PoliceOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
            Text("$label · ${lifts.find { it.id == selected }?.name.orEmpty()}", modifier = Modifier.weight(1f)); Text("↕")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            lifts.forEach { lift -> DropdownMenuItem(text = { Text(lift.name) }, onClick = { expanded = false; onSelect(lift.id) }) }
        }
    }
}
