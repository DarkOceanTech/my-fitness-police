package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.AppDestinations
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.data.displayName
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

@Composable
internal fun PoliceBottomNavigation(current: AppDestinations, onSelect: (AppDestinations) -> Unit) {
    NavigationBar(containerColor = PoliceColors.Card, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("main-navigation")) {
        AppDestinations.entries.forEach { destination ->
            NavigationBarItem(selected = destination == current, onClick = { onSelect(destination) },
                icon = { DestinationIcon(destination, destination == current) },
                label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, softWrap = false) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = PoliceColors.Text,
                    selectedTextColor = PoliceColors.Text, unselectedIconColor = PoliceColors.Muted,
                    unselectedTextColor = PoliceColors.Muted, indicatorColor = PoliceColors.Raised))
        }
    }
}

@Composable
private fun DestinationIcon(destination: AppDestinations, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(painterResource(destination.icon), contentDescription = null,
            tint = if (selected) PoliceColors.Text else PoliceColors.Muted)
        Box(Modifier.width(22.dp).height(2.dp).background(if (selected) PoliceColors.Red else PoliceColors.Blue.copy(alpha = .5f)))
    }
}

@Composable
internal fun CurrentSessionShortcut(workout: WorkoutDetails, onOpen: () -> Unit) {
    val progress = workout.sessionState
    val clock = remember(workout.workout.id) { flow {
        while (true) { emit(System.currentTimeMillis()); delay(1000) }
    } }
    val now by clock.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    val currentSet = workout.orderedSets().firstOrNull { it.id == progress?.currentSetId }
    val active = if (progress?.phase == "active") progress.phaseMillis(now) else currentSet?.activeMillis ?: 0L
    val rest = if (progress?.phase in listOf("rest", "cooldown")) progress?.phaseMillis(now) ?: 0L else 0L
    val planName = workout.workout.trainingPlan.ifBlank { workout.workout.displayName() }
    Surface(onClick = onOpen, color = PoliceColors.Raised, contentColor = PoliceColors.Text,
        modifier = Modifier.fillMaxWidth().testTag("return-to-active-workout")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_workout), null, tint = PoliceColors.LightBlue, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(planName, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("current-session-plan"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Active ${sessionTime(active)}", style = MaterialTheme.typography.bodySmall,
                        color = PoliceColors.Muted, modifier = Modifier.testTag("current-session-active-time"))
                    Text("Break ${sessionTime(rest)}", style = MaterialTheme.typography.bodySmall,
                        color = PoliceColors.Muted, modifier = Modifier.testTag("current-session-break-time"))
                    if (progress?.isPaused == true) Text("Paused", style = MaterialTheme.typography.labelSmall,
                        color = PoliceColors.LightBlue)
                }
            }
            Text("→", color = PoliceColors.LightBlue, fontSize = 22.sp)
        }
    }
}

