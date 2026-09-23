package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlin.math.roundToInt

/** Interactive preview only: no settings are written to storage or applied to sessions. */
@Composable
fun DemoSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var goalNote by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("Build strength") }
    var units by rememberSaveable { mutableStateOf("Pounds (lbs)") }
    var weekStart by rememberSaveable { mutableStateOf("Monday") }
    var reminders by rememberSaveable { mutableStateOf(true) }
    var sounds by rememberSaveable { mutableStateOf(true) }
    var keepAwake by rememberSaveable { mutableStateOf(true) }
    var rest by rememberSaveable { mutableFloatStateOf(90f) }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().testTag("demo-settings")) {
        SectionPageHeader("Settings", onBack = onBack, backLabel = "Back to Dispatch")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp)
            .testTag("settings-scroll"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("DEMO PREVIEW", color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelLarge)
            Text("Try the controls. These examples won’t change your workouts or save preferences.",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
            SettingsSection("Officer profile") {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") },
                    placeholder = { Text("Your callsign") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings-name"))
                OutlinedTextField(goalNote, { goalNote = it }, label = { Text("Personal training goal") },
                    placeholder = { Text("What are you working toward?") }, minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("settings-goal-note"))
            }
            SettingsSection("Training preferences") {
                Text("Primary focus", style = MaterialTheme.typography.labelLarge, color = PoliceColors.Muted)
                Column(Modifier.selectableGroup()) {
                    listOf("Build strength", "Build muscle", "Improve endurance").forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(goal == option, role = Role.RadioButton, onClick = { goal = option }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = goal == option, onClick = null)
                            Text(option, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                DemoSettingsDropdown("Weight units", units, listOf("Pounds (lbs)", "Kilograms (kg)")) { units = it }
                DemoSettingsDropdown("Week starts on", weekStart, listOf("Monday", "Sunday")) { weekStart = it }
                Text("Default rest · ${rest.roundToInt()} seconds", style = MaterialTheme.typography.bodyMedium)
                Slider(rest, { rest = it }, valueRange = 30f..180f, steps = 9, modifier = Modifier.testTag("settings-rest"))
            }
            SettingsSection("Reminders and workout tools") {
                SettingsSwitch("Workout reminders", "A nudge to report for training.", reminders) { reminders = it }
                SettingsSwitch("Timer sounds", "Hear when a rest period ends.", sounds) { sounds = it }
                SettingsSwitch("Keep screen awake", "Keep set controls visible while training.", keepAwake) { keepAwake = it }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
        border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            SirenRule(Modifier.fillMaxWidth())
            content()
        }
    }
}

@Composable
private fun DemoSettingsDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = PoliceColors.Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, Modifier.weight(1f)); Text("▾")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
        Switch(checked, onCheckedChange = onChange, modifier = Modifier.testTag("settings-$title"))
    }
}
