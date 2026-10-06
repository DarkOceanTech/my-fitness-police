package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.formatting.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

/** Read-only history for one catalog exercise, independent of the import source. */
@Composable
internal fun ExerciseProgressDialog(state: LastSessionState, onClose: () -> Unit) {
    val sessions = state.history.ifEmpty { listOfNotNull(state.snapshot) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var collapsedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedSetId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSession = sessions.firstOrNull { session -> session.entry.sets.any { it.id == selectedSetId } }
    val selectedSet = selectedSession?.entry?.sets?.firstOrNull { it.id == selectedSetId }
    val ordered = remember(sessions, ascending) {
        val sorted = sessions.sortedWith(compareBy<ExerciseSessionSnapshot> { it.workout.workout.startedAt }
            .thenBy { it.workout.workout.id }.thenBy { it.entry.workoutExercise.position })
        if (ascending) sorted else sorted.sortedByDescending { it.workout.workout.startedAt }
    }
    fun back() { if (selectedSetId != null) selectedSetId = null else onClose() }
    Dialog(onDismissRequest = ::back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("exercise-progress-dialog").policeBackdrop().safeDrawingPadding(),
            color = Color.Transparent, contentColor = PoliceColors.Text) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back) {
                        Icon(painterResource(R.drawable.ic_back), if (selectedSetId != null) "Back to exercise progress" else "Back to last session")
                    }
                    Text(if (selectedSet != null) "Set ${selectedSet.position + 1} info" else "Exercise history & progress",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                SirenRule(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                if (selectedSession != null && selectedSet != null) {
                    LastSessionSetDetails(selectedSession, selectedSet, Modifier.weight(1f))
                } else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("exercise-progress-list"),
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { ExerciseHistoryOverview(sessions) }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var sortOpen by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                TextButton(onClick = { sortOpen = true }, modifier = Modifier.testTag("exercise-history-sort")) {
                                    Text(if (ascending) "Date: oldest first" else "Date: newest first")
                                }
                                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                                    DropdownMenuItem(text = { Text("Date descending (newest first)") },
                                        onClick = { ascending = false; sortOpen = false })
                                    DropdownMenuItem(text = { Text("Date ascending (oldest first)") },
                                        onClick = { ascending = true; sortOpen = false })
                                }
                            }
                            val allCollapsed = sessions.isNotEmpty() && sessions.all { it.entry.workoutExercise.id in collapsedIds }
                            TextButton(onClick = {
                                collapsedIds = if (allCollapsed) emptyList() else sessions.map { it.entry.workoutExercise.id }
                            }, enabled = sessions.isNotEmpty(), modifier = Modifier.testTag("exercise-history-toggle-all")) {
                                Text(if (allCollapsed) "Expand all" else "Collapse all")
                            }
                        }
                    }
                    if (sessions.isEmpty()) item { Text("No completed sessions for this exercise yet.", color = PoliceColors.Muted) }
                    items(ordered, key = { it.entry.workoutExercise.id }) { session ->
                        val id = session.entry.workoutExercise.id
                        Column(Modifier.testTag("exercise-history-session-$id"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(session.workout.workout.trainingPlan.takeIf {
                                session.workout.workout.historyGroupId != null && it.isNotBlank()
                            } ?: session.workout.workout.displayName(), style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(session.workout.workout.startedAt)),
                                color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                            ActiveExerciseCard(session.entry, session.workout, session.workout.sessionState, true,
                                onInfo = { selectedSetId = it }, historyCollapsed = id in collapsedIds,
                                onToggleHistoryCollapse = {
                                    collapsedIds = if (id in collapsedIds) collapsedIds - id else collapsedIds + id
                                }, historyMenuInBody = true, showHistoryExerciseLabel = false)
                            if (id !in collapsedIds) session.entry.sets.filter { it.completedAt == null }
                                .sortedBy { it.position }.forEach { set ->
                                    LastSessionUnrecordedSet(set, true) { selectedSetId = set.id }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryOverview(sessions: List<ExerciseSessionSnapshot>) {
    val sets = sessions.flatMap { it.entry.sets }.filter { it.completedAt != null }
    val reps = sets.sumOf { (it.actualReps ?: it.reps).toLong() }
    val volumeGrams = sets.sumOf { (it.weightGrams ?: 0) * (it.actualReps ?: it.reps) }
    Surface(Modifier.fillMaxWidth().testTag("exercise-history-overview"), color = PoliceColors.Card,
        shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(sessions.firstOrNull()?.entry?.exercise?.name ?: "Exercise progress", style = MaterialTheme.typography.titleLarge)
            SirenRule(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryMetric("Sessions", sessions.map { it.workout.workout.id }.distinct().size.toString(), Modifier.weight(1f))
                HistoryMetric("Sets", sets.size.toString(), Modifier.weight(1f))
                HistoryMetric("Reps", reps.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryMetric("Lbs lifted", formatSessionPounds(volumeGrams), Modifier.weight(1f))
                HistoryMetric("Active", sessionTime(sets.sumOf { it.activeMillis }), Modifier.weight(1f))
                HistoryMetric("Break", sessionTime(sets.sumOf { it.restMillis }), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
        Text(value, style = StatTypography.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            color = PoliceColors.LightBlue, modifier = Modifier.testTag("exercise-history-total-${label.lowercase().replace(' ', '-')}"))
    }
}
