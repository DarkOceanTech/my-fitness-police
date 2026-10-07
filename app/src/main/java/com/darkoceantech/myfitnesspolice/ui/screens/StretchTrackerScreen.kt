package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.json.JSONObject

internal val stretchPurposes = listOf("Morning", "Warm-up", "Cool-down", "General mobility")
internal fun stretchTime(millis: Long): String = sessionTime(millis)
internal fun stretchGroup(movement: StretchMovement) = Exercise(movement.id, movement.name, movement.equipment, primaryMuscles = movement.muscles).mainMuscleGroup()

@Composable
internal fun StretchTrackerRoute(model: StretchViewModel, onBack: () -> Unit,
    requestedSession: String? = null, onRequestHandled: () -> Unit = {}, strengthActive: Boolean = false) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var history by rememberSaveable { mutableStateOf(false) }
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
    var preferences by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var starting by rememberSaveable { mutableStateOf(false) }
    val active = state.sessions.firstOrNull { it.endedAt == null }
    LaunchedEffect(requestedSession) { requestedSession?.let { activeId = it; editor = null; history = false; onRequestHandled() } }
    LaunchedEffect(action.revision, active?.id) {
        if (starting && action.completedAction == "start-stretch" && active != null) { activeId = active.id; editor = null; starting = false }
    }
    val selected = state.sessions.firstOrNull { it.id == activeId }
    LaunchedEffect(selected?.endedAt) {
        if (selected?.endedAt != null) { history = true; historyId = selected.id; activeId = null }
    }
    fun back() {
        model.clearError()
        when { activeId != null -> activeId = null; historyId != null -> historyId = null; history -> history = false; else -> onBack() }
    }
    BackHandler(editor == null) { back() }
    if (selected != null && selected.endedAt == null) {
        val now by model.now.collectAsStateWithLifecycle()
        ActiveStretchScreen(selected, now, action, state.preferences,
            onBack = { activeId = null }, onControl = { command, note -> model.control(selected.id, command, note) })
    }
    else if (historyId != null) state.sessions.firstOrNull { it.id == historyId }?.let { session ->
        StretchSessionDetails(session, onBack = { historyId = null }, onNote = { model.note(session.id, it) }, action = action)
    }
    else Column(Modifier.fillMaxSize().testTag("stretch-tracker")) {
        SectionPageHeader(if (history) "Session log" else "Mobility Tracker", onBack = ::back, backLabel = "Back to Field Training") {
            Box {
                IconButton(onClick = { options = true }) { Text("⋮", fontSize = 26.sp) }
                DropdownMenu(options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Session log") }, onClick = { options = false; history = true })
                    DropdownMenuItem(text = { Text("Timer preferences") }, onClick = { options = false; preferences = true })
                }
            }
        }
        if (state.loading) CircularProgressIndicator(Modifier.padding(20.dp))
        else if (state.failed) Text("Could not load Mobility Tracker. Reopen the page to try again.", Modifier.padding(20.dp), color = PoliceColors.Error)
        else if (history) StretchSessionList(state.sessions, onOpen = { historyId = it })
        else {
            TabRow(tab, containerColor = PoliceColors.Card) {
                listOf("Stretches", "Routines", "Plans").forEachIndexed { index, label ->
                    Tab(tab == index, onClick = { tab = index }, text = { Text(label) }, modifier = Modifier.testTag("stretch-tab-$index"))
                }
            }
            active?.let {
                PoliceOutlinedButton(onClick = { activeId = it.id }, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("resume-stretch")) {
                    Text("Return to " + StretchJson.routine(it.snapshot).name)
                }
            }
            when (tab) {
                0 -> StretchCatalogPage(state.catalog, action, onSave = model::saveMovement)
                1 -> StretchRoutinesPage(state.routines, onCreate = { editor = "new"; model.clearError() }, onOpen = { editor = it })
                2 -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Plans", style = MaterialTheme.typography.headlineSmall)
                    SirenRule(Modifier.fillMaxWidth())
                    Text("Your next assignment: group and schedule stretch routines. Plans are coming later; you can build and start individual routines now.", color = PoliceColors.Muted)
                }
            }
        }
        action.error?.let { Text(it, Modifier.padding(16.dp), color = PoliceColors.Error) }
    }
    if (editor != null) StretchRoutineEditor(state.routines.firstOrNull { it.id == editor }, state.catalog, state.preferences,
        action, blocked = active != null || strengthActive, onCancel = { editor = null; model.clearError() },
        onSave = model::saveRoutine, onCreated = { editor = null }, onStart = { starting = true; model.start(it) })
    if (preferences) StretchPreferencesDialog(state.preferences, action, onCancel = { preferences = false }, onSave = model::preferences)
}

