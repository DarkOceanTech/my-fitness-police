package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

internal val EquipmentConfigurationSaver = androidx.compose.runtime.saveable.Saver<EquipmentConfiguration, String>(
    save = { EquipmentConfigurationConverters().encode(it)!! }, restore = { EquipmentConfigurationConverters().decode(it)!! })

@Composable internal fun LibraryDropdown(label: String, value: String, options: List<Pair<String, String>>,
    enabled: Boolean = true, tag: String = label, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag),
                contentPadding = PaddingValues(12.dp)) { Text(value.ifBlank { "Choose…" }) }
            DropdownMenu(open, { open = false }) {
                options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) },
                    onClick = { open = false; onSelect(key) }, modifier = Modifier.testTag("$tag-option-$key")) }
            }
        }
    }
}

@Composable internal fun EquipmentConfigurationFields(configuration: EquipmentConfiguration,
    enabled: Boolean, allowManage: Boolean = true, onChange: (EquipmentConfiguration) -> Unit) {
    val model = LocalEquipmentLibraryModel.current ?: return
    val library by model.library.collectAsStateWithLifecycle()
    val gymId = configuration.gymId.orEmpty()
    var manage by rememberSaveable { mutableStateOf(false) }
    val available = library.equipment.filter { !it.isArchived && (gymId.isBlank() || it.gymId == gymId) &&
        (it.gymId == null || library.gyms.any { g -> g.id == it.gymId && !g.isArchived }) }
    val selectedIds = configuration.equipment.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibraryDropdown("Gym", library.gyms.find { it.id == gymId }?.name ?: "All gyms / portable equipment",
            listOf("" to "All gyms / portable equipment") + library.gyms.filterNot { it.isArchived }.map { it.id to it.name },
            enabled, "equipment-gym") { id ->
            val equipment = configuration.equipment.filter { id.isBlank() || it.gymId == null || it.gymId == id }
            val changed = equipment != configuration.equipment
            onChange(configuration.copy(gymId = id.ifBlank { null }, gymName = library.gyms.find { it.id == id }?.name.orEmpty(),
                equipment = equipment, positions = if (changed) emptyList() else configuration.positions,
                setupId = if (changed) null else configuration.setupId, setupName = if (changed) "" else configuration.setupName))
        }
        LibraryDropdown("Equipment (select one or more)", "Select equipment",
            available.map { it.id to ((if (it.id in selectedIds) "✓ " else "") + it.name + " · " + it.type) }, enabled, "equipment-selector") { id ->
            val e = available.single { it.id == id }
            val next = if (id in selectedIds) configuration.equipment.filterNot { it.id == id }
                else configuration.equipment + library.identity(e)
            // Changing equipment invalidates positions; make the reset explicit in the UI.
            onChange(configuration.copy(equipment = next, positions = emptyList(), setupId = null, setupName = "",
                gymId = configuration.gymId ?: e.gymId, gymName = configuration.gymName.ifBlank { library.gyms.find { it.id == e.gymId }?.name.orEmpty() }))
        }
        configuration.equipment.forEach { e ->
            Text(e.name + listOf(e.makeModel, e.gymName).filter { it.isNotBlank() }.joinToString(" · ", prefix = " · "),
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue)
        }
        Text("Changing equipment clears its positions. Select a compatible setup afterward.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        val compatible = library.setups.filter { setup -> !setup.isArchived &&
            (configuration.equipment.isEmpty() || (setup.configuration.equipment.isNotEmpty() &&
                setup.configuration.equipment.map { it.id }.toSet() == selectedIds) ||
                (setup.configuration.equipment.isEmpty() && configuration.equipment.all { it.type.equals(setup.compatibleType, true) })) }
        LibraryDropdown("Saved setup", configuration.setupName.ifBlank { "Custom / no named setup" },
            listOf("" to "Custom / no named setup") + compatible.map { it.id to it.name }, enabled, "equipment-setup") { id ->
            if (id.isBlank()) onChange(configuration.copy(setupId = null, setupName = ""))
            else {
                val setup = compatible.single { it.id == id }
                onChange(setup.configuration.copy(equipment = if (setup.configuration.equipment.isEmpty()) configuration.equipment else
                    setup.configuration.equipment.map { old -> library.equipment.find { it.id == old.id }?.let(library::identity) ?: old },
                    gymId = setup.configuration.gymId ?: configuration.gymId, gymName = setup.configuration.gymName.ifBlank { configuration.gymName }))
            }
        }
        if (allowManage) OutlinedButton(onClick = { model.reset(); manage = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("manage-equipment-library")) {
            Text("Manage gyms, equipment & setups")
        }
    }
    if (manage) EquipmentLibraryDialog(model) { manage = false; model.reset() }
}

