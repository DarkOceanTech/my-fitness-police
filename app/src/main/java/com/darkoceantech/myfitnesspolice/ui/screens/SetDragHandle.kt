package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

@Composable
internal fun SetDragHandle(modifier: Modifier) {
    Canvas(modifier) {
        repeat(3) { row -> repeat(2) { column ->
            drawCircle(PoliceColors.Muted, radius = size.width * .09f,
                center = Offset(size.width * (.32f + column * .36f), size.height * (.22f + row * .28f)))
        } }
    }
}
