package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.domain.formatting.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.ExerciseSessionSnapshot
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.data.displayName
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

/** A read-only snapshot: browsing never changes the editor or the recorded session. */
@Composable
internal fun LastSessionDialog(state: LastSessionState, importing: Boolean, importError: String?,
    onClose: () -> Unit, onRetry: () -> Unit, onImport: (String) -> Unit, allowImport: Boolean = true) {
    val snapshot = state.snapshot
    // The dialog owns these values for its lifetime, including the null/loading snapshot
    // while the activity is recreated. Loading must not reset the user's current page.
    var infoId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingProgress by rememberSaveable { mutableStateOf(false) }
    var confirmingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingExerciseName by rememberSaveable { mutableStateOf("") }
    var confirmingSetCount by rememberSaveable { mutableIntStateOf(0) }
    val infoSet = snapshot?.entry?.sets?.firstOrNull { it.id == infoId }
    fun back() {
        if (!importing) when {
            confirmingEntryId != null -> confirmingEntryId = null
            infoId != null -> infoId = null
            viewingProgress -> viewingProgress = false
            else -> onClose()
        }
    }
    Dialog(onDismissRequest = ::back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("last-session-dialog").policeBackdrop().safeDrawingPadding(),
            color = Color.Transparent, contentColor = PoliceColors.Text) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = ::back, enabled = !importing) {
                        Icon(painterResource(R.drawable.ic_back),
                            if (confirmingEntryId != null || infoId != null) "Back to last session" else "Close last session")
                    }
                    Text(if (confirmingEntryId != null) "Import session" else if (infoSet == null) "Last session" else "Set ${infoSet.position + 1} info",
                        Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (confirmingEntryId != null) TextButton(onClick = onClose, enabled = !importing,
                        modifier = Modifier.testTag("close-last-session")) { Text("Close") }
                    else if (infoSet == null) TextButton(onClick = { viewingProgress = true },
                        enabled = !importing && snapshot != null && !state.loading,
                        modifier = Modifier.testTag("view-exercise-progress")) { Text("View progress") }
                }
                SirenRule(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                when {
                    confirmingEntryId != null -> LastSessionImportConfirmation(confirmingExerciseName, confirmingSetCount, Modifier.weight(1f))
                    state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text("Loading last session…", color = PoliceColors.Muted)
                        }
                    }
                    snapshot == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(state.error ?: "No completed session found for this exercise yet.",
                                textAlign = TextAlign.Center, color = if (state.error != null) PoliceColors.Error else PoliceColors.Muted,
                                modifier = Modifier.testTag("last-session-empty"))
                            if (state.error != null) TextButton(onClick = onRetry, enabled = !importing) { Text("Retry") }
                        }
                    }
                    infoSet != null -> LastSessionSetDetails(snapshot, infoSet, Modifier.weight(1f))
                    else -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("last-session-exercises"),
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { LastSessionSummary(snapshot) }
                        item {
                            ActiveExerciseCard(snapshot.entry, snapshot.workout, snapshot.workout.sessionState, !importing,
                                onInfo = { infoId = it }, showHistoryHeader = false, historyMenuInBody = true)
                        }
                        val unrecorded = snapshot.entry.sets.filter { it.completedAt == null }.sortedBy { it.position }
                        if (unrecorded.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Unperformed sets", style = MaterialTheme.typography.titleMedium)
                                    Text(if (allowImport) "These planned sets were not recorded. They are included when you import this session."
                                        else "These planned sets were not recorded.",
                                        color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            items(unrecorded, key = { it.id }) { set -> LastSessionUnrecordedSet(set, !importing) { infoId = set.id } }
                        }
                        if (allowImport) item {
                            Text("Import replaces this exercise’s current sets with all ${snapshot.entry.sets.size} sets from this session. " +
                                "Your current exercise note stays unchanged. Set notes, actual reps, RPE, and recorded times are not imported.",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted,
                                modifier = Modifier.testTag("last-session-import-description"))
                        }
                    }
                }
                if (importError != null) Text(importError, color = PoliceColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("last-session-import-error"))
                HorizontalDivider(color = PoliceColors.Border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (confirmingEntryId != null) {
                        PoliceOutlinedButton(onClick = { confirmingEntryId = null }, enabled = !importing,
                            modifier = Modifier.weight(1f).testTag("back-from-import-confirmation")) { Text("Back") }
                        PoliceButton(onClick = { confirmingEntryId?.let(onImport) }, enabled = !importing,
                            modifier = Modifier.weight(1f).testTag("confirm-import-session")) {
                            Text(if (importing) "Importing…" else "Import session")
                        }
                    } else {
                        PoliceOutlinedButton(onClick = onClose, enabled = !importing,
                            modifier = Modifier.weight(1f).testTag("close-last-session")) { Text("Close") }
                        if (allowImport) PoliceButton(onClick = { snapshot?.let {
                            confirmingEntryId = it.entry.workoutExercise.id
                            confirmingExerciseName = it.entry.exercise.name
                            confirmingSetCount = it.entry.sets.size
                        } }, enabled = !importing && !state.loading && state.error == null && snapshot?.entry?.sets?.isNotEmpty() == true,
                            modifier = Modifier.weight(1f).testTag("import-session")) { Text("Import session") }
                    }
                }
            }
        }
    }
    if (viewingProgress) ExerciseProgressDialog(state, onClose = { viewingProgress = false })
}

