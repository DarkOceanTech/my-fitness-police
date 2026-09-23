package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

@Composable
internal fun ActualRepsDialog(set: WorkoutSet, action: SessionAction, onSave: (String, Int?, String) -> Unit,
    onPause: () -> Unit, breakMillis: Long = 0, visible: Boolean = true, cooldownRemaining: Long? = null) {
    var actual by rememberSaveable(set.id) { mutableStateOf((set.actualReps ?: set.reps).toString()) }
    var rpe by rememberSaveable(set.id) { mutableStateOf(set.rpe) }
    var note by rememberSaveable(set.id) { mutableStateOf(set.notes) }
    var noteDraft by rememberSaveable(set.id) { mutableStateOf(set.notes) }
    var editingNote by rememberSaveable(set.id) { mutableStateOf(false) }
    if (!visible) return // Retain the unsaved form while the pause dialog is visible.
    AlertDialog(onDismissRequest = {}, title = { Text("Actual reps performed") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (cooldownRemaining != null) "Your one-minute cool-down has started. Save your results to finish automatically when it ends."
                else "Your break timer is running. How many reps did you complete?")
            Text("Break · ${sessionTime(breakMillis)}", color = PoliceColors.LightBlue,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("after-set-break-time"))
            if (cooldownRemaining != null) Text(if (cooldownRemaining == 0L) "Cool-down complete. Save reps to finish."
                else "Cool-down · ${sessionTime(((cooldownRemaining + 999) / 1000) * 1000)} remaining",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.LightBlue,
                modifier = Modifier.testTag("after-set-cooldown"))
            OutlinedTextField(actual, { actual = it }, label = { Text("Actual reps") }, enabled = !action.saving,
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("actual-reps-input"))
            Text("Enter 0 if you could not complete a rep.", style = MaterialTheme.typography.bodySmall)
            Text("RPE (optional)", style = MaterialTheme.typography.labelLarge, color = PoliceColors.Muted)
            TablePicker("Set RPE", rpe?.toString() ?: "Not set", listOf("Not set") + (1..10).map { it.toString() },
                !action.saving, Modifier.fillMaxWidth().testTag("after-set-rpe")) { rpe = it.toIntOrNull() }
            OutlinedButton(onClick = { noteDraft = note; editingNote = true }, enabled = !action.saving,
                modifier = Modifier.fillMaxWidth().testTag("after-set-note")) {
                Text(if (note.isBlank()) "Add a note" else "Edit set note")
            }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(actual, rpe, note) }, enabled = !action.saving) { Text("Save reps") } },
        dismissButton = { TextButton(onClick = onPause, enabled = !action.saving) { Text("Pause workout") } })
    if (editingNote) AlertDialog(onDismissRequest = { editingNote = false }, title = { Text("Set note") },
        text = { OutlinedTextField(noteDraft, { noteDraft = it }, label = { Text("Set notes") }, minLines = 3, maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("after-set-note-input")) },
        confirmButton = { TextButton(onClick = { note = noteDraft.trim(); editingNote = false }) { Text("Save note") } },
        dismissButton = { TextButton(onClick = { editingNote = false }) { Text("Cancel") } })
}

@Composable
internal fun PauseReasonDialog(reason: String, action: SessionAction, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(reason) }
    AlertDialog(onDismissRequest = { if (!action.saving) onDismiss() }, title = { Text("Workout paused") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("All timers are stopped. You can close the app and resume this workout later.")
            OutlinedTextField(text, { text = it }, label = { Text("Pause reason (optional)") },
                enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("pause-reason-input"), maxLines = 4)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = !action.saving) { Text("Save reason") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.saving) { Text("Close") } })
}

@Composable
internal fun SessionNoteDialog(note: String, action: SessionAction, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(note) }
    AlertDialog(onDismissRequest = { if (!action.saving) onDismiss() }, title = { Text("Edit note") },
        text = { Column {
            OutlinedTextField(text, { text = it }, label = { Text("Exercise note") }, enabled = !action.saving,
                modifier = Modifier.heightIn(max = 240.dp))
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = !action.saving) { Text("Save note") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.saving) { Text("Cancel") } })
}