@Composable
internal fun StretchCatalogPage(catalog: List<StretchMovement>, action: SessionAction, onSave: (StretchMovement) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroups by rememberSaveable { mutableStateOf(MuscleGroup.entries.map { it.name }) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action.revision) { if (submitted && action.completedAction == "save-stretch") { editing = null; submitted = false } }
    LazyColumn(Modifier.fillMaxSize().testTag("stretch-catalog"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search stretches") }, modifier = Modifier.weight(1f), singleLine = true)
            PoliceButton(onClick = { editing = "new" }, modifier = Modifier.size(56.dp).testTag("create-stretch"), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 28.sp) }
        } }
        item { ExerciseMuscleFilters(selectedGroups.map { MuscleGroup.valueOf(it) }.toSet(), MuscleGroup.entries.toList(),
            onChange = { selectedGroups = it.map { g -> g.name } }) }
        items(catalog.filter { it.name.contains(query, true) && stretchGroup(it).name in selectedGroups }, key = { it.id }) { movement ->
            StretchMovementCard(movement, onInfo = { viewing = movement.id }, onEdit = { editing = movement.id })
        }
    }
    if (editing != null) StretchMovementForm(catalog.firstOrNull { it.id == editing }, action, onCancel = { editing = null },
        onSave = { submitted = true; onSave(it) })
    catalog.firstOrNull { it.id == viewing }?.let { StretchInfo(it) { viewing = null } }
}

