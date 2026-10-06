package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.formatting.formatSessionPounds
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SwapExerciseDialog(entry: ExerciseWithSets, exercises: List<Exercise>, action: SessionAction,
    preview: LastSessionState, onPreview: (String) -> Unit, onDismiss: () -> Unit,
    onSwap: (String, SwapSetSource, String?) -> Unit) {
    var selectedId by rememberSaveable(entry.workoutExercise.id) { mutableStateOf<String?>(null) }
    var sourceName by rememberSaveable(entry.workoutExercise.id) { mutableStateOf(SwapSetSource.CURRENT.name) }
    var viewingLast by rememberSaveable(selectedId) { mutableStateOf(false) }
    val candidates = exercises.filter { it.id != entry.exercise.id && !it.isArchived }
    val recommended = remember(entry.exercise, candidates) { recommendedExerciseSwaps(entry.exercise, candidates).map { it.id } }
    val replacement = candidates.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId) { selectedId?.let(onPreview) }
    if (replacement == null) ExercisePickerDialog(candidates, action.saving, action.error,
        ExerciseFormState(), {}, { _, _, _, _, _ -> }, onDismiss, onSelect = { selectedId = it; sourceName = SwapSetSource.CURRENT.name },
        title = "Swap exercise", selectLabel = "Choose", backLabel = "Back to active workout", allowCreate = false,
        recommendedExerciseIds = recommended, intro = {
            Text("Replace ${entry.exercise.name} for this session. Recommendations share its ${entry.exercise.mainMuscleGroup().label.lowercase()} muscle group.",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
        })
    else WorkoutEditorDialog("Swap exercise", "swap-exercise-dialog", !action.saving, onDismiss, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = { selectedId = null }, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Choose another") }
        PoliceButton(onClick = { onSwap(replacement.id, SwapSetSource.valueOf(sourceName), preview.snapshot?.entry?.workoutExercise?.id) },
            enabled = !action.saving && (sourceName != SwapSetSource.LAST_SESSION.name || (!preview.loading && preview.error == null && preview.snapshot != null)),
            modifier = Modifier.weight(1f).testTag("confirm-swap-exercise")) { Text(if (action.saving) "Swapping…" else "Swap exercise") }
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("swap-exercise-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(replacement.name, style = MaterialTheme.typography.headlineSmall)
            Text(replacement.equipment, color = PoliceColors.Muted)
            Text("Choose planned sets", style = MaterialTheme.typography.titleMedium)
            val choices = listOf(
                Triple(SwapSetSource.CURRENT, "Copy current set data", "${entry.sets.size} planned sets · keep current weights, reps, types and modifiers"),
                Triple(SwapSetSource.LAST_SESSION, "Use last session data", when {
                    preview.loading -> "Loading recorded sets…"
                    preview.error != null -> preview.error
                    preview.snapshot == null -> "No recorded session is available for this exercise"
                    else -> "${preview.snapshot.entry.sets.size} sets · " + DateFormat.getDateInstance().format(Date(preview.snapshot.workout.workout.startedAt))
                }),
                Triple(SwapSetSource.NEW, "New set data", "Start with 1 working set · 10 reps · 0 Lbs")
            )
            choices.forEach { (source, title, description) ->
                val enabled = !action.saving && (source != SwapSetSource.LAST_SESSION || (!preview.loading && preview.snapshot != null && preview.error == null))
                Surface(Modifier.fillMaxWidth().testTag("swap-source-${source.name.lowercase()}")
                    .clickable(enabled = enabled, role = Role.RadioButton) { sourceName = source.name }
                    .semantics { selected = sourceName == source.name },
                    color = PoliceColors.Card, shape = PoliceCardShape,
                    border = BorderStroke(1.dp, if (sourceName == source.name) PoliceColors.Red else PoliceColors.Border)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sourceName == source.name, onClick = null, enabled = enabled)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, color = if (enabled) PoliceColors.Text else PoliceColors.Muted)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        }
                    }
                }
            }
            if (preview.loading) CircularProgressIndicator(Modifier.size(24.dp))
            if (preview.error != null) TextButton(onClick = { onPreview(replacement.id) }, enabled = !action.saving) { Text("Retry last session") }
            if (preview.snapshot != null) PoliceOutlinedButton(onClick = { viewingLast = true }, enabled = !action.saving,
                modifier = Modifier.fillMaxWidth().testTag("swap-view-last-session")) { Text("View last session") }
            val rows = when (SwapSetSource.valueOf(sourceName)) {
                SwapSetSource.CURRENT -> entry.sets.sortedBy { it.position }
                SwapSetSource.LAST_SESSION -> preview.snapshot?.entry?.sets?.sortedBy { it.position }.orEmpty()
                SwapSetSource.NEW -> emptyList()
            }
            rows.forEachIndexed { index, set -> Text("${index + 1} · ${if (set.isWarmup) "Warm-up" else "Working set"} · ${formatSessionPounds(set.weightGrams)} Lbs · ${set.reps} reps",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue) }
            Text("Only this session changes. Results, RPE, set notes and timing are not imported. Exercise notes and equipment setup are cleared for the replacement.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
    if (viewingLast && replacement != null) LastSessionDialog(preview, action.saving, action.error,
        onClose = { viewingLast = false }, onRetry = { onPreview(replacement.id) }, onImport = {}, allowImport = false)
}
