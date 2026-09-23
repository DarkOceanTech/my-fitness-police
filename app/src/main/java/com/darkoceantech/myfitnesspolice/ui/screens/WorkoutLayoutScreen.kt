package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = PoliceColors.Text
private val Muted = PoliceColors.Muted
private val Edge = PoliceColors.Border

@Composable
fun WorkoutLayoutScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    var paused by remember { mutableStateOf(false) }
    var rest by remember { mutableIntStateOf(69) }
    Box(modifier.policeBackdrop()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(46.dp).height(3.dp)
                        .clip(CircleShape).background(Muted))
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(androidx.compose.ui.res.painterResource(com.darkoceantech.myfitnesspolice.R.drawable.ic_back),
                                contentDescription = "Back to workout home", tint = Ink)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Back Day", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("SAMPLE WORKOUT · 37:12", fontSize = 12.sp, color = Muted)
                        }
                        Surface(shape = CircleShape, color = Ink, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("⚑", color = Color.Black, fontSize = 23.sp) }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(8) { index ->
                            Box(Modifier.weight(1f).height(3.dp).background(if (index < 3) PoliceColors.Blue else PoliceColors.Raised))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            item { ExerciseLayoutCard("Ab Wheel Rollout", "Chest, Shoulders, Triceps", true, 0) }
            item { ExerciseLayoutCard("Bent Over Row", "Back, Lats, Biceps", false, 1) }
            item { ExerciseLayoutCard("Lat Pulldown", "Back, Lats", false, 2) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(26.dp), color = PoliceColors.Raised, shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, PoliceColors.Border),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Skip Rest", color = Ink, fontSize = 12.sp,
                    modifier = Modifier.clickable { rest = 0 }.padding(vertical = 10.dp))
                Text("%d:%02d".format(rest / 60, rest % 60), fontSize = 25.sp, color = Ink, modifier = Modifier.weight(1f))
                Text("+15 sec", color = Ink, fontSize = 11.sp,
                    modifier = Modifier.clickable { rest += 15 }.padding(vertical = 10.dp))
                RoundLabel(if (paused) "▶" else "Ⅱ", Modifier.clickable { paused = !paused })
            }
        }
    }
}

@Composable
private fun ExerciseLayoutCard(title: String, muscles: String, current: Boolean, variant: Int) {
    Surface(
        shape = PoliceCardShape, color = PoliceColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Edge),
    ) {
        Column {
            if (current) {
                Box(Modifier.fillMaxWidth().background(PoliceColors.Raised).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("ϟ  Current", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumbnail(variant)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(muscles, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("⋮", color = Ink, fontSize = 22.sp)
                }
                if (current) {
                    Row(Modifier.fillMaxWidth().border(1.dp, PoliceColors.Border, RoundedCornerShape(12.dp))
                        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✎", color = Ink, fontSize = 17.sp)
                        Text("Felt really good on this session — form was…", color = Ink, fontSize = 11.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("›", color = Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("SET", "LBS ↗", "REPS ↘", "RPE").forEach { label ->
                        Text(label, Modifier.weight(1f), color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.width(32.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index -> SetLayoutRow(index, current && index == 0, variant) }
                }
            }
        }
    }
}

@Composable
private fun SetLayoutRow(index: Int, initiallyChecked: Boolean, variant: Int) {
    var checked by remember { mutableStateOf(initiallyChecked) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("${index + 1} ${listOf("WU", "WRK", "DS")[index]}", if (variant == 0) "65" else "50", "10", "7").forEachIndexed { column, value ->
            Box(Modifier.weight(1f).height(39.dp).clip(RoundedCornerShape(10.dp))
                .background(if (checked) PoliceColors.Background else PoliceColors.Raised)
                .border(1.dp, PoliceColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Text(value + if (column != 1) " ⌄" else "", color = if (checked) Muted else Ink,
                    fontSize = if (column == 0) 10.sp else 13.sp)
            }
        }
        Surface(shape = CircleShape, color = if (checked) Ink else PoliceColors.Raised,
            border = androidx.compose.foundation.BorderStroke(1.dp, Edge),
            modifier = Modifier.size(32.dp).clickable { checked = !checked }) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = if (checked) PoliceColors.Background else Muted, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RoundLabel(label: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(PoliceColors.Raised)
        .border(1.dp, PoliceColors.Border, CircleShape), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontSize = 20.sp)
    }
}

// Small code-drawn equipment illustration keeps this layout independent of remote assets.
@Composable
private fun ExerciseThumbnail(variant: Int) {
    Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.verticalGradient(listOf(PoliceColors.Raised, PoliceColors.Background)))) {
        val center = Offset(size.width / 2, size.height / 2)
        val accent = if (variant == 0) PoliceColors.Red else PoliceColors.Blue
        drawLine(PoliceColors.Muted, Offset(size.width * .2f, size.height * .65f),
            Offset(size.width * .8f, size.height * .35f), strokeWidth = 6.dp.toPx())
        listOf(.25f, .75f).forEach { x ->
            drawCircle(PoliceColors.Border, 10.dp.toPx(), Offset(size.width * x, size.height * (1 - x) * .6f + size.height * .2f))
            drawCircle(accent, 5.dp.toPx(), Offset(size.width * x, size.height * (1 - x) * .6f + size.height * .2f))
        }
        drawCircle(PoliceColors.Text, 3.dp.toPx(), center)
    }
}
