package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun TrainingPlanCard(details: TrainingPlanDetails, library: List<WorkoutDetails>, exercises: List<Exercise> = emptyList(), onOpen: () -> Unit) {
    val entries = trainingItemUi(details.orderedItems(), library, exercises, details)
    Surface(Modifier.fillMaxWidth().testTag("training-plan-${details.plan.id}").clickable(onClick = onOpen),
        shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trainingItemCount(entries), Modifier.weight(1f), color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
                SirenRule(Modifier.width(36.dp))
            }
            Text(details.plan.name, style = MaterialTheme.typography.headlineSmall)
            Text(details.plan.dayOfWeek.ifBlank { "Not scheduled" }, style = MaterialTheme.typography.labelMedium, color = PoliceColors.LightBlue)
            Text("${entries.sumOf { it.sets }} sets · ${entries.sumOf { it.reps }} reps", color = PoliceColors.Muted)
            HorizontalDivider(color = PoliceColors.Border)
            Text(entries.joinToString(" → ") { it.title }.ifBlank { "Add workouts or exercises to this plan" },
                color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TrainingPlanEditorScreen(existing: TrainingPlanDetails?, library: List<WorkoutDetails>, action: SessionAction,
    modifier: Modifier = Modifier, onBack: () -> Unit, onSaved: () -> Unit, onWorkouts: () -> Unit,
    onSave: (String?, String, List<TrainingPlanItem>, String) -> Unit, exercises: List<Exercise> = emptyList()) {
    var name by rememberSaveable(existing?.plan?.id) { mutableStateOf(existing?.plan?.name.orEmpty()) }
    var day by rememberSaveable(existing?.plan?.id) { mutableStateOf(existing?.plan?.dayOfWeek.orEmpty()) }
    var includedItems by rememberSaveable(existing?.plan?.id, stateSaver = TrainingItemsSaver) { mutableStateOf(existing?.orderedItems().orEmpty()) }
    var excludedSource by rememberSaveable { mutableStateOf("Workouts") }
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var includedSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSave by rememberSaveable { mutableStateOf(false) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    val saved = library.filter { it.workout.kind == "plan" }
    val included = trainingItemUi(includedItems, library, exercises, existing)
    val includedKeys = includedItems.map { it.key }.toSet()
    val choices: List<TrainingPlanItem> = if (excludedSource == "Workouts") saved.map { TrainingPlanItem.WorkoutItem(it.workout.id) }
        else exercises.map { TrainingPlanItem.ExerciseItem(it.id) }
    val available = trainingItemUi(choices.filter { it.key !in includedKeys }, library, exercises)
        .sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    val selected = selectedIds.filter { id -> available.any { it.key == id } }
    val selectedIndex = included.indexOfFirst { it.key == includedSelection }
    val canEditIncluded = !action.saving && selectedIds.isEmpty() && selectedIndex >= 0
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            if (requestedSave && action.completedAction == "save-training-plan") { requestedSave = false; onSaved() }
            revision = action.revision
        }
    }
    fun include(ids: List<String>) {
        includedItems = (includedItems + ids.mapNotNull { id -> available.find { it.key == id }?.item }).distinctBy { it.key }
        selectedIds = emptyList()
        includedSelection = ids.lastOrNull()
    }
    fun remove(id: String) {
        includedItems = includedItems.filterNot { it.key == id }
        if (includedSelection == id) includedSelection = null
    }
    fun move(delta: Int) {
        val destination = selectedIndex + delta
        if (!canEditIncluded || destination !in included.indices) return
        includedItems = includedItems.toMutableList().apply {
            val moving = removeAt(selectedIndex); add(destination, moving)
        }
    }
    val heading: @Composable () -> Unit = {
        TrainingHeader(if (existing == null) "Training Plan" else "Edit Training Plan", !action.saving, onBack) {
            PoliceButton(onClick = {
                requestedSave = true
                onSave(existing?.plan?.id, name, includedItems, day)
            }, enabled = !action.saving && name.isNotBlank() && included.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save-training-plan")) {
                Text(if (action.saving) "Saving…" else if (existing == null) "Create" else "Save")
            }
        }
    }
    val fields: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, placeholder = { Text("Back + Arms + Abs") },
                singleLine = true, enabled = !action.saving, modifier = Modifier.weight(1f).testTag("training-plan-name"))
            TrainingDayField(day, !action.saving, Modifier.width(IntrinsicSize.Max).widthIn(min = 96.dp)) { day = it }
        }
        Text("Tap to select. Hold to move workouts or exercises between lists.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
    }
    val selection: @Composable () -> Unit = {
        Surface(Modifier.fillMaxSize().testTag("training-workout-selection"),
            shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    TrainingSelectionPane("Excluded", available, selected.toSet(), !action.saving,
                        Modifier.weight(1f).fillMaxHeight(), "available", onSelect = { id ->
                            includedSelection = null
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        }, onHold = { include(listOf(it)) },
                        emptyText = if (excludedSource == "Exercises") {
                            if (exercises.isEmpty()) "No exercises available." else "All exercises included."
                        } else if (saved.isEmpty()) "Create a workout first." else "All workouts included.",
                        source = excludedSource, onSource = { excludedSource = it; selectedIds = emptyList() },
                        footer = {
                            if (excludedSource == "Workouts" && saved.isEmpty()) TextButton(onClick = onWorkouts, enabled = !action.saving) { Text("Workouts") }
                        })
                    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 12.dp), color = PoliceColors.Border)
                    TrainingSelectionPane("Included", included, setOfNotNull(includedSelection), !action.saving,
                        Modifier.weight(1f).fillMaxHeight(), "included", onSelect = {
                            selectedIds = emptyList()
                            includedSelection = if (includedSelection == it) null else it
                        }, onHold = { selectedIds = emptyList(); remove(it) }, emptyText = "Add workouts or exercises to your plan.",
                        onEditExercise = { editingExerciseId = it }, footer = {})
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PoliceButton(onClick = { include(selected) }, enabled = !action.saving && selected.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("include-selected-workouts")) { Text("Add") }
                    TrainingReorderButton("↑", "Move selected item up", "move-included-up", canEditIncluded && selectedIndex > 0) { move(-1) }
                    TrainingReorderButton("↓", "Move selected item down", "move-included-down", canEditIncluded && selectedIndex < included.lastIndex) { move(1) }
                    OutlinedButton(onClick = { includedSelection?.let(::remove) }, enabled = canEditIncluded,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("remove-selected-workout")) { Text("Remove") }
                }
            }
        }
    }
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(modifier.fillMaxSize().imePadding().padding(12.dp).testTag("training-plan-editor"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                heading()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fields()
                    action.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { selection() }
        }
    } else {
        Column(modifier.fillMaxSize().imePadding().padding(12.dp).testTag("training-plan-editor"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            heading()
            fields()
            Box(Modifier.weight(1f).fillMaxWidth()) { selection() }
            action.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    val editing = includedItems.filterIsInstance<TrainingPlanItem.ExerciseItem>().find { it.exerciseId == editingExerciseId }
    if (editing != null) TrainingExerciseSetsDialog(editing, included.first { it.key == editing.key }.title,
        saving = false, error = null, onDismiss = { editingExerciseId = null }, onSave = { sets ->
            includedItems = includedItems.map { if (it.key == editing.key) editing.copy(sets = sets) else it }
            editingExerciseId = null
        })
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
private fun TrainingSelectionPane(title: String, entries: List<TrainingItemUi>, selection: Set<String>, enabled: Boolean,
    modifier: Modifier, tag: String, onSelect: (String) -> Unit, onHold: (String) -> Unit, emptyText: String,
    source: String? = null, onSource: (String) -> Unit = {}, onEditExercise: ((String) -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
            Text("(${entries.size})", modifier = Modifier.testTag("$tag-count"),
                style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        }
        if (source != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("training-excluded-source")) {
                    Text(source, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("⌄")
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    listOf("Workouts", "Exercises").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, modifier = Modifier.testTag("training-source-${option.lowercase()}"),
                            onClick = { onSource(option); expanded = false })
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("$tag-workouts"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entries.isEmpty()) item { Text(emptyText, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted) }
            items(entries, key = { it.key }) { entry ->
                val id = entry.key
                Column(Modifier.fillMaxWidth().testTag(entry.tag(tag))
                    .background(if (id in selection) PoliceColors.Raised else PoliceColors.Background, MaterialTheme.shapes.small)
                    .border(1.dp, if (id in selection) PoliceColors.LightBlue else PoliceColors.Border, MaterialTheme.shapes.small)
                    .combinedClickable(enabled = enabled, onClick = { onSelect(id) },
                        onLongClickLabel = if (tag == "available") "Include ${entry.kind.lowercase()}" else "Exclude ${entry.kind.lowercase()}", onLongClick = { onHold(id) })
                    .semantics { this.selected = id in selection }
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${entry.sets} sets · ${entry.reps} reps", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.kind, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = PoliceColors.LightBlue)
                        if (entry.isExercise && onEditExercise != null) IconButton(onClick = { onEditExercise(entry.id) }, enabled = enabled,
                            modifier = Modifier.size(40.dp).testTag("edit-included-exercise-${entry.id}")
                                .semantics { contentDescription = "Edit sets for ${entry.title}" }) {
                            Text("✎", color = PoliceColors.LightBlue, fontSize = 20.sp)
                        }
                    }
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
    modifier: Modifier = Modifier, onBack: () -> Unit, onEdit: () -> Unit, onStart: () -> Unit, onWorkout: (String) -> Unit, onDelete: () -> Unit,
    hasActiveSession: Boolean = false, exercises: List<Exercise> = emptyList(), onSaveItems: (List<TrainingPlanItem>) -> Unit = {}) {
    val entries = trainingItemUi(details.orderedItems(), library, exercises, details)
    var deleting by rememberSaveable(details.plan.id) { mutableStateOf(false) }
    var options by remember(details.plan.id) { mutableStateOf(false) }
    var editingExerciseId by rememberSaveable(details.plan.id) { mutableStateOf<String?>(null) }
    var requestedSave by rememberSaveable(details.plan.id) { mutableStateOf(false) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            if (requestedSave && action.completedAction == "save-training-plan") { requestedSave = false; editingExerciseId = null }
            revision = action.revision
        }
    }
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
            Text("${trainingItemCount(entries)} · ${entries.sumOf { it.sets }} sets · ${entries.sumOf { it.reps }} reps",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            Text("Day of Week · ${details.plan.dayOfWeek.ifBlank { "Not scheduled" }}",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.LightBlue, modifier = Modifier.testTag("training-plan-schedule"))
            LazyColumn(Modifier.weight(1f).testTag("training-plan-workouts"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entries.isEmpty()) item { Text("This plan has no workouts or exercises. Edit the plan to include one.") }
                items(entries, key = { it.key }) { entry ->
                    Surface(Modifier.fillMaxWidth().testTag(entry.tag("training"))
                        .clickable(enabled = !action.saving) {
                            if (entry.isExercise) editingExerciseId = entry.id else onWorkout(entry.id)
                        },
                        color = PoliceColors.Card, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${entries.indexOf(entry) + 1}", color = PoliceColors.LightBlue)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${entry.sets} sets · ${entry.reps} reps", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                                Text(entry.kind, style = MaterialTheme.typography.labelSmall, color = PoliceColors.LightBlue)
                            }
                            Text(if (entry.isExercise) "✎" else "→", color = PoliceColors.LightBlue)
                        }
                    }
                }
            }
            if (hasActiveSession) {
                Text("You already have a workout in progress. Return to it before starting another.",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            PoliceButton(onClick = onStart, enabled = !hasActiveSession && !action.saving && entries.isNotEmpty() && entries.all { it.sets > 0 },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start-training")) { Text("Start training!") }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { if (!action.saving) deleting = false },
        title = { Text("Delete training plan?") }, text = { Text("Delete ${details.plan.name}? Your saved workouts and Workout Log will remain available.") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !action.saving) { Text("Delete", color = PoliceColors.Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !action.saving) { Text("Cancel") } })
    val editing = details.orderedItems().filterIsInstance<TrainingPlanItem.ExerciseItem>().find { it.exerciseId == editingExerciseId }
    if (editing != null) TrainingExerciseSetsDialog(editing, entries.first { it.key == editing.key }.title,
        saving = action.saving, error = action.error, onDismiss = { editingExerciseId = null }, onSave = { sets ->
            requestedSave = true
            onSaveItems(details.orderedItems().map { if (it.key == editing.key) editing.copy(sets = sets) else it })
        })
}
