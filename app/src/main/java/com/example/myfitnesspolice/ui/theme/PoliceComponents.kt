package com.example.myfitnesspolice.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val PoliceCardShape = CutCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 4.dp)

// Corner lights fade out before the midpoint, preserving an almost-black center at every aspect ratio.
fun Modifier.policeBackdrop() = background(Color(0xFF020306)).drawWithCache {
    val radius = Offset(size.width, size.height).getDistance().coerceAtLeast(1f) * .27f
    fun cornerLight(color: Color, center: Offset) = Brush.radialGradient(
        0f to color,
        .22f to color.copy(alpha = .35f),
        .6f to color.copy(alpha = .08f),
        1f to color.copy(alpha = 0f),
        center = center, radius = radius)
    val red = cornerLight(PoliceColors.Red, Offset.Zero)
    val blue = cornerLight(PoliceColors.Blue, Offset(size.width, size.height))
    onDrawBehind { drawRect(red); drawRect(blue) }
}

@Composable
fun SirenRule(modifier: Modifier = Modifier) {
    Row(modifier.height(2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(PoliceColors.Red))
        Box(Modifier.weight(1f).fillMaxHeight().background(PoliceColors.Blue))
    }
}

@Composable
fun ShieldMark(modifier: Modifier = Modifier, tint: Color = PoliceColors.LightBlue, checked: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * .5f, h * .07f); lineTo(w * .87f, h * .22f)
            lineTo(w * .82f, h * .57f)
            cubicTo(w * .78f, h * .74f, w * .6f, h * .88f, w * .5f, h * .94f)
            cubicTo(w * .4f, h * .88f, w * .22f, h * .74f, w * .18f, h * .57f)
            lineTo(w * .13f, h * .22f); close()
        }
        drawPath(shield, tint.copy(alpha = tint.alpha * .08f))
        drawPath(shield, tint, style = Stroke(w * .04f))
        val line = w * .055f
        if (checked) {
            drawLine(tint, Offset(w * .32f, h * .49f), Offset(w * .46f, h * .61f), line, StrokeCap.Round)
            drawLine(tint, Offset(w * .46f, h * .61f), Offset(w * .7f, h * .36f), line, StrokeCap.Round)
        } else {
            drawLine(tint, Offset(w * .28f, h * .47f), Offset(w * .72f, h * .47f), line, StrokeCap.Round)
            listOf(.32f, .68f).forEach { x ->
                drawLine(tint, Offset(w * x, h * .35f), Offset(w * x, h * .59f), line * 1.4f, StrokeCap.Round)
            }
        }
    }
}

