package com.example.myfitnesspolice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
private val Heading = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
val StatTypography = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
    fontSize = 24.sp, fontFeatureSettings = "tnum", letterSpacing = (-.5).sp)
val Typography = Typography(
    headlineLarge = Heading.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = Heading.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = Heading.copy(fontSize = 25.sp, lineHeight = 31.sp),
    titleLarge = Heading.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = Heading.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = .5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .8.sp),
)
