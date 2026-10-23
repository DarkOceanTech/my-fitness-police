package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun ExerciseSetupSelector(current: EquipmentConfiguration, equipment: EquipmentIdentity,
    library: EquipmentLibraryState, onDismiss: () -> Unit, onDone: (EquipmentConfiguration) -> Unit) {
    val original = remember { current }
    var selectedId by rememberSaveable { mutableStateOf(original.setupId) }
    val available = compatibleExerciseSetups(EquipmentConfiguration(equipment = listOf(equipment)), library)
        .filter { it.configuration.equipment.size <= 1 }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().testTag("setup-selector-screen"),
            color = Color.Transparent, contentColor = PoliceColors.Text) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to Setups")
                    }
                    Text("Select setup", style = MaterialTheme.typography.titleLarge)
                }
                Text(equipment.name, color = PoliceColors.LightBlue)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SetupChoice("No named setup", "Keep custom positions for this exercise", selectedId == null,
                            "setup-choice-none") { selectedId = null }
                    }
                    if (original.setupId != null && available.none { it.id == original.setupId }) item {
                        SetupChoice(original.setupName.ifBlank { "Current saved setup" },
                            "Current assignment · unavailable in the shared library", selectedId == original.setupId,
                            "setup-choice-current") { selectedId = original.setupId }
                    }
                    if (available.isEmpty()) item { Text("No compatible saved setups. Create a setup from the Setups tab.",
                        color = PoliceColors.Muted) }
                    items(available, key = { it.id }) { setup ->
                        val association = setup.configuration.equipment.joinToString(" · ") { saved ->
                            library.equipment.find { it.id == saved.id }?.name ?: saved.name
                        }.ifBlank { "Equipment type: " + setup.compatibleType }
                        SetupChoice(setup.name, association, selectedId == setup.id,
                            "setup-choice-" + setup.id) { selectedId = setup.id }
                    }
                }
                HorizontalDivider(color = PoliceColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("cancel-setup-selection")) { Text("Cancel") }
                    PoliceButton(onClick = {
                        val next = when {
                            selectedId == original.setupId -> original
                            selectedId == null -> original.copy(setupId = null, setupName = "")
                            else -> available.find { it.id == selectedId }?.configuration ?: original
                        }
                        onDone(next)
                    }, modifier = Modifier.weight(1f).testTag("done-setup-selection")) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SetupChoice(name: String, equipment: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .testTag(tag).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(equipment, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
    }
}