@Composable
private fun StretchMovementCard(movement: StretchMovement, onInfo: () -> Unit, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    StretchPanel(Modifier.testTag("stretch-movement-" + movement.id)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(movement.name, style = MaterialTheme.typography.titleLarge)
                Text(movement.muscles, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 24.sp) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("View stretch info") }, onClick = { menu = false; onInfo() })
                    DropdownMenuItem(text = { Text("Edit stretch details") }, onClick = { menu = false; onEdit() })
                }
            }
        }
        Text(movement.movementType + " · " + if (movement.separateSides) "Left and right separately" else "Both sides together",
            color = PoliceColors.LightBlue, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun StretchPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun StretchRoutinesPage(routines: List<StretchRoutine>, onCreate: () -> Unit, onOpen: (String) -> Unit) {
    var purpose by rememberSaveable { mutableStateOf("All") }
    LazyColumn(Modifier.fillMaxSize().testTag("stretch-routines"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Build timed morning, warm-up and cool-down routines. Each stretch can have its own work and rest times.", Modifier.weight(1f), color = PoliceColors.Muted)
            PoliceButton(onClick = onCreate, modifier = Modifier.size(56.dp).testTag("create-stretch-routine"), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 28.sp) }
        } }
        item { TablePicker("Routine purpose filter", purpose, listOf("All") + stretchPurposes, true, Modifier.fillMaxWidth()) { purpose = it } }
        if (routines.isEmpty()) item { PoliceOutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) { Text("Create My First Routine") } }
        items(routines.filter { purpose == "All" || it.purpose == purpose }, key = { it.id }) { routine ->
            StretchPanel(Modifier.testTag("stretch-routine-" + routine.id).clickable { onOpen(routine.id) }) {
                Text(routine.name, style = MaterialTheme.typography.titleLarge)
                SuggestionChip(onClick = {}, label = { Text(routine.purpose) })
                Text("${routine.stretches.size} stretches · ${stretchTime(StretchTiming.estimate(routine))} estimated", color = PoliceColors.Muted)
                SirenRule(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun StretchMovementForm(original: StretchMovement?, action: SessionAction, onCancel: () -> Unit, onSave: (StretchMovement) -> Unit) {
    var json by rememberSaveable(original?.id) { mutableStateOf(StretchJson.movement(original ?: StretchMovement(name = "")).toString()) }
    val movement = StretchJson.movement(JSONObject(json))
    fun update(value: StretchMovement) { if (!action.saving) json = StretchJson.movement(value).toString() }
    WorkoutEditorDialog(if (original == null) "Create New Stretch" else "Edit stretch details", "stretch-form", !action.saving, onCancel, fullScreen = true,
        footer = {
            PoliceOutlinedButton(onClick = onCancel, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
            PoliceButton(onClick = { onSave(movement) }, enabled = !action.saving && movement.name.isNotBlank() && movement != original,
                modifier = Modifier.weight(1f).testTag("save-stretch")) { Text("Save") }
        }) {
        LazyColumn(Modifier.weight(1f).testTag("stretch-form-fields"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(movement.name, { update(movement.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("stretch-name")) }
            item { OutlinedTextField(movement.instructions, { update(movement.copy(instructions = it)) }, label = { Text("Instructions / description") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(movement.equipment, { update(movement.copy(equipment = it)) }, label = { Text("Equipment (No equipment is allowed)") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("Target muscles", color = PoliceColors.Muted)
                ExerciseMuscleFilters(MuscleGroup.entries.filter { movement.muscles.split(";").map(String::trim).contains(it.label) ||
                    (movement.muscles.isNotBlank() && it == stretchGroup(movement)) }.toSet(),
                    MuscleGroup.entries.toList(), onChange = { update(movement.copy(muscles = it.joinToString("; ") { g -> g.label })) })
                Text(movement.muscles.ifBlank { "None selected" }, color = PoliceColors.LightBlue)
            }
            item { StretchChoice("Movement type", movement.movementType, listOf("Held stretch", "Moving mobility")) { update(movement.copy(movementType = it)) } }
            item { StretchChoice("Side configuration", if (movement.separateSides) "Left and right separately" else "Both sides together",
                listOf("Both sides together", "Left and right separately")) { update(movement.copy(separateSides = it == "Left and right separately")) } }
            if (movement.separateSides) item { StretchChoice("Default starting side", movement.startingSide, listOf("Left", "Right")) { update(movement.copy(startingSide = it)) } }
            action.error?.let { item { Text(it, color = PoliceColors.Error) } }
        }
    }
}

@Composable
internal fun StretchChoice(label: String, value: String, values: List<String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = PoliceColors.Muted, style = MaterialTheme.typography.labelMedium)
        TablePicker(label, value, values, enabled, Modifier.fillMaxWidth(), onSelect = onChange)
    }
}

@Composable
internal fun StretchInfo(movement: StretchMovement, onClose: () -> Unit) {
    WorkoutEditorDialog("Stretch info", "stretch-info", true, onClose, fullScreen = true, footer = {
        PoliceButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(movement.name, style = MaterialTheme.typography.titleLarge) }
            listOf("Instructions" to movement.instructions, "Target muscles" to movement.muscles, "Equipment" to movement.equipment,
                "Movement type" to movement.movementType, "Sides" to if (movement.separateSides) "Separate · starts on ${movement.startingSide}" else "Both together").forEach { (label, value) ->
                item { Text(label, color = PoliceColors.LightBlue); Text(value.ifBlank { "Not specified" }) }
            }
        }
    }
}

@Composable
private fun StretchPreferencesDialog(original: StretchPreferences, action: SessionAction, onCancel: () -> Unit, onSave: (StretchPreferences) -> Unit) {
    var work by rememberSaveable { mutableIntStateOf(original.work) }
    var rest by rememberSaveable { mutableIntStateOf(original.rest) }
    var switch by rememberSaveable { mutableIntStateOf(original.switch) }
    var sound by rememberSaveable { mutableStateOf(original.sound) }
    var vibration by rememberSaveable { mutableStateOf(original.vibration) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action.revision) { if (submitted && action.completedAction == "stretch-preferences") onCancel() }
    WorkoutEditorDialog("Timer preferences", "stretch-preferences", !action.saving, onCancel, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onCancel, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { submitted = true; onSave(StretchPreferences(work = work, rest = rest, switch = switch, sound = sound, vibration = vibration)) },
            enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Save") }
    }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Starting defaults for new routines. These are editable timer defaults, not personalized training advice.", color = PoliceColors.Muted) }
            item { Text("Default work (seconds)", color = PoliceColors.Muted); StretchSeconds("Default work (seconds)", work, 1, enabled = !action.saving) { work = it } }
            item { Text("Default rest (seconds)", color = PoliceColors.Muted); StretchSeconds("Default rest (seconds)", rest, enabled = !action.saving) { rest = it } }
            item { Text("Default switch sides (seconds)", color = PoliceColors.Muted); StretchSeconds("Default switch (seconds)", switch, enabled = !action.saving) { switch = it } }
            item { StretchToggle("Sound cues", sound) { if (!action.saving) sound = it } }
            item { StretchToggle("Vibration cues", vibration) { if (!action.saving) vibration = it } }
            item { Text("Cues sound while the app is visible. The timer continues across backgrounding and screen lock.", color = PoliceColors.Muted) }
            action.error?.let { item { Text(it, color = PoliceColors.Error) } }
        }
    }
}

@Composable
internal fun StretchSeconds(label: String, value: Int, min: Int = 0, enabled: Boolean = true, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val options = remember(min) { (min..3600).map { it.toString() } }
    Column(modifier) { TablePicker(label, value.toString(), options, enabled,
        label = { "$it s" }, onSelect = { onChange(it.toInt()) }) }
}

@Composable
internal fun StretchToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onCheckedChange = onChange)
    }
}