@Composable internal fun EquipmentPositionFields(config: EquipmentConfiguration, enabled: Boolean, onChange: (EquipmentConfiguration) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        config.positions.forEachIndexed { index, p ->
            Surface(color = PoliceColors.Card, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text("POSITION " + (index + 1), Modifier.weight(1f), color = PoliceColors.Muted)
                        TextButton(onClick = { onChange(config.copy(positions = config.positions.filterIndexed { i, _ -> i != index })) }, enabled = enabled,
                            modifier = Modifier.testTag("remove-position-$index")) { Text("Remove", color = PoliceColors.Error) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(p.name, { value -> onChange(config.copy(positions = config.positions.mapIndexed { i, old -> if (i == index) old.copy(name = value) else old })) },
                            label = { Text("Name") }, enabled = enabled, modifier = Modifier.weight(1f).testTag("position-name-$index"))
                        OutlinedTextField(p.position, { value -> onChange(config.copy(positions = config.positions.mapIndexed { i, old -> if (i == index) old.copy(position = value) else old })) },
                            label = { Text("Position") }, enabled = enabled, modifier = Modifier.weight(1f).testTag("position-value-$index"))
                    }
                }
            }
        }
        OutlinedButton(onClick = { onChange(config.copy(positions = config.positions + EquipmentPosition("", ""))) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("add-equipment-position")) { Text("+ Add position") }
    }
}

