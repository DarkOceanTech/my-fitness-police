package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfitnesspolice.data.WeeklyProgress
import com.example.myfitnesspolice.data.WeeklyProgressPoint
import com.example.myfitnesspolice.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal enum class ProgressMetric(val label: String, val totalLabel: String, val info: String) {
    Sets("Sets", "Sets",
        "Recorded sets in workouts started this week, Monday–Sunday. Includes warmup and working sets."),
    Reps("Reps", "Reps",
        "Actual repetitions recorded in workouts started this week. Planned and unfinished sets are excluded."),
    Weight("Weight", "Lbs lifted",
        "Total pounds lifted in workouts started this week: set weight × actual reps, added across recorded sets."),
    Activity("Activity", "of Work",
        "Time spent investing into your sexy body in workouts started this week. Includes sets and breaks, excludes pauses, and updates during an active workout.");

    fun chartValue(week: WeeklyProgressPoint): Double = when (this) {
        Sets -> week.sets.toDouble()
        Reps -> week.reps.toDouble()
        Weight -> week.weightPounds.toDouble()
        Activity -> week.activityMillis / 60_000.0
    }
    val axisUnit: String get() = when (this) {
        Weight -> "Lbs"
        Activity -> "Minutes"
        else -> label
    }
}

@Composable
internal fun ProgressChartCard(metric: ProgressMetric, weekly: WeeklyProgress?, modifier: Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    val value = weekly?.let {
        when (metric) {
            ProgressMetric.Sets -> number.format(it.sets)
            ProgressMetric.Reps -> number.format(it.reps)
            ProgressMetric.Weight -> number.format(it.weightPounds)
            ProgressMetric.Activity -> "${number.format(it.activityMillis.coerceAtLeast(0) / 60_000)}m"
        }
    } ?: "—"
    Surface(modifier.testTag("weekly-${metric.label}-card"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(value, style = StatTypography.copy(fontSize = 28.sp),
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("weekly-${metric.label}-value"))
                    Text(metric.totalLabel, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alignByBaseline())
                }
                Box {
                    TextButton(onClick = { showInfo = true }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("weekly-${metric.label}-insights").semantics { contentDescription = "See insights about ${metric.label}" },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                        Text("See insights", color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(18.dp).border(1.dp, PoliceColors.LightBlue, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text("i", color = PoliceColors.LightBlue, fontSize = 12.sp, lineHeight = 12.sp)
                        }
                    }
                    DropdownMenu(expanded = showInfo, onDismissRequest = { showInfo = false },
                        modifier = Modifier.widthIn(max = 300.dp), containerColor = PoliceColors.Raised,
                        border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Text(metric.info, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("weekly-${metric.label}-info"), color = PoliceColors.Text,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SirenRule(Modifier.fillMaxWidth())
            WeeklyChart(metric, weekly?.weeks, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WeeklyChart(metric: ProgressMetric, weeks: List<WeeklyProgressPoint>?, modifier: Modifier) {
    val values = weeks?.map(metric::chartValue).orEmpty()
    val date = SimpleDateFormat("M/d", Locale.getDefault())
    val dates = weeks?.map { date.format(Date(it.weekStartMillis)) }.orEmpty()
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    val description = if (weeks == null) "Loading ${metric.label} chart" else
        "${metric.label}, last 8 weeks. " + weeks.zip(values).joinToString(". ") { (week, value) ->
            "Week of ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(week.weekStartMillis))}: ${number.format(value)} ${metric.axisUnit}"
        } + ". Current week in progress."
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = PoliceColors.Muted, fontSize = 10.sp, fontFeatureSettings = "tnum")
    val step = chartStep(values.maxOrNull() ?: 0.0, metric == ProgressMetric.Sets || metric == ProgressMetric.Reps)
    val ceiling = step * 4
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("weekly-${metric.label}-chart")
                .semantics { contentDescription = description }) {
                val ticks = (0..4).map { textMeasurer.measure(compactAxisNumber(it * step), axisStyle) }
                val labels = dates.map { textMeasurer.measure(it, axisStyle) }
                val left = ticks.maxOf { it.size.width }.toFloat() + 10.dp.toPx()
                val right = size.width - (labels.lastOrNull()?.size?.width ?: 0) / 2f - 5.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height - 28.dp.toPx()
                val plotWidth = (right - left).coerceAtLeast(1f)
                for (index in 0..4) {
                    val y = bottom - (bottom - top) * index / 4f
                    drawLine(PoliceColors.Muted.copy(alpha = .14f), Offset(left, y), Offset(right, y),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
                    drawText(ticks[index], topLeft = Offset(left - ticks[index].size.width - 10.dp.toPx(), y - ticks[index].size.height / 2f))
                }
                labels.forEachIndexed { index, label ->
                    val x = left + plotWidth * index / (labels.size - 1).coerceAtLeast(1)
                    drawText(label, topLeft = Offset(x - label.size.width / 2f, bottom + 10.dp.toPx()))
                }
                if (values.isNotEmpty()) {
                    val points = values.mapIndexed { index, value ->
                        Offset(left + plotWidth * index / (values.size - 1).coerceAtLeast(1),
                            bottom - (value / ceiling).toFloat().coerceIn(0f, 1f) * (bottom - top))
                    }
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(PoliceColors.Blue.copy(alpha = .2f), Color.Transparent), top, bottom))
                    drawPath(line, PoliceColors.Blue.copy(alpha = .1f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(line, PoliceColors.Blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    points.dropLast(1).forEach { drawCircle(PoliceColors.LightBlue, 2.dp.toPx(), it) }
                    val current = points.last()
                    drawCircle(PoliceColors.Red.copy(alpha = .16f), 8.dp.toPx(), current)
                    drawCircle(PoliceColors.Red, 4.dp.toPx(), current)
                    drawCircle(PoliceColors.Text, 1.5.dp.toPx(), current)
                }
            }
            if (weeks == null) Text("Loading workout data…", modifier = Modifier.align(Alignment.Center),
                color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("Last 8 weeks", modifier = Modifier.align(Alignment.CenterHorizontally),
            color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Keep count axes integral and zero/low-volume weeks readable without a fixed or clipped scale. */
private fun chartStep(peak: Double, wholeNumbers: Boolean): Double {
    if (peak <= 0.0) return 1.0
    val raw = peak / 4
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    return if (wholeNumbers) ceil(step).coerceAtLeast(1.0) else step.coerceAtLeast(.1)
}

private fun compactAxisNumber(value: Double): String {
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return when {
        value >= 1_000_000_000 -> "${number.format(value / 1_000_000_000)}b"
        value >= 1_000_000 -> "${number.format(value / 1_000_000)}m"
        value >= 1_000 -> "${number.format(value / 1_000)}k"
        else -> number.format(value)
    }
}
