package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.data.displayName
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HistoryGroupSummary(group: HistoryGroup, modifier: Modifier,
    infoTitle: String? = null, exerciseName: String? = null) {
    if (!group.isGrouped) {
        WorkoutSummary(group.workouts.first(), modifier, infoTitle, exerciseName)
        return
    }
    val performed = group.performedSets
    // Sum recorded session time, not the gaps between separately completed workouts.
    val duration = group.workouts.sumOf { member ->
        (member.sessionState?.dutyElapsedMillis
            ?: ((member.workout.finishedAt ?: member.workout.startedAt) - member.workout.startedAt)).coerceAtLeast(0L)
    }
    Surface(modifier.fillMaxWidth().testTag("history-workout-totals"), color = PoliceColors.Card,
        shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column {
            if (infoTitle != null) {
                Column(Modifier.fillMaxWidth().testTag("history-set-info-header").padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(infoTitle, style = MaterialTheme.typography.titleLarge)
                    exerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
                }
                SirenRule(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ShieldMark(Modifier.size(32.dp), checked = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${performed.size} sets · ${performed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Training total · ${group.workouts.size} workouts", style = MaterialTheme.typography.labelSmall,
                        color = PoliceColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sessionTime(duration), style = StatTypography.copy(fontSize = 20.sp))
                    Text("ON DUTY", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
            }
        }
    }
}

@Composable
internal fun HistoryGroupMemberDialog(workouts: List<WorkoutDetails>, onDismiss: () -> Unit,
    onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = PoliceColors.Card,
        title = { Text("Delete a workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (workouts.size > 1) "Choose one workout from this recorded group. The other workouts will stay available."
                    else "Choose the recorded workout to delete. You’ll confirm before it is removed.",
                    style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("history-delete-member-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(workouts, key = { it.workout.id }) { member ->
                        OutlinedButton(onClick = { onSelect(member.workout.id) }, shape = PoliceCardShape,
                            modifier = Modifier.fillMaxWidth().testTag("delete-history-member-${member.workout.id}")) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(member.workout.displayName(), style = MaterialTheme.typography.titleSmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(member.workout.startedAt)),
                                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            }
                        }
                    }
                }
            }
        }, confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
