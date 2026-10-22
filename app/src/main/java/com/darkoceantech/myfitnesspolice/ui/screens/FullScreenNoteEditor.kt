package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.*

/** Controlled draft: Cancel/back never invokes Save. The caller owns persistence. */
@Composable
internal fun FullScreenNoteEditor(title: String, note: String, onChange: (String) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit, action: SessionAction = SessionAction(),
    label: String = "Exercise note", subtitle: String = "", tag: String = "note-editor",
    inputTag: String = "$tag-input", saveTag: String = "$tag-save", cancelTag: String = "$tag-cancel",
    saveLabel: String = "Save note") {
    WorkoutEditorDialog(title, tag, !action.saving, onDismiss, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onDismiss, enabled = !action.saving,
            modifier = Modifier.weight(1f).testTag(cancelTag)) { Text("Cancel") }
        PoliceButton(onClick = onSave, enabled = !action.saving,
            modifier = Modifier.weight(1f).testTag(saveTag)) { Text(if (action.saving) "Saving…" else saveLabel) }
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (subtitle.isNotBlank()) Text(subtitle, color = PoliceColors.Muted)
            OutlinedTextField(note, onChange, label = { Text(label) }, enabled = !action.saving,
                minLines = 6, modifier = Modifier.fillMaxWidth().testTag(inputTag))
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}