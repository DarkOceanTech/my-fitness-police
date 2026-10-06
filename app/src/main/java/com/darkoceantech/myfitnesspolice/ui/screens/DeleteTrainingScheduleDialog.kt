package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun DeleteTrainingScheduleDialog(planName: String, dates: List<String>, initialDate: String?,
    action: SessionAction, onCancel: () -> Unit, onDelete: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initialDate?.takeIf { it in dates } ?: dates.minOrNull()) }
    WorkoutEditorDialog("Delete scheduled training", "delete-training-schedule-dialog", !action.saving, onCancel,
        fullScreen = true, footer = {
            PoliceOutlinedButton(onClick = onCancel, enabled = !action.saving,
                modifier = Modifier.weight(1f).testTag("cancel-delete-schedule")) { Text("Cancel") }
            PoliceButton(onClick = { selected?.let(onDelete) }, enabled = !action.saving && selected in dates,
                modifier = Modifier.weight(1f).testTag("confirm-delete-schedule")) { Text(if (action.saving) "Deleting…" else "Delete") }
        }) {
        LazyColumn(Modifier.weight(1f).testTag("delete-schedule-dates")) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(planName, style = MaterialTheme.typography.titleLarge)
                    Text("Choose the calendar date to remove. The training plan, its workouts, and recorded sessions will be kept.",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                    action.error?.let { Text(it, color = PoliceColors.Error) }
                }
            }
            items(dates.sorted(), key = { it }) { date ->
                Row(Modifier.fillMaxWidth().testTag("delete-schedule-date-$date").clickable(enabled = !action.saving) { selected = date },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == date, onClick = { selected = date }, enabled = !action.saving)
                    Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
                }
            }
        }
    }
}
