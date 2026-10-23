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
    enabled: Boolean, allowManage: Boolean = true, selectionLabel: String = "Equipment (select one or more)", onChange: (EquipmentConfiguration) -> Unit) {
    val model = LocalEquipmentLibraryModel.current ?: return
    val library by model.library.collectAsStateWithLifecycle()
    val gymId = configuration.gymId.orEmpty()
    var manage by rememberSaveable { mutableStateOf(false) }
    val available = library.equipment.filter { !it.isArchived && (gymId.isBlank() || it.gymId == gymId) &&
        (it.gymId == null || library.gyms.any { g -> g.id == it.gymId && !g.isArchived }) }
    val selectedIds = configuration.equipment.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (configuration.equipmentSetups.isNotEmpty()) LibraryDropdown("Use equipment option",
            configuration.equipment.joinToString(" + ") { it.name }.ifBlank { "Choose an alternative" },
            configuration.equipmentSetups.map { option ->
                option.equipment.single().id to (option.equipment.single().name + " · " + option.setupName.ifBlank { "Custom setup" })
            }, enabled, "use-equipment-option") { id ->
            val selected = configuration.equipmentSetups.single { it.equipment.single().id == id }
            onChange(selected.copy(compatibleEquipment = configuration.compatibleEquipment,
                equipmentSetups = configuration.equipmentSetups))
        }
        LibraryDropdown("Gym", library.gyms.find { it.id == gymId }?.name ?: "All gyms / portable equipment",
            listOf("" to "All gyms / portable equipment") + library.gyms.filterNot { it.isArchived }.map { it.id to it.name },
            enabled, "equipment-gym") { id ->
            val equipment = configuration.equipment.filter { id.isBlank() || it.gymId == null || it.gymId == id }
            val changed = equipment != configuration.equipment
            onChange(configuration.copy(gymId = id.ifBlank { null }, gymName = library.gyms.find { it.id == id }?.name.orEmpty(),
                equipment = equipment, positions = if (changed) emptyList() else configuration.positions,
                setupId = if (changed) null else configuration.setupId, setupName = if (changed) "" else configuration.setupName))
        }
        LibraryDropdown(selectionLabel, "Select equipment",
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
                            label = { RequiredFieldLabel("Name", p.position.isNotBlank()) }, enabled = enabled, modifier = Modifier.weight(1f).testTag("position-name-$index"))
                        OutlinedTextField(p.position, { value -> onChange(config.copy(positions = config.positions.mapIndexed { i, old -> if (i == index) old.copy(position = value) else old })) },
                            label = { RequiredFieldLabel("Position", p.name.isNotBlank()) }, enabled = enabled, modifier = Modifier.weight(1f).testTag("position-value-$index"))
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
                    OutlinedTextField(named, { named = it }, label = { Text("Save a new reusable setup") }, modifier = Modifier.fillMaxWidth().testTag("new-setup-name"), enabled = !action.saving)
                    if (named.isNotBlank() && config.equipment.isEmpty()) OutlinedTextField(type, { type = it }, label = { RequiredFieldLabel("Compatible equipment type") }, modifier = Modifier.fillMaxWidth(), enabled = !action.saving)
                }
            }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}

