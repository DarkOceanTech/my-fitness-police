package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutHistoryScreen(model: SessionViewModel, selectedId: String?, onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val completed = state.sessions.filter { it.workout.kind == "session" && it.workout.finishedAt != null }
    val groups = remember(completed) { groupWorkoutHistory(completed) }
    val selectedGroup = groups.firstOrNull { group -> group.workouts.any { it.workout.id == selectedId } }
    var menu by remember { mutableStateOf(false) }
    var deletingAll by rememberSaveable { mutableStateOf(false) }
    var deletingWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingDeletion by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var grouping by rememberSaveable { mutableStateOf(false) }
    var infoId by rememberSaveable(selectedId) { mutableStateOf<String?>(null) }
    var replacingEntryId by rememberSaveable(selectedId) { mutableStateOf<String?>(null) }
    var collapsedEntryIds by rememberSaveable(selectedId) { mutableStateOf(emptyList<String>()) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var dateWindowName by rememberSaveable { mutableStateOf(HistoryDateWindow.AllTime.name) }
    var startDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var exerciseFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var workoutFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var planFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = HistoryFilters(HistoryDateWindow.valueOf(dateWindowName), startDay, endDay, exerciseFilter, workoutFilter, planFilter)
    val matches = remember(completed, filters) { filterWorkoutHistory(completed, filters) }
    val matchingGroups = remember(groups, matches) { filterHistoryGroups(groups, matches) }
    fun changeFilters(value: HistoryFilters) {
        dateWindowName = value.dateWindow.name; startDay = value.startDay; endDay = value.endDay
        exerciseFilter = value.exerciseId; workoutFilter = value.workoutId; planFilter = value.trainingPlanId
    }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    fun back() {
        when {
            replacingEntryId != null -> { replacingEntryId = null; model.clearError() }
            infoId != null -> infoId = null
            selectedId != null -> onSelect(null)
            else -> onBack?.invoke()
        }
    }
    BackHandler(selectedId != null || onBack != null) { if (!action.saving) back() }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            when (action.completedAction) {
                "rename-history-workout" -> renaming = false
                "replace-history-exercise" -> replacingEntryId = null
                "delete-all-history", "delete-history-workout" -> {
                    deletingAll = false; deletingWorkoutId = null; choosingDeletion = false; infoId = null; onSelect(null)
                }
            }
            revision = action.revision
        }
    }
    val infoOwner = selectedGroup?.exercises?.find { item -> item.entry.sets.any { it.id == infoId } }
    val infoEntry = infoOwner?.entry
    val infoSet = infoEntry?.sets?.find { it.id == infoId }
    Column(modifier.fillMaxSize().testTag(if (selectedId == null) "history-list" else "history-detail")) {
        SectionPageHeader("Progress Reports",
            location = if (selectedId == null) "Workout Log" else "Workout Log / ${selectedGroup?.title ?: "Workout"}",
            onBack = if (selectedId != null || onBack != null) ({ back() }) else null,
            backLabel = if (infoId != null) "Close set info" else if (selectedId != null) "Back to Workout Log" else "Back to Reports",
            enabled = !action.saving) {
            Box {
                IconButton(onClick = { menu = true }, enabled = !action.saving,
                    modifier = Modifier.semantics { contentDescription = "Workout Log options" }) { Text("⋮", fontSize = 26.sp) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (selectedId == null) DropdownMenuItem(text = { Text("Group workouts into plan") }, enabled = completed.isNotEmpty(),
                        modifier = Modifier.testTag("group-history-workouts-menu"), onClick = {
                            menu = false; model.clearError(); grouping = true
                        })
                    if (selectedGroup != null) DropdownMenuItem(text = { Text(if (selectedGroup.isGrouped) "Edit workout names" else "Edit workout name") },
                        onClick = {
                            menu = false; model.clearError(); renaming = true
                        })
                    if (selectedGroup != null) DropdownMenuItem(text = { Text(if (selectedGroup.isGrouped) "Delete a workout…" else "Delete workout", color = PoliceColors.Error) }, onClick = {
                        menu = false; model.clearError()
                        if (selectedGroup.isGrouped) choosingDeletion = true else deletingWorkoutId = selectedGroup.representativeId
                    })
                    if (selectedId == null) DropdownMenuItem(text = { Text("Delete all", color = PoliceColors.Error) }, enabled = completed.isNotEmpty(), onClick = {
                        menu = false; model.clearError(); deletingAll = true
                    })
                }
            }
        }
        if (selectedGroup != null) {
            if (infoOwner != null && infoEntry != null && infoSet != null) key(infoEntry.workoutExercise.id) {
                SetDetailsPager(infoEntry.sets.filter { it.completedAt != null }.sortedBy { it.position }, infoSet.id,
                    !action.saving, Modifier.weight(1f), tagPrefix = "history", onSelect = { infoId = it; model.clearError() }) { detailSet, lock ->
                    RecordedSetInfoPanel(infoEntry.exercise.name, detailSet, action, Modifier.fillMaxSize(),
                        onNavigationLocked = lock, onEdit = model::clearError,
                        onSave = { pounds, planned, actual, rpe, warmup -> model.correctHistorySet(infoOwner.workout.workout.id, detailSet.id, pounds, planned, actual, rpe, warmup) },
                        onSaveNote = { model.saveHistorySetNote(infoOwner.workout.workout.id, detailSet.id, it) },
                        totals = { HistoryGroupSummary(selectedGroup, Modifier, "Set ${detailSet.position + 1} info", infoEntry.exercise.name) })
                }
            }
            else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history-exercises"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "totals") { HistoryGroupSummary(selectedGroup, Modifier.padding(top = 12.dp)) }
                if (selectedGroup.performedSets.isEmpty()) item { Text("No sets were recorded in this session.") }
                selectedGroup.workouts.forEach { member ->
                    member.sessionState?.pauseReason?.takeIf { it.isNotBlank() }?.let { reason -> item(key = "pause-${member.workout.id}") {
                        Text((if (selectedGroup.isGrouped) "${member.workout.displayName()} · " else "") + "Last pause: $reason",
                            style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                    } }
                }
                val entries = selectedGroup.exercises.filter { item -> item.entry.sets.any { it.completedAt != null } }
                if (entries.isNotEmpty()) item(key = "collapse-controls") {
                    val allCollapsed = entries.all { it.entry.workoutExercise.id in collapsedEntryIds }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entries.size} exercises", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                        TextButton(onClick = { collapsedEntryIds = if (allCollapsed) emptyList() else entries.map { it.entry.workoutExercise.id } },
                            enabled = !action.saving, modifier = Modifier.testTag("history-collapse-all")) {
                            Text(if (allCollapsed) "Expand all" else "Collapse all")
                        }
                    }
                }
                itemsIndexed(entries, key = { _, item -> item.entry.workoutExercise.id }) { index, item ->
                    val entry = item.entry
                    Column(Modifier.fillMaxWidth().testTag("history-group-entry-$index-${entry.workoutExercise.id}"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedGroup.isGrouped && (index == 0 || entries[index - 1].workout.workout.id != item.workout.workout.id)) {
                        Text(item.workout.workout.displayName(), style = MaterialTheme.typography.titleSmall, color = PoliceColors.LightBlue)
                    }
                    ActiveExerciseCard(entry, item.workout, item.workout.sessionState, !action.saving,
                        onInfo = { model.clearError(); infoId = it },
                        historyCollapsed = entry.workoutExercise.id in collapsedEntryIds,
                        onToggleHistoryCollapse = { collapsedEntryIds = if (entry.workoutExercise.id in collapsedEntryIds)
                            collapsedEntryIds - entry.workoutExercise.id else collapsedEntryIds + entry.workoutExercise.id },
                        onChangeExercise = { model.clearError(); replacingEntryId = entry.workoutExercise.id })
                    }
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("history-sessions"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (selectedId == null && (completed.isNotEmpty() || filters.activeCount > 0)) item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilterControls(completed, filters, matches.size, filtersExpanded,
                        onExpanded = { filtersExpanded = it }, onChange = ::changeFilters)
                    if (filters.activeCount > 0 && matchingGroups.any { it.isGrouped }) Text(
                        "Matching sessions show their complete groups, including all recorded sets and totals.",
                        style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
            else if (state.failed) item {
                Text("Could not load Workout Log.")
                TextButton(onClick = model::retry) { Text("Retry") }
            } else if (selectedId != null) item { Text("Loading saved workout…") }
            else if (completed.isEmpty()) item { Text("No completed workouts yet") }
            else if (matchingGroups.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("history-no-matches")) {
                    Text("No workouts match these filters.")
                    TextButton(onClick = { changeFilters(HistoryFilters()) }, modifier = Modifier.testTag("clear-empty-history-filters")) { Text("Clear filters") }
                }
            }
            else items(matchingGroups, key = { it.key }) { group ->
                Surface(Modifier.fillMaxWidth().testTag("history-workout-${group.representativeId}").clickable { onSelect(group.representativeId) },
                    color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShieldMark(Modifier.size(36.dp), checked = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(group.title, style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(group.startedAt)),
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            Text("${group.performedSets.size} sets · ${group.performedSets.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            if (group.isGrouped) Text("${group.workouts.size} recorded workouts", style = MaterialTheme.typography.labelSmall,
                                color = PoliceColors.LightBlue)
                        }
                        Text("→", color = PoliceColors.LightBlue)
                    }
                }
            }
        }
        if (action.error != null && infoId == null && replacingEntryId == null && !deletingAll && deletingWorkoutId == null && !renaming && !grouping) Text(action.error!!, Modifier.padding(16.dp), color = PoliceColors.Error)
    }
    if (grouping) HistoryPlanGroupingDialog(model, onDismiss = { grouping = false },
        initialSelectedIds = selectedGroup?.workouts?.map { it.workout.id }?.toSet().orEmpty())
    val replacing = selectedGroup?.exercises?.find { it.entry.workoutExercise.id == replacingEntryId }
    if (replacing != null) ExercisePickerDialog(state.exercises, action.saving, action.error,
        form = ExerciseFormState(), onResetForm = {}, onAddExercise = { _, _, _, _, _ -> },
        onDismiss = { replacingEntryId = null; model.clearError() }, onSelect = { id ->
            if (id == replacing.entry.exercise.id) replacingEntryId = null
            else model.replaceHistoryExercise(replacing.workout.workout.id, replacing.entry.workoutExercise.id, id)
        }, title = "Change exercise", selectLabel = "Select", backLabel = "Back to Workout Log", allowCreate = false)
    if (renaming) HistoryWorkoutNameDialog(selectedGroup?.workouts ?: completed,
        if (selectedGroup?.isGrouped == true) null else selectedId, action,
        onDismiss = { renaming = false; model.clearError() }, onEdit = model::clearError, onSave = model::renameHistoryWorkout)
    if (choosingDeletion && selectedGroup != null) HistoryGroupMemberDialog(selectedGroup.workouts,
        onDismiss = { choosingDeletion = false }, onSelect = { deletingWorkoutId = it; choosingDeletion = false })
    val deletingWorkout = completed.find { it.workout.id == deletingWorkoutId }
    if (deletingAll || deletingWorkout != null) AlertDialog(
        onDismissRequest = { if (!action.saving) { deletingAll = false; deletingWorkoutId = null } },
        title = { Text(if (deletingAll) "Delete all Workout Log entries?" else "Delete workout?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (deletingAll) "This permanently deletes all ${completed.size} logged workouts, including their sets, notes, and timing. This cannot be undone."
                else "This permanently deletes “${deletingWorkout!!.workout.displayName()}”, including its sets, notes, and timing. This cannot be undone.")
            if (!deletingAll && selectedGroup != null && selectedGroup.workouts.size > 1) Text("The other workouts in this recorded group will stay available.")
            Text("Saved workout plans, unfinished workouts, and your exercise catalog will stay available.")
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { if (deletingAll) model.deleteAllHistory() else deletingWorkoutId?.let(model::deleteWorkout) },
            enabled = !action.saving, modifier = Modifier.testTag("confirm-delete-history")) {
            Text(if (action.saving) "Deleting…" else if (deletingAll) "Delete all" else "Delete", color = PoliceColors.Error)
        } },
        dismissButton = { TextButton(onClick = { deletingAll = false; deletingWorkoutId = null }, enabled = !action.saving) { Text("Cancel") } })
}

