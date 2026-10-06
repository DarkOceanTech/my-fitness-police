package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.ui.theme.*
import com.darkoceantech.myfitnesspolice.R
import androidx.compose.ui.res.painterResource
import com.darkoceantech.myfitnesspolice.data.Workout
import com.darkoceantech.myfitnesspolice.data.WorkoutEditorRepository
import com.darkoceantech.myfitnesspolice.data.displayName
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun WorkoutBuilderScreen(model: SessionViewModel, exercisesModel: ExercisesViewModel, modifier: Modifier = Modifier, onSaved: () -> Unit = {},
    planId: String? = null, onBack: () -> Unit = {}, onDeleted: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val lastSession by model.lastSession.collectAsStateWithLifecycle()
    val exerciseForm by exercisesModel.form.collectAsStateWithLifecycle()
    val editingId = planId?.let { WorkoutEditorRepository.editId(it) }
    LaunchedEffect(planId) { model.clearError() }
    val original = state.sessions.firstOrNull { it.workout.id == planId && it.workout.kind == "plan" }
    val workout = state.sessions.firstOrNull {
        if (planId == null) it.workout.kind == "draft" else it.workout.id == editingId && it.workout.kind == "edit"
    }
    val entries = workout?.orderedExercises().orEmpty()
    val ids = entries.map { it.workoutExercise.id }
    var draftName by rememberSaveable(planId) { mutableStateOf<String?>(null) }
    val trainingIds = state.trainingPlans.filter { plan -> plan.members.any { it.workoutId == planId } }.map { it.plan.id }
    var collapsedIds by rememberSaveable(planId) { mutableStateOf(arrayListOf<String>()) }
    var initializedCollapse by rememberSaveable(planId) { mutableStateOf(planId == null) }
    LaunchedEffect(workout?.workout?.id) {
        if (!initializedCollapse && workout != null) {
            collapsedIds = ArrayList(ids)
            initializedCollapse = true
        }
    }
    var saved by remember { mutableStateOf(false) }
    var importing by rememberSaveable(planId) { mutableStateOf(false) }
    var imported by remember(planId) { mutableStateOf(false) }
    var importError by remember(planId) { mutableStateOf<String?>(null) }
    var lastSessionEntry by rememberSaveable(planId) { mutableStateOf<String?>(null) }
    LaunchedEffect(lastSessionEntry, workout?.workout?.id) {
        val entryId = lastSessionEntry
        val workoutId = workout?.workout?.id
        if (entryId != null && workoutId != null) model.loadLastSession(workoutId, entryId)
    }
    DisposableEffect(model) { onDispose { model.clearLastSession() } }
    fun closeLastSession() {
        if (!action.saving) {
            lastSessionEntry = null
            importError = null
            model.clearLastSession()
            model.clearError()
        }
    }
    val contentSnapshot = remember(workout) { workout?.editSnapshot() }
    val savedSnapshot = remember(original) { original?.editSnapshot() }
    val hasChanges = if (planId == null) !draftName.isNullOrBlank() || workout?.let {
        it.exercises.isNotEmpty() || it.workout.name.isNotBlank() || it.workout.targetMuscles.isNotBlank()
    } == true else savedSnapshot != null && contentSnapshot != null && contentSnapshot != savedSnapshot
    LaunchedEffect(saved) {
        if (saved) { delay(6000); saved = false }
    }
    LaunchedEffect(imported) {
        if (imported) { delay(6000); imported = false }
    }
    LaunchedEffect(action.saving, action.error) {
        if (action.saving || action.error == null) importError = null
        else if (importing && action.error != null) {
            importing = false
            importError = action.error
        }
    }
    var picker by rememberSaveable { mutableStateOf(false) }
    var equipmentEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var noteEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var metadata by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable(planId) { mutableStateOf(false) }
    var exitAfterSave by rememberSaveable(planId) { mutableStateOf(false) }
    var discarding by rememberSaveable(planId) { mutableStateOf(false) }
    var draggingSetEntry by remember { mutableStateOf<String?>(null) }
    val interactionBusy = action.saving || draggingSetEntry != null
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    LaunchedEffect(planId, workout?.workout?.id, action.saving, action.error) {
        if (planId != null && workout == null && !action.saving && action.error == null && !discarding && !deleting) {
            model.beginPlanEdit(planId)
        }
    }

    fun discardAndExit() {
        model.clearError()
        if (planId != null) {
            discarding = true
            model.discardPlanEdit(planId)
        } else if (workout != null) {
            discarding = true
            model.clearDraft()
        } else onBack()
    }
    fun requestBack() {
        if (interactionBusy) return
        if (hasChanges) { model.clearError(); leaving = true } else discardAndExit()
    }
    BackHandler { requestBack() }

    val list = rememberLazyListState()
    var listViewport by remember { mutableStateOf(Rect.Zero) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val scrollStep = with(LocalDensity.current) { 10.dp.toPx() }
    val targetId = if (draggedId == null) null else list.layoutInfo.visibleItemsInfo
        .filter { it.key in ids }.minByOrNull { abs(it.offset - draggedTop) }?.key as? String

    fun move(from: String, target: String) {
        if (from == target || workout == null || action.saving) return
        val destination = ids.indexOf(target)
        if (destination < 0 || from !in ids) return
        val ordered = ids.toMutableList().apply { remove(from); add(destination, from) }
        model.reorderExercises(workout.workout.id, ordered)
    }
    LaunchedEffect(draggedId) {
        if (draggedId != null) {
            while (true) {
                val delta = when {
                    draggedTop < list.layoutInfo.viewportStartOffset + edge -> -scrollStep
                    draggedTop + edge > list.layoutInfo.viewportEndOffset -> scrollStep
                    else -> 0f
                }
                if (delta != 0f) list.scrollBy(delta)
                delay(16)
            }
        }
    }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            imported = importing && action.completedAction == "import-last-session"
            if (imported) {
                lastSessionEntry = null
                model.clearLastSession()
            }
            importing = false
            importError = null
            if (finishing && action.completedAction == "save-plan") {
                if (exitAfterSave) {
                    exitAfterSave = false
                    leaving = false
                    if (planId == null) onSaved() else discardAndExit()
                } else if (planId == null) onSaved() else saved = true
            } else saved = false
            if (discarding && (action.completedAction == "discard-plan-edit" ||
                    (planId == null && action.completedAction == "clear-draft"))) {
                discarding = false
                leaving = false
                onBack()
            }
            if (deleting && action.completedAction == "delete-plan") { deleting = false; onDeleted() }
            if (action.completedAction == "save-metadata") metadata = false
            if (action.completedAction == "save-equipment-positions") equipmentEntry = null
            if (clearing) draftName = null
            clearing = false
            noteEntry = null
            finishing = false
            revision = action.revision
        }
    }
    Column(modifier.fillMaxSize()) {
        SectionPageHeader(if (planId == null) "Create your Workout" else "Edit workout",
            onBack = ::requestBack, backLabel = "Back to workout home", enabled = !interactionBusy) {
            if (planId != null) Box {
                IconButton(onClick = { options = true }, enabled = workout != null && !interactionBusy,
                    modifier = Modifier.semantics { contentDescription = "Workout options" }) { Text("⋮", fontSize = 26.sp) }
                DropdownMenu(options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Edit details") }, onClick = {
                        model.clearError(); options = false; metadata = true
                    })
                    DropdownMenuItem(text = { Text("Delete workout", color = PoliceColors.Error) }, onClick = {
                        model.clearError(); options = false; deleting = true
                    })
                }
            }
        }
        if (saved) WorkoutSavedNotice(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { saved = false }
        if (imported) WorkoutBuilderNotice("Imported", "Imported sets from last session. Save to keep your changes.",
            "workout-imported", "Dismiss import notification",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { imported = false }
        importError?.takeIf { lastSessionEntry == null }?.let { message ->
            WorkoutBuilderNotice("Could not import", message, "workout-import-error", "Dismiss import error",
                isError = true, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                importError = null
                model.clearError()
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("workout-builder")
            .onGloballyPositioned { listViewport = it.boundsInWindow() }, state = list,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.loading || (planId != null && workout == null && action.error == null)) item { CircularProgressIndicator() }
            else if (state.failed) item {
                Text("Could not load your workout.")
                TextButton(onClick = model::retry) { Text("Retry") }
            } else {
                item(key = "metadata") {
                    if (planId == null) WorkoutDetailsFields((workout?.workout ?: Workout(kind = "draft"))
                        .copy(name = draftName ?: workout?.workout?.name.orEmpty()), !interactionBusy) { field, value ->
                        if (field == "name") draftName = value else model.setDetails(field, value, workout?.workout?.id)
                    } else workout?.workout?.let { MetadataSummary(it) }
                }
                if (entries.isNotEmpty()) item(key = "exercise-list-controls") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entries.sumOf { it.sets.size }} sets · ${entries.sumOf { entry -> entry.sets.sumOf { it.reps.toLong() } }} reps",
                            Modifier.weight(1f).testTag("plan-totals"), style = MaterialTheme.typography.labelLarge,
                            color = PoliceColors.Muted)
                        val allCollapsed = ids.all { it in collapsedIds }
                        TextButton(onClick = { collapsedIds = if (allCollapsed) arrayListOf() else ArrayList(ids) },
                            enabled = !interactionBusy && draggedId == null, modifier = Modifier.testTag("toggle-all-exercises")) {
                            Text(if (allCollapsed) "Expand all" else "Collapse all")
                        }
                    }
                }
                if (workout != null) items(entries, key = { it.workoutExercise.id }) { entry ->
                    val id = entry.workoutExercise.id
                    val dragging = draggedId == id
                    val currentOffset = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0
                    BuilderExerciseCard(entry, enabled = !action.saving && draggedId == null &&
                        (draggingSetEntry == null || draggingSetEntry == id),
                        modifier = Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer {
                            translationY = if (dragging) draggedTop - currentOffset else 0f
                            alpha = if (dragging) .85f else 1f
                        },
                        collapsed = id in collapsedIds,
                        onToggleCollapse = { collapsedIds = ArrayList(collapsedIds).apply { if (id in this) remove(id) else add(id) } },
                        dropTarget = targetId == id && !dragging,
                        dragEnabled = !interactionBusy && ids.size > 1,
                        onDragStart = { draggedTop = currentOffset.toFloat(); draggedId = id },
                        onDrag = { draggedTop += it },
                        onDragEnd = {
                            val target = list.layoutInfo.visibleItemsInfo.filter { it.key in ids }
                                .minByOrNull { abs(it.offset - draggedTop) }?.key as? String
                            draggedId = null
                            if (target != null) move(id, target)
                        },
                        onDragCancel = { draggedId = null },
                        onMoveUp = if (ids.indexOf(id) > 0) { { move(id, ids[ids.indexOf(id) - 1]) } } else null,
                        onMoveDown = if (ids.indexOf(id) < ids.lastIndex) { { move(id, ids[ids.indexOf(id) + 1]) } } else null,
                        onAdd = { model.addDefaultSet(workout.workout.id, id) },
                        onCopyLast = { model.copyLastSet(workout.workout.id, id) },
                        onViewLastSession = {
                            saved = false
                            imported = false
                            importError = null
                            model.clearError()
                            model.clearLastSession()
                            lastSessionEntry = id
                        },
                        onEquipment = { model.clearError(); equipmentEntry = id },
                        onRemove = { model.removeExercise(workout.workout.id, id) },
                        onDeleteSet = { model.deleteSet(workout.workout.id, id, it) },
                        onNote = { model.clearError(); note = entry.workoutExercise.notes; noteEntry = id },
                        onChange = { set, field, value -> model.changeSet(workout.workout.id, id, set, field, value) },
                        setAction = action, parentListState = list, parentViewport = listViewport,
                        onSetDragState = { active ->
                            if (active) draggingSetEntry = id else if (draggingSetEntry == id) draggingSetEntry = null
                        },
                        onReorderSets = { model.reorderSets(workout.workout.id, id, it) })
                }
                item(key = "actions") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PoliceOutlinedButton(onClick = { model.clearError(); picker = true }, enabled = !interactionBusy && (planId == null || workout != null),
                            modifier = Modifier.weight(1f).testTag("add-workout-exercise")) { Text("Add Exercise") }
                        PoliceButton(onClick = { model.clearError(); finishing = true },
                            enabled = !interactionBusy && hasChanges && entries.any { it.sets.isNotEmpty() },
                            modifier = Modifier.weight(1f).testTag("save-workout")) { Text("Save") }
                    }
                }
                if (planId == null) item {
                    TextButton(onClick = { model.clearError(); clearing = true },
                        enabled = !interactionBusy && (workout != null || !draftName.isNullOrEmpty())) { Text("Clear") }
                }
            }
            if (action.error != null && importError != action.error && lastSessionEntry == null && !picker && noteEntry == null && !finishing && !metadata && !clearing && !deleting && !leaving && equipmentEntry == null) item {
                Text(action.error!!, color = MaterialTheme.colorScheme.error)
                if (planId != null && workout == null) TextButton(onClick = { model.beginPlanEdit(planId) }, enabled = !action.saving) {
                    Text("Retry")
                }
            }
        }
    }
    if (lastSessionEntry != null && workout != null) LastSessionDialog(lastSession, action.saving, importError,
        onClose = ::closeLastSession,
        onRetry = { model.loadLastSession(workout.workout.id, lastSessionEntry!!) },
        onImport = { sourceEntryId ->
            importError = null
            importing = true
            model.importLastSession(workout.workout.id, lastSessionEntry!!, sourceEntryId)
        })
    if (deleting && planId != null) AlertDialog(
        onDismissRequest = { if (!action.saving) deleting = false }, title = { Text("Delete this workout?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This permanently deletes the saved workout and its exercises and sets, and removes it from any training plans. This cannot be undone.")
            Text("Your Workout Log and any workout already in progress will remain available.")
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { model.deletePlan(planId) }, enabled = !action.saving,
            modifier = Modifier.testTag("confirm-delete-plan")) { Text(if (action.saving) "Deleting…" else "Delete", color = PoliceColors.Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !action.saving) { Text("Cancel") } },
    )
    if (metadata && workout != null) WorkoutDetailsEditor(workout.workout, action,
        onCancel = { metadata = false },
        onSave = { name, muscles -> model.saveMetadata(workout.workout.id, name, muscles, workout.workout.dayOfWeek, trainingIds) })
    if (picker) ExercisePickerDialog(state.exercises, action.saving, action.error,
        form = exerciseForm, onResetForm = exercisesModel::resetForm, onAddExercise = exercisesModel::addExercise,
        onDismiss = { picker = false; model.clearError() }, onSelect = { model.chooseExercise(it, workout?.workout?.id) },
        selectedExerciseIds = entries.map { it.exercise.id }.toSet(),
        onRemove = { exerciseId ->
            val entry = entries.firstOrNull { it.exercise.id == exerciseId }
            if (workout != null && entry != null) model.removeExercise(workout.workout.id, entry.workoutExercise.id)
        })
    if (equipmentEntry != null && workout != null) {
        entries.firstOrNull { it.workoutExercise.id == equipmentEntry }?.let { entry ->
            EquipmentPositionEditor(entry, action,
                onDismiss = { equipmentEntry = null; model.clearError() }, onEdit = model::clearError,
                onSave = { model.saveEquipmentPositions(workout.workout.id, entry.workoutExercise.id, it) })
        }
    }
    if (noteEntry != null && workout != null) AlertDialog(
        onDismissRequest = { if (!action.saving) noteEntry = null }, title = { Text("Edit note") },
        text = { Column {
            OutlinedTextField(note, { note = it }, label = { Text("Exercise note") },
                enabled = !action.saving, modifier = Modifier.heightIn(max = 240.dp))
            action.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { model.note(workout.workout.id, noteEntry!!, note) },
            enabled = !action.saving) { Text("Save note") } },
        dismissButton = { TextButton(onClick = { noteEntry = null }, enabled = !action.saving) { Text("Cancel") } },
    )
    if (finishing && !leaving && workout != null) AlertDialog(
        onDismissRequest = { if (!action.saving) finishing = false }, title = { Text("Save workout?") },
        text = { Text(action.error ?: if (planId == null) "Save this workout to your Workouts list?" else "Save changes to this workout?") },
        confirmButton = { TextButton(onClick = { model.savePlan(workout.workout.id, if (planId == null) draftName else null) },
            enabled = !action.saving, modifier = Modifier.testTag("confirm-save-workout")) { Text("Save") } },
        dismissButton = { TextButton(onClick = { finishing = false }, enabled = !action.saving) { Text("Cancel") } },
    )
    if (clearing) AlertDialog(
        onDismissRequest = { if (!action.saving) clearing = false }, title = { Text("Clear this workout?") },
        text = { Text(action.error ?: "Remove this draft's exercises, sets, notes, and selections? Saved workouts will remain.") },
        confirmButton = { TextButton(onClick = model::clearDraft, enabled = !action.saving) { Text("Clear draft") } },
        dismissButton = { TextButton(onClick = { clearing = false }, enabled = !action.saving) { Text("Cancel") } },
    )
    if (leaving) AlertDialog(
        onDismissRequest = { if (!action.saving) { leaving = false; exitAfterSave = false; finishing = false } },
        title = { Text("Save your changes?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save this workout before leaving, discard your changes, or continue editing.")
            if (entries.none { it.sets.isNotEmpty() }) Text("Add at least one set before saving.", color = PoliceColors.Muted)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PoliceButton(onClick = {
                val current = workout ?: return@PoliceButton
                finishing = true
                exitAfterSave = true
                model.savePlan(current.workout.id, if (planId == null) draftName else null)
            }, enabled = !action.saving && entries.any { it.sets.isNotEmpty() },
                modifier = Modifier.fillMaxWidth().testTag("save-workout-and-exit")) { Text("Save changes") }
            TextButton(onClick = ::discardAndExit, enabled = !action.saving,
                modifier = Modifier.fillMaxWidth().testTag("discard-workout-changes")) {
                Text("Exit without saving", color = PoliceColors.Error)
            }
            TextButton(onClick = { leaving = false; exitAfterSave = false; finishing = false; model.clearError() },
                enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("continue-editing-workout")) {
                Text("Continue editing")
            }
        } },
    )
}

