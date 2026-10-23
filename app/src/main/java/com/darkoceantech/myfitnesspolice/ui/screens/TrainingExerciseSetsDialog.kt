package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.domain.muscles.MuscleLabels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private data class DirectPlanSetRow(val id: String = UUID.randomUUID().toString(), val prescription: TrainingPlanSet)
private val DirectPlanSetRowsSaver = listSaver<List<DirectPlanSetRow>, String>(
    save = { rows -> rows.map { row -> JSONObject().put("id", row.id)
        .put("prescription", TrainingPlanSetConverters().encode(listOf(row.prescription))).toString() } },
    restore = { values -> values.map { value -> JSONObject(value).let {
        DirectPlanSetRow(it.getString("id"), TrainingPlanSetConverters().decode(it.getString("prescription")).single())
    } } }
)

/** Local prescriptions are committed together on Save; dismissing never mutates the plan. */
@Composable
internal fun TrainingExerciseSetsDialog(item: TrainingPlanItem.ExerciseItem, planName: String, exercise: Exercise,
    saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (List<TrainingPlanSet>) -> Unit,
    onSaveWithEquipment: ((List<TrainingPlanSet>, EquipmentConfiguration?) -> Unit)? = null,
    library: List<WorkoutDetails> = emptyList(), onRemove: () -> Unit = {}, canRemove: Boolean = true,
    onSaveDetails: ((List<TrainingPlanSet>, EquipmentConfiguration?, String) -> Unit)? = null) {
    var rows by rememberSaveable(item.exerciseId, stateSaver = DirectPlanSetRowsSaver) {
        mutableStateOf(item.sets.map { DirectPlanSetRow(prescription = it) })
    }
    var note by rememberSaveable(item.exerciseId) { mutableStateOf(item.notes) }
    var noteDraft by rememberSaveable(item.exerciseId) { mutableStateOf(item.notes) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var config by rememberSaveable(item.exerciseId, stateSaver = EquipmentConfigurationSaver) { mutableStateOf(item.equipmentOverride ?: exercise.equipmentDefault ?: EquipmentConfiguration()) }
    var override by rememberSaveable(item.exerciseId) { mutableStateOf(item.equipmentOverride != null) }
    val equipmentModel = LocalEquipmentLibraryModel.current
    var exerciseEditorTab by rememberSaveable(item.exerciseId) { mutableStateOf<Int?>(null) }
    var viewingLastSession by rememberSaveable { mutableStateOf(false) }
    var setupEditor by rememberSaveable { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    val equipmentDirty = (if (override) config else null) != item.equipmentOverride
    var dragging by remember { mutableStateOf(false) }
    val prescriptions = rows.map { it.prescription }
    val dirty = prescriptions != item.sets || equipmentDirty || note != item.notes
    val enabled = !saving && !dragging
    var leaving by rememberSaveable { mutableStateOf(false) }
    fun saveChanges() {
        if (onSaveDetails != null) onSaveDetails(prescriptions, if (override) config else null, note)
        else if (onSaveWithEquipment != null) onSaveWithEquipment(prescriptions, if (override) config else null)
        else onSave(prescriptions)
    }
    fun requestBack() {
        if (enabled) {
            if (dirty) leaving = true else onDismiss()
        }
    }
    val entryId = "training-direct-${item.exerciseId}"
    val entry = ExerciseWithSets(WorkoutExercise(entryId, "training-local", exercise.id, 0), exercise,
        rows.mapIndexed { index, row -> WorkoutSet(row.id, entryId, index, row.prescription.reps,
            row.prescription.weightGrams, isWarmup = row.prescription.isWarmup, modifier = row.prescription.modifier) })
    fun change(id: String, field: String, value: String) {
        rows = rows.map { row -> if (row.id != id) row else row.copy(prescription = when (field) {
            "reps" -> row.prescription.copy(reps = value.toInt())
            "weight" -> row.prescription.copy(weightGrams = value.toBigDecimal().multiply(BigDecimal("453.59237"))
                .setScale(0, RoundingMode.HALF_UP).longValueExact())
            "type" -> row.prescription.copy(isWarmup = value == "warmup")
            "modifier" -> row.prescription.copy(modifier = value)
            else -> row.prescription
        }) }
    }
    WorkoutEditorDialog("Edit exercise sets", "training-exercise-sets", enabled, ::requestBack, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f)
            .testTag("cancel-training-exercise-sets")) { Text("Cancel") }
        PoliceButton(onClick = ::saveChanges, enabled = enabled && dirty && prescriptions.isNotEmpty(),
            modifier = Modifier.weight(1f).testTag("save-training-exercise-sets")) { Text(if (saving) "Saving…" else "Save") }
    }) {
        val listState = rememberLazyListState()
        var viewport by remember { mutableStateOf(Rect.Zero) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("training-direct-sets-list")
            .onGloballyPositioned { viewport = Rect(it.positionInWindow(), it.size.toSize()) },
            state = listState, verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "details") { DirectExercisePlanSummary(planName, exercise) }
            item(key = "totals") {
                Text("${prescriptions.size} sets · ${prescriptions.sumOf { it.reps.toLong() }} reps",
                    style = MaterialTheme.typography.titleMedium, color = PoliceColors.Muted,
                    modifier = Modifier.testTag("training-direct-set-totals"))
            }
            item(key = "exercise") {
                Surface(Modifier.fillMaxWidth().testTag("training-direct-exercise-card"), color = PoliceColors.Card,
                    contentColor = PoliceColors.Text, shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
                    Column {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShieldMark(Modifier.size(44.dp).padding(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                    if (exercise.equipment.isNotBlank()) Text(exercise.equipment,
                                        style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                                }
                                BuilderExerciseMenu(entry, enabled, onEquipment = { setupEditor = true },
                                    onViewLastSession = { viewingLastSession = true }, onRemove = onRemove,
                                    removalContext = "training plan", canRemove = canRemove,
                                    onEditExercise = if (equipmentModel == null) null else { tab ->
                                        equipmentModel.reset()
                                        exerciseEditorTab = tab
                                    })
                            }
                            EquipmentConfigurationSummary(if (override) config else exercise.equipmentDefault)
                            Text(note.ifBlank { "Add a note prior to workout" },
                                modifier = Modifier.fillMaxWidth().testTag("training-exercise-note")
                                    .border(1.dp, PoliceColors.Border, RoundedCornerShape(10.dp))
                                    .clickable(enabled = enabled, onClickLabel = "Edit note") { noteDraft = note; editingNote = true }
                                    .heightIn(min = 48.dp).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            ReorderableSetTable(entry, !saving, SessionAction(saving = saving, error = error), listState, viewport,
                                onDragState = { dragging = it }, onReorder = { ids -> rows = ids.map { id -> rows.single { it.id == id } } },
                                onDeleteSet = { id -> rows = rows.filterNot { it.id == id } }, onChange = ::change)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { rows = rows + DirectPlanSetRow(prescription = TrainingPlanSet()) }, enabled = enabled,
                                    modifier = Modifier.weight(1f).testTag("add-training-set"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("Add Set") }
                                OutlinedButton(onClick = { rows.lastOrNull()?.let { rows = rows + DirectPlanSetRow(prescription = it.prescription) } },
                                    enabled = enabled && rows.isNotEmpty(), modifier = Modifier.weight(1.4f).testTag("copy-training-set"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("Copy last set") }
                            }
                        }
                    }
                }
            }
            if (error != null) item(key = "error") { Text(error, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (leaving) AlertDialog(
        onDismissRequest = { if (!saving) leaving = false },
        title = { Text("Save your changes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save this exercise's sets and note before leaving, discard your changes, or continue editing.")
                if (prescriptions.isEmpty()) Text("Add at least one set before saving.", color = PoliceColors.Muted)
                error?.let { Text(it, color = PoliceColors.Error) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PoliceButton(onClick = ::saveChanges, enabled = enabled && prescriptions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("save-training-exercise-and-exit")) {
                    Text(if (saving) "Saving…" else "Save changes")
                }
                TextButton(onClick = onDismiss, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("discard-training-exercise-changes")) {
                    Text("Exit without saving", color = PoliceColors.Error)
                }
                TextButton(onClick = { leaving = false }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("continue-editing-training-exercise")) {
                    Text("Continue editing")
                }
            }
        }
    )
    // The plan's local sets, note and override stay mounted while shared details are edited.
    if (exerciseEditorTab != null && equipmentModel != null) ExerciseFormDialog(
        formState = ExerciseFormState(), onDismiss = { exerciseEditorTab = null },
        onEdit = equipmentModel::reset, exercise = exercise, initialTab = exerciseEditorTab!!,
        onSave = { name, equipment, description, primary, secondary ->
            equipmentModel.saveExercise(exercise, name, equipment, description, primary, secondary, exercise.equipmentDefault)
        })
    if (editingNote) FullScreenNoteEditor("Edit exercise note", noteDraft, { noteDraft = it },
        onDismiss = { editingNote = false }, onSave = { note = noteDraft; editingNote = false },
        subtitle = exercise.name, tag = "training-exercise-note-editor")
    if (viewingLastSession) {
        val history = library.filter { it.workout.kind == "session" && it.workout.finishedAt != null }
            .sortedWith(compareByDescending<WorkoutDetails> { it.workout.finishedAt }
                .thenByDescending { it.workout.startedAt }.thenByDescending { it.workout.id })
            .flatMap { workout -> workout.exercises.filter { it.exercise.id == exercise.id }
                .sortedWith(compareByDescending<ExerciseWithSets> { it.workoutExercise.position }.thenByDescending { it.workoutExercise.id })
                .map { ExerciseSessionSnapshot(workout, it) } }
        val snapshot = history.firstOrNull()
        LastSessionDialog(LastSessionState(snapshot = snapshot, history = history), false, null,
            onClose = { viewingLastSession = false }, onRetry = {}, onImport = { id ->
                snapshot?.takeIf { it.entry.workoutExercise.id == id }?.let { source ->
                    rows = source.entry.sets.sortedBy { it.position }.map {
                        DirectPlanSetRow(prescription = TrainingPlanSet(it.reps, it.weightGrams, it.isWarmup, it.modifier))
                    }
                    viewingLastSession = false
                }
            })
    }
    if (setupEditor) EquipmentConfigurationEditor(if (override) config else exercise.equipmentDefault, "Plan exercise equipment", onDismiss = { setupEditor = false },
        action = SessionAction(error = setupError), allowNamedSetup = false, onSave = { selection, _, _, _ ->
            val positions = selection?.positions.orEmpty().map { EquipmentPosition(it.name.trim(), it.position.trim()) }.filterNot { it.name.isBlank() && it.position.isBlank() }
            if (positions.any { it.name.isBlank() || it.position.isBlank() }) setupError = "Enter both name and position, or remove the unfinished row."
            else { override = selection != null; config = selection?.copy(positions = positions) ?: EquipmentConfiguration(); setupError = null; setupEditor = false }
        })
}

@Composable
private fun DirectExercisePlanSummary(planName: String, exercise: Exercise) {
    val muscles = MuscleLabels.displayTags(exercise.primaryMuscles).split(';', ',').map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
        .ifBlank { exercise.mainMuscleGroup().takeIf { it != MuscleGroup.OTHER }?.label ?: "None selected" }
    Surface(Modifier.fillMaxWidth().testTag("training-direct-plan-details"), color = PoliceColors.Card, shape = PoliceCardShape,
        border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRAINING PLAN DETAILS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                SirenRule(Modifier.width(36.dp))
            }
            listOf("Name" to planName, "Target muscles" to muscles).forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = PoliceColors.Border)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_workout), null, Modifier.size(22.dp), tint = PoliceColors.LightBlue)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                        Text(value, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
