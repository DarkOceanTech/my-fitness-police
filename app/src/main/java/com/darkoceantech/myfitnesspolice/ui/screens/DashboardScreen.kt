package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.WeeklyProgress
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(weekly: WeeklyProgress?, modifier: Modifier = Modifier, onSettings: () -> Unit = {}) {
    val date = SimpleDateFormat("MMM d", Locale.getDefault())
    val weekLabel = weekly?.let {
        "This week · ${date.format(Date(it.weekStartMillis))} – ${date.format(Date(it.weekEndMillis - 1))}"
    } ?: "This week · Mon – Sun"
    val pager = rememberPagerState { ProgressMetric.entries.size }
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.testTag("dashboard-home"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShieldMark(Modifier.size(46.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    SirenRule(Modifier.padding(top = 8.dp).width(88.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = PoliceColors.LightBlue)
                }
            }
        }
        item {
            Text(weekLabel, style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("weekly-progress-cards"),
                    pageSpacing = 12.dp, beyondViewportPageCount = ProgressMetric.entries.size - 1,
                    verticalAlignment = Alignment.Top) { page ->
                    ProgressChartCard(ProgressMetric.entries[page], weekly, Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ProgressMetric.entries.forEachIndexed { index, metric ->
                        val selected = pager.currentPage == index
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Show ${metric.label} chart" },
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(metric.label, style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) PoliceColors.Text else PoliceColors.Muted)
                                Box(Modifier.width(if (selected) 22.dp else 8.dp).height(3.dp)
                                    .background(if (selected) PoliceColors.Red else PoliceColors.Blue.copy(alpha = .6f), CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
