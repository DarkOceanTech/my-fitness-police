package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.data.*
import com.example.myfitnesspolice.ui.theme.*

@Composable
internal fun TrainingPlanCard(details: TrainingPlanDetails, library: List<WorkoutDetails>, onOpen: () -> Unit) {
    val workouts = details.workouts(library)
    Surface(Modifier.fillMaxWidth().testTag("training-plan-${details.plan.id}").clickable(onClick = onOpen),
        shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${workouts.size} workouts", Modifier.weight(1f), color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
                SirenRule(Modifier.width(36.dp))
            }
            Text(details.plan.name, style = MaterialTheme.typography.headlineSmall)
            Text(details.plan.dayOfWeek.ifBlank { "Not scheduled" }, style = MaterialTheme.typography.labelMedium, color = PoliceColors.LightBlue)
            Text("${workouts.sumOf { it.plannedSets() }} sets · ${workouts.sumOf { it.plannedReps() }} reps", color = PoliceColors.Muted)
            HorizontalDivider(color = PoliceColors.Border)
            Text(workouts.joinToString(" → ") { it.workout.displayName() }.ifBlank { "Add workouts to this plan" },
                color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TrainingPlanEditorScreen(existing: TrainingPlanDetails?, library: List<WorkoutDetails>, action: SessionAction,
    modifier: Modifier = Modifier, onBack: () -> Unit, onSaved: () -> Unit, onWorkouts: () -> Unit,
    onSave: (String?, String, List<String>, String) -> Unit) {
    var name by rememberSaveable(existing?.plan?.id) { mutableStateOf(existing?.plan?.name.orEmpty()) }
    var day by rememberSaveable(existing?.plan?.id) { mutableStateOf(existing?.plan?.dayOfWeek.orEmpty()) }
    var includedIds by rememberSaveable(existing?.plan?.id) { mutableStateOf(existing?.workoutIds().orEmpty()) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var includedSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSave by rememberSaveable { mutableStateOf(false) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    val saved = library.filter { it.workout.kind == "plan" }
    val byId = saved.associateBy { it.workout.id }
    val included = includedIds.mapNotNull { byId[it] }
    val available = saved.filter { it.workout.id !in includedIds }.sortedBy { it.workout.displayName().lowercase(java.util.Locale.ROOT) }
    val selected = selectedIds.filter { id -> available.any { it.workout.id == id } }
    val selectedIndex = included.indexOfFirst { it.workout.id == includedSelection }
    val canEditIncluded = !action.saving && selectedIds.isEmpty() && selectedIndex >= 0
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            if (requestedSave && action.completedAction == "save-training-plan") { requestedSave = false; onSaved() }
            revision = action.revision
        }
    }
    fun include(ids: List<String>) {
        includedIds = (includedIds + ids).distinct()
        selectedIds = emptyList()
        includedSelection = ids.lastOrNull()
    }
    fun remove(id: String) {
        includedIds = includedIds - id
        if (includedSelection == id) includedSelection = null
    }
    fun move(delta: Int) {
        val destination = selectedIndex + delta
        if (!canEditIncluded || destination !in included.indices) return
        includedIds = included.map { it.workout.id }.toMutableList().apply {
            val moving = removeAt(selectedIndex); add(destination, moving)
        }
    }
    Column(modifier.fillMaxSize().imePadding().padding(12.dp).testTag("training-plan-editor"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TrainingHeader(if (existing == null) "Training Plan" else "Edit Training Plan", !action.saving, onBack) {
            PoliceButton(onClick = {
                requestedSave = true
                onSave(existing?.plan?.id, name, included.map { it.workout.id }, day)
            }, enabled = !action.saving && name.isNotBlank() && included.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save-training-plan")) {
                Text(if (action.saving) "Saving…" else if (existing == null) "Create" else "Save")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, placeholder = { Text("Back + Arms + Abs") },
                singleLine = true, enabled = !action.saving, modifier = Modifier.weight(1f).testTag("training-plan-name"))
            TrainingDayField(day, !action.saving, Modifier.width(IntrinsicSize.Max).widthIn(min = 96.dp)) { day = it }
        }
        Text("Tap to select a workout. Hold to move it between lists.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        Surface(Modifier.weight(1f).fillMaxWidth().testTag("training-workout-selection"),
            shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    TrainingSelectionPane("Excluded", available, selected.toSet(), !action.saving,
                        Modifier.weight(1f).fillMaxHeight(), "available", onSelect = { id ->
                            includedSelection = null
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        }, onHold = { include(listOf(it)) }, emptyText = if (saved.isEmpty()) "Create a workout first." else "All workouts included.",
                        footer = {
                            if (saved.isEmpty()) TextButton(onClick = onWorkouts, enabled = !action.saving) { Text("Workouts") }
                        })
                    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 12.dp), color = PoliceColors.Border)
                    TrainingSelectionPane("Included", included, setOfNotNull(includedSelection), !action.saving,
                        Modifier.weight(1f).fillMaxHeight(), "included", onSelect = {
                            selectedIds = emptyList()
                            includedSelection = if (includedSelection == it) null else it
                        }, onHold = { selectedIds = emptyList(); remove(it) }, emptyText = "Add workouts to your plan.", footer = {})
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PoliceButton(onClick = { include(selected) }, enabled = !action.saving && selected.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("include-selected-workouts")) { Text("Add") }
                    TrainingReorderButton("↑", "Move selected workout up", "move-included-up", canEditIncluded && selectedIndex > 0) { move(-1) }
                    TrainingReorderButton("↓", "Move selected workout down", "move-included-down", canEditIncluded && selectedIndex < included.lastIndex) { move(1) }
                    OutlinedButton(onClick = { includedSelection?.let(::remove) }, enabled = canEditIncluded,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("remove-selected-workout")) { Text("Remove") }
                }
            }
        }
        action.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TrainingReorderButton(symbol: String, description: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).testTag(tag)
            .border(1.dp, if (enabled) PoliceColors.LightBlue else PoliceColors.Border, RoundedCornerShape(10.dp))
            .semantics { contentDescription = description }) {
        Text(symbol, fontSize = 24.sp, color = if (enabled) PoliceColors.LightBlue else PoliceColors.Muted.copy(alpha = .45f))
    }
}