@Composable
private fun MetadataSummary(workout: Workout) {
    Surface(Modifier.fillMaxWidth().testTag("workout-metadata"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("WORKOUT DETAILS", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted,
                    modifier = Modifier.weight(1f))
                SirenRule(Modifier.width(36.dp))
            }
            MetadataRow("Workout name", workout.displayName(), R.drawable.ic_workout)
            HorizontalDivider(color = PoliceColors.Border)
            MetadataRow("Target muscles", workout.targetMuscles.replace(",", ", ").ifBlank { "None selected" }, R.drawable.ic_workout)
        }
    }
}

@Composable
private fun WorkoutSavedNotice(modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    WorkoutBuilderNotice("Saved", "Workout changes saved.", "plan-saved", "Dismiss saved notification",
        modifier = modifier, onDismiss = onDismiss)
}

@Composable
private fun WorkoutBuilderNotice(title: String, message: String, tag: String, dismissLabel: String,
    isError: Boolean = false, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val accent = if (isError) PoliceColors.Error else PoliceColors.ButtonGlow
    Surface(modifier.fillMaxWidth().testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
        shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, accent)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isError) "!" else "✓", color = accent, fontSize = 24.sp)
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PoliceColors.Text, style = MaterialTheme.typography.titleSmall)
                Text(message, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = dismissLabel }) {
                Text("×", color = PoliceColors.LightBlue, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String, icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = PoliceColors.LightBlue)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

