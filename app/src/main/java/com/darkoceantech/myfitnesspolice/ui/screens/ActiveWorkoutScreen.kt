package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ActiveWorkoutScreen(model: SessionViewModel, modifier: Modifier = Modifier,
    onBack: () -> Unit, onFinished: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val workout = state.sessions.firstOrNull { it.workout.kind == "session" && it.workout.finishedAt == null }
    val progress = workout?.sessionState
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var pauseDialog by rememberSaveable { mutableStateOf(false) }
    var noteId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    var infoId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSetDetails by remember { mutableStateOf(false) }
    var pendingFinishId by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(editingSetDetails, noteId, equipmentId, finishDialog) {
        model.deferAutoFinish(editingSetDetails || noteId != null || equipmentId != null || finishDialog)
        onDispose { model.deferAutoFinish(false) }
    }
    fun back() { if (infoId != null) infoId = null else onBack() }
    BackHandler { if (!action.saving) back() }
    LaunchedEffect(workout?.workout?.id) {
        if (workout != null && progress == null) model.ensureSession(workout.workout.id)
    }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            when (action.completedAction) {
                "finish-session" -> { finishDialog = false; pendingFinishId?.let(onFinished) }
                "cancel-session" -> onBack()
                "save-equipment-positions" -> equipmentId = null
                "pause-session" -> { finishDialog = false; pauseDialog = true }
                "pause-reason", "resume-session" -> pauseDialog = false
                "session-note" -> noteId = null
            }
            revision = action.revision
        }
    }
    if (workout == null || progress == null) {
        Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Workout", style = MaterialTheme.typography.headlineLarge)
            if (state.loading || action.saving) CircularProgressIndicator()
            else if (state.failed) PoliceButton(onClick = model::retry) { Text("Retry") }
            else if (workout != null) PoliceButton(onClick = { model.clearError(); model.ensureSession(workout.workout.id) }) { Text("Load session") }
            TextButton(onClick = onBack) { Text("Back to workout home") }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
        return
    }
    key(workout.workout.id) {
        val entries = workout.orderedExercises()
        val initial = entries.indexOfFirst { entry -> entry.sets.any { it.id == progress.currentSetId } }.coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = initial) { entries.size }
        val scope = rememberCoroutineScope()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(progress.isPaused) {
            now = System.currentTimeMillis()
            if (!progress.isPaused) while (true) { delay(250); now = System.currentTimeMillis() }
        }
        LaunchedEffect(progress.currentSetId) {
            val index = entries.indexOfFirst { entry -> entry.sets.any { it.id == progress.currentSetId } }
            if (index >= 0 && progress.phase == "active") pager.animateScrollToPage(index)
        }
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }, enabled = !action.saving) { Icon(painterResource(R.drawable.ic_back), if (infoId != null) "Back to exercise" else "Back to workout home") }
                Column(Modifier.weight(1f)) {
                    Text(workout.workout.displayName(),
                        style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.workout.startedAt)),
                        style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                }
            }
            ExerciseProgressStrip(entries, pager.currentPage, entries.indexOfFirst { entry -> entry.sets.any { it.id == progress.currentSetId } }) { index -> if (!action.saving) { infoId = null; scope.launch { pager.animateScrollToPage(index) } } }
            SessionTimers(workout, progress, now, Modifier.padding(horizontal = 16.dp))
            if (progress.phase == "cooldown") {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("cooldown-banner"),
                    shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Blue)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if (progress.isPaused) "COOL-DOWN PAUSED" else "COOL-DOWN", color = PoliceColors.LightBlue,
                                style = MaterialTheme.typography.labelLarge)
                            Text(if (editingSetDetails || equipmentId != null || noteId != null) "Save or close your edits to finish."
                                else "Finishes automatically · counts as break time", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        }
                        Text(sessionTime(((progress.cooldownRemainingMillis(now) + 999) / 1000) * 1000),
                            style = StatTypography.copy(fontSize = 22.sp), modifier = Modifier.testTag("cooldown-remaining"))
                    }
                }
            }
            if (!progress.hasStarted) {
                Text("Ready when you are. Tap Start on the first set to begin.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("training-ready"), style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue)
            }
            if (progress.isPaused) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PAUSED" + progress.pauseReason.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { pauseDialog = true }, enabled = !action.saving) { Text("Edit reason") }
            }
            val infoEntry = entries.find { entry -> entry.sets.any { it.id == infoId } }
            val infoSet = infoEntry?.sets?.find { it.id == infoId }
            if (infoEntry != null && infoSet != null) key(infoEntry.workoutExercise.id) {
                SetDetailsPager(infoEntry.sets.filter { it.completedAt != null }.sortedBy { it.position }, infoSet.id, !action.saving, Modifier.weight(1f),
                    tagPrefix = "active", onSelect = { infoId = it; model.clearError() },
                    onNavigationLocked = { editingSetDetails = it }) { detailSet, lock ->
                    RecordedSetInfoPanel(infoEntry.exercise.name, detailSet, action, Modifier.fillMaxSize(),
                        tagPrefix = "active", correctionAction = "correct-active-set", noteAction = "save-active-set-note",
                        activeMillis = detailSet.activeMillis + if (progress.phase == "active" && progress.currentSetId == detailSet.id) progress.phaseMillis(now) else 0,
                        restMillis = detailSet.restMillis + if (progress.phase in listOf("rest", "cooldown") && progress.currentSetId == detailSet.id) progress.phaseMillis(now) else 0,
                        onNavigationLocked = lock, onEdit = model::clearError,
                        onSave = { pounds, planned, actual, rpe -> model.correctActiveSet(workout.workout.id, detailSet.id, pounds, planned, actual, rpe) },
                        onSaveNote = { model.saveActiveSetNote(workout.workout.id, detailSet.id, it) },
                        totals = { WorkoutSummary(workout, Modifier, "Set ${detailSet.position + 1} info", infoEntry.exercise.name,
                            tagPrefix = "active", compactSetInfo = true) })
                }
            }
            else if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No sets in this session.")
            } else HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("active-exercise-pager"),
                pageSpacing = 12.dp, key = { entries[it].workoutExercise.id }, verticalAlignment = Alignment.Top) { index ->
                val entry = entries[index]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ActiveExerciseCard(entry, workout, progress, !action.saving,
                        onNote = { model.clearError(); noteId = entry.workoutExercise.id },
                        onEquipment = { model.clearError(); equipmentId = entry.workoutExercise.id },
                        onComplete = { model.completeActiveSet(workout.workout.id, it) },
                        onStart = { model.startNextSet(workout.workout.id, it) },
                        onInfo = { model.clearError(); infoId = it })
                    val activeEntry = entries.firstOrNull { e -> e.sets.any { it.id == progress.currentSetId } }
                    if (progress.phase == "active" && activeEntry != null && activeEntry != entry) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(entries.indexOf(activeEntry)) } }) {
                            Text("Return to active set · ${activeEntry.exercise.name}")
                        }
                    }
                }
            }
            if (action.error != null && !finishDialog && !pauseDialog && !progress.awaitingActual && noteId == null && infoId == null && equipmentId == null) {
                Text(action.error!!, Modifier.padding(horizontal = 16.dp), color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall)
            }
            if (infoId == null) Surface(color = PoliceColors.Card, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        model.clearError()
                        if (!progress.hasStarted) model.cancelUnstartedSession(workout.workout.id)
                        else if (progress.isPaused) model.resumeSession(workout.workout.id) else model.pauseSession(workout.workout.id)
                    }, enabled = !action.saving, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("pause-resume-workout")) {
                        Text(if (!progress.hasStarted) "Cancel workout" else if (progress.isPaused) "Resume workout" else "Pause workout")
                    }
                    PoliceButton(onClick = { model.clearError(); finishDialog = true },
                        enabled = !action.saving && progress.hasStarted, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Finish workout") }
                }
            }
        }
        val pendingSet = workout.orderedSets().firstOrNull { it.id == progress.currentSetId }
        if (progress.awaitingActual && pendingSet != null) ActualRepsDialog(pendingSet, action,
            onSave = { actual, rpe, notes -> model.recordActual(workout.workout.id, pendingSet.id, actual, rpe, notes) },
            onPause = { model.pauseSession(workout.workout.id) }, breakMillis = progress.phaseMillis(now), visible = !progress.isPaused,
            cooldownRemaining = if (progress.phase == "cooldown") progress.cooldownRemainingMillis(now) else null)
        if (pauseDialog && progress.isPaused) PauseReasonDialog(progress.pauseReason, action,
            onDismiss = { pauseDialog = false }, onSave = { model.savePauseReason(workout.workout.id, it) })
        val noteEntry = entries.firstOrNull { it.workoutExercise.id == noteId }
        if (noteEntry != null) SessionNoteDialog(noteEntry.workoutExercise.notes, action,
            onDismiss = { noteId = null }, onSave = { model.sessionNote(workout.workout.id, noteEntry.workoutExercise.id, it) })
        val equipmentEntry = entries.firstOrNull { it.workoutExercise.id == equipmentId }
        if (equipmentEntry != null) EquipmentPositionEditor(equipmentEntry, action,
            onDismiss = { equipmentId = null; model.clearError() }, onEdit = model::clearError,
            onSave = { model.saveEquipmentPositions(workout.workout.id, equipmentEntry.workoutExercise.id, it) })
        if (finishDialog) AlertDialog(onDismissRequest = { if (!action.saving) finishDialog = false },
            title = { Text("Finish this workout?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("You can pause your workout if you need to come back to it later.")
                Text("Finish now to save the sets you performed to History. Uncompleted sets will remain unperformed.")
                action.error?.let { Text(it, color = PoliceColors.Error) }
            } },
            confirmButton = { TextButton(onClick = { pendingFinishId = workout.workout.id; model.finishSession(workout.workout.id) }, enabled = !action.saving) { Text("Finish") } },
            dismissButton = { TextButton(onClick = { finishDialog = false }, enabled = !action.saving) { Text("Cancel") } })
    }
}

