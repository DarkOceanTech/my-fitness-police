package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myfitnesspolice.data.EquipmentPosition
import com.example.myfitnesspolice.data.ExerciseWithSets
import com.example.myfitnesspolice.ui.theme.*

private val PositionListSaver = listSaver<List<EquipmentPosition>, String>(
    save = { items -> items.flatMap { listOf(it.name, it.position) } },
    restore = { values -> values.chunked(2).map { EquipmentPosition(it[0], it[1]) } },
)

@Composable
internal fun EquipmentPositionEditor(entry: ExerciseWithSets, action: SessionAction,
    onDismiss: () -> Unit, onEdit: () -> Unit, onSave: (List<EquipmentPosition>) -> Unit) {
    var positions by rememberSaveable(entry.workoutExercise.id, stateSaver = PositionListSaver) {
        mutableStateOf(entry.workoutExercise.equipmentPositions.ifEmpty { listOf(EquipmentPosition("", "")) })
    }
    val list = rememberLazyListState()
    var focusNewRow by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(positions.size, focusNewRow) {
        focusNewRow?.let { list.animateScrollToItem(it + 1) }
    }
    fun update(index: Int, item: EquipmentPosition) {
        positions = positions.toMutableList().apply { this[index] = item }
        onEdit()
    }
    WorkoutEditorDialog("Equipment setup", "equipment-position-editor", !action.saving, onDismiss, footer = {
        OutlinedButton(onClick = onDismiss, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { onSave(positions) }, enabled = !action.saving,
            modifier = Modifier.weight(1f).testTag("save-equipment-positions")) { Text(if (action.saving) "Saving…" else "Save") }
    }) {
        // Instructions and Add share the scroll viewport with fields; neither can overlay the focused row.
        LazyColumn(Modifier.weight(1f, fill = false).testTag("equipment-position-list"), state = list,
            contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "instructions") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.exercise.name, color = PoliceColors.LightBlue, style = MaterialTheme.typography.titleMedium)
                    Text("Record a setting such as Seat / 4, Back support / 2, Cable / 12, Bench angle / 30°, or Rack bar rest / 8.",
                        color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (positions.isEmpty()) item(key = "empty") {
                Text("No equipment positions. Add a position, or save to clear the setup.", color = PoliceColors.Muted)
            }
            items(positions.indices.toList(), key = { "position-$it" }) { index ->
                val item = positions[index]
                val nameFocus = remember { FocusRequester() }
                LaunchedEffect(focusNewRow) {
                    if (focusNewRow == index) { nameFocus.requestFocus(); focusNewRow = null }
                }
                Surface(color = PoliceColors.Card, shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, PoliceColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("POSITION ${index + 1}", Modifier.weight(1f), color = PoliceColors.Muted,
                                style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = {
                                focusNewRow = null
                                positions = positions.filterIndexed { i, _ -> i != index }; onEdit()
                            }, enabled = !action.saving, modifier = Modifier.testTag("remove-position-$index")
                                .semantics { contentDescription = "Remove equipment position ${index + 1}" }) {
                                Text("Remove", color = PoliceColors.Error)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(item.name, { update(index, item.copy(name = it)) },
                                label = { Text("Name") }, placeholder = { Text("Seat") }, maxLines = 2,
                                enabled = !action.saving, modifier = Modifier.weight(1f).focusRequester(nameFocus).testTag("position-name-$index"))
                            OutlinedTextField(item.position, { update(index, item.copy(position = it)) },
                                label = { Text("Position") }, placeholder = { Text("4") }, maxLines = 2,
                                enabled = !action.saving, modifier = Modifier.weight(1f).testTag("position-value-$index"))
                        }
                    }
                }
            }
            item(key = "add") {
                OutlinedButton(onClick = {
                    val index = positions.size
                    positions = positions + EquipmentPosition("", ""); focusNewRow = index; onEdit()
                }, enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("add-equipment-position")) {
                    Text("+ Add position")
                }
            }
        }
        action.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Error) }
    }
}

@Composable
internal fun EquipmentPositionSummary(positions: List<EquipmentPosition>) {
    if (positions.isEmpty()) return
    Surface(Modifier.fillMaxWidth(), color = PoliceColors.Raised, shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("EQUIPMENT SETUP", color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelSmall)
            positions.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.name, Modifier.weight(1f), color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                    Text(item.position, Modifier.weight(1f), color = PoliceColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
