package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.Exercise
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun ExerciseInfoDialog(exercise: Exercise, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("exercise-info-dialog"),
        containerColor = PoliceColors.Card, shape = PoliceCardShape,
        title = { Text(exercise.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SirenRule(Modifier.fillMaxWidth())
                listOf("Description" to exercise.description, "Equipment" to exercise.equipment,
                    "Primary muscles" to exercise.primaryMuscles, "Secondary muscles" to exercise.secondaryMuscles).forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelLarge)
                        Text(value.ifBlank { "Not specified" }, color = PoliceColors.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
