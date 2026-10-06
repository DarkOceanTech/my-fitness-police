package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun TrainingScheduleDialog(planId: String, name: String, dates: List<String>, action: SessionAction,
    onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by rememberSaveable(planId) { mutableStateOf(dates.distinct().sorted()) }
    var monthText by rememberSaveable(planId) { mutableStateOf(YearMonth.now().toString()) }
    var saving by rememberSaveable(planId) { mutableStateOf(false) }
    var revision by rememberSaveable(planId) { mutableIntStateOf(action.revision) }
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            if (saving && action.completedAction == "schedule-training") onDismiss()
            revision = action.revision
        }
    }
    val changed = selected.toSet() != dates.toSet()
    val month = YearMonth.parse(monthText)
    WorkoutEditorDialog("Schedule training", "training-schedule-dialog", !action.saving, onDismiss, fullScreen = true, footer = {
        PoliceOutlinedButton(onClick = onDismiss, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { saving = true; onSave(selected) }, enabled = changed && !action.saving,
            modifier = Modifier.weight(1f).testTag("save-training-schedule")) { Text(if (action.saving) "Saving…" else "Save schedule") }
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("training-schedule-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text("Tap dates to add or remove this plan. Saved dates appear in Launchpad when they are within its 10-day window.",
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = !action.saving) {
                    Text("‹", modifier = Modifier.semantics { contentDescription = "Previous month" })
                }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = !action.saving) {
                    Text("›", modifier = Modifier.semantics { contentDescription = "Next month" })
                }
            }
            Surface(Modifier.fillMaxWidth(), color = PoliceColors.Card, shape = PoliceCardShape,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                            Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
                            }
                        }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val weeks = (offset + month.lengthOfMonth() + 6) / 7
                    repeat(weeks) { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(7) { weekday ->
                                val number = week * 7 + weekday - offset + 1
                                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                    if (number in 1..month.lengthOfMonth()) {
                                        val date = month.atDay(number)
                                        val value = date.toString()
                                        val chosen = value in selected
                                        Surface(onClick = { selected = if (chosen) selected - value else (selected + value).sorted() },
                                            enabled = !action.saving, modifier = Modifier.size(44.dp).testTag("schedule-date-$value").semantics {
                                                this.selected = chosen
                                                contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                                            }, shape = CircleShape, color = if (chosen) PoliceColors.Raised else PoliceColors.Card,
                                            border = BorderStroke(if (chosen) 2.dp else 1.dp,
                                                if (chosen) PoliceColors.Red else if (date == LocalDate.now()) PoliceColors.LightBlue else PoliceColors.Border)) {
                                            Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = if (chosen) PoliceColors.Text else PoliceColors.Muted) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} scheduled date${if (selected.size == 1) "" else "s"}", Modifier.weight(1f), color = PoliceColors.LightBlue)
                TextButton(onClick = { selected = emptyList() }, enabled = !action.saving && selected.isNotEmpty()) { Text("Clear dates") }
            }
            selected.forEach { date -> Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")),
                style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted) }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}
