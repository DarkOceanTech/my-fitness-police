package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutHistoryScreen(model: SessionViewModel, selectedId: String?, onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val completed = state.sessions.filter { it.workout.kind == "session" && it.workout.finishedAt != null }
    val workout = completed.find { it.workout.id == selectedId }
    var menu by remember { mutableStateOf(false) }
    var deletingAll by rememberSaveable { mutableStateOf(false) }
    var deletingOne by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var infoId by rememberSaveable(selectedId) { mutableStateOf<String?>(null) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    fun back() {
        when {
            infoId != null -> infoId = null
            selectedId != null -> onSelect(null)
            else -> onBack?.invoke()
        }
    }
    BackHandler(selectedId != null || onBack != null) { if (!action.saving) back() }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            when (action.completedAction) {
                "rename-history-workout" -> renaming = false
                "delete-all-history", "delete-history-workout" -> {
                    deletingAll = false; deletingOne = false; infoId = null; onSelect(null)
                }
            }
            revision = action.revision
        }
    }
    val infoEntry = workout?.exercises?.find { entry -> entry.sets.any { it.id == infoId } }
    val infoSet = infoEntry?.sets?.find { it.id == infoId }
    Column(modifier.fillMaxSize().testTag(if (selectedId == null) "history-list" else "history-detail")) {
        SectionPageHeader("Progress Reports",
            location = if (selectedId == null) "Workout History" else "Workout History / ${workout?.workout?.displayName() ?: "Workout"}",
            onBack = if (selectedId != null || onBack != null) ({ back() }) else null,
            backLabel = if (infoId != null) "Close set info" else if (selectedId != null) "Back to history" else "Back to Reports",
            enabled = !action.saving) {
            Box {
                IconButton(onClick = { menu = true }, enabled = !action.saving,
                    modifier = Modifier.semantics { contentDescription = "History options" }) { Text("⋮", fontSize = 26.sp) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (selectedId == null) "Edit workout names" else "Edit workout name") },
                        enabled = if (selectedId == null) completed.isNotEmpty() else workout != null, onClick = {
                            menu = false; model.clearError(); renaming = true
                        })
                    if (workout != null) DropdownMenuItem(text = { Text("Delete workout", color = PoliceColors.Error) }, onClick = {
                        menu = false; model.clearError(); deletingOne = true
                    })
                    if (selectedId == null) DropdownMenuItem(text = { Text("Delete all", color = PoliceColors.Error) }, enabled = completed.isNotEmpty(), onClick = {
                        menu = false; model.clearError(); deletingAll = true
                    })
                }
            }
        }
        if (workout != null) {
            if (infoEntry != null && infoSet != null) key(infoEntry.workoutExercise.id) {
                SetDetailsPager(infoEntry.sets.filter { it.completedAt != null }.sortedBy { it.position }, infoSet.id,
                    !action.saving, Modifier.weight(1f), tagPrefix = "history", onSelect = { infoId = it; model.clearError() }) { detailSet, lock ->
                    RecordedSetInfoPanel(infoEntry.exercise.name, detailSet, action, Modifier.fillMaxSize(),
                        onNavigationLocked = lock, onEdit = model::clearError,
                        onSave = { pounds, planned, actual, rpe -> model.correctHistorySet(workout.workout.id, detailSet.id, pounds, planned, actual, rpe) },
                        onSaveNote = { model.saveHistorySetNote(workout.workout.id, detailSet.id, it) },
                        totals = { WorkoutSummary(workout, Modifier, "Set ${detailSet.position + 1} info", infoEntry.exercise.name) })
                }
            }
            else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history-exercises"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "totals") { WorkoutSummary(workout, Modifier.padding(top = 12.dp)) }
                if (workout.performedSets().isEmpty()) item { Text("No sets were recorded in this session.") }
                workout.sessionState?.pauseReason?.takeIf { it.isNotBlank() }?.let { reason -> item {
                    Text("Last pause: $reason", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                } }
                items(workout.orderedExercises().filter { entry -> entry.sets.any { it.completedAt != null } }, key = { it.workoutExercise.id }) { entry ->
                    ActiveExerciseCard(entry, workout, workout.sessionState, !action.saving,
                        onInfo = { model.clearError(); infoId = it })
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            else if (state.failed) item {
                Text("Could not load workout history.")
                TextButton(onClick = model::retry) { Text("Retry") }
            } else if (selectedId != null) item { Text("Loading saved workout…") }
            else if (completed.isEmpty()) item { Text("No completed workouts yet") }
            else items(completed, key = { it.workout.id }) { session ->
                Surface(Modifier.fillMaxWidth().testTag("history-workout-${session.workout.id}").clickable { onSelect(session.workout.id) },
                    color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShieldMark(Modifier.size(36.dp), checked = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.workout.displayName(), style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.workout.startedAt)),
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            Text("${session.performedSets().size} sets · ${session.performedSets().sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        }
                        Text("→", color = PoliceColors.LightBlue)
                    }
                }
            }
        }
        if (action.error != null && infoId == null && !deletingAll && !deletingOne && !renaming) Text(action.error!!, Modifier.padding(16.dp), color = PoliceColors.Error)
    }
    if (renaming) HistoryWorkoutNameDialog(completed, selectedId, action,
        onDismiss = { renaming = false; model.clearError() }, onEdit = model::clearError, onSave = model::renameHistoryWorkout)
    if (deletingAll || deletingOne) AlertDialog(
        onDismissRequest = { if (!action.saving) { deletingAll = false; deletingOne = false } },
        title = { Text(if (deletingAll) "Delete all workout history?" else "Delete workout?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (deletingAll) "This permanently deletes all ${completed.size} logged workouts, including their sets, notes, and timing. This cannot be undone."
                else "This permanently deletes this logged workout, including its sets, notes, and timing. This cannot be undone.")
            Text("Saved workout plans, unfinished workouts, and your exercise catalog will stay available.")
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { if (deletingAll) model.deleteAllHistory() else selectedId?.let(model::deleteWorkout) },
            enabled = !action.saving, modifier = Modifier.testTag("confirm-delete-history")) {
            Text(if (action.saving) "Deleting…" else if (deletingAll) "Delete all" else "Delete", color = PoliceColors.Error)
        } },
        dismissButton = { TextButton(onClick = { deletingAll = false; deletingOne = false }, enabled = !action.saving) { Text("Cancel") } })
}

