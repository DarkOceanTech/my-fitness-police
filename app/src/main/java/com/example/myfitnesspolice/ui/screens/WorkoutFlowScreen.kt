package com.example.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import com.example.myfitnesspolice.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfitnesspolice.data.*
import java.text.DateFormat
import java.util.Date
import java.math.BigDecimal
import kotlinx.coroutines.delay

@Composable
fun WorkoutFlowScreen(model: SessionViewModel, history: Boolean, modifier: Modifier = Modifier, initialSelection: String? = null) {
    if (history) {
        var selection by rememberSaveable(initialSelection) { mutableStateOf(initialSelection) }
        WorkoutHistoryScreen(model, selection, { selection = it }, modifier)
        return
    }
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(initialSelection) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var editEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var editSet by rememberSaveable { mutableStateOf<String?>(null) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var deleteEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSet by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteWorkout by rememberSaveable { mutableStateOf<String?>(null) }
    val details = if (history) state.sessions.firstOrNull { it.workout.id == selectedId && it.workout.kind == "session" }
        else state.sessions.firstOrNull { it.workout.finishedAt == null && it.workout.kind == "session" }
    LaunchedEffect(action.revision) {
        picker = false
        editEntry = null
        editSet = null
        finish = false
        deleteSet = null
        deleteEntry = null
        if (deleteWorkout != null) {
            if (selectedId == deleteWorkout) selectedId = null
            deleteWorkout = null
        }
    }
    BackHandler(history && selectedId != null) { selectedId = null }
    LazyColumn(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (history) "History" else "Workout", style = MaterialTheme.typography.headlineLarge)
                SirenRule(Modifier.width(56.dp))
            }
        }
        if (history && selectedId != null) item {
            TextButton(onClick = { selectedId = null }) { Text("Back to history") }
        }
        if (state.loading) item { CircularProgressIndicator() }
        else if (state.failed) item {
            Text("Could not load workouts.")
            TextButton(onClick = model::retry) { Text("Retry") }
        } else if (details == null) {
            if (history) {
                val completed = state.sessions.filter { it.workout.finishedAt != null && it.workout.kind == "session" }
                if (completed.isEmpty()) item { Text("No completed workouts yet") }
                items(completed, key = { it.workout.id }) { session ->
                    Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                        border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth().testTag("history-workout-" + session.workout.id)
                                .clickable { selectedId = session.workout.id }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShieldMark(Modifier.size(38.dp), checked = true)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(workoutDate(session.workout.startedAt), style = MaterialTheme.typography.titleMedium)
                                    val count = session.performedSets().size
                                    val reps = session.performedSets().sumOf { (it.actualReps ?: it.reps).toLong() }
                                    val minutes = ((session.sessionState?.dutyElapsedMillis ?: (session.workout.finishedAt!! - session.workout.startedAt)) / 60000).coerceAtLeast(0)
                                    Text("$count sets · $reps reps · $minutes min", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                                }
                                Text("→")
                            }
                            TextButton(onClick = { model.clearError(); deleteWorkout = session.workout.id },
                                enabled = !action.saving) { Text("Delete workout", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            } else item {
                Text("Ready for your next session?")
                PoliceButton(onClick = model::start, enabled = !action.saving) { Text("Start workout") }
            }
        } else {
            item {
                Text(workoutDate(details.workout.startedAt), style = MaterialTheme.typography.titleMedium)
                if (details.workout.finishedAt == null) ActiveSessionClock(details.workout.startedAt)
                else Text("Completed", color = PoliceColors.Muted)
            }
            if (details.workout.finishedAt == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoliceButton(onClick = { model.clearError(); picker = true }, enabled = !action.saving) { Text("Choose exercise") }
                    OutlinedButton(onClick = { model.clearError(); finish = true }, enabled = !action.saving) { Text("Finish workout") }
                }
            }
            if (history && details.workout.finishedAt != null) item {
                TextButton(onClick = { model.clearError(); deleteWorkout = details.workout.id },
                    enabled = !action.saving) { Text("Delete workout", color = MaterialTheme.colorScheme.error) }
            }
            if (details.exercises.isEmpty()) item { Text("Choose an exercise to start recording sets.") }
            if (history && details.performedSets().isEmpty()) item { Text("No sets were recorded in this session.") }
            if (history && details.sessionState?.pauseReason?.isNotBlank() == true) item {
                Text("Last pause: ${details.sessionState.pauseReason}", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            details.orderedExercises().filter { !history || it.sets.any { set -> set.completedAt != null } }.forEach { entry ->
                item(key = entry.workoutExercise.id) {
                    Text(entry.exercise.name, style = MaterialTheme.typography.titleLarge)
                }
                items(entry.sets.filter { !history || it.completedAt != null }.sortedBy { it.position }, key = { it.id }) { set ->
                    Card(Modifier.fillMaxWidth(), shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${set.actualReps ?: set.reps} reps × ${pounds(set.weightGrams)} lbs", style = StatTypography.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                            if (details.sessionState != null && set.actualReps != null) Text(
                                "Planned: ${set.reps} reps · Active ${sessionTime(set.activeMillis)} · Break ${sessionTime(set.restMillis)}",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            Row {
                                TextButton(onClick = {
                                    model.clearError(); editEntry = entry.workoutExercise.id; editSet = set.id
                                }, enabled = !action.saving) { Text("Edit set") }
                                TextButton(onClick = {
                                    model.clearError(); deleteEntry = entry.workoutExercise.id; deleteSet = set.id
                                }, enabled = !action.saving) { Text("Delete set") }
                            }
                        }
                    }
                }
                if (details.workout.finishedAt == null) item {
                    OutlinedButton(onClick = {
                        model.clearError(); editEntry = entry.workoutExercise.id; editSet = null
                    }, enabled = !action.saving) { Text("Add set") }
                }
            }
        }
        if (action.error != null && !picker && editEntry == null && !finish && deleteSet == null)
            item { Text(action.error!!, color = MaterialTheme.colorScheme.error) }
    }
    if (deleteWorkout != null) AlertDialog(
        onDismissRequest = { if (!action.saving) deleteWorkout = null },
        title = { Text("Delete workout?") },
        text = { Text(action.error ?: "This permanently deletes the workout and all its recorded sets. Your exercise catalog stays available.") },
        confirmButton = { TextButton(onClick = { model.deleteWorkout(deleteWorkout!!) },
            enabled = !action.saving) { Text(if (action.saving) "Deleting…" else "Delete") } },
        dismissButton = { TextButton(onClick = { deleteWorkout = null },
            enabled = !action.saving) { Text("Cancel") } },
    )
    if (picker && details != null) AlertDialog(
        onDismissRequest = { if (!action.saving) picker = false },
        title = { Text("Choose exercise") },
        text = {
            LazyColumn {
                if (state.exercises.isEmpty()) item { Text("Add an exercise in the Exercises tab first.") }
                items(state.exercises, key = { it.id }) { exercise ->
                    TextButton(onClick = { model.addExercise(details.workout.id, exercise.id) }, enabled = !action.saving) {
                        Text(exercise.name)
                    }
                }
                action.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { TextButton(onClick = { picker = false }, enabled = !action.saving) { Text("Cancel") } },
    )
    val entry = details?.exercises?.firstOrNull { it.workoutExercise.id == editEntry }
    if (entry != null) key(editEntry, editSet) {
        SetDialog(entry.sets.firstOrNull { it.id == editSet }, action,
            onDismiss = { editEntry = null },
            onSave = { reps, weight, unit ->
                model.saveSet(details.workout.id, entry.workoutExercise.id, editSet, reps, weight, unit)
            })
    }
    if (finish && details != null) AlertDialog(
        onDismissRequest = { if (!action.saving) finish = false },
        title = { Text("Finish this workout?") },
        text = { Text(action.error ?: "Your saved sets will be available in History. You can correct them there later.") },
        confirmButton = { TextButton(onClick = { model.finish(details.workout.id) }, enabled = !action.saving) { Text("Finish") } },
        dismissButton = { TextButton(onClick = { finish = false }, enabled = !action.saving) { Text("Cancel") } },
    )
    if (deleteSet != null && deleteEntry != null && details != null) AlertDialog(
        onDismissRequest = { if (!action.saving) deleteSet = null },
        title = { Text("Delete this set?") },
        text = { Text(action.error ?: "This removes the recorded set from this workout.") },
        confirmButton = { TextButton(onClick = {
            model.deleteSet(details.workout.id, deleteEntry!!, deleteSet!!)
        }, enabled = !action.saving) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteSet = null }, enabled = !action.saving) { Text("Cancel") } },
    )
}

