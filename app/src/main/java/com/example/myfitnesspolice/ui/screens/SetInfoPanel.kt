package com.example.myfitnesspolice.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myfitnesspolice.data.WorkoutSet

/** Active sessions share the recorded-set layout and editing workflow used in Progress. */
@Composable
internal fun SetInfoPanel(exerciseName: String, set: WorkoutSet, action: SessionAction,
    modifier: Modifier = Modifier, restMillis: Long = set.restMillis,
    onEdit: () -> Unit, onSave: (String?, String, String, Int?) -> Unit,
    onSaveNote: (String) -> Unit, totals: @Composable () -> Unit) {
    RecordedSetInfoPanel(exerciseName, set, action, modifier, onEdit, onSave, onSaveNote, totals,
        tagPrefix = "active", correctionAction = "correct-active-set", noteAction = "save-active-set-note",
        restMillis = restMillis)
}
