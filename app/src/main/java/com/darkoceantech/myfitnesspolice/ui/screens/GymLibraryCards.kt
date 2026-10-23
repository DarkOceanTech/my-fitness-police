package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun GymLibraryCard(gym: Gym, isDefault: Boolean, enabled: Boolean,
    onEdit: () -> Unit, onDefault: () -> Unit, onViewEquipment: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("gym-card-" + gym.id), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isDefault) Text("Default", Modifier.testTag("default-gym-" + gym.id),
                        color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
                    Text(gym.name, style = MaterialTheme.typography.headlineSmall)
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled,
                        modifier = Modifier.testTag("gym-menu-" + gym.id).semantics { contentDescription = "Options for " + gym.name }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier)
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("View equipment") },
                            onClick = { menu = false; onViewEquipment() }, enabled = enabled,
                            modifier = Modifier.testTag("view-gym-equipment-" + gym.id))
                        DropdownMenuItem(text = { Text("Edit gym") },
                            onClick = { menu = false; onEdit() }, enabled = enabled,
                            modifier = Modifier.testTag("edit-gym-" + gym.id))
                        DropdownMenuItem(text = { Text(if (isDefault) "Clear default gym" else "Set as default gym") },
                            onClick = { menu = false; onDefault() }, modifier = Modifier.testTag("gym-default-action-" + gym.id))
                    }
                }
            }
            val address = gym.compactAddressLines()
            if (address.isEmpty()) Text("No address added", color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
            else address.forEach { Text(it, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall) }

        }
    }
}

@Composable
internal fun GymEquipmentView(gym: Gym, equipment: List<Equipment>, onBack: () -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().testTag("gym-equipment-screen"),
            color = Color.Transparent, contentColor = PoliceColors.Text) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to Gym") }
                    Text(gym.name + " · Equipment", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (equipment.isEmpty()) item {
                        Text("No equipment assigned to this gym.", color = PoliceColors.Muted)
                    }
                    items(equipment, key = { it.id }) { item ->
                        Surface(Modifier.fillMaxWidth().testTag("gym-equipment-" + item.id), shape = PoliceCardShape,
                            color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.type, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                        color = PoliceColors.LightBlue)
                                    SirenRule(Modifier.width(36.dp))
                                }
                                Text(item.name, style = MaterialTheme.typography.headlineSmall)
                                if (item.makeModel.isNotBlank()) Text(item.makeModel, style = MaterialTheme.typography.bodySmall,
                                    color = PoliceColors.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}