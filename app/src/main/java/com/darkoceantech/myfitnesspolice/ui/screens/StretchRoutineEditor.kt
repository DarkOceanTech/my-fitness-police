package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlin.math.abs

@Composable
internal fun StretchRoutineEditor(original: StretchRoutine?, catalog: List<StretchMovement>, preferences: StretchPreferences,
    action: SessionAction, blocked: Boolean, onCancel: () -> Unit, onSave: (StretchRoutine) -> Unit,
    onCreated: () -> Unit, onStart: (String) -> Unit) {
    val initial = remember(original?.id) { original ?: StretchRoutine(defaultWork = preferences.work, defaultRest = preferences.rest, defaultSwitch = preferences.switch) }
    var payload by rememberSaveable(initial.id) { mutableStateOf(StretchJson.routine(initial)) }
    val draft = remember(payload) { StretchJson.routine(payload) }
    var baseline by rememberSaveable(initial.id) { mutableStateOf(StretchJson.routine(initial)) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var exitPrompt by rememberSaveable { mutableStateOf(false) }
    var saveAndExit by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var remove by rememberSaveable { mutableStateOf<String?>(null) }
    var noteEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var infoEntry by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val changed = payload != baseline
    val valid = runCatching { StretchTiming.validate(draft) }.isSuccess
    fun update(value: StretchRoutine) { if (!action.saving) payload = StretchJson.routine(value) }
    fun updateEntry(value: RoutineStretch) { update(draft.copy(stretches = draft.stretches.map { if (it.id == value.id) value else it })) }
    fun exit() { if (changed) exitPrompt = true else onCancel() }
    fun save() { submitted = true; onSave(draft) }
    LaunchedEffect(action.revision) {
        if (submitted && action.completedAction == "save-routine") {
            submitted = false; baseline = payload
            if (original == null) onCreated() else if (saveAndExit) onCancel()
        }
    }
    BackHandler(!picking && !exitPrompt && noteEntry == null && infoEntry == null && remove == null) { if (!action.saving) exit() }
    WorkoutEditorDialog(if (original == null) "Create your routine" else "Edit routine", "stretch-routine-editor", !action.saving, ::exit, fullScreen = true,
        footer = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PoliceOutlinedButton(onClick = { picking = true }, enabled = !action.saving, modifier = Modifier.weight(1f).testTag("add-stretch")) { Text("Add Stretch") }
                    PoliceButton(onClick = ::save, enabled = changed && valid && !action.saving, modifier = Modifier.weight(1f).testTag("save-stretch-routine")) { Text("Save") }
                }
                if (original != null) PoliceButton(onClick = { onStart(draft.id) }, enabled = !changed && valid && !blocked && !action.saving,
                    modifier = Modifier.fillMaxWidth().testTag("start-stretch-routine")) { Text("Start routine") }
                if (blocked && original != null) Text("Return to your current session before starting another.", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }) {
        LazyColumn(Modifier.weight(1f).testTag("stretch-builder-list").onGloballyPositioned { viewport = it.boundsInWindow() },
            state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { StretchPanel {
                Text("ROUTINE DETAILS", style = MaterialTheme.typography.labelLarge, color = PoliceColors.LightBlue)
                OutlinedTextField(draft.name, { update(draft.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("routine-name"))
                StretchChoice("Purpose", draft.purpose, stretchPurposes) { update(draft.copy(purpose = it)) }
                OutlinedTextField(draft.description, { update(draft.copy(description = it)) }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Default times · seconds", color = PoliceColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Work", "Rest", "Switch sides").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StretchSeconds("Default work", draft.defaultWork, 1, modifier = Modifier.weight(1f)) { update(draft.copy(defaultWork = it)) }
                    StretchSeconds("Default rest", draft.defaultRest, modifier = Modifier.weight(1f)) { update(draft.copy(defaultRest = it)) }
                    StretchSeconds("Default switch sides", draft.defaultSwitch, modifier = Modifier.weight(1f)) { update(draft.copy(defaultSwitch = it)) }
                }
                StretchToggle("Include final rest", draft.finalRest) { update(draft.copy(finalRest = it)) }
                Text("Defaults prefill new sets. Work applies to each side for separate-side movements.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            } }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${draft.stretches.sumOf { it.sets.size }} sets · ${stretchTime(StretchTiming.estimate(draft))} estimated", Modifier.weight(1f), color = PoliceColors.Muted)
                val all = draft.stretches.isNotEmpty() && draft.stretches.all { it.id in collapsed }
                TextButton(onClick = { collapsed = if (all) emptyList() else draft.stretches.map { it.id } }) { Text(if (all) "Expand all" else "Collapse all") }
            } }
            item {
                StretchReorderColumn(draft.stretches.map { it.id }, listState, viewport, onOrder = { ids ->
                    update(draft.copy(stretches = ids.map { id -> draft.stretches.single { it.id == id } }))
                }) { id, handle ->
                    val entry = draft.stretches.single { it.id == id }
                    StretchPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            handle()
                            Text(if (id in collapsed) entry.movement.name else "STRETCH", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { collapsed = if (id in collapsed) collapsed - id else collapsed + id }) { Text(if (id in collapsed) "+" else "−", fontSize = 24.sp) }
                        }
                        SirenRule(Modifier.fillMaxWidth())
                        if (id !in collapsed) {
                            var menu by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.movement.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                Box {
                                    IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 24.sp) }
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("View stretch info") }, onClick = { menu = false; infoEntry = id })
                                        DropdownMenuItem(text = { Text("Remove", color = PoliceColors.Error) }, onClick = { menu = false; remove = id })
                                    }
                                }
                            }
                            Text(entry.note.ifBlank { "Add a routine-specific stretch note" }, Modifier.fillMaxWidth()
                                .border(1.dp, PoliceColors.Border, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable { noteEntry = id }.padding(12.dp))
                            if (entry.movement.separateSides) StretchChoice("Starting side", entry.startingSide, listOf("Left", "Right")) { updateEntry(entry.copy(startingSide = it)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Set", if (entry.movement.separateSides) "Work/side" else "Work", "Rest", "Switch sides").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
                                Spacer(Modifier.width(40.dp))
                            }
                            StretchReorderColumn(entry.sets.map { it.id }, listState, viewport, onOrder = { ids -> updateEntry(entry.copy(sets = ids.map { key -> entry.sets.single { it.id == key } })) }) { setId, setHandle ->
                                val set = entry.sets.single { it.id == setId }
                                fun change(value: StretchSet) { updateEntry(entry.copy(sets = entry.sets.map { if (it.id == value.id) value else it })) }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { setHandle(); Text("${entry.sets.indexOf(set) + 1}") }
                                    StretchSeconds("Work set ${entry.sets.indexOf(set) + 1}", set.work, 1, modifier = Modifier.weight(1f)) { change(set.copy(work = it)) }
                                    StretchSeconds("Rest set ${entry.sets.indexOf(set) + 1}", set.rest, modifier = Modifier.weight(1f)) { change(set.copy(rest = it)) }
                                    if (entry.movement.separateSides) StretchSeconds("Switch set ${entry.sets.indexOf(set) + 1}", set.switch, modifier = Modifier.weight(1f)) { change(set.copy(switch = it)) }
                                    else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("—", color = PoliceColors.Muted) }
                                    IconButton(onClick = { updateEntry(entry.copy(sets = entry.sets.filterNot { it.id == setId })) }, modifier = Modifier.size(40.dp)) { Text("×", color = PoliceColors.Error, fontSize = 24.sp) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { updateEntry(entry.copy(sets = entry.sets + StretchSet(work = draft.defaultWork, rest = draft.defaultRest, switch = draft.defaultSwitch))) },
                                    modifier = Modifier.weight(1f).testTag("stretch-add-set-$id")) { Text("Add Set") }
                                OutlinedButton(onClick = { entry.sets.lastOrNull()?.let { updateEntry(entry.copy(sets = entry.sets + it.copy(id = java.util.UUID.randomUUID().toString()))) } },
                                    enabled = entry.sets.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Copy last set") }
                            }
                        }
                    }
                }
            }
            action.error?.let { item { Text(it, color = PoliceColors.Error) } }
            if (!valid && draft.stretches.any { it.sets.isEmpty() }) item { Text("Add at least one set to every stretch before saving.", color = PoliceColors.Error) }
        }
    }
    if (picking) StretchPicker(catalog, draft.stretches.map { it.movement.id }, onClose = { picking = false }, onToggle = { movement ->
        if (draft.stretches.any { it.movement.id == movement.id }) update(draft.copy(stretches = draft.stretches.filterNot { it.movement.id == movement.id }))
        else update(draft.copy(stretches = draft.stretches + RoutineStretch(movement = movement,
            sets = listOf(StretchSet(work = draft.defaultWork, rest = draft.defaultRest, switch = draft.defaultSwitch)))))
    })
    if (exitPrompt) AlertDialog(onDismissRequest = { exitPrompt = false }, title = { Text("Save routine changes?") },
        text = { Text("Save your changes, leave without saving, or continue editing.") },
        confirmButton = { TextButton(onClick = { exitPrompt = false; saveAndExit = true; save() }, enabled = valid && !action.saving) { Text("Save changes") } },
        dismissButton = { Column {
            TextButton(onClick = onCancel, enabled = !action.saving) { Text("Exit without saving") }
            TextButton(onClick = { exitPrompt = false }) { Text("Continue editing") }
        } })
    if (remove != null) AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove stretch?") }, text = { Text("Remove this stretch and its planned sets from the routine?") },
        confirmButton = { TextButton(onClick = { update(draft.copy(stretches = draft.stretches.filterNot { it.id == remove })); remove = null }) { Text("Remove", color = PoliceColors.Error) } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } })
    noteEntry?.let { id -> draft.stretches.firstOrNull { it.id == id }?.let { entry ->
        StretchTextNote("Stretch note", entry.note, onCancel = { noteEntry = null }, onSave = { updateEntry(entry.copy(note = it)); noteEntry = null })
    } }
    draft.stretches.firstOrNull { it.id == infoEntry }?.let { entry ->
        StretchInfo(entry.movement) { infoEntry = null }
    }
}

