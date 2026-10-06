package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.domain.formatting.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun ActivePlannedSetsDialog(entry: ExerciseWithSets, action: SessionAction,
    onCancel: () -> Unit, onEdit: () -> Unit, onSave: (List<PlannedSetUpdate>) -> Unit) {
    val originals = remember(entry.workoutExercise.id) { entry.sets.sortedBy { it.position } }
    val saver = remember {
        listSaver<List<PlannedSetUpdate>, Any>(save = { rows -> rows.flatMap {
            listOf(it.id, it.reps, it.weightGrams, it.isWarmup, it.modifier)
        } }, restore = { saved -> saved.chunked(5).map {
            PlannedSetUpdate(it[0] as String, it[1] as Int, it[2] as Long, it[3] as Boolean, it[4] as String)
        } })
    }
    var rows by rememberSaveable(entry.workoutExercise.id, stateSaver = saver) {
        mutableStateOf(originals.map { PlannedSetUpdate(it.id, it.reps, it.weightGrams, it.isWarmup, it.modifier) })
    }
    val changes = rows.filter { row -> originals.single { it.id == row.id }.let {
        row.reps != it.reps || row.weightGrams != it.weightGrams || row.isWarmup != it.isWarmup || row.modifier != it.modifier
    } }
    fun change(row: PlannedSetUpdate) { rows = rows.map { if (it.id == row.id) row else it }; onEdit() }
    WorkoutEditorDialog("Edit planned sets", "active-planned-sets-dialog", !action.saving, onCancel,
        fullScreen = true, footer = {
            PoliceOutlinedButton(onClick = onCancel, enabled = !action.saving,
                modifier = Modifier.weight(1f).testTag("cancel-planned-sets")) { Text("Cancel") }
            PoliceButton(onClick = { onSave(changes) }, enabled = !action.saving && changes.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("save-planned-sets")) { Text(if (action.saving) "Saving…" else "Save") }
        }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleLarge)
                Text("Edit existing unfinished sets. Completed sets stay read only. Changes apply to this session.",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            items(rows, key = { it.id }) { row ->
                val original = originals.single { it.id == row.id }
                val completed = entry.sets.singleOrNull { it.id == row.id }?.completedAt != null
                val enabled = !action.saving && !completed
                val pounds = sessionPounds(row.weightGrams)
                val weights = remember(pounds) {
                    ((0..600).map { BigDecimal.valueOf(it * 25L, 1).stripTrailingZeros().toPlainString() } + pounds)
                        .distinct().sortedBy { it.toBigDecimal() }
                }
                Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                    border = BorderStroke(1.dp, PoliceColors.Border)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Set ${original.position + 1}" + if (completed) " · Completed" else "",
                            style = MaterialTheme.typography.titleMedium, color = if (completed) PoliceColors.Muted else PoliceColors.Text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecordedNumberField("Weight (Lbs)", pounds, weights, !completed,
                                "planned-weight-${row.id}", Modifier.weight(1f), enabled, formatValue = ::formatPoundsText) {
                                if (it != pounds) change(row.copy(weightGrams = it.toBigDecimal().multiply(BigDecimal("453.59237"))
                                    .setScale(0, RoundingMode.HALF_UP).longValueExact()))
                            }
                            RecordedNumberField("Planned reps", row.reps.toString(),
                                ((1..500).map { it.toString() } + row.reps.toString()).distinct().sortedBy { it.toInt() },
                                !completed, "planned-reps-${row.id}", Modifier.weight(1f), enabled) { change(row.copy(reps = it.toInt())) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Modifier", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                                TablePicker("planned-modifier-${row.id}", row.modifier, listOf("none", "superset", "drop_set"), enabled,
                                    label = { when (it) { "superset" -> "S"; "drop_set" -> "D"; else -> "R" } },
                                    fullName = { when (it) { "superset" -> "Superset"; "drop_set" -> "Drop set"; else -> "Regular" } }) {
                                    change(row.copy(modifier = it))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Type", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                                TablePicker("planned-type-${row.id}", if (row.isWarmup) "warmup" else "working",
                                    listOf("warmup", "working"), enabled, label = { if (it == "warmup") "Wu" else "Ws" },
                                    fullName = { if (it == "warmup") "Warm-up" else "Working set" }) { change(row.copy(isWarmup = it == "warmup")) }
                            }
                        }
                    }
                }
            }
        }
        action.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
    }
}
