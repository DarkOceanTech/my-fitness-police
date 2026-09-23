package com.darkoceantech.myfitnesspolice.ui.theme

import androidx.compose.ui.graphics.Color

// Shared surface brightness: original 25% reduction, followed by three successive 10% reductions.
private const val SurfaceBrightness = .75f * .90f * .90f * .90f
internal fun dimSurface(color: Color) = color.copy(red = color.red * SurfaceBrightness, green = color.green * SurfaceBrightness, blue = color.blue * SurfaceBrightness)

object PoliceColors {
    val Background = dimSurface(Color(0xFF0A0F1E))
    val Card = dimSurface(Color(0xFF141C2D))
    val Raised = dimSurface(Color(0xFF1D2940))
    val Border = dimSurface(Color(0xFF34415A))
    val Text = Color(0xFFF5F7FC)
    val Muted = Color(0xFFB8C3D7)
    val Blue = Color(0xFF0066FF)
    // Button fill: original 25% reduction, followed by a further 15%. The neon rim stays luminous.
    val ButtonBlue = Blue.copy(green = Blue.green * .75f * .85f, blue = Blue.blue * .75f * .85f)
    val ButtonBlueLight = Color(0xFF0861D6).let { it.copy(red = it.red * .85f, green = it.green * .85f, blue = it.blue * .85f) }
    val ButtonBlueDark = Color(0xFF032F89).let { it.copy(red = it.red * .85f, green = it.green * .85f, blue = it.blue * .85f) }
    val ButtonGlow = Color(0xFF70BEFF)
    val Red = Color(0xFFFF0033)
    val AmbientRed = dimSurface(Red)
    val AmbientBlue = dimSurface(Blue)
    val LightBlue = Color(0xFF9BC2FF)
    val Error = Color(0xFFFFB3BF)
    val Destructive = Color(0xFFB51F39)
}
