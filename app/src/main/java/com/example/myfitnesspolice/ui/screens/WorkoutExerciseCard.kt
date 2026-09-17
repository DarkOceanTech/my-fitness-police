package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.data.ExerciseWithSets
import com.example.myfitnesspolice.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun BuilderExerciseCard(entry: ExerciseWithSets, enabled: Boolean, modifier: Modifier = Modifier,
    dropTarget: Boolean, dragEnabled: Boolean, collapsed: Boolean, onToggleCollapse: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?,
    onAdd: () -> Unit, onCopyLast: () -> Unit, onNote: () -> Unit, onEquipment: () -> Unit, onRemove: () -> Unit, onChange: (String, String, String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
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
                IconButton(onClick = onToggleCollapse, enabled = enabled,
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
                        IconButton(onClick = { menu = true }, enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = "Exercise options for " + entry.exercise.name }) {
                            Text("⋮", fontSize = 24.sp)
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit equipment setup") },
                                onClick = { menu = false; onEquipment() })
                            DropdownMenuItem(text = { Text("Remove", color = PoliceColors.Error) },
                                onClick = { menu = false; onRemove() })
                        }
                    }
                }
                EquipmentPositionSummary(entry.workoutExercise.equipmentPositions)
                Text(entry.workoutExercise.notes.ifBlank { "Add a note prior to workout" },
                    modifier = Modifier.fillMaxWidth().border(1.dp, PoliceColors.Border, RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled, onClickLabel = "Edit note", onClick = onNote)
                        .heightIn(min = 48.dp).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Set", "Modifier", "Type", "Lbs", "Reps").forEach { title ->
                            Text(title, Modifier.weight(1f), fontSize = 10.sp, color = PoliceColors.Muted, textAlign = TextAlign.Center)
                        }
                    }
                    entry.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                                .background(PoliceColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, PoliceColors.Border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center) {
                                Text((index + 1).toString(), fontSize = 13.sp)
                            }
                            TablePicker("Modifier set ${index + 1}", set.modifier, listOf("none", "superset"), enabled, Modifier.weight(1f),
                                label = { if (it == "none") "Na" else "Ss" },
                                fullName = { if (it == "none") "None" else "Superset" }) { onChange(set.id, "modifier", it) }
                            TablePicker("Type set ${index + 1}", if (set.isWarmup) "warmup" else "working set",
                                listOf("warmup", "working set"), enabled, Modifier.weight(1f),
                                label = { if (it == "warmup") "Wu" else "Ws" },
                                fullName = { if (it == "warmup") "Warmup" else "Working set" }) { onChange(set.id, "type", it) }
                            val weight = BigDecimal.valueOf(set.weightGrams).divide(BigDecimal("453.59237"), 2,
                                RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            val weights = remember(weight) { ((0..600).map { BigDecimal.valueOf(it * 25L, 1)
                                .stripTrailingZeros().toPlainString() } + weight).distinct().sortedBy { it.toBigDecimal() } }
                            TablePicker("Weight set ${index + 1}", weight, weights, enabled, Modifier.weight(1f)) {
                                if (it != weight) onChange(set.id, "weight", it)
                            }
                            val reps = remember(set.reps) { ((1..100).map { it.toString() } + set.reps.toString())
                                .distinct().sortedBy { it.toInt() } }
                            TablePicker("Reps set ${index + 1}", set.reps.toString(), reps, enabled, Modifier.weight(1f)) {
                                onChange(set.id, "reps", it)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAdd, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).testTag("add-set-${entry.workoutExercise.id}")) { Text("Add Set") }
                    OutlinedButton(onClick = onCopyLast, enabled = enabled && entry.sets.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1.4f).testTag("copy-last-set-${entry.workoutExercise.id}")) { Text("Copy last set") }
                }
            }
        }
    }
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