@Composable
private fun SetDialog(set: WorkoutSet?, action: SessionAction, onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit) {
    var reps by rememberSaveable { mutableStateOf((set?.actualReps ?: set?.reps)?.toString() ?: "10") }
    var weight by rememberSaveable { mutableStateOf(set?.let { pounds(it.weightGrams) } ?: "0") }
    val repOptions = remember(reps) { ((1..100).map { it.toString() } + reps).distinct().sortedBy { it.toInt() } }
    val weightOptions = remember(weight) {
        ((0..600).map { BigDecimal.valueOf(it * 25L, 1).stripTrailingZeros().toPlainString() } + weight)
            .distinct().sortedBy { it.toBigDecimal() }
    }
    AlertDialog(
        onDismissRequest = { if (!action.saving) onDismiss() },
        title = { Text(if (set == null) "Record set" else "Edit set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrollPicker("Reps", reps, repOptions, !action.saving) { reps = it }
                ScrollPicker("Weight (lbs)", weight, weightOptions, !action.saving) { weight = it }
                Text("Drag the dropdown list to choose a value. Use 0 lbs for bodyweight.")
                action.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val savedWeight = if (set != null && weight == pounds(set.weightGrams))
                BigDecimal.valueOf(set.weightGrams).divide(BigDecimal("453.59237"), 9,
                    java.math.RoundingMode.HALF_UP).toPlainString() else weight
            onSave(reps, savedWeight, "lb")
        }, enabled = !action.saving) {
            Text(if (action.saving) "Saving…" else "Save set")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.saving) { Text("Cancel") } },
    )
}

@Composable
private fun ScrollPicker(label: String, value: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }) {
                Text(value, Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                val scroll = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (options.indexOf(value) - 2).coerceAtLeast(0))
                LazyColumn(Modifier.width(240.dp).height(240.dp).testTag("$label options"), state = scroll) {
                    items(options, key = { it }) { option ->
                        DropdownMenuItem(text = { Text(if (option == value) "$option  ✓" else option) },
                            onClick = { onSelect(option); expanded = false })
                    }
                }
            }
        }
    }
}

private fun pounds(grams: Long) = BigDecimal.valueOf(grams).divide(BigDecimal("453.59237"), 2,
    java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
private fun workoutDate(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

@Composable
private fun ActiveSessionClock(startedAt: Long) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), color = PoliceColors.Card,
        shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShieldMark(Modifier.size(40.dp))
            Column {
                Text("ON DUTY", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                Text("%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60), style = StatTypography)
            }
        }
    }
}
