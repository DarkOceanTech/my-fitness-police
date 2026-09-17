package com.example.myfitnesspolice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PoliceColorScheme = darkColorScheme(
    primary = PoliceColors.LightBlue, onPrimary = PoliceColors.Background,
    primaryContainer = PoliceColors.ButtonBlue, onPrimaryContainer = PoliceColors.Text,
    secondary = PoliceColors.Muted, onSecondary = PoliceColors.Background,
    secondaryContainer = PoliceColors.Raised, onSecondaryContainer = PoliceColors.Text,
    tertiary = PoliceColors.Error, onTertiary = PoliceColors.Background,
    tertiaryContainer = PoliceColors.Destructive, onTertiaryContainer = PoliceColors.Text,
    background = PoliceColors.Background, onBackground = PoliceColors.Text,
    surface = PoliceColors.Card, onSurface = PoliceColors.Text,
    surfaceVariant = PoliceColors.Raised, onSurfaceVariant = PoliceColors.Muted,
    surfaceTint = PoliceColors.AmbientBlue,
    surfaceDim = PoliceColors.Background, surfaceBright = PoliceColors.Raised,
    surfaceContainerLowest = PoliceColors.Background, surfaceContainerLow = PoliceColors.Card,
    surfaceContainer = PoliceColors.Card, surfaceContainerHigh = PoliceColors.Raised,
    surfaceContainerHighest = PoliceColors.Raised,
    outline = PoliceColors.Border, outlineVariant = PoliceColors.Border,
    error = PoliceColors.Error, onError = PoliceColors.Background,
    errorContainer = PoliceColors.Destructive, onErrorContainer = PoliceColors.Text,
    inverseSurface = PoliceColors.Text, inverseOnSurface = PoliceColors.Background,
    inversePrimary = PoliceColors.Blue,
)

@Composable
fun MyFitnessPoliceTheme(content: @Composable () -> Unit) {
    // Keep a consistent dark uniform, independent of wallpaper/dynamic colors.
    MaterialTheme(colorScheme = PoliceColorScheme, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