@Composable
internal fun WorkoutSummary(workout: WorkoutDetails, modifier: Modifier, infoTitle: String? = null, exerciseName: String? = null,
    durationMillis: Long? = null, tagPrefix: String = "history", compactSetInfo: Boolean = false) {
    val performed = workout.performedSets()
    val duration = durationMillis ?: workout.sessionState?.dutyElapsedMillis ?: ((workout.workout.finishedAt ?: workout.workout.startedAt) - workout.workout.startedAt)
    Surface(modifier.fillMaxWidth().testTag("$tagPrefix-workout-totals"), color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        if (compactSetInfo && infoTitle != null) {
            Column(Modifier.fillMaxWidth().testTag("$tagPrefix-set-info-header").padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(infoTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text("${performed.size} sets · ${performed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                        Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge,
                        color = PoliceColors.LightBlue)
                }
                exerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
                SirenRule(Modifier.fillMaxWidth())
            }
        } else Column {
            if (infoTitle != null) {
                Column(Modifier.fillMaxWidth().testTag("$tagPrefix-set-info-header").padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(infoTitle, style = MaterialTheme.typography.titleLarge)
                    exerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
                }
                SirenRule(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ShieldMark(Modifier.size(32.dp), checked = true)
                Column(Modifier.weight(1f)) {
                    Text("${performed.size} sets · ${performed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps", style = MaterialTheme.typography.titleMedium)
                    Text("Workout total", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sessionTime(duration), style = StatTypography.copy(fontSize = 20.sp))
                    Text("ON DUTY", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
            }
        }
    }
}
