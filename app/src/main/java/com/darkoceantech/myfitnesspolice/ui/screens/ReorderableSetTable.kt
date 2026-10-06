package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.domain.formatting.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.darkoceantech.myfitnesspolice.data.ExerciseWithSets
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import java.math.BigDecimal
import kotlin.math.roundToInt

private data class PendingSetOrder(val ids: List<String>, val original: List<String>, val revision: Int)

/** Rows retain their identities and values; only the single release callback writes the order. */
@Composable
internal fun ReorderableSetTable(entry: ExerciseWithSets, enabled: Boolean, action: SessionAction,
    parentListState: LazyListState, parentViewport: Rect, onDragState: (Boolean) -> Unit,
    onReorder: (List<String>) -> Unit, onDeleteSet: (String) -> Unit,
    onChange: (String, String, String) -> Unit) {
    val source = entry.sets.sortedBy { it.position }
    val sourceIds = source.map { it.id }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val gap = with(density) { 8.dp.toPx() }
    val inset = with(density) { 6.dp.toPx() }
    var rowHeight by remember(density) { mutableFloatStateOf(with(density) { 52.dp.toPx() }) }
    val stride = rowHeight + gap
    val totalHeight = if (source.isEmpty()) 0f else rowHeight * source.size + gap * (source.size - 1) + inset * 2
    var tableTop by remember { mutableFloatStateOf(0f) }
    var draggedId by remember(entry.workoutExercise.id) { mutableStateOf<String?>(null) }
    var dragOriginal by remember { mutableStateOf(emptyList<String>()) }
    var pointerWindowY by remember { mutableFloatStateOf(0f) }
    var gripOffset by remember { mutableFloatStateOf(0f) }
    var pending by remember(entry.workoutExercise.id) { mutableStateOf<PendingSetOrder?>(null) }
    val currentSource by rememberUpdatedState(sourceIds)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAction by rememberUpdatedState(action)
    val currentStride by rememberUpdatedState(stride)
    val currentInset by rememberUpdatedState(inset)
    val currentHeight by rememberUpdatedState(totalHeight)
    val currentViewport by rememberUpdatedState(parentViewport)
    val currentReorder by rememberUpdatedState(onReorder)
    val currentDragState by rememberUpdatedState(onDragState)
    val canInteract = enabled && !action.saving && draggedId == null && pending == null

    fun targetIndex(): Int = ((pointerWindowY - tableTop - gripOffset - currentInset) / currentStride)
        .roundToInt().coerceIn(0, (dragOriginal.size - 1).coerceAtLeast(0))
    fun clearDrag() {
        if (draggedId != null) { draggedId = null; currentDragState(false) }
    }
    fun submit(ids: List<String>) {
        val original = currentSource
        if (ids != original && currentEnabled && !currentAction.saving) {
            pending = PendingSetOrder(ids, original, currentAction.revision)
            currentReorder(ids)
        }
    }
    fun finishDrag() {
        val id = draggedId ?: return
        val ids = moveSetId(dragOriginal, id, targetIndex())
        val unchangedSource = currentSource == dragOriginal
        clearDrag()
        if (unchangedSource) submit(ids)
    }
    BackHandler(draggedId != null) { clearDrag() }
    DisposableEffect(entry.workoutExercise.id) { onDispose { if (draggedId != null) currentDragState(false) } }
    LaunchedEffect(sourceIds, action, enabled) {
        if (draggedId != null && (!enabled || action.saving || sourceIds != dragOriginal)) clearDrag()
        pending?.let { expected ->
            if (sourceIds == expected.ids || sourceIds != expected.original ||
                (!action.saving && action.error != null) ||
                (action.revision != expected.revision && action.completedAction != "reorder-sets")) pending = null
        }
    }
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val maxSpeed = with(density) { 720.dp.toPx() }
        val clearance = with(density) { 12.dp.toPx() }
        var previousFrame = withFrameNanos { it }
        while (draggedId != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previousFrame) / 1_000_000_000f).coerceAtMost(.04f)
            previousFrame = now
            val viewport = currentViewport
            if (viewport.height <= 0f) continue
            val fraction = when {
                pointerWindowY < viewport.top + edge -> -((viewport.top + edge - pointerWindowY) / edge).coerceIn(0f, 1f)
                pointerWindowY > viewport.bottom - edge -> ((pointerWindowY - viewport.bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            val requested = fraction * maxSpeed * seconds
            // Reveal this table's rows without dragging the card into a neighboring exercise.
            val bounded = if (requested > 0f) requested.coerceAtMost((tableTop + currentHeight - viewport.bottom + clearance).coerceAtLeast(0f))
                else requested.coerceAtLeast((tableTop - viewport.top - clearance).coerceAtMost(0f))
            if (bounded != 0f) parentListState.scrollBy(bounded)
        }
    }
    val target = if (draggedId == null) -1 else targetIndex()
    val displayIds = if (draggedId != null) moveSetId(dragOriginal, draggedId!!, target) else pending?.ids ?: sourceIds
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Set", "Modifier", "Type", "Lbs", "Reps").forEach { title ->
                Text(title, Modifier.weight(1f), fontSize = 10.sp, color = PoliceColors.Muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.fillMaxWidth().height(with(density) { totalHeight.toDp() })
            .testTag("set-table-${entry.workoutExercise.id}")
            .onGloballyPositioned { tableTop = it.positionInWindow().y }
            .then(if (source.size < 2) Modifier else Modifier.pointerInput(entry.workoutExercise.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        if (currentEnabled && !currentAction.saving && pending == null && currentSource.size > 1) {
                            val index = ((point.y - currentInset) / currentStride).toInt().coerceIn(0, currentSource.lastIndex)
                            val top = currentInset + index * currentStride
                            if (point.y >= top && point.y <= top + rowHeight) {
                                dragOriginal = currentSource
                                draggedId = currentSource[index]
                                gripOffset = point.y - top
                                pointerWindowY = tableTop + point.y
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentDragState(true)
                            }
                        }
                    },
                    onDragEnd = { finishDrag() }, onDragCancel = { clearDrag() },
                ) { change, _ ->
                    if (draggedId != null) {
                        change.consume()
                        pointerWindowY = tableTop + change.position.y
                    }
                }
            })) {
            source.forEach { set -> key(set.id) {
                val index = displayIds.indexOf(set.id).coerceAtLeast(0)
                val lifted = draggedId == set.id
                val y = if (lifted) (pointerWindowY - tableTop - gripOffset)
                    .coerceIn(inset, inset + (source.size - 1) * stride) else inset + index * stride
                val animatedY by animateFloatAsState(y, animationSpec = if (lifted) snap() else spring(stiffness = 600f), label = "set-row-position")
                Box(Modifier.fillMaxWidth().offset { IntOffset(0, animatedY.roundToInt()) }
                    .zIndex(if (lifted) 1f else 0f)
                    .then(if (lifted) Modifier.testTag("set-dragged-${set.id}") else Modifier)
                    .background(if (lifted) PoliceColors.Raised else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .border(if (lifted) 2.dp else 0.dp,
                        if (lifted) PoliceColors.Red else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))) {
                    PlannedSetRow(set, index, entry.exercise.name, canInteract, Modifier.fillMaxWidth()
                        .heightIn(min = with(density) { rowHeight.toDp() })
                        .onSizeChanged { if (it.height > rowHeight) rowHeight = it.height.toFloat() }
                        .testTag("set-row-${set.id}").semantics {
                            stateDescription = if (lifted) "Dragging set" else "Set ${index + 1}"
                            customActions = if (canInteract) listOfNotNull(
                                if (index > 0) CustomAccessibilityAction("Move set up") {
                                    submit(moveSetId(currentSource, set.id, index - 1)); true
                                } else null,
                                if (index < displayIds.lastIndex) CustomAccessibilityAction("Move set down") {
                                    submit(moveSetId(currentSource, set.id, index + 1)); true
                                } else null,
                            ) else emptyList()
                        }, onDeleteSet, onChange)
                }
            } }
            if (draggedId != null) {
                val lineY = when (target) {
                    0 -> 0f
                    source.lastIndex -> totalHeight - with(density) { 3.dp.toPx() }
                    else -> inset + target * stride - gap / 2f
                }
                Box(Modifier.fillMaxWidth().height(3.dp).offset { IntOffset(0, lineY.roundToInt()) }.zIndex(2f)
                    .background(PoliceColors.Blue, RoundedCornerShape(2.dp))
                    .testTag("set-insertion-${entry.workoutExercise.id}")
                    .semantics { stateDescription = "Insert at position ${target + 1}" })
            }
        }
    }
}

