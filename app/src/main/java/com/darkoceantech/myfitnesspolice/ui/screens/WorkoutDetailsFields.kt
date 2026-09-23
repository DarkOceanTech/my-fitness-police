package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.darkoceantech.myfitnesspolice.data.Workout

@Composable
fun WorkoutDetailsFields(workout: Workout?, enabled: Boolean, onChange: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(workout?.name.orEmpty(), { onChange("name", it) }, enabled = enabled,
            label = { Text("Workout name") }, placeholder = { Text("Biceps — Strength") }, maxLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("workout-name"))
        val muscles = workout?.targetMuscles.orEmpty().split(",").filter { it.isNotBlank() }
        DetailDropdown("Target muscles", muscles.joinToString(", ").ifBlank { "Select muscles" },
            (listOf("Shoulders", "Triceps", "Biceps", "Forearms", "Chest", "Back", "Lower Back", "Legs", "Quads", "Hamstrings", "Glutes", "Calves", "Abdominal") + muscles).distinct(),
            muscles.toSet(), enabled, multi = true) { muscle ->
            onChange("muscles", (if (muscle in muscles) muscles - muscle else muscles + muscle).joinToString(","))
        }
    }
}

@Composable
private fun DetailDropdown(title: String, value: String, options: List<String>, selected: Set<String>,
    enabled: Boolean, multi: Boolean = false, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = PoliceColors.Card, contentColor = PoliceColors.Text)) {
                Text(value, Modifier.weight(1f)); Text("⌄")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        leadingIcon = if (multi) { { Checkbox(checked = option in selected, onCheckedChange = null) } } else null,
                        enabled = enabled,
                        onClick = { onSelect(option); if (!multi) expanded = false })
                }
                if (multi) DropdownMenuItem(text = { Text("Done") }, onClick = { expanded = false })
            }
        }
    }
}

