package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.Exercise
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun ExerciseInfoDialog(exercise: Exercise, onDismiss: () -> Unit) {
    WorkoutEditorDialog("Exercise info", "exercise-info-dialog", true, onDismiss,
        fullScreen = true, footer = {
            PoliceButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = PoliceColors.Text)
            listOf("Description" to exercise.description, "Equipment" to exercise.equipment,
                "Primary muscles" to exercise.primaryMuscles, "Secondary muscles" to exercise.secondaryMuscles).forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelLarge)
                    Text(value.ifBlank { "Not specified" }, color = PoliceColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