@Composable
private fun LastSessionImportConfirmation(exerciseName: String, setCount: Int, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().testTag("last-session-import-confirmation"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Error)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Replace current sets?", style = MaterialTheme.typography.titleLarge, color = PoliceColors.Text)
                    Text(exerciseName, style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
                    Text("All current sets for this exercise will be overwritten with $setCount sets from the session you viewed.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Current weights, planned reps, set types, and modifiers will be replaced. Any notes or results on the current sets will be cleared.",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your exercise note stays unchanged.", style = MaterialTheme.typography.bodyMedium)
                    Text("The recorded session in Workout Log is not changed.", style = MaterialTheme.typography.bodyMedium)
                    Text("Set notes, actual reps, RPE, and recorded times are not copied from the session.",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
            }
        }
        item {
            Text("Choose Back to keep your current sets, or Import session to replace them. Then use Save on the workout screen to keep your changes.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}

@Composable
private fun LastSessionSummary(snapshot: ExerciseSessionSnapshot) {
    val workout = snapshot.workout.workout
    val completed = snapshot.entry.sets.filter { it.completedAt != null }
    val title = workout.trainingPlan.takeIf { workout.historyGroupId != null && it.isNotBlank() } ?: workout.displayName()
    Surface(Modifier.fillMaxWidth().testTag("last-session-summary"), shape = PoliceCardShape, color = PoliceColors.Card,
        border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.startedAt)),
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            Text(snapshot.entry.exercise.name, style = MaterialTheme.typography.titleSmall, color = PoliceColors.LightBlue)
            Text("${completed.size} sets · ${completed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                style = MaterialTheme.typography.labelLarge)
            Text("Active ${sessionTime(completed.sumOf { it.activeMillis })} · Break ${sessionTime(completed.sumOf { it.restMillis })}",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}

@Composable
internal fun LastSessionUnrecordedSet(set: WorkoutSet, enabled: Boolean, onInfo: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("last-session-unrecorded-${set.id}")
        .clickable(enabled = enabled, onClickLabel = "View set ${set.position + 1} info", onClick = onInfo),
        shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Set ${set.position + 1} · ${if (set.isWarmup) "Warm-up" else "Working set"}", style = MaterialTheme.typography.labelLarge)
                Text("${formatSessionPounds(set.weightGrams)} Lbs · ${set.reps} planned reps · ${modifierName(set)}",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, PoliceColors.Muted)) {
                Box(Modifier.size(28.dp).semantics { contentDescription = "Info for unperformed set ${set.position + 1}" },
                    contentAlignment = Alignment.Center) { Text("i", color = PoliceColors.LightBlue) }
            }
        }
    }
}

@Composable
internal fun LastSessionSetDetails(snapshot: ExerciseSessionSnapshot, set: WorkoutSet, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().testTag("last-session-set-details"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(snapshot.entry.exercise.name, style = MaterialTheme.typography.titleLarge)
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (set.isWarmup) "Warm-up Set" else "Working Set", style = MaterialTheme.typography.titleMedium)
                    SirenRule(Modifier.fillMaxWidth())
                    if (set.completedAt == null) Text("This set was not recorded.", color = PoliceColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LastSessionValue("Weight (Lbs)", formatSessionPounds(set.weightGrams), "last-session-set-weight", Modifier.weight(1f))
                        LastSessionValue("RPE", set.rpe?.toString() ?: "—", "last-session-set-rpe", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LastSessionValue("Planned reps", set.reps.toString(), "last-session-set-planned", Modifier.weight(1f),
                            valueColor = repComparisonColor(set.reps, set.actualReps))
                        LastSessionValue("Actual reps", set.actualReps?.toString() ?: if (set.completedAt != null) set.reps.toString() else "—",
                            "last-session-set-actual", Modifier.weight(1f), valueColor = repComparisonColor(set.reps, set.actualReps))
                    }
                    Text(modifierName(set), color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Active" to set.activeMillis, "Break" to set.restMillis,
                        "Total" to set.activeMillis + set.restMillis).forEach { (label, duration) ->
                        LastSessionValue(label, sessionTime(duration), "last-session-set-${label.lowercase()}-time", Modifier.weight(1f), 20)
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set Notes", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                    Text(set.notes.ifBlank { "No set note." }, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("last-session-set-notes"))
                }
            }
        }
    }
}

@Composable
private fun LastSessionValue(label: String, value: String, tag: String, modifier: Modifier, size: Int = 24,
    valueColor: Color = PoliceColors.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        Text(value, style = StatTypography.copy(fontSize = size.sp), color = valueColor, modifier = Modifier.testTag(tag))
    }
}

private fun modifierName(set: WorkoutSet): String = when (set.modifier) {
    "superset" -> "Superset"
    "drop_set" -> "Drop set"
    else -> "Regular"
}
