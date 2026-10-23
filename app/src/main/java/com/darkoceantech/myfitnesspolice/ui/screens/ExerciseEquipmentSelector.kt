package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
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

/** Local draft: only Done changes the exercise; hidden selections keep their identities and setups. */
@Composable
internal fun ExerciseEquipmentSelector(
    configuration: EquipmentConfiguration, library: EquipmentLibraryState,
    defaultGymId: String? = null, onDismiss: () -> Unit, onChooseGym: () -> Unit, onDone: (EquipmentConfiguration) -> Unit
) {
    val original = remember { configuration }
    val originalOptions = remember { exerciseEquipmentOptions(original) }
    var selectedIds by rememberSaveable { mutableStateOf(originalOptions.map { it.id }) }
    var gymId by rememberSaveable { mutableStateOf(original.gymId ?: defaultGymId) }
    var gymExplicitlyChosen by rememberSaveable { mutableStateOf(original.gymId != null) }
    val gyms = library.gyms.filterNot { it.isArchived }
    val gym = gyms.find { it.id == gymId }
    val equipment = if (gym == null) emptyList() else
        library.equipment.filter { it.gymId == gym.id && !it.isArchived }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding()
            .testTag("equipment-selector-screen"), color = Color.Transparent, contentColor = PoliceColors.Text) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to Equipment")
                    }
                    Text("Select equipment", style = MaterialTheme.typography.titleLarge)
                }
                LibraryDropdown("Gym", gym?.name ?: "Select a gym", gyms.map { it.id to it.name },
                    true, "selector-gym") { gymId = it; gymExplicitlyChosen = true }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (gym == null) item {
                        Text("Select a gym to view its equipment.", color = PoliceColors.Muted)
                        if (gyms.isEmpty()) TextButton(onClick = onChooseGym) { Text("Create a gym in the Gym tab") }
                    } else if (equipment.isEmpty()) item {
                        Text("No equipment assigned to this gym.", color = PoliceColors.Muted)
                    }
                    items(equipment, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().toggleable(item.id in selectedIds, role = Role.Checkbox,
                            onValueChange = { checked ->
                                selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                            }).testTag("exercise-equipment-option-" + item.id).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(item.id in selectedIds, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(item.type, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            }
                        }
                    }
                }
                HorizontalDivider(color = PoliceColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("cancel-equipment-selection")) {
                        Text("Cancel")
                    }
                    PoliceButton(onClick = {
                        val next = originalOptions.filter { it.id in selectedIds } +
                            library.equipment.filter { it.id in selectedIds && it.id !in originalOptions.map { e -> e.id } }
                                .map(library::identity)
                        val invalidDefault = original.equipment.any { it.id !in selectedIds }
                        val base = if (invalidDefault) original.copy(equipment = emptyList(), positions = emptyList(),
                            setupId = null, setupName = "") else original
                        onDone(base.copy(compatibleEquipment = next,
                            equipmentSetups = original.equipmentSetups.filter { setup ->
                                setup.equipment.all { it.id in selectedIds }
                            },
                            gymId = if (gymExplicitlyChosen) gym?.id else original.gymId,
                            gymName = if (gymExplicitlyChosen) gym?.name.orEmpty() else original.gymName))
                    }, enabled = gym != null, modifier = Modifier.weight(1f).testTag("done-equipment-selection")) {
                        Text("Done")
                    }
                }
            }
        }
    }
}