@Composable
internal fun WorkoutSummary(workout: WorkoutDetails, modifier: Modifier, infoTitle: String? = null, exerciseName: String? = null,
    durationMillis: Long? = null, tagPrefix: String = "history", compactSetInfo: Boolean = false) {
    val performed = workout.performedSets()
    val duration = durationMillis ?: workout.sessionState?.dutyElapsedMillis ?: ((workout.workout.finishedAt ?: workout.workout.startedAt) - workout.workout.startedAt)
    Surface(modifier.fillMaxWidth().testTag("$tagPrefix-workout-totals"), color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        if (compactSetInfo && infoTitle != null) {
            Column(Modifier.fillMaxWidth().testTag("$tagPrefix-set-info-header").padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(infoTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text("${performed.size} sets · ${performed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                        Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge,
                        color = PoliceColors.LightBlue)
                }
                exerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
                SirenRule(Modifier.fillMaxWidth())
            }
        } else Column {
            if (infoTitle != null) {
                Column(Modifier.fillMaxWidth().testTag("$tagPrefix-set-info-header").padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(infoTitle, style = MaterialTheme.typography.titleLarge)
                    exerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
                }
                SirenRule(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ShieldMark(Modifier.size(32.dp), checked = true)
                Column(Modifier.weight(1f)) {
                    Text("${performed.size} sets · ${performed.sumOf { (it.actualReps ?: it.reps).toLong() }} reps", style = MaterialTheme.typography.titleMedium)
                    Text("Workout total", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sessionTime(duration), style = StatTypography.copy(fontSize = 20.sp))
                    Text("ON DUTY", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
            }
        }
    }
}