@Composable
private fun StretchPicker(catalog: List<StretchMovement>, selected: List<String>, onClose: () -> Unit, onToggle: (StretchMovement) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    WorkoutEditorDialog("Add Stretch", "stretch-picker", true, onClose, fullScreen = true, footer = {
        PoliceButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, label = { Text("Search stretches") }, modifier = Modifier.weight(1f))
            TextButton(onClick = { onlySelected = !onlySelected }) { Text("${selected.size} selected") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(catalog.filter { it.name.contains(query, true) && (!onlySelected || it.id in selected) }, key = { it.id }) { movement ->
                StretchPanel {
                    Text(movement.name, style = MaterialTheme.typography.titleMedium)
                    Text(movement.muscles, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                    if (movement.id in selected) OutlinedButton(onClick = { onToggle(movement) }) { Text("Remove", color = PoliceColors.Error) }
                    else PoliceButton(onClick = { onToggle(movement) }, modifier = Modifier.testTag("select-stretch-" + movement.id)) { Text("Add") }
                }
            }
        }
    }
}

/** Long-press grip, red lifted row, blue insertion rule, and animated displaced neighbors. */
@Composable
private fun StretchReorderColumn(ids: List<String>, parentList: LazyListState, viewport: Rect, onOrder: (List<String>) -> Unit,
    content: @Composable (String, @Composable () -> Unit) -> Unit) {
    var dragging by remember { mutableStateOf<String?>(null) }
    var delta by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val latestIds by rememberUpdatedState(ids)
    val latestOrder by rememberUpdatedState(onOrder)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { 12.dp.toPx() }
    val latestViewport by rememberUpdatedState(viewport)
    var columnTop by remember { mutableFloatStateOf(0f) }
    var columnHeight by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val speed = with(density) { 720.dp.toPx() }
        var previous = withFrameNanos { it }
        while (dragging != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(.04f)
            previous = now
            val bounds = latestViewport
            if (bounds.height <= 0f) continue
            val fraction = when {
                pointerY < bounds.top + edge -> -((bounds.top + edge - pointerY) / edge).coerceIn(0f, 1f)
                pointerY > bounds.bottom - edge -> ((pointerY - bounds.bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            val requested = fraction * speed * seconds
            val bounded = if (requested > 0f) requested.coerceAtMost((columnTop + columnHeight - bounds.bottom).coerceAtLeast(0f))
                else requested.coerceAtLeast((columnTop - bounds.top).coerceAtMost(0f))
            if (bounded != 0f) delta += parentList.scrollBy(bounded)
        }
    }
    val origin = ids.indexOf(dragging)
    fun targetIndex(): Int {
        val current = latestIds
        var target = current.indexOf(dragging)
        if (target >= 0) {
            var distance = abs(delta)
            val step = if (delta > 0) 1 else -1
            while (target + step in current.indices && distance > (heights[current[target + step]] ?: 80) / 2f) {
                target += step; distance -= (heights[current[target]] ?: 80) + gap
            }
        }
        return target
    }
    val target = targetIndex()
    Column(Modifier.onGloballyPositioned { columnTop = it.positionInWindow().y; columnHeight = it.size.height.toFloat() },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ids.forEachIndexed { index, id -> key(id) {
            val shifted = when {
                dragging == null || index == origin -> 0f
                origin < target && index in (origin + 1)..target -> -(heights[dragging] ?: 80).toFloat() - gap
                target < origin && index in target until origin -> (heights[dragging] ?: 80).toFloat() + gap
                else -> 0f
            }
            val translation by animateFloatAsState(shifted, spring(), label = "stretch-row-move")
            Box(Modifier.fillMaxWidth().onSizeChanged { heights[id] = it.height }
                .graphicsLayer { translationY = if (dragging == id) delta else translation }
                .zIndex(if (dragging == id) 2f else 0f)
                .then(if (dragging == id) Modifier.border(2.dp, PoliceColors.Red) else Modifier)) {
                content(id) {
                    var handleTop by remember { mutableFloatStateOf(0f) }
                    SetDragHandle(Modifier.size(28.dp).testTag("stretch-drag-$id").onGloballyPositioned { handleTop = it.positionInWindow().y }.semantics {
                        contentDescription = "Drag to reorder"
                        customActions = listOfNotNull(
                            if (index > 0) CustomAccessibilityAction("Move up") { latestOrder(latestIds.toMutableList().apply { add(index - 1, removeAt(index)) }); true } else null,
                            if (index < ids.lastIndex) CustomAccessibilityAction("Move down") { latestOrder(latestIds.toMutableList().apply { add(index + 1, removeAt(index)) }); true } else null)
                    }.pointerInput(id) {
                        detectDragGesturesAfterLongPress(onDragStart = { point -> dragging = id; delta = 0f; pointerY = handleTop + point.y },
                            onDragCancel = { dragging = null; delta = 0f }, onDragEnd = {
                                val from = latestIds.indexOf(id)
                                val to = targetIndex()
                                if (from >= 0 && to >= 0 && from != to) latestOrder(latestIds.toMutableList().apply { add(to, removeAt(from)) })
                                dragging = null; delta = 0f
                            }) { change, amount -> change.consume(); delta += amount.y; pointerY += amount.y }
                    })
                }
                if (dragging != null && index == target && index != origin) Box(Modifier.fillMaxWidth().height(3.dp)
                    .align(if (target < origin) Alignment.TopCenter else Alignment.BottomCenter).background(PoliceColors.Blue))
            }
        } }
    }
}

@Composable
internal fun StretchTextNote(title: String, initial: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    WorkoutEditorDialog(title, "stretch-note", true, onCancel, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { onSave(text) }, modifier = Modifier.weight(1f)) { Text("Save") }
    }) { OutlinedTextField(text, { text = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()) }
}