@Composable internal fun EquipmentConfigurationEditor(initial: EquipmentConfiguration?, title: String, session: Boolean = false,
    allowDefault: Boolean = false, allowInherit: Boolean = true, allowNamedSetup: Boolean = true, onDismiss: () -> Unit,
    onSave: (EquipmentConfiguration?, Boolean, String, String) -> Unit,
    action: SessionAction, screenTag: String = "equipment-position-editor") {
    var config by rememberSaveable(stateSaver = EquipmentConfigurationSaver) { mutableStateOf(initial ?: EquipmentConfiguration()) }
    var scope by rememberSaveable { mutableStateOf(if (session) "session" else "entry") }
    var named by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var inherit by rememberSaveable { mutableStateOf(false) }
    WorkoutEditorDialog(title, screenTag, !action.saving, onDismiss, fullScreen = true, footer = {
        OutlinedButton(onClick = onDismiss, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { onSave(if (inherit && scope != "default") null else config, scope == "default", if (inherit && scope != "default") "" else named, type) }, enabled = !action.saving,
            modifier = Modifier.weight(1f).testTag("save-equipment-positions")) { Text(if (action.saving) "Saving…" else "Save") }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("equipment-configuration-form"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (session) "Changes apply to this session only. Reusable setups and other sessions remain independent." else
                "Choose equipment and reuse a setup, or record positions for this entry.", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            if (allowDefault) {
                LibraryDropdown("Save destination", if (scope == "default") "Exercise default" else "This workout exercise",
                    listOf("entry" to "This workout exercise", "default" to "Exercise default"), !action.saving, "equipment-save-scope") { scope = it }
                if (scope == "default") Text("This updates the library default. Workout Save/Discard does not undo library changes.", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (allowInherit && !session && scope != "default") Row {
                Checkbox(inherit, { inherit = it }, enabled = !action.saving)
                Text("Inherit exercise default", Modifier.padding(top = 12.dp))
            }
            if (!inherit) {
                EquipmentConfigurationFields(config, !action.saving) { config = it }
                OutlinedButton(onClick = { config = EquipmentConfiguration(legacyDescription = config.legacyDescription); named = "" },
                    enabled = !action.saving, modifier = Modifier.testTag("clear-equipment-setup")) { Text("Use no equipment / setup") }
                EquipmentPositionFields(config, !action.saving) { config = it }
                if (!session && allowNamedSetup) {
                    OutlinedTextField(named, { named = it }, label = { Text("Save a new reusable setup (optional name)") }, modifier = Modifier.fillMaxWidth().testTag("new-setup-name"), enabled = !action.saving)
                    if (named.isNotBlank() && config.equipment.isEmpty()) OutlinedTextField(type, { type = it }, label = { Text("Compatible equipment type") }, modifier = Modifier.fillMaxWidth(), enabled = !action.saving)
                }
            }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}

@Composable internal fun EquipmentLibraryDialog(model: EquipmentLibraryViewModel, onDismiss: () -> Unit) {
    val library by model.library.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableIntStateOf(0) }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var make by rememberSaveable { mutableStateOf("") }
    var gym by rememberSaveable { mutableStateOf("") }
    var config by rememberSaveable(stateSaver = EquipmentConfigurationSaver) { mutableStateOf(EquipmentConfiguration()) }
    val revision = remember { action.revision }
    LaunchedEffect(action.revision) { if (action.revision > revision && action.completedAction == "library") {
        id = null; name = ""; type = ""; make = ""; gym = ""; config = EquipmentConfiguration(); model.reset()
    } }
    WorkoutEditorDialog("Gym & equipment library", "equipment-library", !action.saving, onDismiss, fullScreen = true, footer = {
        OutlinedButton(onClick = onDismiss, enabled = !action.saving) { Text("Close") }
        Spacer(Modifier.weight(1f))
        PoliceButton(onClick = { when(category) {
            0 -> model.gym(id, name)
            1 -> model.equipment(id, type, name, make, gym.ifBlank { null })
            else -> model.setup(id, name, type, config)
        } }, enabled = !action.saving, modifier = Modifier.testTag("save-library-record")) { Text("Save") }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { listOf("Gyms", "Equipment", "Setups").forEachIndexed { i, title ->
                FilterChip(category == i, onClick = { category = i; id = null; name = ""; type = ""; make = ""; gym = ""; config = EquipmentConfiguration(); model.reset() },
                    label = { Text(title) }, enabled = !action.saving, modifier = Modifier.weight(1f).testTag("library-category-$i"))
            } }
            val records = when(category) {
                0 -> library.gyms.filterNot { it.isArchived }.map { it.id to it.name }
                1 -> library.equipment.filterNot { it.isArchived }.map { it.id to (it.name + " · " + it.type) }
                else -> library.setups.filterNot { it.isArchived }.map { it.id to it.name }
            }
            LibraryDropdown("Create or edit", records.find { it.first == id }?.second ?: "New record", listOf("" to "New record") + records,
                !action.saving, "library-record") { key ->
                id = key.ifBlank { null }; name = ""; type = ""; make = ""; gym = ""; config = EquipmentConfiguration()
                when(category) {
                    0 -> name = library.gyms.find { it.id == key }?.name.orEmpty()
                    1 -> library.equipment.find { it.id == key }?.let { name = it.name; type = it.type; make = it.makeModel; gym = it.gymId.orEmpty() }
                    else -> library.setups.find { it.id == key }?.let { name = it.name; type = it.compatibleType; config = it.configuration }
                }; model.reset()
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-name"))
            if (category > 0) OutlinedTextField(type, { type = it }, label = { Text(if (category == 1) "Equipment type" else "Compatible equipment type") },
                enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-type"))
            if (category == 1) {
                OutlinedTextField(make, { make = it }, label = { Text("Make / model (optional)") }, enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-model"))
                LibraryDropdown("Gym", library.gyms.find { it.id == gym }?.name ?: "Portable / no gym",
                    listOf("" to "Portable / no gym") + library.gyms.filterNot { it.isArchived }.map { it.id to it.name }, !action.saving, "library-gym") { gym = it }
            }
            if (category == 2) {
                EquipmentConfigurationFields(config, !action.saving, allowManage = false) { config = it }
                EquipmentPositionFields(config, !action.saving) { config = it }
            }
            if (id != null) TextButton(onClick = { model.archive(category, requireNotNull(id)) }, enabled = !action.saving) { Text("Archive", color = PoliceColors.Error) }
            Text("Archiving hides records from new selections and preserves recorded sessions.", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}

@Composable internal fun EquipmentConfigurationSummary(config: EquipmentConfiguration?) {
    if (config == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (config.gymName.isNotBlank()) Text("Gym: " + config.gymName, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
        config.equipment.forEach { e -> Text(listOf(e.name, e.makeModel, e.gymName).filter { it.isNotBlank() }.joinToString(" · "),
            color = PoliceColors.LightBlue, style = MaterialTheme.typography.bodySmall) }
        if (config.setupName.isNotBlank()) Text("Setup: " + config.setupName, color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
        if (config.equipment.isEmpty() && config.legacyDescription.isNotBlank()) Text(config.legacyDescription, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
        EquipmentPositionSummary(config.positions)
    }
}
