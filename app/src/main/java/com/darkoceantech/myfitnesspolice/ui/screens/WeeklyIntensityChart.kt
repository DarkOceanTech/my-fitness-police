package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

/** Raw counts stay in the model; only the segment widths are normalized to 100%. */
@Composable
internal fun WeeklyIntensityChart(chart: DashboardChart, selectedWeek: Int, rowHeights: List<Dp> = emptyList(), onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("weekly-intensity-chart")) {
        chart.labels.forEachIndexed { week, date ->
            val percentages = chart.intensityPercentages(week)
            val selected = week == selectedWeek
            Row(Modifier.fillMaxWidth().heightIn(min = rowHeights.getOrNull(week) ?: 28.dp)
                .testTag("weekly-intensity-week-$week")
                .semantics {
                    this.selected = selected
                    contentDescription = "Week of $date. " + chart.series.mapIndexed { i, zone ->
                        "${zone.name}: ${DashboardMetricsCalculator.number(zone.values.getOrNull(week) ?: 0.0)}, " +
                            "${DashboardMetricsCalculator.number(percentages[i])} percent"
                    }.joinToString("; ")
                }
                .background(if (selected) PoliceColors.LightBlue.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(role = Role.Button) { onSelect(week) }, verticalAlignment = Alignment.CenterVertically) {
                Text(date, Modifier.width(56.dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) PoliceColors.Text else PoliceColors.Muted, maxLines = 1)
                Canvas(Modifier.weight(1f).height(22.dp).testTag("weekly-intensity-bar-$week")) {
                    // Grid remains visible on empty weeks; never draw a percentage segment for them.
                    for (tick in 0..4) {
                        val x = size.width * tick / 4
                        drawLine(PoliceColors.Border, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    var offset = 0f
                    percentages.forEachIndexed { index, percent ->
                        val width = size.width * (percent / 100).toFloat()
                        if (width > 0) {
                            drawRect(seriesColor(index), Offset(offset, 2.dp.toPx()), Size(width, 18.dp.toPx()))
                            val text = DashboardMetricsCalculator.number(percent) + "%"
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = 10.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                color = (if (seriesColor(index).luminance() > .4f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White).toArgb()
                            }
                            if (paint.measureText(text) + 8.dp.toPx() <= width) drawIntoCanvas {
                                it.nativeCanvas.drawText(text, offset + width / 2,
                                    size.height / 2 - (paint.ascent() + paint.descent()) / 2, paint)
                            }
                        }
                        offset += width
                    }
                    if (selected) drawLine(PoliceColors.Text.copy(alpha = .75f),
                        Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 25, 50, 75, 100).forEach { tick ->
                Text(tick.toString(), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
            }
        }
        Text("Percentage of Total Volume", Modifier.fillMaxWidth().padding(top = 8.dp)
            .testTag("weekly-intensity-axis-label"), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Week of ${chart.labels.getOrNull(selectedWeek).orEmpty()}", Modifier.weight(1f)
            .testTag("weekly-intensity-selected-week"), style = MaterialTheme.typography.labelLarge, color = PoliceColors.LightBlue)
        Text("${DashboardMetricsCalculator.number((chart.weeklySetTotals.getOrNull(selectedWeek) ?: 0).toDouble())} total sets",
            Modifier.testTag("weekly-intensity-summary-total"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
    }
    // Two pairs per row keep all labels and right-aligned counts readable on phones.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chart.series.chunked(2).forEachIndexed { rowIndex, zones ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                zones.forEachIndexed { columnIndex, zone ->
                    val index = rowIndex * 2 + columnIndex
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(seriesColor(index), CircleShape)
                            .testTag("weekly-intensity-marker-$index"))
                        Text(listOf("Low <60%", "Mid 60-85%", "High >85%", "Unknown 1RM").getOrElse(index) { zone.name },
                            Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                        Text(DashboardMetricsCalculator.number(zone.values.getOrNull(selectedWeek) ?: 0.0),
                            Modifier.testTag("weekly-intensity-legend-$index"),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

/** Measure at the same width/font scale, then distribute the height difference across weekly rows.
 * Reference/probe slots are measured only, never placed or exposed as duplicate UI. */
@Composable
internal fun HeightMatchedIntensityCard(chart: DashboardChart, muscleChart: DashboardChart, onInsights: () -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val naturalConstraints = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val reference = subcompose("reference") {
            Box(Modifier.clearAndSetSemantics {}) { DashboardChartCard(muscleChart, onInsights = {}) }
        }.single().measure(naturalConstraints)
        val probe = subcompose("probe") {
            Box(Modifier.clearAndSetSemantics {}) { DashboardChartCard(chart, onInsights = {}) }
        }.single().measure(naturalConstraints)
        val extra = (reference.height - probe.height).coerceAtLeast(0)
        val count = chart.labels.size.coerceAtLeast(1)
        val rowHeights = List(count) { index -> 28.dp + (extra / count + if (index < extra % count) 1 else 0).toDp() }
        val content = subcompose("content") {
            DashboardChartCard(chart, intensityRowHeights = rowHeights, onInsights = onInsights)
        }.single().measure(naturalConstraints)
        layout(content.width, content.height) { content.placeRelative(0, 0) }
    }
}
