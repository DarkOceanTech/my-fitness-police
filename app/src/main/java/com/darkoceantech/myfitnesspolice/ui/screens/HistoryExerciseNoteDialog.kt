package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

@Composable
internal fun HistoryExerciseNoteDialog(
    exerciseName: String, initialNote: String, action: SessionAction,
    onDismiss: () -> Unit, onEdit: () -> Unit, onSave: (String) -> Unit,
) {
    var note by rememberSaveable { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = { if (!action.saving) onDismiss() },
        modifier = Modifier.testTag("history-exercise-note-dialog"),
        containerColor = PoliceColors.Card,
        title = { Text("Edit exercise note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(exerciseName, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                OutlinedTextField(note, { note = it; onEdit() }, label = { Text("Exercise note") },
                    minLines = 3, maxLines = 6, enabled = !action.saving,
                    modifier = Modifier.fillMaxWidth().testTag("history-exercise-note-input"))
                action.error?.let { Text(it, color = PoliceColors.Error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }, enabled = !action.saving,
                modifier = Modifier.testTag("history-exercise-note-save")) {
                Text(if (action.saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !action.saving,
                modifier = Modifier.testTag("history-exercise-note-cancel")) { Text("Cancel") }
        },
    )
}
