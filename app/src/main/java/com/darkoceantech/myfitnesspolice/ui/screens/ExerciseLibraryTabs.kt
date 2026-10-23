package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

/** Compatible alternatives stay separate from the equipment actually used by the default setup. */
internal fun exerciseEquipmentOptions(c: EquipmentConfiguration): List<EquipmentIdentity> =
    c.compatibleEquipment.ifEmpty { c.equipment }

internal fun compatibleExerciseSetups(c: EquipmentConfiguration, library: EquipmentLibraryState): List<NamedEquipmentSetup> {
    val options = exerciseEquipmentOptions(c)
    val ids = options.map { it.id }.toSet()
    return library.setups.filter { setup ->
        !setup.isArchived && if (setup.configuration.equipment.isNotEmpty())
            setup.configuration.equipment.all { it.id in ids }
        else c.equipment.isNotEmpty() && c.equipment.all { it.type.equals(setup.compatibleType, true) }
    }
}

@Composable
internal fun ExerciseLibraryTab(tab: Int, configuration: EquipmentConfiguration, enabled: Boolean,
    editingEquipmentId: String, onEquipmentSelected: (String) -> Unit, onOpenSetup: (String) -> Unit, onOpenGym: () -> Unit,
    onChange: (EquipmentConfiguration) -> Unit) {
    val model = LocalEquipmentLibraryModel.current
    if (model == null) { Text("Equipment library is unavailable."); return }
    val library by model.library.collectAsStateWithLifecycle()
    val defaultGymId by model.defaultGymId.collectAsStateWithLifecycle()
    var viewingGymId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingLibrary by rememberSaveable { mutableStateOf(false) }
    var sharedRecordId by rememberSaveable { mutableStateOf<String?>(null) }

    var selectingSetup by rememberSaveable { mutableStateOf(false) }
    var selectingEquipment by rememberSaveable { mutableStateOf(false) }
    val options = exerciseEquipmentOptions(configuration)
    val selectedIds = options.map { it.id }.toSet()
    val category = when (tab) { 1 -> 1; 2 -> 2; else -> 0 }
    val categoryName = when (tab) { 1 -> "equipment"; 2 -> "setup"; else -> "gym" }

    when (tab) {
        1 -> {
            options.forEach { saved ->
                val equipment = library.equipment.find { it.id == saved.id }
                    ?: Equipment(saved.id, saved.type, saved.name, saved.makeModel, saved.gymId)
                Surface(Modifier.fillMaxWidth().testTag("selected-equipment-card-" + equipment.id),
                    shape = PoliceCardShape, color = PoliceColors.Card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PoliceColors.Border)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(equipment.type, Modifier.weight(1f), color = PoliceColors.LightBlue,
                                style = MaterialTheme.typography.labelMedium)
                            SirenRule(Modifier.width(36.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(equipment.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                            LibraryCardEditMenu(equipment.name, "Edit equipment", enabled,
                                "equipment-menu-" + equipment.id, "edit-equipment-" + equipment.id) {
                                model.reset(); sharedRecordId = equipment.id; editingLibrary = true
                            }
                        }
                        HorizontalDivider(color = PoliceColors.Border)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val gym = library.gyms.find { it.id == equipment.gymId }
                            if (gym != null && !gym.isArchived) {
                                Row(
                                    modifier = Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button,
                                        onClickLabel = "Select gym " + gym.name) {
                                        onChange(configuration.copy(gymId = gym.id, gymName = gym.name,
                                            compatibleEquipment = options))
                                        onOpenGym()
                                    }.padding(top = 4.dp).testTag("equipment-gym-link-" + equipment.id),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(gym.name, modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue)
                                    Text("›", modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics {},
                                        style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
                                }
                            } else Text(gym?.name ?: "No gym assigned", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            val assignedSetup = if (equipment.id in selectedIds)
                                configuration.equipmentSetups.find { it.equipment.singleOrNull()?.id == equipment.id }
                                    ?: configuration.takeIf { it.equipment.singleOrNull()?.id == equipment.id }
                                else null
                            val setupLabel = assignedSetup?.setupName?.takeIf { it.isNotBlank() }
                                ?: if (assignedSetup?.positions?.isNotEmpty() == true) "Custom setup" else null
                            Row(
                                modifier = Modifier.weight(1f).clickable(enabled = enabled && equipment.id in selectedIds,
                                    role = Role.Button, onClickLabel = "Change setup for " + equipment.name) {
                                    onOpenSetup(equipment.id)
                                }.padding(top = 4.dp).testTag("equipment-setup-link-" + equipment.id),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(setupLabel?.let { "Setup: " + it } ?: "No setup assigned",
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (setupLabel != null) PoliceColors.LightBlue else PoliceColors.Muted)
                                if (equipment.id in selectedIds) Text("›",
                                    modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics {},
                                    style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
                            }
                        }

                    }
                }
            }
            if (options.isEmpty()) Text("No equipment selected.", color = PoliceColors.Muted)
            PoliceButton(onClick = { selectingEquipment = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("select-equipment")) { Text("Select equipment") }
        }
        2 -> {
            Text("Each equipment option has its own exercise-default setup. Choose an item to configure it.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            LibraryDropdown("Equipment to configure",
                options.find { it.id == editingEquipmentId }?.name ?: "Choose equipment",
                options.map { it.id to listOf(it.name, it.gymName).filter(String::isNotBlank).joinToString(" · ") },
                enabled, "exercise-setup-equipment") { onEquipmentSelected(it) }
            val selected = options.find { it.id == editingEquipmentId }
            if (selected != null) {
                val current = configuration.equipmentSetups.find { it.equipment.singleOrNull()?.id == selected.id }
                    ?: configuration.takeIf { it.equipment.singleOrNull()?.id == selected.id }?.copy(compatibleEquipment = emptyList(), equipmentSetups = emptyList())
                    ?: EquipmentConfiguration(equipment = listOf(selected), gymId = selected.gymId, gymName = selected.gymName)
                fun changeSetup(next: EquipmentConfiguration) {
                    val copy = next.copy(equipment = listOf(selected), compatibleEquipment = emptyList(), equipmentSetups = emptyList(),
                        gymId = selected.gymId, gymName = selected.gymName)
                    val base = if (configuration.equipment.singleOrNull()?.id == selected.id) copy else configuration
                    onChange(base.copy(compatibleEquipment = options,
                        equipmentSetups = configuration.equipmentSetups.filterNot { it.equipment.singleOrNull()?.id == selected.id } + copy))
                }
                val assigned = library.setups.find { it.id == current.setupId }
                if (current.setupId != null || current.positions.isNotEmpty()) {
                    AssignedSetupCard(current.setupName.ifBlank { "Custom setup" }, selected.name,
                        "assigned-setup-" + selected.id,
                        editId = assigned?.id, enabled = enabled,
                        onEdit = { model.reset(); sharedRecordId = assigned?.id; editingLibrary = true })
                } else Text("No setup assigned.", color = PoliceColors.Muted)
                PoliceButton(onClick = { selectingSetup = true }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("select-setup")) { Text("Select setup") }
                if (selectingSetup) ExerciseSetupSelector(current, selected, library,
                    onDismiss = { selectingSetup = false },
                    onDone = { next -> changeSetup(next); selectingSetup = false })
                Text("Ownership: this exercise's default for " + selected.name + ". Positions below are saved with the exercise, not the shared setup.",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                EquipmentPositionFields(current, enabled, ::changeSetup)
            }
            if (selected == null) {
                Text(if (options.isEmpty()) "Select compatible equipment in the Equipment tab first."
                    else "Choose equipment above to select a setup.", color = PoliceColors.Muted)

            }
            if (configuration.positions.isNotEmpty() && configuration.equipment.size != 1) {
                Text("Existing default setup (preserved)", style = MaterialTheme.typography.labelMedium)
                EquipmentPositionSummary(configuration.positions)
            }
        }
        3 -> {
            library.gyms.filterNot { it.isArchived }.forEach { gym ->
                GymLibraryCard(gym, defaultGymId == gym.id, enabled,
                    onEdit = { model.reset(); sharedRecordId = gym.id; editingLibrary = true },
                    onDefault = { model.setDefaultGym(if (defaultGymId == gym.id) null else gym.id) },
                    onViewEquipment = { viewingGymId = gym.id })
            }
            if (library.gyms.none { !it.isArchived }) Text("No gyms added.", color = PoliceColors.Muted)
        }
    }
    OutlinedButton(onClick = { model.reset(); sharedRecordId = null; editingLibrary = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("edit-shared-$categoryName")) {
        Text(when (tab) { 1 -> "Create equipment"; 3 -> "Create gym"; else -> "Create setup" })
    }
    Text("Shared records save immediately in their editor. Exercise Save commits only your exercise details and selections; Cancel does not undo shared-library saves.",
        color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
    if (selectingEquipment) ExerciseEquipmentSelector(configuration, library, defaultGymId = defaultGymId,
        onDismiss = { selectingEquipment = false },
        onChooseGym = { selectingEquipment = false; onOpenGym() },
        onDone = { next -> onChange(next); selectingEquipment = false })
    viewingGymId?.let { id ->
        library.gyms.find { it.id == id }?.let { gym ->
            GymEquipmentView(gym, library.equipment.filter { it.gymId == id && !it.isArchived }) { viewingGymId = null }
        }
    }
    if (editingLibrary) EquipmentLibraryDialog(model, initialCategory = category, fixedCategory = true, directRecord = true, recordId = sharedRecordId) {
        editingLibrary = false; model.reset()
    }
}

@Composable
private fun AssignedSetupCard(title: String, equipment: String, tag: String, editId: String?,
    enabled: Boolean, onEdit: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag(tag), shape = PoliceCardShape, color = PoliceColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Setup", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = PoliceColors.LightBlue)
                SirenRule(Modifier.width(36.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (editId != null) LibraryCardEditMenu(title, "Edit setup", enabled,
                    "setup-menu-" + editId, "edit-setup-" + editId, onEdit)
            }
            HorizontalDivider(color = PoliceColors.Border)
            Text(equipment, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}

/** This menu is a sibling of selection controls, so opening or editing never toggles selection. */
@Composable
private fun LibraryCardEditMenu(name: String, label: String, enabled: Boolean,
    menuTag: String, editTag: String, onEdit: () -> Unit) {
    var expanded by remember(name) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.testTag(menuTag).semantics { contentDescription = "Options for " + name }) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(label) }, enabled = enabled,
                onClick = { expanded = false; onEdit() }, modifier = Modifier.testTag(editTag))
        }
    }
}