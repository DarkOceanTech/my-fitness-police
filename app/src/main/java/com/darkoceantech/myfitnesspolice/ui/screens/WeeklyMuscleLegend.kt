package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.domain.muscles.MuscleLabels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.domain.dashboard.DashboardChart
import com.darkoceantech.myfitnesspolice.domain.dashboard.DashboardMetricsCalculator
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import java.util.Locale

/** Three balanced name/value pairs, alphabetical down each column, with enough rows for every group. */
@Composable
internal fun WeeklyMuscleLegend(chart: DashboardChart, week: Int) {
    val sorted = remember(chart.series) {
        chart.series.withIndex().sortedWith(compareBy({ MuscleLabels.full(it.value.name).lowercase(Locale.ROOT) }, { it.value.name }))
    }
    val rowCount = (sorted.size + 2) / 3
    val style = MaterialTheme.typography.labelSmall
    val countStyle = style.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Reserve the same numeric width for all columns and every week, preventing values from shifting.
    val valueWidth = remember(chart.series, countStyle, density) {
        val widths = (listOf("0") + chart.series.flatMap { series -> series.values.map {
            DashboardMetricsCalculator.number(it ?: 0.0)
        } }).map { measurer.measure(it, countStyle).size.width }
        with(density) { widths.max().toDp() + 4.dp }.coerceIn(24.dp, 42.dp)
    }
    Column(Modifier.fillMaxWidth().testTag("weekly-muscles-legend"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rowCount) { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    val entry = sorted.getOrNull(column * rowCount + row)
                    if (entry == null) Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    else Row(Modifier.weight(1f).heightIn(min = 24.dp).fillMaxHeight()
                        .testTag("muscle-legend-item-${entry.index}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(8.dp).background(muscleGroupColor(entry.value.name), CircleShape)
                            .testTag("muscle-legend-marker-${entry.index}"))
                        Text(MuscleLabels.abbreviated(entry.value.name), Modifier.weight(1f).testTag("muscle-legend-label-${entry.index}"),
                            style = style, color = PoliceColors.Muted)
                        Text(DashboardMetricsCalculator.number(entry.value.values.getOrNull(week) ?: 0.0),
                            Modifier.width(valueWidth).testTag("muscle-legend-count-${entry.index}"),
                            style = countStyle, color = PoliceColors.Text, textAlign = TextAlign.End, maxLines = 1,
                            softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp))
                    }
                }
            }
        }
    }
}
