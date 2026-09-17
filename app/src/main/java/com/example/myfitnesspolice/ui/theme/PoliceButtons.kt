package com.example.myfitnesspolice.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/** Fixed physical direction: lighter on the left, darker on the right, including in RTL layouts. */
internal fun Modifier.policeButtonSurface(enabled: Boolean, shape: Shape, outlined: Boolean = false) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val fill = Brush.horizontalGradient(listOf(PoliceColors.ButtonBlueLight, PoliceColors.ButtonBlue, PoliceColors.ButtonBlueDark))
    val rim = Brush.horizontalGradient(listOf(PoliceColors.ButtonGlow, Color(0xFF4098FF), Color(0xFF1269DD)))
    val core = Brush.horizontalGradient(listOf(Color(0xFFD0F0FF).copy(alpha = .9f), PoliceColors.ButtonGlow.copy(alpha = .4f), Color.Transparent))
    onDrawBehind {
        if (enabled) {
            // A bright tube edge and layered halo keep the darker fill legible without animation.
            drawOutline(outline, PoliceColors.ButtonGlow.copy(alpha = .025f), style = Stroke(14.dp.toPx()))
            drawOutline(outline, PoliceColors.ButtonGlow.copy(alpha = .045f), style = Stroke(10.dp.toPx()))
            drawOutline(outline, PoliceColors.ButtonGlow.copy(alpha = .09f), style = Stroke(6.dp.toPx()))
            if (!outlined) drawOutline(outline, fill)
            drawOutline(outline, rim, style = Stroke(1.5.dp.toPx()))
            drawOutline(outline, core, style = Stroke(.5.dp.toPx()))
        } else if (outlined) drawOutline(outline, PoliceColors.Border, style = Stroke(1.dp.toPx()))
        else drawOutline(outline, PoliceColors.Raised)
    }
}

@Composable
fun PoliceButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.policeButtonSurface(enabled, shape), enabled = enabled,
        shape = shape, contentPadding = contentPadding, elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PoliceColors.Text,
            disabledContainerColor = Color.Transparent, disabledContentColor = PoliceColors.Muted.copy(alpha = .45f)),
        content = content)
}

@Composable
fun PoliceIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape, content: @Composable () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = modifier.policeButtonSurface(enabled, shape), enabled = enabled,
        shape = shape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent,
            contentColor = PoliceColors.Text, disabledContainerColor = Color.Transparent,
            disabledContentColor = PoliceColors.Muted.copy(alpha = .45f)), content = content)
}

@Composable
fun PoliceOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.policeButtonSurface(enabled, CircleShape, outlined = true),
        enabled = enabled, shape = CircleShape, border = null,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = PoliceColors.ButtonGlow,
            disabledContainerColor = Color.Transparent, disabledContentColor = PoliceColors.Muted.copy(alpha = .45f)),
        content = content)
}
