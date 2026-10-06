package com.darkoceantech.myfitnesspolice.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlin.math.*

private val chartColors = listOf(PoliceColors.LightBlue, PoliceColors.Red, PoliceColors.Blue, PoliceColors.Muted,
    Color(0xFF49CEEF), Color(0xFFB1A3F7), Color(0xFF547BCE), Color(0xFFFF8AA0), Color(0xFFDAE8FF),
    Color(0xFF456397), Color(0xFF85A9D6), Color(0xFF599DAD), Color(0xFFBDCADC))
private fun seriesColor(index: Int) = chartColors[index % chartColors.size]

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardChartCard(chart: DashboardChart, modifier: Modifier = Modifier, onInsights: () -> Unit) {
    var selectedPoint by remember(chart.id, chart.labels, chart.series) { mutableStateOf<Int?>(null) }
    Surface(modifier.fillMaxWidth().testTag(chart.id), shape = PoliceCardShape, color = PoliceColors.Card,
        border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(chart.title, style = MaterialTheme.typography.titleMedium)
            Text(chart.headline, style = StatTypography.copy(fontSize = 26.sp), modifier = Modifier.testTag("${chart.id}-value"))
            TextButton(onClick = onInsights, modifier = Modifier.align(Alignment.End).testTag("${chart.id}-insights"),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text("Insights", style = MaterialTheme.typography.labelMedium, color = PoliceColors.LightBlue)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(22.dp).border(1.dp, PoliceColors.LightBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Text("i", color = PoliceColors.LightBlue)
                }
            }
            SirenRule(Modifier.fillMaxWidth())
            Text(chart.caption, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            if (chart.emptyReason != null) Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("${chart.id}-empty"),
                contentAlignment = Alignment.Center) {
                Text(chart.emptyReason, style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
            } else {
                val description = buildString {
                    append(chart.title).append(". ").append(chart.headline).append(". ")
                    chart.series.forEachIndexed { seriesIndex, series ->
                        append(series.name).append(": ")
                        series.values.forEachIndexed { index, value -> if (value != null) {
                            append(chart.labels.getOrNull(index).orEmpty()).append(" ").append(chart.displayNumber(value, seriesIndex)).append("; ")
                        } }
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(if (chart.kind == ChartKind.RADAR) 240.dp else 220.dp)
                    .testTag("${chart.id}-chart").semantics { contentDescription = description }
                    .pointerInput(chart) {
                        if (chart.kind !in listOf(ChartKind.DONUT, ChartKind.GAUGE, ChartKind.RADAR)) detectTapGestures { point ->
                            val left = chartAxisInset(chart).dp.toPx(); val right = if (chart.rightUnit == null) 12.dp.toPx() else 38.dp.toPx()
                            val fraction = (point.x - left) / (size.width - left - right)
                            val index = if (chart.kind in listOf(ChartKind.BAR, ChartKind.STACKED_BAR, ChartKind.COMBO))
                                floor(fraction * chart.labels.size).toInt() else (fraction * (chart.labels.size - 1)).roundToInt()
                            selectedPoint = index.coerceIn(0, (chart.labels.size - 1).coerceAtLeast(0))
                        }
                    }) {
                    when (chart.kind) {
                        ChartKind.DONUT -> drawDonut(chart)
                        ChartKind.GAUGE -> drawGauge(chart)
                        ChartKind.RADAR -> drawRadar(chart)
                        else -> drawCartesian(chart, selectedPoint)
                    }
                }
                selectedPoint?.let { index ->
                    Text(chart.labels.getOrNull(index).orEmpty() + " · " + chart.series.mapIndexed { i, series ->
                        series.name + ": " + (series.values.getOrNull(index)?.let { chart.displayNumber(it, i) } ?: "—") +
                            " " + if (i == 1 && chart.rightUnit != null) chart.rightUnit else chart.unit
                    }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue,
                        modifier = Modifier.testTag("${chart.id}-point"))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chart.series.forEachIndexed { index, series ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(seriesColor(index), CircleShape))
                        Text(series.name + when (chart.kind) {
                            ChartKind.DONUT -> " · " + DashboardMetricsCalculator.number(series.values.firstOrNull() ?: 0.0) + " ${chart.unit}"
                            else -> ""
                        }, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                    }
                }
            }
            if (chart.reference != null) Text("Reference: ${chart.displayNumber(chart.reference)} Lbs estimated 1RM",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}

private fun DrawScope.label(value: String, x: Float, y: Float, align: Paint.Align = Paint.Align.CENTER,
    color: Color = PoliceColors.Muted, fontSize: Float = 11.sp.toPx()) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = fontSize; textAlign = align }
    drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)
}

private fun niceMax(value: Double): Double {
    if (!value.isFinite() || value <= 0) return 1.0
    val power = 10.0.pow(floor(log10(value)))
    return ceil(value / power) * power
}

// Compact ticks keep large volume totals inside the plot on narrow phones.
// The headline, tap readout and insight data table retain the full numbers.
private fun axisNumber(value: Double): String = when {
    abs(value) >= 1_000_000 -> DashboardMetricsCalculator.number(value / 1_000_000) + "m"
    abs(value) >= 1_000 -> DashboardMetricsCalculator.number(value / 1_000) + "k"
    else -> DashboardMetricsCalculator.number(value)
}

private fun chartAxisInset(chart: DashboardChart): Int = when (chart.unit) {
    "Lbs-reps" -> 80
    "Lbs" -> 64
    else -> 38
}

private fun DrawScope.drawCartesian(chart: DashboardChart, selected: Int?) {
    val left = chartAxisInset(chart).dp.toPx(); val right = size.width - if (chart.rightUnit == null) 12.dp.toPx() else 38.dp.toPx()
    val top = 14.dp.toPx(); val bottom = size.height - 30.dp.toPx(); val height = bottom - top
    val count = chart.labels.size.coerceAtLeast(1)
    val stacked = chart.kind == ChartKind.STACKED_BAR
    val primary = if (stacked) (0 until count).map { i -> chart.series.sumOf { it.values.getOrNull(i) ?: 0.0 } }
        else chart.series.filterIndexed { index, _ -> chart.rightUnit == null || index == 0 }.flatMap { it.values.filterNotNull() }
    val maximum = niceMax(maxOf(primary.maxOrNull() ?: 0.0, chart.reference ?: 0.0))
    val secondMax = niceMax(chart.series.getOrNull(1)?.values?.filterNotNull()?.maxOrNull() ?: 0.0)
    val banded = chart.kind in listOf(ChartKind.BAR, ChartKind.STACKED_BAR, ChartKind.COMBO)
    fun x(index: Int) = if (banded) left + (right - left) * (index + .5f) / count
        else if (count == 1) (left + right) / 2 else left + (right - left) * index / (count - 1)
    fun y(value: Double, secondary: Boolean = false) = bottom - (value / if (secondary) secondMax else maximum).toFloat() * height
    repeat(5) { tick ->
        val ypos = bottom - height * tick / 4
        drawLine(PoliceColors.Border, Offset(left, ypos), Offset(right, ypos), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        label(if (chart.unit in listOf("Lbs", "Lbs-reps")) chart.displayNumber(maximum * tick / 4) else axisNumber(maximum * tick / 4),
            left - 6.dp.toPx(), ypos + 4.dp.toPx(), Paint.Align.RIGHT)
        if (chart.rightUnit != null) label(axisNumber(secondMax * tick / 4), right + 6.dp.toPx(), ypos + 4.dp.toPx(), Paint.Align.LEFT)
    }
    label(chart.unit, left, top - 2.dp.toPx(), Paint.Align.LEFT)
    chart.rightUnit?.let { label(it, right, top - 2.dp.toPx(), Paint.Align.RIGHT) }
    val step = if (count <= 8) 1 else ((count - 1) / 4).coerceAtLeast(1)
    for (i in 0 until count) if (i % step == 0 || i == count - 1) {
        if (i != count - 1 || count <= 8 || (count - 1) % step >= step / 2 || (count - 1) % step == 0)
            label(chart.labels.getOrNull(i).orEmpty(), x(i), bottom + 20.dp.toPx())
    }
    val barWidth = ((right - left) / count * .52f).coerceAtMost(24.dp.toPx())
    if (chart.kind in listOf(ChartKind.STACKED_BAR, ChartKind.BAR, ChartKind.COMBO)) {
        for (i in 0 until count) {
            var previous = 0.0
            val bars = if (stacked) chart.series else chart.series.take(1)
            bars.forEachIndexed { seriesIndex, series ->
                val value = series.values.getOrNull(i) ?: 0.0
                val upper = y(previous + value); val lower = y(previous)
                drawRect(seriesColor(seriesIndex).copy(alpha = .85f), Offset(x(i) - barWidth / 2, upper), Size(barWidth, (lower - upper).coerceAtLeast(0f)))
                previous += value
            }
        }
    }
    if (chart.kind !in listOf(ChartKind.BAR, ChartKind.STACKED_BAR)) chart.series.forEachIndexed { seriesIndex, series ->
        if (chart.kind == ChartKind.COMBO && seriesIndex == 0) return@forEachIndexed
        val points = series.values.mapIndexedNotNull { index, value -> value?.let { Offset(x(index), y(it, chart.rightUnit != null && seriesIndex == 1)) } }
        if (chart.kind != ChartKind.SCATTER && points.isNotEmpty()) {
            val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, seriesColor(seriesIndex).copy(alpha = .1f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, seriesColor(seriesIndex), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEach { drawCircle(seriesColor(seriesIndex), if (chart.kind == ChartKind.SCATTER) 5.dp.toPx() else 3.dp.toPx(), it) }
    }
    chart.reference?.let { drawLine(PoliceColors.Red, Offset(left, y(it)), Offset(right, y(it)), 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))) }
    selected?.let { drawLine(PoliceColors.Text.copy(alpha = .4f), Offset(x(it), top), Offset(x(it), bottom), 1.dp.toPx()) }
}

private fun DrawScope.drawDonut(chart: DashboardChart) {
    val total = chart.series.sumOf { it.values.firstOrNull() ?: 0.0 }
    val diameter = minOf(size.width, size.height) - 44.dp.toPx()
    val position = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawArc(PoliceColors.Border, 0f, 360f, false, position, Size(diameter, diameter), style = Stroke(20.dp.toPx()))
    if (total <= 0) return
    var angle = -90f
    chart.series.forEachIndexed { index, series ->
        val sweep = ((series.values.firstOrNull() ?: 0.0) / total * 360).toFloat()
        if (sweep > 0) drawArc(seriesColor(index), angle + .8f, (sweep - 1.6f).coerceAtLeast(.1f), false,
            position, Size(diameter, diameter), style = Stroke(20.dp.toPx()))
        angle += sweep
    }
    label(DashboardMetricsCalculator.number(total), size.width / 2, size.height / 2, color = PoliceColors.Text, fontSize = 26.sp.toPx())
    label(chart.unit, size.width / 2, size.height / 2 + 24.dp.toPx())
}

private fun DrawScope.drawGauge(chart: DashboardChart) {
    val value = (chart.series.firstOrNull()?.values?.firstOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val diameter = minOf(size.width, size.height) - 44.dp.toPx()
    val position = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawArc(PoliceColors.Border, 135f, 270f, false, position, Size(diameter, diameter), style = Stroke(20.dp.toPx(), cap = StrokeCap.Round))
    if (value > 0) drawArc(Brush.sweepGradient(listOf(PoliceColors.Blue, PoliceColors.LightBlue, PoliceColors.Red)), 135f,
        (270 * value / 100).toFloat(), false, position, Size(diameter, diameter), style = Stroke(20.dp.toPx(), cap = StrokeCap.Round))
    label(DashboardMetricsCalculator.number(value) + "%", size.width / 2, size.height / 2 + 8.dp.toPx(), color = PoliceColors.Text, fontSize = 26.sp.toPx())
}

private fun DrawScope.drawRadar(chart: DashboardChart) {
    val values = chart.series.firstOrNull()?.values.orEmpty()
    val maximum = niceMax(values.filterNotNull().maxOrNull() ?: 0.0)
    val center = Offset(size.width / 2, size.height / 2)
    val radius = minOf(size.width, size.height) * .32f
    fun point(index: Int, magnitude: Float): Offset {
        val angle = -PI / 2 + index * 2 * PI / 3
        return center + Offset((cos(angle) * magnitude).toFloat(), (sin(angle) * magnitude).toFloat())
    }
    fun polygon(magnitudes: List<Float>) = Path().apply {
        magnitudes.forEachIndexed { index, distance -> point(index, distance).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close()
    }
    repeat(4) { ring -> drawPath(polygon(List(3) { radius * (ring + 1) / 4 }), PoliceColors.Border, style = Stroke(1.dp.toPx())) }
    for (i in 0..2) {
        drawLine(PoliceColors.Border, center, point(i, radius), 1.dp.toPx())
        val p = point(i, radius + 16.dp.toPx())
        val title = when (i) { 0 -> "Strength 1–5"; 1 -> "Hypertrophy 6–12"; else -> "Endurance 13+" }
        val labelY = p.y + if (i == 0) 0f else 10.dp.toPx()
        label(title, p.x, labelY, fontSize = 10.sp.toPx())
        label(DashboardMetricsCalculator.number(values.getOrNull(i) ?: 0.0), p.x, labelY + 14.dp.toPx(), color = PoliceColors.LightBlue)
    }
    val path = polygon(List(3) { ((values.getOrNull(it) ?: 0.0) / maximum).toFloat() * radius })
    drawPath(path, PoliceColors.Blue.copy(alpha = .3f)); drawPath(path, PoliceColors.LightBlue, style = Stroke(2.dp.toPx()))
}
