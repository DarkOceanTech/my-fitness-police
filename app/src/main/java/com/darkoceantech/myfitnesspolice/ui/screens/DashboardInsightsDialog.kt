package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun DashboardInsightsDialog(chart: DashboardChart, onClose: () -> Unit) {
    val insight = DashboardInsights.forChart(chart.id)
    val uri = LocalUriHandler.current
    WorkoutEditorDialog("Insights", "dashboard-insights-dialog", true, onClose, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).testTag("close-dashboard-insights")) { Text("Close") }
    }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("dashboard-insights-content"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(chart.title, style = MaterialTheme.typography.headlineSmall); Text(chart.caption, color = PoliceColors.Muted) }
            listOf("Calculation" to insight.formula, "What this helps you see" to insight.purpose,
                "Science baseline" to insight.evidence, "Interpretation and limits" to insight.limitations,
                "Data included" to DashboardInsights.dataContract).forEach { (title, text) ->
                item { InsightText(title, text) }
            }
            item { Text("References", style = MaterialTheme.typography.titleLarge) }
            if (insight.references.isEmpty()) item { Text("Application-defined reporting metric; no validated physiological or behavioral effect is claimed.", color = PoliceColors.Muted) }
            insight.references.forEach { reference -> item {
                InsightText("Peer-reviewed source", reference.citation)
                TextButton(onClick = { uri.openUri(reference.url) }) { Text("Read source ↗", color = PoliceColors.LightBlue) }
            } }
            item { Text("Chart data · ${chart.unit}" + (chart.rightUnit?.let { " / $it" } ?: ""), style = MaterialTheme.typography.titleLarge) }
            chart.labels.forEachIndexed { index, label ->
                val values = chart.series.mapIndexed { seriesIndex, series -> series.name + ": " + (series.values.getOrNull(index)?.let { chart.displayNumber(it, seriesIndex) } ?: "—") }
                item { InsightText(label, values.joinToString("\n")) }
            }
        }
    }
}

@Composable
private fun InsightText(title: String, text: String) {
    Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
