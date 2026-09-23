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
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.data.displayName
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HistoryWorkoutNameDialog(workouts: List<WorkoutDetails>, initialId: String?, action: SessionAction,
    onDismiss: () -> Unit, onEdit: () -> Unit, onSave: (String, String) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(initialId ?: workouts.firstOrNull()?.workout?.id) }
    val selected = workouts.find { it.workout.id == selectedId }
    var name by rememberSaveable(selectedId) { mutableStateOf(selected?.workout?.displayName().orEmpty()) }
    var choosing by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!action.saving) onDismiss() }, title = { Text("Edit workout name") },
        containerColor = PoliceColors.Card,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (initialId == null) Box {
                OutlinedButton(onClick = { choosing = true }, enabled = !action.saving,
                    modifier = Modifier.fillMaxWidth().testTag("rename-workout-selector")) {
                    Text(selected?.let { "${it.workout.displayName()} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.workout.startedAt))}" }
                        ?: "Choose a workout")
                }
                DropdownMenu(choosing, onDismissRequest = { choosing = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                    workouts.forEach { item ->
                        DropdownMenuItem(text = { Column {
                            Text(item.workout.displayName())
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.workout.startedAt)),
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        } }, modifier = Modifier.testTag("rename-workout-${item.workout.id}"),
                            onClick = { selectedId = item.workout.id; choosing = false; onEdit() })
                    }
                }
            }
            OutlinedTextField(name, { name = it; onEdit() }, label = { Text("Workout name") }, singleLine = true,
                enabled = !action.saving && selected != null, modifier = Modifier.fillMaxWidth().testTag("history-workout-name"))
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { selectedId?.let { onSave(it, name) } },
            enabled = !action.saving && selected != null && name.isNotBlank(), modifier = Modifier.testTag("save-history-workout-name")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.saving) { Text("Cancel") } })
}
