package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfitnesspolice.data.WorkoutSet
import com.example.myfitnesspolice.ui.theme.*
import java.math.BigDecimal

@Composable
internal fun RecordedSetInfoPanel(
    exerciseName: String, set: WorkoutSet, action: SessionAction, modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onSave: (String?, String, String, Int?) -> Unit, onSaveNote: (String) -> Unit,
    totals: @Composable () -> Unit,
    tagPrefix: String = "history", correctionAction: String = "correct-history-set",
    noteAction: String = "save-history-set-note", restMillis: Long = set.restMillis,
) {
    val originalWeight = sessionPounds(set.weightGrams)
    val originalActual = (set.actualReps ?: set.reps).toString()
    var editing by rememberSaveable(set.id) { mutableStateOf(false) }
    var weight by rememberSaveable(set.id) { mutableStateOf(originalWeight) }
    var planned by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var actual by rememberSaveable(set.id) { mutableStateOf(originalActual) }
    var rpe by rememberSaveable(set.id) { mutableStateOf(set.rpe) }
    var noteDialog by rememberSaveable(set.id) { mutableStateOf(false) }
    var note by rememberSaveable(set.id) { mutableStateOf(set.notes) }
    var revision by rememberSaveable(set.id) { mutableIntStateOf(action.revision) }
    val dirty = editing && (weight != originalWeight || planned != set.reps.toString() || actual != originalActual || rpe != set.rpe)
    fun resetDraft() {
        weight = originalWeight; planned = set.reps.toString(); actual = originalActual; rpe = set.rpe
    }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            when (action.completedAction) {
                correctionAction -> editing = false
                noteAction -> noteDialog = false
            }
            revision = action.revision
        }
    }
    val weights = remember(originalWeight) {
        ((0..600).map { BigDecimal.valueOf(it * 25L, 1).stripTrailingZeros().toPlainString() } + originalWeight)
            .distinct().sortedBy { it.toBigDecimal() }
    }
    val plannedOptions = remember(set.reps) { ((1..500).map { it.toString() } + set.reps.toString()).distinct().sortedBy { it.toInt() } }
    val actualOptions = remember(originalActual) { ((0..500).map { it.toString() } + originalActual).distinct().sortedBy { it.toInt() } }
    val rpeOptions = remember { (1..10).map { it.toString() } }
    Column(modifier.fillMaxSize().testTag("$tagPrefix-set-info-panel")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("$tagPrefix-set-info-scroll")
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            totals()
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (set.isWarmup) "Warmup Set" else "Working Set", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, color = PoliceColors.Text)
                        if (editing) TextButton(onClick = {
                            resetDraft(); editing = false; onEdit()
                        }, enabled = !action.saving, contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("cancel-$tagPrefix-set")) {
                            Text("Cancel")
                        }
                        PoliceButton(onClick = {
                            if (dirty) onSave(weight.takeIf { it != originalWeight }, planned, actual, rpe)
                            else {
                                onEdit(); resetDraft(); editing = true
                            }
                        }, enabled = !action.saving && (!editing || dirty), shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)
                                .testTag(if (dirty) "save-$tagPrefix-set" else "edit-$tagPrefix-set")) {
                            Text(if (action.saving && dirty) "Saving…" else if (dirty) "Save" else "Edit")
                        }
                    }
                    SirenRule(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordedNumberField("Weight (Lbs)", if (editing) weight else originalWeight, weights, editing,
                            "$tagPrefix-set-weight", Modifier.weight(1f), enabled = !action.saving) { weight = it; onEdit() }
                        RecordedNumberField("RPE", (if (editing) rpe else set.rpe)?.toString() ?: "—", rpeOptions,
                            editing, "$tagPrefix-set-rpe", Modifier.weight(1f), enabled = !action.saving) { rpe = it.toInt(); onEdit() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordedNumberField("Planned reps", if (editing) planned else set.reps.toString(), plannedOptions,
                            editing, "$tagPrefix-set-planned", Modifier.weight(1f), enabled = !action.saving) { planned = it; onEdit() }
                        RecordedNumberField("Actual reps", if (editing) actual else originalActual, actualOptions,
                            editing, "$tagPrefix-set-actual", Modifier.weight(1f), enabled = !action.saving) { actual = it; onEdit() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordedNumberField("Active", sessionTime(set.activeMillis), emptyList(), false,
                            "$tagPrefix-set-active-time", Modifier.weight(1f)) { }
                        RecordedNumberField("Break", sessionTime(restMillis), emptyList(), false,
                            "$tagPrefix-set-break-time", Modifier.weight(1f)) { }
                    }
                    if (set.modifier == "superset") Text("SUPERSET", color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set Notes", style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                    Surface(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("$tagPrefix-set-note")
                        .clickable(enabled = !action.saving, onClickLabel = "Edit set note") {
                            onEdit(); note = set.notes; noteDialog = true
                        }, shape = RoundedCornerShape(10.dp), color = PoliceColors.Background,
                        border = BorderStroke(1.dp, PoliceColors.Border)) {
                        Text(set.notes.ifBlank { "Add a note about this set." }, modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (set.notes.isBlank()) PoliceColors.Muted else PoliceColors.Text,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // TODO: Add more recorded-set statistics and analysis here when defined.
            if (!noteDialog) action.error?.let { Text(it, color = PoliceColors.Error) }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (noteDialog) AlertDialog(onDismissRequest = { if (!action.saving) noteDialog = false },
        title = { Text("Set note") }, containerColor = PoliceColors.Card,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exerciseName, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            OutlinedTextField(note, { note = it; onEdit() }, label = { Text("Set notes") }, minLines = 3, maxLines = 6,
                enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-set-note-input"))
            action.error?.let { Text(it, color = PoliceColors.Error) }
        } },
        confirmButton = { TextButton(onClick = { onSaveNote(note) }, enabled = !action.saving,
            modifier = Modifier.testTag("save-$tagPrefix-set-note")) { Text("Save") } },
        dismissButton = { TextButton(onClick = { noteDialog = false }, enabled = !action.saving) { Text("Cancel") } })
}

@Composable
private fun RecordedNumberField(label: String, value: String, options: List<String>, editable: Boolean,
    tag: String, modifier: Modifier, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        Box {
            Surface(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
                .then(if (editable && enabled) Modifier.clickable { expanded = true } else Modifier)
                .semantics { stateDescription = if (editable) "Editable" else "Read only" },
                shape = RoundedCornerShape(10.dp), color = if (editable) PoliceColors.Background else Color.Transparent,
                border = if (editable) BorderStroke(1.dp, PoliceColors.LightBlue) else null) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = StatTypography.copy(fontSize = 24.sp))
                    if (editable) Text("↕", color = PoliceColors.LightBlue)
                }
            }
            DropdownMenu(expanded && editable && enabled, onDismissRequest = { expanded = false }) {
                val scroll = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(value) - 2).coerceAtLeast(0))
                LazyColumn(Modifier.width(200.dp).height(240.dp).testTag("$tag-options"), state = scroll) {
                    items(options, key = { it }) { option ->
                        DropdownMenuItem(text = { Text(option, color = if (option == value) PoliceColors.LightBlue else PoliceColors.Text) },
                            onClick = { onSelect(option); expanded = false })
                    }
                }
            }
        }
    }
}

