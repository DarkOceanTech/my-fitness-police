package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfitnesspolice.data.*
import com.example.myfitnesspolice.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

// Shared by live sessions and history. Prescribed values never change here.
@Composable
internal fun ActiveExerciseCard(entry: ExerciseWithSets, workout: WorkoutDetails, progress: WorkoutSessionState?,
    enabled: Boolean, onActual: (String, String) -> Unit = { _, _ -> }, onNote: (() -> Unit)? = null,
    onEquipment: (() -> Unit)? = null,
    onComplete: (String) -> Unit = {}, onStart: (String) -> Unit = {}, onInfo: (String) -> Unit) {
    val history = workout.workout.finishedAt != null
    val sets = entry.sets.sortedBy { it.position }.filter { !history || it.completedAt != null }
    val currentInCard = !history && sets.any { it.id == progress?.currentSetId }
    val next = workout.nextSet(progress?.currentSetId)
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("active-card-${entry.workoutExercise.id}"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column {
            Row(Modifier.fillMaxWidth().background(PoliceColors.Raised).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (currentInCard) "CURRENT" else "EXERCISE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(if (history) "${sets.size} sets · ${sets.sumOf { (it.actualReps ?: it.reps).toLong() }} reps"
                    else "${sets.count { it.completedAt != null }} / ${sets.size} sets",
                    style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
            }
            SirenRule(Modifier.fillMaxWidth())
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldMark(Modifier.size(42.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(entry.exercise.name, style = MaterialTheme.typography.titleLarge)
                        Text(entry.exercise.equipment, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                    }
                    if (onEquipment != null) Box {
                        IconButton(onClick = { menu = true }, enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = "Session exercise options for ${entry.exercise.name}" }) { Text("⋮", fontSize = 24.sp) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit equipment setup") }, enabled = enabled,
                                onClick = { menu = false; onEquipment() })
                        }
                    }
                }
                EquipmentPositionSummary(entry.workoutExercise.equipmentPositions)
                if (onNote != null || entry.workoutExercise.notes.isNotBlank()) Text(
                    entry.workoutExercise.notes.ifBlank { "Add an exercise note" },
                    Modifier.fillMaxWidth().border(1.dp, PoliceColors.Border, RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled && onNote != null, onClickLabel = "Edit note") { onNote?.invoke() }
                        .heightIn(min = 48.dp).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Set", "Lbs", "Reps", "Actual").forEach { title ->
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                color = PoliceColors.Muted, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.width(56.dp))
                    }
                    sets.forEach { set ->
                        val completed = set.completedAt != null
                        val isActive = !completed && progress?.phase == "active" && progress.currentSetId == set.id
                        val upcoming = !history && ((progress?.phase == "rest" && next?.id == set.id) ||
                            (progress?.phase == "ready" && progress.currentSetId == set.id))
                        val canControl = enabled && !history && progress != null && !progress.isPaused && !progress.awaitingActual
                        val buttonEnabled = if (completed) enabled else canControl &&
                            (isActive || progress?.phase in listOf("rest", "ready"))
                        Row(Modifier.fillMaxWidth().testTag("session-set-${set.id}")
                            .then(if (history) Modifier.clickable(enabled = enabled, onClickLabel = "View set ${set.position + 1} info") { onInfo(set.id) } else Modifier)
                            .semantics { stateDescription = if (completed) "Recorded, read only" else if (isActive) "Active" else "Upcoming" }
                            .background(when { completed -> PoliceColors.Raised.copy(alpha = .35f)
                                isActive -> PoliceColors.Red.copy(alpha = .1f); else -> Color.Transparent }, RoundedCornerShape(10.dp))
                            .border(1.dp, when { isActive -> PoliceColors.Red; upcoming -> PoliceColors.Blue; else -> Color.Transparent }, RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SessionReadOnlyCell("${set.position + 1} " + if (set.isWarmup) "Wu" else "Ws",
                                "Set type ${set.id}", completed, Modifier.weight(1f))
                            SessionReadOnlyCell(sessionPounds(set.weightGrams), "Session weight ${set.id}", completed, Modifier.weight(1f))
                            SessionReadOnlyCell(set.reps.toString(), "Session reps ${set.id}", completed, Modifier.weight(1f))
                            if (completed) SessionReadOnlyCell((set.actualReps ?: set.reps).toString(), "Actual reps ${set.id}",
                                true, Modifier.weight(1f).testTag("actual-${set.id}"))
                            else {
                                val actual = set.actualReps?.toString() ?: "—"
                                val options = remember(actual) { ((0..100).map { it.toString() } + listOfNotNull(set.actualReps?.toString()))
                                    .distinct().sortedBy { it.toInt() } }
                                TablePicker("Actual reps ${set.id}", actual, options, canControl, Modifier.weight(1f).testTag("actual-${set.id}")) {
                                    onActual(set.id, it)
                                }
                            }
                            Button(onClick = { when { completed -> onInfo(set.id); isActive -> onComplete(set.id); else -> onStart(set.id) } },
                                enabled = buttonEnabled,
                                modifier = Modifier.width(56.dp).heightIn(min = 48.dp).testTag("set-action-${set.id}")
                                    .then(if (!completed && !isActive) Modifier.policeButtonSurface(buttonEnabled, RoundedCornerShape(14.dp)) else Modifier)
                                    .semantics { contentDescription = when {
                                        completed -> "Info for set ${set.position + 1} of ${entry.exercise.name}"
                                        isActive -> "Complete set ${set.position + 1} of ${entry.exercise.name}"
                                        else -> "Start set ${set.position + 1} of ${entry.exercise.name}"
                                    } },
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 12.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when { completed -> PoliceColors.Raised; isActive -> PoliceColors.Destructive; else -> Color.Transparent },
                                    contentColor = if (completed) PoliceColors.Muted else PoliceColors.Text,
                                    disabledContainerColor = PoliceColors.Raised, disabledContentColor = PoliceColors.Muted)) {
                                if (completed) Box(Modifier.size(24.dp).border(1.5.dp, PoliceColors.Muted, androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center) { Text("i", fontSize = 16.sp) }
                                else Text(if (isActive) "Active" else "Start", fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (!history && sets.isNotEmpty() && sets.all { it.completedAt != null && it.actualReps != null }) {
                    Text("Exercise complete", style = MaterialTheme.typography.labelMedium, color = PoliceColors.LightBlue)
                }
            }
        }
    }
}

@Composable
private fun SessionReadOnlyCell(value: String, description: String, muted: Boolean, modifier: Modifier) {
    Box(modifier.heightIn(min = 48.dp).semantics { contentDescription = description }
        .background(if (muted) PoliceColors.Raised.copy(alpha = .45f) else PoliceColors.Background, RoundedCornerShape(8.dp))
        .border(1.dp, PoliceColors.Border.copy(alpha = if (muted) .45f else 1f), RoundedCornerShape(8.dp)).padding(2.dp),
        contentAlignment = Alignment.Center) {
        Text(value, style = StatTypography.copy(fontSize = 12.sp), color = if (muted) PoliceColors.Muted else PoliceColors.Text)
    }
}

internal fun sessionPounds(grams: Long): String = BigDecimal.valueOf(grams)
    .divide(BigDecimal("453.59237"), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
