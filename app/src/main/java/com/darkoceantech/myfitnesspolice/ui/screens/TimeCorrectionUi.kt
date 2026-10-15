package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

private const val timeInstructions = "Find a flagged set in Reports → Time Review, or open Set Details directly. " +
    "Use the time menu → Correct time. Choose the timing mistake, review the neighboring intervals, then use a comparable-set estimate or enter Work seconds manually. " +
    "Review where time is taken from and where it goes. Save correction to apply both sides together, or Cancel to leave timing unchanged. " +
    "View correction details to inspect or restore the entire linked transfer later. Restore dependent corrections newest first. " +
    "Example: a missed Start records only 2 seconds of Work. Correcting it to 42 seconds transfers 40 seconds from the previous set’s Rest. " +
    "The combined duration and session start/end timestamps stay unchanged. Pauses and unrecorded gaps are never invented as Work or Rest. " +
    "If there is no suitable closed neighbor or insufficient time, the correction cannot be applied. Wait for running or paused intervals to finish and confirm results. " +
    "Estimates require at least two comparable sets (same exercise/type, reps within 2 and load within 10% or 5 Lbs). " +
    "Unresolved flags and estimated corrections cannot establish estimates. No estimate is applied automatically. " +
    "Use Flag time issue or write #time in the set note. Mark as accurate dismisses a flag without deleting its note. " +
    "Amber clock means needs review; blue clock with pencil means a correction exists."

@Composable
internal fun SetTimeActions(model: SessionViewModel, workout: WorkoutDetails, set: WorkoutSet) {
    var menu by remember { mutableStateOf(false) }
    var page by rememberSaveable(set.id) { mutableStateOf<String?>(null) }
    val corrections = workout.correctionsFor(set.id)
    Column(horizontalAlignment = Alignment.End) {
        IconButton(onClick = { menu = true }, Modifier.testTag("time-menu-${set.id}")
            .semantics { contentDescription = "Time options for set ${set.position + 1}" }) { Text("⋮", fontSize = 24.sp) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            listOf("Correct time" to "correct", "Flag time issue" to "flag", "About time corrections" to "about").forEach { (label, value) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; page = value })
            }
            if (corrections.isNotEmpty()) {
                DropdownMenuItem(text = { Text("View correction details") }, onClick = { menu = false; page = "details" })
                DropdownMenuItem(text = { Text("Restore recorded time") }, onClick = { menu = false; page = "details" })
            }
        }
    }
    if (page != null) TimeCorrectionDialog(model, workout.workout.id, set.id, page!!) { page = null }
}

