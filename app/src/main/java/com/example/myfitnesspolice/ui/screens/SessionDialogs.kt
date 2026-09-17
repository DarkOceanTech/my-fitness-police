package com.example.myfitnesspolice.ui.screens

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
import com.example.myfitnesspolice.data.WorkoutSet
import com.example.myfitnesspolice.ui.theme.PoliceColors

@Composable
internal fun ActualRepsDialog(set: WorkoutSet, action: SessionAction, onSave: (String) -> Unit, onPause: () -> Unit) {
    var actual by rememberSaveable(set.id) { mutableStateOf((set.actualReps ?: set.reps).toString()) }
    AlertDialog(onDismissRequest = {}, title = { Text("Actual reps performed") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your break timer is running. How many reps did you complete?")
            OutlinedTextField(actual, { actual = it }, label = { Text("Actual reps") }, enabled = !action.saving,
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("actual-reps-input"))
            Text("Enter 0 if you could not complete a rep.", style = MaterialTheme.typography.bodySmall)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(actual) }, enabled = !action.saving) { Text("Save reps") } },
        dismissButton = { TextButton(onClick = onPause, enabled = !action.saving) { Text("Pause workout") } })
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
