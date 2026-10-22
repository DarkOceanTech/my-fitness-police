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
    FullScreenNoteEditor("Edit exercise note", note, { note = it; onEdit() }, onDismiss, { onSave(note) },
        action = action, subtitle = exerciseName, tag = "history-exercise-note-dialog",
        inputTag = "history-exercise-note-input", saveTag = "history-exercise-note-save",
        cancelTag = "history-exercise-note-cancel", saveLabel = "Save")
}