/** A finite reveal honors Android's animation scale and never continuously distracts from a set. */
@Composable
internal fun LandscapeWorkoutNavigation(current: AppDestinations, onSelect: (AppDestinations) -> Unit,
    modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (expanded) 1f else 0f,
        tween(durationMillis = 1200, easing = LinearEasing), label = "workout-navigation-reveal")
    BoxWithConstraints(modifier.fillMaxWidth().padding(8.dp).testTag("landscape-workout-navigation")) {
        val menuWidth = (maxWidth - 60.dp).coerceAtMost(560.dp)
        Column {
            Box(Modifier.width(menuWidth + 56.dp).height(32.dp)) {
                if (reveal > 0f && reveal < 1f) {
                    DonutChase(reveal, expanded, Modifier.fillMaxSize().testTag("navigation-donut-chase"))
                }
            }
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoliceIconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)
                    .testTag("landscape-navigation-toggle").semantics {
                        contentDescription = if (expanded) "Collapse navigation menu" else "Open navigation menu"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }, shape = RoundedCornerShape(14.dp)) {
                    Canvas(Modifier.size(22.dp)) {
                        if (expanded) {
                            drawLine(PoliceColors.Text, Offset(size.width * .2f, size.height * .2f),
                                Offset(size.width * .8f, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
                            drawLine(PoliceColors.Text, Offset(size.width * .8f, size.height * .2f),
                                Offset(size.width * .2f, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
                        } else (1..3).forEach { row ->
                            drawLine(PoliceColors.Text, Offset(0f, size.height * row / 4),
                                Offset(size.width, size.height * row / 4), 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
                if (reveal > 0f) Box(Modifier.width(menuWidth * reveal).clip(RoundedCornerShape(16.dp))) {
                    Surface(color = PoliceColors.Card, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true)
                            .requiredWidth(menuWidth).height(64.dp).testTag("landscape-navigation-items")) {
                        Row(Modifier.selectableGroup()) {
                            AppDestinations.entries.forEach { destination ->
                                Column(Modifier.weight(1f).fillMaxHeight()
                                    .selectable(destination == current, enabled = expanded, role = Role.Tab,
                                        onClick = { expanded = false; onSelect(destination) })
                                    .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                                    DestinationIcon(destination, destination == current)
                                    Text(stringResource(destination.label), fontSize = 11.sp,
                                        color = if (destination == current) PoliceColors.Text else PoliceColors.Muted,
                                        maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChase(progress: Float, opening: Boolean, modifier: Modifier) {
    Canvas(modifier.semantics {
        contentDescription = if (opening) "Officer chasing a dog with a donut" else "Dog chasing an officer with a donut"
    }) {
        val unit = size.height / 38f
        // Both chases travel in the reveal direction; the character in front owns the donut.
        val lead = -50f + progress * (size.width / unit + 100f)
        val stride = sin(progress * 38f) * 4f
        scale(unit, unit, pivot = Offset.Zero) {
            if (opening) {
                translate(lead - 43f, 0f) { officer(stride, carryingDonut = false) }
                translate(lead, 7f) { dog(stride, carryingDonut = true) }
            } else {
                translate(lead, 0f) { scale(-1f, 1f, pivot = Offset(15f, 18f)) { officer(stride, carryingDonut = true) } }
                translate(lead + 44f, 7f) { scale(-1f, 1f, pivot = Offset(15f, 15f)) { dog(stride, carryingDonut = false) } }
            }
        }
    }
}

private fun DrawScope.officer(stride: Float, carryingDonut: Boolean) {
    val skin = Color(0xFFE8B58B)
    val uniform = Color(0xFF347DE3)
    drawLine(uniform, Offset(14f, 24f), Offset(8f + stride, 36f), 5f, StrokeCap.Round)
    drawLine(uniform, Offset(18f, 24f), Offset(24f - stride, 36f), 5f, StrokeCap.Round)
    drawRoundRect(uniform, Offset(10f, 13f), Size(13f, 15f), CornerRadius(3f))
    drawCircle(skin, 6f, Offset(17f, 8f))
    drawRect(Color(0xFF143463), Offset(10f, 1f), Size(14f, 5f))
    drawLine(Color(0xFF143463), Offset(11f, 6f), Offset(27f, 6f), 3f, StrokeCap.Round)
    drawCircle(Color(0xFFFFD773), 2f, Offset(16f, 17f))
    drawCircle(Color(0xFF121A2C), 1f, Offset(21f, 8f))
    drawLine(skin, Offset(21f, 17f), Offset(29f, 21f - stride / 2), 4f, StrokeCap.Round)
    if (carryingDonut) donut(Offset(32f, 20f - stride / 2))
}

private fun DrawScope.dog(stride: Float, carryingDonut: Boolean) {
    val fur = Color(0xFFC8915A)
    drawRoundRect(fur, Offset(5f, 11f), Size(24f, 11f), CornerRadius(6f))
    drawCircle(fur, 7f, Offset(29f, 10f))
    drawRoundRect(Color(0xFF78502D), Offset(22f, 3f), Size(5f, 11f), CornerRadius(2f))
    drawCircle(Color(0xFF171A20), 1.6f, Offset(32f, 8f))
    drawLine(fur, Offset(7f, 14f), Offset(1f, 6f), 4f, StrokeCap.Round)
    drawLine(fur, Offset(10f, 20f), Offset(7f + stride, 29f), 4f, StrokeCap.Round)
    drawLine(fur, Offset(25f, 20f), Offset(29f - stride, 29f), 4f, StrokeCap.Round)
    drawLine(PoliceColors.Red, Offset(26f, 15f), Offset(31f, 14f), 3f)
    if (carryingDonut) donut(Offset(37f, 13f))
}

private fun DrawScope.donut(center: Offset) {
    drawCircle(Color(0xFFE9B66C), 6f, center)
    drawCircle(Color(0xFFFF85B3), 4.5f, center, style = Stroke(3f))
    drawCircle(PoliceColors.Background, 2f, center)
    drawLine(Color.White, center + Offset(-3f, -3f), center + Offset(-1f, -4f), 1f)
    drawLine(Color(0xFF79E3FF), center + Offset(3f, 1f), center + Offset(3f, 3f), 1f)
}