@Composable internal fun EquipmentLibraryDialog(model: EquipmentLibraryViewModel, initialCategory: Int = 0, fixedCategory: Boolean = false, directRecord: Boolean = false, recordId: String? = null, onDismiss: () -> Unit) {
    val library by model.library.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val defaultGymId by model.defaultGymId.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableIntStateOf(initialCategory) }
    var id by rememberSaveable { mutableStateOf(recordId) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf(List(6) { "" }) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var make by rememberSaveable { mutableStateOf("") }
    var gym by rememberSaveable { mutableStateOf("") }
    var config by rememberSaveable(stateSaver = EquipmentConfigurationSaver) { mutableStateOf(EquipmentConfiguration()) }
    fun loadRecord(key: String?) {
        id = key; name = ""; type = ""; make = ""; gym = ""; config = EquipmentConfiguration(); address = List(6) { "" }
        when (category) {
            0 -> library.gyms.find { it.id == key }?.let {
                name = it.name
                address = listOf(it.addressLine1, it.addressLine2, it.city, it.region, it.postalCode, it.country)
            }
            1 -> library.equipment.find { it.id == key }?.let { name = it.name; type = it.type; make = it.makeModel; gym = it.gymId.orEmpty() }
            else -> library.setups.find { it.id == key }?.let { name = it.name; type = it.compatibleType; config = it.configuration }
        }
    }
    LaunchedEffect(library, recordId) {
        if (!loaded && (recordId == null || when (category) {
            0 -> library.gyms.any { it.id == recordId }
            1 -> library.equipment.any { it.id == recordId }
            else -> library.setups.any { it.id == recordId }
        })) { loadRecord(recordId); loaded = true }
    }
    val revision = remember { action.revision }
    LaunchedEffect(action.revision) { if (action.revision > revision && action.completedAction == "library") {
        if (directRecord) onDismiss() else loadRecord(null); model.reset()
    } }
    val title = if (fixedCategory || category == 1) (if (id == null) "Create " else "Edit ") +
        when (category) { 0 -> "gym"; 1 -> "equipment"; else -> "setup" } else "Gym & equipment library"
    WorkoutEditorDialog(title, "equipment-library", !action.saving, onDismiss, fullScreen = true, footer = {
        OutlinedButton(onClick = onDismiss, enabled = !action.saving) { Text("Close") }
        Spacer(Modifier.weight(1f))
        PoliceButton(onClick = { when(category) {
            0 -> model.gym(id, name, address)
            1 -> model.equipment(id, type, name, make, gym.ifBlank { null })
            else -> model.setup(id, name, type, config)
        } }, enabled = !action.saving, modifier = Modifier.testTag("save-library-record")) { Text("Save") }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Shared library record. Save here updates the library immediately; saving or cancelling the exercise only affects its selections.", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            if (!fixedCategory) Row { listOf("Gyms", "Equipment", "Setups").forEachIndexed { i, title ->
                FilterChip(category == i, onClick = { category = i; if (directRecord) onDismiss() else loadRecord(null); model.reset() },
                    label = { Text(title) }, enabled = !action.saving, modifier = Modifier.weight(1f).testTag("library-category-$i"))
            } }
            val records = when(category) {
                0 -> library.gyms.filterNot { it.isArchived }.map { it.id to it.name }
                1 -> library.equipment.filterNot { it.isArchived }.map { it.id to (it.name + " · " + it.type) }
                else -> library.setups.filterNot { it.isArchived }.map { it.id to it.name }
            }
            if (!directRecord && category != 1) LibraryDropdown("Create or edit",
                records.find { it.first == id }?.second ?: "New record", listOf("" to "New record") + records,
                !action.saving, "library-record") { key -> loadRecord(key.ifBlank { null }); model.reset() }
            if (!directRecord && category == 1) {
                TextButton(onClick = { loadRecord(null); model.reset() }) { Text("Create equipment") }
                records.forEach { (key, label) ->
                    TextButton(onClick = { loadRecord(key); model.reset() }) { Text("Edit " + label) }
                }
            }
            if (category == 0 && id != null) {
                if (defaultGymId == id) Text("Default gym", color = PoliceColors.LightBlue)
                TextButton(onClick = { model.setDefaultGym(if (defaultGymId == id) null else id) },
                    enabled = !action.saving) {
                    Text(if (defaultGymId == id) "Clear default gym" else "Set as default gym")
                }
            }
            RequiredFieldsHint()
            OutlinedTextField(name, { name = it }, label = { RequiredFieldLabel("Name") }, enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-name"))
            if (category == 0) address.forEachIndexed { index, value ->
                OutlinedTextField(value, { updated -> address = address.toMutableList().apply { set(index, updated) } },
                    label = { Text(listOf("Address line 1", "Address line 2", "City", "State/province/region", "Postal code", "Country")[index]) },
                    enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("gym-address-$index"))
            }
            if (category > 0) OutlinedTextField(type, { type = it }, label = { RequiredFieldLabel(if (category == 1) "Equipment type" else "Compatible equipment type", category == 1 || config.equipment.isEmpty()) },
                enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-type"))
            if (category == 1) {
                OutlinedTextField(make, { make = it }, label = { Text("Make / model") }, enabled = !action.saving, modifier = Modifier.fillMaxWidth().testTag("library-model"))
                LibraryDropdown("Gym", library.gyms.find { it.id == gym }?.name ?: "Portable / no gym",
                    listOf("" to "Portable / no gym") + library.gyms.filterNot { it.isArchived }.map { it.id to it.name }, !action.saving, "library-gym") { gym = it }
            }
            if (category == 2) {
                EquipmentConfigurationFields(config, !action.saving, allowManage = false, selectionLabel = "Equipment used by this setup") { config = it }
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
        if (config.equipmentSetups.isNotEmpty()) {
            Text("Equipment alternatives · individual setups", color = PoliceColors.Muted, style = MaterialTheme.typography.labelSmall)
            config.equipmentSetups.forEach { option ->
                Text(option.equipment.joinToString(" · ") { it.name } + " · " + option.setupName.ifBlank { "Custom setup" },
                    color = PoliceColors.LightBlue, style = MaterialTheme.typography.bodySmall)
                EquipmentPositionSummary(option.positions)
            }
        }
        EquipmentPositionSummary(config.positions)
    }
}