private fun moveSetId(ids: List<String>, id: String, target: Int): List<String> = ids.toMutableList().apply {
    if (remove(id)) add(target.coerceIn(0, size), id)
}

@Composable
private fun PlannedSetRow(set: WorkoutSet, index: Int, exerciseName: String, enabled: Boolean, modifier: Modifier,
    onDeleteSet: (String) -> Unit, onChange: (String, String, String) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).heightIn(min = 48.dp)
            .testTag("set-number-${set.id}").semantics { contentDescription = "Set ${index + 1} of $exerciseName. Hold and drag to move up or down." },
            contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SetDragHandle(Modifier.size(16.dp).testTag("set-drag-handle-${set.id}"))
                Text((index + 1).toString(), fontSize = 13.sp)
            }
        }
        TablePicker("Modifier set ${index + 1}", set.modifier, listOf("none", "superset", "drop_set"), enabled, Modifier.weight(1f),
            label = { when (it) { "superset" -> "S"; "drop_set" -> "D"; else -> "R" } },
            fullName = { when (it) { "superset" -> "Superset"; "drop_set" -> "Drop set"; else -> "Regular" } }) { onChange(set.id, "modifier", it) }
        TablePicker("Type set ${index + 1}", if (set.isWarmup) "warmup" else "working set", listOf("warmup", "working set"), enabled,
            Modifier.weight(1f), label = { if (it == "warmup") "Wu" else "Ws" },
            fullName = { if (it == "warmup") "Warm-up" else "Working set" }) { onChange(set.id, "type", it) }
        val weight = sessionPounds(set.weightGrams)
        val weights = remember(weight) { ((0..600).map { BigDecimal.valueOf(it * 25L, 1).stripTrailingZeros().toPlainString() } + weight)
            .distinct().sortedBy { it.toBigDecimal() } }
        TablePicker("Weight set ${index + 1}", weight, weights, enabled, Modifier.weight(1f), label = ::formatPoundsText) {
            if (it != weight) onChange(set.id, "weight", it)
        }
        val reps = remember(set.reps) { ((1..100).map { it.toString() } + set.reps.toString()).distinct().sortedBy { it.toInt() } }
        TablePicker("Reps set ${index + 1}", set.reps.toString(), reps, enabled, Modifier.weight(1f)) { onChange(set.id, "reps", it) }
        IconButton(onClick = { onDeleteSet(set.id) }, enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("delete-planned-set-${set.id}")
                .semantics { contentDescription = "Remove set ${index + 1} of $exerciseName" }) {
            Text("×", color = if (enabled) PoliceColors.Error else PoliceColors.Muted, fontSize = 24.sp)
        }
    }
}
