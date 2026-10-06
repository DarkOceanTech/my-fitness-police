package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.ExerciseWithSets
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun BuilderExerciseCard(entry: ExerciseWithSets, enabled: Boolean, modifier: Modifier = Modifier,
    dropTarget: Boolean, dragEnabled: Boolean, collapsed: Boolean, onToggleCollapse: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?,
    onAdd: () -> Unit, onCopyLast: () -> Unit, onViewLastSession: () -> Unit,
    onNote: () -> Unit, onEquipment: () -> Unit, onRemove: () -> Unit,
    onDeleteSet: (String) -> Unit, onChange: (String, String, String) -> Unit,
    setAction: SessionAction, parentListState: LazyListState, parentViewport: Rect,
    onSetDragState: (Boolean) -> Unit, onReorderSets: (List<String>) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showInfo by androidx.compose.runtime.saveable.rememberSaveable(entry.exercise.id) { mutableStateOf(false) }
    var confirmingRemoval by androidx.compose.runtime.saveable.rememberSaveable(entry.workoutExercise.id) { mutableStateOf(false) }
    var draggingSet by remember { mutableStateOf(false) }
    val controlsEnabled = enabled && !draggingSet
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    Surface(modifier.testTag("exercise-card-" + entry.workoutExercise.id), shape = PoliceCardShape,
        color = PoliceColors.Card,
        border = BorderStroke(if (dropTarget) 2.dp else 1.dp,
            if (dropTarget) MaterialTheme.colorScheme.primary else PoliceColors.Border)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(PoliceColors.Raised),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp)
                    .semantics {
                        contentDescription = "Drag to reorder " + entry.exercise.name
                        customActions = if (dragEnabled) listOfNotNull(
                            onMoveUp?.let { CustomAccessibilityAction("Move exercise up") { it(); true } },
                            onMoveDown?.let { CustomAccessibilityAction("Move exercise down") { it(); true } },
                        ) else emptyList()
                    }
                    .pointerInput(entry.workoutExercise.id, dragEnabled) {
                        if (dragEnabled) detectDragGesturesAfterLongPress(
                            onDragStart = { dragStart() }, onDragEnd = { dragEnd() }, onDragCancel = { dragCancel() },
                        ) { change, amount -> change.consume(); dragMove(amount.y) }
                    }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(24.dp)) {
                        repeat(3) { row -> repeat(2) { column ->
                            drawCircle(PoliceColors.Muted, radius = 2.dp.toPx(),
                                center = Offset(size.width * (if (column == 0) .32f else .68f), size.height * (.22f + row * .28f)))
                        } }
                    }
                }
                Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    if (collapsed) Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                IconButton(onClick = onToggleCollapse, enabled = controlsEnabled,
                    modifier = Modifier.size(56.dp).semantics {
                        contentDescription = (if (collapsed) "Expand " else "Collapse ") + entry.exercise.name
                        stateDescription = if (collapsed) "Collapsed" else "Expanded"
                    }) { Text(if (collapsed) "+" else "−", fontSize = 26.sp) }
            }
            SirenRule(Modifier.fillMaxWidth())
            if (!collapsed) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldMark(Modifier.size(44.dp).padding(4.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(entry.exercise.equipment, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = controlsEnabled,
                            modifier = Modifier.semantics { contentDescription = "Exercise options for " + entry.exercise.name }) {
                            Text("⋮", fontSize = 24.sp)
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("View exercise info") },
                                onClick = { menu = false; showInfo = true })
                            DropdownMenuItem(text = { Text("Edit equipment setup") },
                                onClick = { menu = false; onEquipment() })
                            DropdownMenuItem(text = { Text("View last session") },
                                enabled = enabled,
                                modifier = Modifier.testTag("view-last-session-${entry.workoutExercise.id}"),
                                onClick = { menu = false; onViewLastSession() })
                            DropdownMenuItem(text = { Text("Remove", color = PoliceColors.Error) },
                                enabled = enabled,
                                onClick = { menu = false; confirmingRemoval = true })
                        }
                    }
                }
                EquipmentPositionSummary(entry.workoutExercise.equipmentPositions)
                Text(entry.workoutExercise.notes.ifBlank { "Add a note prior to workout" },
                    modifier = Modifier.fillMaxWidth().border(1.dp, PoliceColors.Border, RoundedCornerShape(10.dp))
                        .clickable(enabled = controlsEnabled, onClickLabel = "Edit note", onClick = onNote)
                        .heightIn(min = 48.dp).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
                ReorderableSetTable(entry, enabled, setAction, parentListState, parentViewport,
                    onDragState = { draggingSet = it; onSetDragState(it) },
                    onReorder = onReorderSets, onDeleteSet = onDeleteSet, onChange = onChange)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAdd, enabled = controlsEnabled,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).testTag("add-set-${entry.workoutExercise.id}")) { Text("Add Set") }
                    OutlinedButton(onClick = onCopyLast, enabled = controlsEnabled && entry.sets.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1.4f).testTag("copy-last-set-${entry.workoutExercise.id}")) { Text("Copy last set") }
                }
            }
        }
    }
    if (showInfo) ExerciseInfoDialog(entry.exercise, onDismiss = { showInfo = false })
    if (confirmingRemoval) AlertDialog(
        onDismissRequest = { confirmingRemoval = false },
        containerColor = PoliceColors.Card,
        title = { Text("Remove exercise?") },
        text = {
            val setCount = entry.sets.size
            Text("Remove ${entry.exercise.name} and its $setCount planned ${if (setCount == 1) "set" else "sets"} from this workout?")
        },
        confirmButton = {
            TextButton(onClick = { confirmingRemoval = false; onRemove() }, enabled = enabled,
                modifier = Modifier.testTag("confirm-remove-exercise-${entry.workoutExercise.id}")) {
                Text("Remove", color = if (enabled) PoliceColors.Error else PoliceColors.Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmingRemoval = false },
                modifier = Modifier.testTag("cancel-remove-exercise-${entry.workoutExercise.id}")) { Text("Cancel") }
        },
    )
}

@Composable
internal fun TablePicker(description: String, value: String, options: List<String>, enabled: Boolean,
    modifier: Modifier = Modifier, label: (String) -> String = { it }, fullName: ((String) -> String)? = null,
    onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(PoliceColors.Background, RoundedCornerShape(8.dp))
            .border(1.dp, PoliceColors.Border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { expanded = true }
            .semantics { contentDescription = description }.padding(horizontal = 2.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) { Text(label(value), style = StatTypography.copy(fontSize = 12.sp)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            val scroll = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(value) - 2).coerceAtLeast(0))
            LazyColumn(Modifier.width(220.dp).height(minOf(240, options.size * 48).dp).testTag("$description options"), state = scroll) {
                items(options, key = { it }) { item ->
                    DropdownMenuItem(text = {
                        if (fullName == null) Text(label(item))
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label(item), Modifier.width(44.dp))
                            Text(fullName(item), Modifier.weight(1f))
                        }
                    }, onClick = { onSelect(item); expanded = false })
                }
            }
        }
    }
}

