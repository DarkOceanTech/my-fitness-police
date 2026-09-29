package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal data class TrainingItemUi(val item: TrainingPlanItem, val title: String, val sets: Int, val reps: Long) {
    val key get() = item.key
    val isExercise get() = item is TrainingPlanItem.ExerciseItem
    val kind get() = if (isExercise) "Exercise" else "Workout"
    val id get() = when (item) {
        is TrainingPlanItem.WorkoutItem -> item.workoutId
        is TrainingPlanItem.ExerciseItem -> item.exerciseId
    }
    fun tag(prefix: String) = "$prefix-${if (isExercise) "exercise" else "workout"}-$id"
}

internal fun trainingItemUi(items: List<TrainingPlanItem>, library: List<WorkoutDetails>, exercises: List<Exercise>,
    details: TrainingPlanDetails? = null): List<TrainingItemUi> = items.map { item ->
    when (item) {
        is TrainingPlanItem.WorkoutItem -> {
            val workout = library.firstOrNull { it.workout.id == item.workoutId }
            TrainingItemUi(item, workout?.workout?.displayName() ?: "Unavailable workout", workout?.plannedSets() ?: 0,
                workout?.plannedReps() ?: 0L)
        }
        is TrainingPlanItem.ExerciseItem -> {
            val exercise = details?.exerciseFor(item.exerciseId) ?: exercises.firstOrNull { it.id == item.exerciseId }
            TrainingItemUi(item, exercise?.name ?: "Unavailable exercise", item.sets.size, item.sets.sumOf { it.reps.toLong() })
        }
    }
}

internal fun trainingItemCount(items: List<TrainingItemUi>): String {
    val workouts = items.count { !it.isExercise }
    val exercises = items.count { it.isExercise }
    return listOfNotNull(if (workouts > 0 || exercises == 0) "$workouts ${if (workouts == 1) "workout" else "workouts"}" else null,
        if (exercises > 0) "$exercises ${if (exercises == 1) "exercise" else "exercises"}" else null).joinToString(" · ")
}

private fun TrainingPlanSet.json() = JSONObject().put("reps", reps).put("weightGrams", weightGrams)
    .put("warmup", isWarmup).put("modifier", modifier)
private fun trainingSet(json: JSONObject) = TrainingPlanSet(json.getInt("reps"), json.getLong("weightGrams"),
    json.getBoolean("warmup"), json.getString("modifier"))

internal val TrainingItemsSaver = listSaver<List<TrainingPlanItem>, String>(
    save = { items -> items.map { item -> when (item) {
        is TrainingPlanItem.WorkoutItem -> JSONObject().put("workout", item.workoutId)
        is TrainingPlanItem.ExerciseItem -> JSONObject().put("exercise", item.exerciseId)
            .put("sets", JSONArray().apply { item.sets.forEach { put(it.json()) } })
    }.toString() } },
    restore = { saved -> saved.map { value ->
        val json = JSONObject(value)
        if (json.has("workout")) TrainingPlanItem.WorkoutItem(json.getString("workout"))
        else TrainingPlanItem.ExerciseItem(json.getString("exercise"), json.getJSONArray("sets").let { sets ->
            (0 until sets.length()).map { trainingSet(sets.getJSONObject(it)) }
        })
    } }
)

private val TrainingSetsSaver = listSaver<List<TrainingPlanSet>, String>(
    save = { sets -> sets.map { it.json().toString() } },
    restore = { sets -> sets.map { trainingSet(JSONObject(it)) } }
)

@Composable
internal fun TrainingExerciseSetsDialog(item: TrainingPlanItem.ExerciseItem, exerciseName: String,
    saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (List<TrainingPlanSet>) -> Unit) {
    var sets by rememberSaveable(item.exerciseId, stateSaver = TrainingSetsSaver) { mutableStateOf(item.sets) }
    fun update(index: Int, value: TrainingPlanSet) { sets = sets.toMutableList().apply { set(index, value) } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(Modifier.fillMaxWidth().heightIn(max = 640.dp).testTag("training-exercise-sets"),
            color = PoliceColors.Card, contentColor = PoliceColors.Text, shape = PoliceCardShape,
            border = BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Exercise sets", style = MaterialTheme.typography.titleLarge)
                Text(exerciseName, maxLines = 2, overflow = TextOverflow.Ellipsis, color = PoliceColors.LightBlue,
                    style = MaterialTheme.typography.titleSmall)
                SirenRule(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f, fill = false).testTag("training-direct-sets-list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(sets) { index, set ->
                        Surface(color = PoliceColors.Background, shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, PoliceColors.Border), modifier = Modifier.testTag("training-direct-set-$index")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Set ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    IconButton(onClick = { sets = sets.filterIndexed { i, _ -> i != index } }, enabled = !saving,
                                        modifier = Modifier.size(40.dp).testTag("delete-training-set-$index")
                                            .semantics { contentDescription = "Remove set ${index + 1}" }) {
                                        Text("×", color = PoliceColors.Error, fontSize = 24.sp)
                                    }
                                }
                                val weight = sessionPounds(set.weightGrams)
                                val weights = remember(weight) { ((0..600).map { BigDecimal.valueOf(it * 25L, 1)
                                    .stripTrailingZeros().toPlainString() } + weight).distinct().sortedBy { it.toBigDecimal() } }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RecordedNumberField("Lbs", weight, weights, true, "training-set-weight-$index", Modifier.weight(1f), !saving) {
                                        if (it != weight) update(index, set.copy(weightGrams = it.toBigDecimal().multiply(BigDecimal("453.59237"))
                                            .setScale(0, RoundingMode.HALF_UP).longValueExact()))
                                    }
                                    RecordedNumberField("Reps", set.reps.toString(), ((1..100).map { it.toString() } + set.reps.toString()).distinct().sortedBy { it.toInt() },
                                        true, "training-set-reps-$index", Modifier.weight(1f), !saving) { update(index, set.copy(reps = it.toInt())) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Type", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                                        TablePicker("Training set ${index + 1} type", if (set.isWarmup) "warmup" else "working", listOf("warmup", "working"), !saving,
                                            label = { if (it == "warmup") "Wu" else "Ws" },
                                            fullName = { if (it == "warmup") "Warm-up" else "Working set" }) { update(index, set.copy(isWarmup = it == "warmup")) }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Modifier", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                                        TablePicker("Training set ${index + 1} modifier", set.modifier, listOf("none", "superset", "drop_set"), !saving,
                                            label = { when (it) { "superset" -> "S"; "drop_set" -> "D"; else -> "R" } },
                                            fullName = { when (it) { "superset" -> "Superset"; "drop_set" -> "Drop set"; else -> "Regular" } }) { update(index, set.copy(modifier = it)) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { sets = sets + TrainingPlanSet() }, enabled = !saving,
                                modifier = Modifier.weight(1f).testTag("add-training-set"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Add set") }
                            OutlinedButton(onClick = { sets.lastOrNull()?.let { sets = sets + it } }, enabled = !saving && sets.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("copy-training-set"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Copy last") }
                        }
                    }
                }
                error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("cancel-training-exercise-sets")) { Text("Cancel") }
                    PoliceButton(onClick = { onSave(sets) }, enabled = !saving && sets.isNotEmpty(),
                        modifier = Modifier.testTag("save-training-exercise-sets")) { Text(if (saving) "Saving…" else "Save") }
                }
            }
        }
    }
}