@Composable
private fun ExerciseProgressStrip(entries: List<ExerciseWithSets>, selectedIndex: Int, activeIndex: Int, onSelect: (Int) -> Unit) {
    if (entries.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val width = (maxWidth / entries.size).coerceAtLeast(48.dp)
        val scroll = rememberScrollState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(selectedIndex) {
            val offset = with(density) { (width * selectedIndex).toPx() }.toInt()
            scroll.animateScrollTo(offset.coerceAtMost(scroll.maxValue))
        }
        Row(Modifier.horizontalScroll(scroll)) {
            entries.forEachIndexed { index, entry ->
                val complete = entry.sets.isNotEmpty() && entry.sets.all { it.completedAt != null && it.actualReps != null }
                val color = when { activeIndex == index -> PoliceColors.Red; complete -> PoliceColors.LightBlue; else -> androidx.compose.ui.graphics.Color(0xFF2454A0) }
                Box(Modifier.width(width).height(48.dp).testTag("exercise-tab-${entry.workoutExercise.id}")
                    .clickable { onSelect(index) }.semantics {
                        selected = selectedIndex == index
                        stateDescription = if (activeIndex == index) "Active exercise" else if (complete) "Completed exercise" else "Incomplete exercise"
                        contentDescription = "Exercise ${index + 1} of ${entries.size}: ${entry.exercise.name}, " +
                            if (complete) "completed" else "incomplete"
                    }.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    if (selectedIndex == index) Box(Modifier.fillMaxWidth().height(24.dp).offset(y = 12.dp).drawWithCache {
                        val glowColor = if (activeIndex == index) PoliceColors.Red else PoliceColors.Blue
                        val center = Offset(size.width / 2, 0f)
                        val radius = size.width / 2
                        val glow = Brush.radialGradient(listOf(glowColor.copy(alpha = .65f), Color.Transparent), center, radius)
                        onDrawBehind {
                            clipRect {
                                scale(1f, size.height / radius, pivot = center) { drawCircle(glow, radius, center) }
                            }
                        }
                    })
                    Box(Modifier.fillMaxWidth().height(4.dp).background(color))
                }
            }
        }
    }
}

@Composable
private fun SessionTimers(workout: WorkoutDetails, state: WorkoutSessionState, now: Long, modifier: Modifier) {
    val current = workout.orderedSets().firstOrNull { it.id == state.currentSetId }
    val active = if (state.phase == "active") state.phaseMillis(now) else current?.activeMillis ?: 0
    val rest = if (state.phase in listOf("rest", "cooldown")) state.phaseMillis(now) else 0
    Surface(modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShieldMark(Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("ON DUTY", style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                Text(sessionTime(state.dutyMillis(now)), style = StatTypography.copy(fontSize = 22.sp), modifier = Modifier.testTag("duty-time"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTIVE", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                    Text(sessionTime(active), style = StatTypography.copy(fontSize = 16.sp), modifier = Modifier.testTag("active-time"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BREAK", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                    Text(sessionTime(rest), style = StatTypography.copy(fontSize = 16.sp), modifier = Modifier.testTag("break-time"))
                }
            }
        }
    }
}

internal fun sessionTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