@Composable
internal fun TimeCorrectionDialog(model: SessionViewModel, workoutId: String, setId: String,
    initial: String = "correct", onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val workout = state.sessions.firstOrNull { it.workout.id == workoutId }
    val set = workout?.orderedSets()?.firstOrNull { it.id == setId }
    val scope = rememberCoroutineScope()
    DisposableEffect(model) { model.holdTimeReview(true); onDispose { model.holdTimeReview(false) } }
    var page by rememberSaveable(workoutId, setId) { mutableStateOf(initial) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var problem by rememberSaveable { mutableStateOf("Forgot to press Start") }
    var restId by rememberSaveable { mutableStateOf<String?>(null) }
    var seconds by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("Manual") }
    var provenance by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var expectedWork by rememberSaveable { mutableLongStateOf(0L) }
    var expectedRest by rememberSaveable { mutableLongStateOf(0L) }
    var proposed by rememberSaveable { mutableLongStateOf(0L) }
    var elapsed by rememberSaveable { mutableLongStateOf(0L) }
    var restoreId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun runSave(block: suspend () -> Unit) { saving = true; error = null; scope.launch {
        try { block(); onClose() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "Unable to save. Try again." }
        finally { saving = false }
    } }
    fun back() { if (!saving) { if (page == "correct" && step > 0) step-- else onClose() } }
    BackHandler { back() }
    WorkoutEditorDialog(if (page == "about") "About time corrections" else "Set time review", "time-correction-dialog",
        !saving, ::back, fullScreen = true, footer = {
            TextButton(onClick = onClose, enabled = !saving, modifier = Modifier.weight(1f).testTag("cancel-time-correction")) { Text("Cancel") }
        }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("time-correction-scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (workout == null || set == null) Text("This set is no longer available.")
            else {
                Text(workout.workout.displayName(), style = MaterialTheme.typography.titleMedium)
                Text(workout.setTimeLabel(setId), color = PoliceColors.LightBlue)
                if (page == "about") Text(timeInstructions)
                else if (page == "flag") {
                    Text("Flag this set’s timing for review? Your note and recorded durations remain unchanged.")
                    PoliceButton(onClick = { runSave { model.timeCorrections.review(workoutId, setId, "needs_review") } }, enabled = !saving) { Text("Flag time issue") }
                } else if (page == "details") {
                    Text("Work ${sessionTime(set.activeMillis)} · Rest ${sessionTime(set.restMillis)}")
                    val corrections = workout.timeCorrections.filter { it.workSetId == setId || it.restSetId == setId }.sortedByDescending { it.id }
                    if (corrections.isEmpty()) Text("No accepted time corrections for this set.")
                    corrections.forEach { c ->
                        Surface(shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${c.source} · Linked correction #${c.id}${if (!c.applied) " · Restored" else ""}", color = PoliceColors.LightBlue)
                                Text(DateFormat.getDateTimeInstance().format(Date(c.createdAt)))
                                Text("${c.workLabel}\nWork: recorded ${sessionTime(c.recordedWork)}, before ${sessionTime(c.beforeWork)}, accepted ${sessionTime(c.afterWork)}")
                                Text("${c.restLabel}\nRest: recorded ${sessionTime(c.recordedRest)}, before ${sessionTime(c.beforeRest)}, accepted ${sessionTime(c.afterRest)}")
                                Text("Combined time unchanged: ${sessionTime(c.beforeWork + c.beforeRest)}")
                                if (c.provenance.isNotBlank()) Text(c.provenance)
                                if (c.reason.isNotBlank()) Text("Reason: ${c.reason}")
                                if (c.applied) TextButton(onClick = { restoreId = c.id }, enabled = !saving) { Text("Restore this linked transfer") }
                            }
                        }
                    }
                    PoliceButton(onClick = { page = "correct"; step = 0 }, enabled = !saving) { Text("Correct time") }
                } else {
                    when (step) {
                        0 -> {
                            Text("1. What happened?", style = MaterialTheme.typography.titleMedium)
                            listOf("Forgot to press Start", "Pressed Done late", "Other timing mistake").forEach { item ->
                                Row(Modifier.fillMaxWidth().clickable { problem = item }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(problem == item, onClick = { problem = item }); Text(item)
                                }
                            }
                            PoliceButton(onClick = { step = 1 }, enabled = workout.confirmed(set)) { Text("Next: review intervals") }
                            if (!workout.confirmed(set)) Text("Finish this set and confirm its results first.", color = PoliceColors.Error)
                        }
                        1 -> {
                            Text("2. Choose the neighboring Rest interval", style = MaterialTheme.typography.titleMedium)
                            Text("Choose where the time was actually recorded. Pauses and unrecorded time cannot be transferred.")
                            workout.neighboringRests(setId).forEach { neighbor ->
                                val available = workout.closedRest(neighbor)
                                Row(Modifier.fillMaxWidth().clickable(enabled = available) { restId = neighbor.id; source = "Manual"; provenance = "" }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(restId == neighbor.id, onClick = { restId = neighbor.id; source = "Manual"; provenance = "" }, enabled = available)
                                    Text("${if (neighbor.id == setId) "Following Rest" else "Previous Rest"} · ${workout.setTimeLabel(neighbor.id)}\n${sessionTime(neighbor.restMillis)}${if (!available) " · Wait for interval to finish, including if paused" else ""}")
                                }
                            }
                            if (workout.neighboringRests(setId).size == 1) Text("This is the first recorded set. There is no previous recorded Rest to borrow from.")
                            PoliceButton(onClick = { step = 2 }, enabled = restId != null) { Text("Next: Work duration") }
                        }
                        2 -> {
                            Text("3. Correct Work duration", style = MaterialTheme.typography.titleMedium)
                            Text("Currently ${sessionTime(set.activeMillis)}")
                            val estimate = estimateWorkTime(workout, setId, restId, state.sessions)
                            if (estimate != null) {
                                Text("Estimate: ${sessionTime(estimate.millis)} · ${estimate.count} comparable sets\nTypical observed range: ${sessionTime(estimate.low)}–${sessionTime(estimate.high)}")
                                TextButton(onClick = { seconds = BigDecimal.valueOf(estimate.millis, 3).stripTrailingZeros().toPlainString(); source = "Estimated"; provenance = estimate.provenance }) { Text("Use estimate") }
                            } else Text("Not enough comparable sets for an estimate. Enter a manual duration.")
                            OutlinedTextField(seconds, { seconds = it; source = "Manual"; provenance = "" }, label = { Text("Work duration (seconds)") },
                                modifier = Modifier.fillMaxWidth().testTag("corrected-work-seconds"), singleLine = true)
                            OutlinedTextField(reason, { reason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
                            PoliceButton(onClick = {
                                error = null
                                try {
                                    val rest = workout.orderedSets().first { it.id == restId }
                                    require(workout.confirmed(set) && workout.closedRest(rest)) { "Wait for the affected intervals to finish and confirm their results." }
                                    val value = seconds.trim().toBigDecimalOrNull()?.multiply(BigDecimal(1000))?.longValueExact()
                                    require(value != null) { "Enter seconds with up to three decimal places." }
                                    transferTime(set.activeMillis, rest.restMillis, value)
                                    expectedWork = set.activeMillis; expectedRest = rest.restMillis; proposed = value
                                    elapsed = ((workout.workout.finishedAt ?: System.currentTimeMillis()) - workout.workout.startedAt).coerceAtLeast(0)
                                    step = 3
                                } catch (e: Exception) { error = e.message ?: "Enter a valid duration." }
                            }, modifier = Modifier.testTag("review-time-transfer")) { Text("Review transfer") }
                        }
                        3 -> {
                            Text("4. Review before saving", style = MaterialTheme.typography.titleMedium)
                            val rest = workout.orderedSets().firstOrNull { it.id == restId }
                            val rawWork = workout.timeCorrections.firstOrNull { it.workSetId == setId }?.recordedWork ?: set.activeMillis
                            val rawRest = workout.timeCorrections.firstOrNull { it.restSetId == restId }?.recordedRest ?: rest?.restMillis ?: 0L
                            val afterRest = expectedWork + expectedRest - proposed
                            Text("${workout.setTimeLabel(setId)}\nWork\nRecorded: ${sessionTime(rawWork)}\nCurrent: ${sessionTime(expectedWork)}\nProposed: ${sessionTime(proposed)}")
                            Text("${restId?.let { workout.setTimeLabel(it) }}\nRest\nRecorded: ${sessionTime(rawRest)}\nCurrent: ${sessionTime(expectedRest)}\nProposed: ${sessionTime(afterRest)}")
                            Text("Transfer ${sessionTime(kotlin.math.abs(proposed - expectedWork))} ${if (proposed > expectedWork) "from the selected Rest into this Work interval" else "from this Work into the selected Rest interval"}.", color = PoliceColors.LightBlue)
                            Text("Combined duration stays ${sessionTime(expectedWork + expectedRest)}.\nSession elapsed at review: ${sessionTime(elapsed)} — unchanged by correction. ${if (workout.workout.finishedAt == null) "The session clock continues normally." else ""}\nSession start/end timestamps are not edited. Work + Rest need not equal elapsed time because of pauses.")
                            Text("Source: $source"); if (provenance.isNotBlank()) Text(provenance)
                            if (reason.isNotBlank()) Text(reason)
                            PoliceButton(onClick = { runSave { model.timeCorrections.save(workoutId, setId, requireNotNull(restId), proposed,
                                expectedWork, expectedRest, source, provenance, "$problem${if (reason.isBlank()) "" else ": $reason"}") } },
                                enabled = !saving, modifier = Modifier.testTag("save-time-correction")) { Text(if (saving) "Saving…" else "Save correction") }
                        }
                    }
                    if (step > 0) TextButton(onClick = { step--; error = null }, enabled = !saving) { Text("Back") }
                }
                if (workout.timeReviewStatus(set) == "needs_review" && page != "about") TextButton(onClick = {
                    runSave { model.timeCorrections.review(workoutId, setId, "dismissed") }
                }, enabled = !saving) { Text("Mark as accurate") }
            }
            error?.let { Text(it, color = PoliceColors.Error, modifier = Modifier.testTag("time-correction-error")) }
            Spacer(Modifier.height(16.dp))
        }
    }
    restoreId?.let { id -> AlertDialog(onDismissRequest = { if (!saving) restoreId = null },
        title = { Text("Restore linked timing?") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reverse both sides of correction #$id. Later dependent corrections must be reviewed and restored first.")
            workout?.timeCorrections?.firstOrNull { it.id == id }?.let { c ->
                Text("${c.workLabel}\nWork: ${sessionTime(c.afterWork)} → ${sessionTime(c.beforeWork)}")
                Text("${c.restLabel}\nRest: ${sessionTime(c.afterRest)} → ${sessionTime(c.beforeRest)}")
                Text("Combined time remains ${sessionTime(c.beforeWork + c.beforeRest)}. Session start/end timestamps remain unchanged.")
                val dependent = workout.timeCorrections.filter { it.applied && it.id > id && (it.workSetId == c.workSetId || it.restSetId == c.restSetId) }
                if (dependent.isNotEmpty()) Text("Restore these dependent transfers first: ${dependent.joinToString { "#${it.id}" }}", color = PoliceColors.Error)
            }
        } },
        confirmButton = { TextButton(onClick = { restoreId = null; runSave { model.timeCorrections.restore(workoutId, id) } }, enabled = !saving) { Text("Restore transfer") } },
        dismissButton = { TextButton(onClick = { restoreId = null }, enabled = !saving) { Text("Cancel") } }) }
}

@Composable
internal fun TimeReviewReport(model: SessionViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("needs_review") }
    var selectedWorkout by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSet by rememberSaveable { mutableStateOf<String?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    WorkoutEditorDialog("Time Review", "time-review-report", true, onClose, fullScreen = true, footer = {}) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Review flagged timer mistakes and corrected set durations.", Modifier.weight(1f))
            IconButton(onClick = { about = true }, Modifier.semantics { contentDescription = "About Time Review" }) {
                Box(Modifier.size(24.dp).border(1.dp, PoliceColors.LightBlue, CircleShape), contentAlignment = Alignment.Center) { Text("i") }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("needs_review" to "Needs review", "corrected" to "Corrected", "dismissed" to "Dismissed", "all" to "All").forEach { (key, label) ->
                FilterChip(filter == key, onClick = { filter = key }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val rows = state.sessions.filter { it.workout.kind == "session" }.sortedByDescending { it.workout.startedAt }.flatMap { w ->
                w.orderedSets().filter { w.confirmed(it) && w.timeReviewStatus(it) != null &&
                    (filter == "all" || w.timeReviewStatus(it) == filter) }.map { w to it }
            }
            if (rows.isEmpty()) Text("No timing entries in this view.")
            rows.forEach { (w, set) -> Surface(Modifier.fillMaxWidth().clickable { selectedWorkout = w.workout.id; selectedSet = set.id }
                .testTag("time-review-${set.id}"), shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(w.workout.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(DateFormat.getDateTimeInstance().format(Date(w.workout.startedAt)))
                    Text(w.setTimeLabel(set.id))
                    Text("Work ${sessionTime(set.activeMillis)} · Rest ${sessionTime(set.restMillis)}")
                    if (set.notes.isNotBlank()) Text(set.notes)
                    Text(when (w.timeReviewStatus(set)) { "needs_review" -> "Needs review"; "corrected" -> "Corrected"; else -> "Dismissed" }, color = PoliceColors.LightBlue)
                }
            } }
        }
    }
    if (about) WorkoutEditorDialog("About Time Review", "time-review-instructions", true, { about = false }, fullScreen = true, footer = {}) {
        Text(timeInstructions, Modifier.weight(1f).verticalScroll(rememberScrollState()))
    }
    if (selectedWorkout != null && selectedSet != null) TimeCorrectionDialog(model, selectedWorkout!!, selectedSet!!, "details") {
        selectedWorkout = null; selectedSet = null
    }
}
