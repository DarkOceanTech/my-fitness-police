package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

@Composable
internal fun MobilitySessionShortcut(session: StretchSessionRow, model: StretchViewModel, onOpen: () -> Unit) {
    val now by model.now.collectAsStateWithLifecycle()
    val routine = remember(session.snapshot) { StretchJson.routine(session.snapshot) }
    val run = StretchTiming.advance(StretchJson.run(session.progress), now)
    val phase = run.phases.getOrNull(run.index)
    Surface(onClick = onOpen, color = PoliceColors.Raised, contentColor = PoliceColors.Text,
        modifier = Modifier.fillMaxWidth().testTag("mobility-session-shortcut")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(androidx.compose.ui.res.painterResource(com.darkoceantech.myfitnesspolice.R.drawable.ic_timer),
                null, tint = PoliceColors.LightBlue, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(if (run.status == "paused") "Mobility · Paused" else "Mobility · ${phase?.kind ?: "Complete"} · ${stretchTime(((phase?.plannedMillis ?: 0) - run.elapsed).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            Text("→", color = PoliceColors.LightBlue, fontSize = 22.sp)
        }
    }
}

@Composable
internal fun ActiveStretchScreen(session: StretchSessionRow, now: Long, action: SessionAction, preferences: StretchPreferences,
    onBack: () -> Unit, onControl: (String, String?) -> Unit) {
    val routine = remember(session.snapshot) { StretchJson.routine(session.snapshot) }
    val run = remember(session.progress, now) { StretchTiming.advance(StretchJson.run(session.progress), now) }
    val phase = run.phases.getOrNull(run.index)
    val entry = routine.stretches.firstOrNull { it.id == phase?.entryId }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(session.notes) }
    val preparing = phase?.kind == "PREPARE"
    val planned = run.phases.sumOf { it.plannedMillis }
    val remaining = ((phase?.plannedMillis ?: 0) - run.elapsed).coerceAtLeast(0)
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().testTag("active-stretch-session")) {
        SectionPageHeader(routine.name, onBack = onBack, backLabel = "Back to Mobility Tracker") {
            if (preparing) TextButton(onClick = { onControl("cancel", null) }, enabled = !action.saving,
                modifier = Modifier.testTag("cancel-stretch-preparation")) { Text("Cancel") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("active-stretch-content"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { StretchPanel {
                val clock: @Composable () -> Unit = {
                    Text(if (preparing) "GET READY" else when {
                        phase?.kind == "WORK" && phase.side.isNotEmpty() -> phase.side.uppercase() + " SIDE"
                        else -> phase?.kind ?: "COMPLETE"
                    }, style = MaterialTheme.typography.titleLarge, color = if (phase?.kind == "WORK") PoliceColors.Red else PoliceColors.LightBlue,
                        modifier = Modifier.testTag("stretch-phase"))
                    Text(ceil(remaining / 1000.0).toInt().toString(), style = StatTypography.copy(fontSize = if (landscape) 56.sp else 72.sp),
                        modifier = Modifier.testTag("stretch-countdown"))
                    if (run.status == "paused") Text("PAUSED", color = PoliceColors.LightBlue)
                }
                val details: @Composable () -> Unit = {
                    Text(entry?.movement?.name ?: "Five seconds to prepare. You can cancel before stretching starts.",
                        style = if (landscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                    val set = entry?.sets?.indexOfFirst { it.id == phase?.setId }?.plus(1)
                    Text(if (entry != null) "Set $set of ${entry.sets.size} · ${StretchTiming.completedSets(run)} / ${routine.stretches.sumOf { it.sets.size }} sets completed" else "Preparation countdown",
                        color = PoliceColors.Muted)
                    LinearProgressIndicator(progress = { if (planned == 0L) 0f else ((run.phases.take(run.index).sumOf { it.plannedMillis } + run.elapsed).toFloat() / planned).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = PoliceColors.Blue, trackColor = PoliceColors.Raised)
                    if (entry != null) {
                        if (entry.movement.instructions.isNotBlank()) Text(entry.movement.instructions,
                            style = if (landscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                        if (entry.note.isNotBlank()) Text(entry.note, color = PoliceColors.LightBlue)
                        if (entry.movement.separateSides) Text("Work per side · starts on ${entry.startingSide}", color = PoliceColors.Muted)
                    }
                }
                if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(.35f), verticalArrangement = Arrangement.spacedBy(6.dp)) { clock() }
                    Column(Modifier.weight(.65f), verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
                } else { clock(); details() }
            } }
            item { StretchPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("WORK", "REST", "SWITCH SIDES").forEach { kind ->
                        Column(Modifier.weight(1f)) { Text(kind, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted); Text(stretchTime(StretchTiming.actual(run, kind)), style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Text("Sound ${if (preferences.sound) "on" else "off"} · Vibration ${if (preferences.vibration) "on" else "off"}", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PoliceOutlinedButton(onClick = { onControl(if (run.status == "paused") "resume" else "pause", null) }, enabled = !action.saving,
                        modifier = Modifier.weight(1f).testTag("pause-stretch")) { Text(if (run.status == "paused") "Resume" else "Pause") }
                    PoliceOutlinedButton(onClick = { onControl("skip", null) }, enabled = !action.saving && !preparing,
                        modifier = Modifier.weight(1f).testTag("skip-stretch-phase")) { Text("Skip phase") }
                }
                PoliceOutlinedButton(onClick = { onControl("next", null) }, enabled = !action.saving && !preparing,
                    modifier = Modifier.fillMaxWidth()) { Text("Next stretch") }
                PoliceButton(onClick = { if (preparing) onControl("cancel", null) else finishing = true }, enabled = !action.saving,
                    modifier = Modifier.fillMaxWidth().testTag("finish-stretch")) { Text(if (preparing) "Cancel countdown" else "Finish routine") }
                action.error?.let { Text(it, color = PoliceColors.Error) }
            } }
        }
    }
    if (finishing) AlertDialog(onDismissRequest = { if (!action.saving) finishing = false }, title = { Text("Finish routine early?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your performed time will be saved. Remaining phases will not count as completed. You can Pause instead and come back later.")
            OutlinedTextField(notes, { notes = it }, label = { Text("Session notes (optional)") })
        } },
        confirmButton = { TextButton(onClick = { onControl("finish", notes); finishing = false }, enabled = !action.saving) { Text("Finish routine") } },
        dismissButton = { TextButton(onClick = { finishing = false }) { Text("Continue routine") } })
}

@Composable
internal fun StretchSessionList(sessions: List<StretchSessionRow>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("stretch-session-log"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (sessions.isEmpty()) item { Text("No routine sessions yet. Complete a routine to start your session log.", color = PoliceColors.Muted) }
        sessions.forEach { session -> item(key = session.id) {
            val routine = StretchJson.routine(session.snapshot)
            val run = StretchJson.run(session.progress)
            Surface(onClick = { onOpen(session.id) }, shape = PoliceCardShape, color = PoliceColors.Card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(routine.name, style = MaterialTheme.typography.titleLarge)
                    Text(DateFormat.getDateTimeInstance().format(Date(session.startedAt)), color = PoliceColors.Muted)
                    Text(stretchStatus(run), color = PoliceColors.LightBlue)
                    Text("${StretchTiming.completedSets(run)} completed sets", color = PoliceColors.Muted)
                }
            }
        } }
    }
}

internal fun stretchStatus(run: StretchRun) = when {
    run.status == "completed" && run.results.any { it.skipped } -> "Completed with skipped phases"
    run.status == "completed" -> "Completed"
    run.status == "finished early" -> "Finished early"
    run.status == "paused" -> "Paused"
    else -> "In progress"
}

@Composable
internal fun StretchSessionDetails(session: StretchSessionRow, onBack: () -> Unit, onNote: (String) -> Unit, action: SessionAction) {
    val routine = remember(session.snapshot) { StretchJson.routine(session.snapshot) }
    val run = StretchJson.run(session.progress)
    var editingNote by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("stretch-session-details")) {
        SectionPageHeader("Session details", onBack = onBack, backLabel = "Back to Session log")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { StretchPanel {
                Text(routine.name, style = MaterialTheme.typography.titleLarge)
                Text(DateFormat.getDateTimeInstance().format(Date(session.startedAt)), color = PoliceColors.Muted)
                session.endedAt?.let { Text("Ended " + DateFormat.getDateTimeInstance().format(Date(it)), color = PoliceColors.Muted) }
                Text(stretchStatus(run), color = PoliceColors.LightBlue)
                Text("${StretchTiming.completedSets(run)} / ${routine.stretches.sumOf { it.sets.size }} sets completed")
                SirenRule(Modifier.fillMaxWidth())
                listOf("Work" to "WORK", "Rest" to "REST", "Switch sides" to "SWITCH SIDES", "Preparation" to "PREPARE").forEach { (label, kind) ->
                    Row { Text(label, Modifier.weight(1f)); Text(stretchTime(StretchTiming.actual(run, kind))) }
                }
                Text("Total elapsed: " + stretchTime(run.results.sumOf { it.actualMillis } + if (run.status in listOf("running", "paused")) run.elapsed else 0))
                Text("Paused time excluded. Preparation is shown separately.", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            } }
            routine.stretches.forEach { entry -> item(key = entry.id) {
                StretchPanel {
                    Text(entry.movement.name, style = MaterialTheme.typography.titleLarge)
                    Text(if (entry.movement.separateSides) "Separate sides · starts on ${entry.startingSide}" else "Both sides together", color = PoliceColors.Muted)
                    if (entry.note.isNotBlank()) Text(entry.note)
                    entry.sets.forEachIndexed { index, set ->
                        Text("Set ${index + 1} · ${set.work}s ${if (entry.movement.separateSides) "per side" else "work"} · ${set.rest}s rest · ${if (entry.movement.separateSides) "${set.switch}s switch" else "no side transition"}",
                            style = MaterialTheme.typography.titleSmall)
                        run.phases.filter { it.setId == set.id }.forEach { phase ->
                            val result = run.results.firstOrNull { it.phase == phase }
                            Text("${phase.side.ifBlank { phase.kind }} · planned ${stretchTime(phase.plannedMillis)} · actual ${stretchTime(result?.actualMillis ?: 0)} · ${if (result?.skipped == true) "Skipped" else if (result != null) "Completed" else "Not completed"}",
                                color = if (result?.skipped == true) PoliceColors.Error else PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } }
            item { StretchPanel {
                Text("Session notes", color = PoliceColors.LightBlue)
                Text(session.notes.ifBlank { "Add a session note" }, Modifier.fillMaxWidth().testTag("stretch-session-note").then(
                    Modifier.clickable(enabled = !action.saving) { editingNote = true }).padding(12.dp))
                action.error?.let { Text(it, color = PoliceColors.Error) }
            } }
        }
    }
    if (editingNote) StretchTextNote("Session notes", session.notes, onCancel = { editingNote = false },
        onSave = { onNote(it); editingNote = false })
}