@Composable
private fun TrainingDayField(day: String, enabled: Boolean, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Day of Week", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("training-plan-day")) {
                Text(if (day.isBlank()) "—" else day.take(3), Modifier.weight(1f), maxLines = 1); Text("⌄")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                (listOf("Not scheduled") + trainingWeekdays).forEach { option ->
                    DropdownMenuItem(text = {
                        Row(Modifier.widthIn(min = 208.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (option == "Not scheduled") "—" else option.take(3), Modifier.width(40.dp),
                                style = MaterialTheme.typography.labelLarge, color = PoliceColors.LightBlue)
                            Text(option, Modifier.weight(1f))
                        }
                    }, onClick = { onSelect(if (option == "Not scheduled") "" else option); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrainingSelectionPane(title: String, workouts: List<WorkoutDetails>, selection: Set<String>, enabled: Boolean,
    modifier: Modifier, tag: String, onSelect: (String) -> Unit, onHold: (String) -> Unit, emptyText: String,
    footer: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
            Text("(${workouts.size})", modifier = Modifier.testTag("$tag-count"),
                style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        }
        LazyColumn(Modifier.weight(1f).testTag("$tag-workouts"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (workouts.isEmpty()) item { Text(emptyText, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
            items(workouts, key = { it.workout.id }) { workout ->
                val id = workout.workout.id
                Column(Modifier.fillMaxWidth().testTag("$tag-workout-$id")
                    .background(if (id in selection) PoliceColors.Raised else PoliceColors.Background, MaterialTheme.shapes.small)
                    .border(1.dp, if (id in selection) PoliceColors.LightBlue else PoliceColors.Border, MaterialTheme.shapes.small)
                    .combinedClickable(enabled = enabled, onClick = { onSelect(id) },
                        onLongClickLabel = if (tag == "available") "Include workout" else "Exclude workout", onLongClick = { onHold(id) })
                    .semantics { this.selected = id in selection }
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(workout.workout.displayName(), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${workout.plannedSets()} sets · ${workout.plannedReps()} reps", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
            }
        }
        footer()
    }
}

@Composable
private fun WorkoutModuleLabel(workout: WorkoutDetails, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(workout.workout.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${workout.plannedSets()} sets · ${workout.plannedReps()} reps", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
    }
}

@Composable
private fun TrainingHeader(title: String, enabled: Boolean, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = { SirenRule(Modifier.width(24.dp)) }) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, enabled = enabled) { Icon(painterResource(R.drawable.ic_back), "Back to Plan") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        actions()
    }
}

@Composable
internal fun TrainingPlanDetailScreen(details: TrainingPlanDetails, library: List<WorkoutDetails>, action: SessionAction,
    modifier: Modifier = Modifier, onBack: () -> Unit, onEdit: () -> Unit, onStart: () -> Unit, onWorkout: (String) -> Unit, onDelete: () -> Unit) {
    val workouts = details.workouts(library)
    var deleting by rememberSaveable(details.plan.id) { mutableStateOf(false) }
    var options by remember(details.plan.id) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().testTag("training-plan-detail")) {
        SectionPageHeader(details.plan.name, onBack = onBack, backLabel = "Back to Plan", enabled = !action.saving) {
            Box {
                IconButton(onClick = { options = true }, enabled = !action.saving,
                    modifier = Modifier.semantics { contentDescription = "Training plan options" }) { Text("⋮", fontSize = 26.sp) }
                DropdownMenu(options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Edit plan") }, enabled = !action.saving,
                        modifier = Modifier.testTag("edit-training-plan"), onClick = { options = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete training plan", color = PoliceColors.Error) }, enabled = !action.saving,
                        modifier = Modifier.testTag("delete-training-plan"), onClick = { options = false; deleting = true })
                }
            }
        }
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${workouts.size} workouts · ${workouts.sumOf { it.plannedSets() }} sets · ${workouts.sumOf { it.plannedReps() }} reps",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            Text("Day of Week · ${details.plan.dayOfWeek.ifBlank { "Not scheduled" }}",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.LightBlue, modifier = Modifier.testTag("training-plan-schedule"))
            LazyColumn(Modifier.weight(1f).testTag("training-plan-workouts"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (workouts.isEmpty()) item { Text("This plan has no workouts. Edit the plan to include one.") }
                items(workouts, key = { it.workout.id }) { workout ->
                    Surface(Modifier.fillMaxWidth().testTag("training-workout-${workout.workout.id}")
                        .clickable(enabled = !action.saving) { onWorkout(workout.workout.id) },
                        color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${workouts.indexOf(workout) + 1}", color = PoliceColors.LightBlue)
                            WorkoutModuleLabel(workout, Modifier.weight(1f))
                            Text("→", color = PoliceColors.LightBlue)
                        }
                    }
                }
            }
            PoliceButton(onClick = onStart, enabled = !action.saving && workouts.isNotEmpty() && workouts.all { it.plannedSets() > 0 },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start-training")) { Text("Start training!") }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { if (!action.saving) deleting = false },
        title = { Text("Delete training plan?") }, text = { Text("Delete ${details.plan.name}? Your workouts and logged history will remain available.") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !action.saving) { Text("Delete", color = PoliceColors.Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !action.saving) { Text("Cancel") } })
}
