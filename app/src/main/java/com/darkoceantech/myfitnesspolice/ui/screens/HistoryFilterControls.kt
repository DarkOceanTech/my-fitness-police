package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun HistoryFilterControls(history: List<WorkoutDetails>, filters: HistoryFilters, matches: Int,
    expanded: Boolean, onExpanded: (Boolean) -> Unit, onChange: (HistoryFilters) -> Unit) {
    var dates by rememberSaveable { mutableStateOf(false) }
    val exercises = remember(history) { history.flatMap { it.exercises }.filter { it.sets.any { set -> set.completedAt != null } }
        .map { HistoryFilterOption(it.exercise.id, it.exercise.name) }.distinctBy { it.id }.sortedBy { it.label.lowercase() } }
    val workouts = remember(history) { historyWorkoutOptions(history) }
    val plans = remember(history) { historyTrainingPlanOptions(history) }
    Surface(Modifier.fillMaxWidth().testTag("history-filters"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters" + if (filters.activeCount > 0) " (${filters.activeCount})" else "",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (filters.activeCount > 0) TextButton(onClick = { onChange(HistoryFilters()) },
                    modifier = Modifier.testTag("clear-history-filters")) { Text("Clear") }
                TextButton(onClick = { onExpanded(!expanded) }, modifier = Modifier.testTag("toggle-history-filters")) {
                    Text(if (expanded) "Hide ↑" else "Show ↓")
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryFilterField("Date", filters.dateWindow.label, Modifier.weight(1f), "date",
                        HistoryDateWindow.entries.map { HistoryFilterOption(it.name, it.label) },
                        selectedId = filters.dateWindow.name, allLabel = null) { value ->
                        val window = HistoryDateWindow.valueOf(value!!)
                        if (window == HistoryDateWindow.Custom) dates = true else onChange(filters.copy(dateWindow = window))
                    }
                    HistoryFilterField("Exercise", exercises.firstOrNull { it.id == filters.exerciseId }?.label
                        ?: if (filters.exerciseId == null) "All exercises" else "Unavailable exercise",
                        Modifier.weight(1f), "exercise", exercises, filters.exerciseId, "All exercises") { onChange(filters.copy(exerciseId = it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryFilterField("Workout", workouts.firstOrNull { it.id == filters.workoutId }?.label
                        ?: if (filters.workoutId == null) "All workouts" else "Unavailable workout",
                        Modifier.weight(1f), "workout", workouts, filters.workoutId, "All workouts") { onChange(filters.copy(workoutId = it)) }
                    HistoryFilterField("Plan", plans.firstOrNull { it.id == filters.trainingPlanId }?.label
                        ?: if (filters.trainingPlanId == null) "All plans" else "Unavailable plan",
                        Modifier.weight(1f), "plan", plans, filters.trainingPlanId, "All plans") { onChange(filters.copy(trainingPlanId = it)) }
                }
                if (filters.dateWindow == HistoryDateWindow.Custom && filters.startDay != null && filters.endDay != null) {
                    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    Text("${format.format(Date(filters.startDay * HISTORY_DAY_MILLIS))} – ${format.format(Date(filters.endDay * HISTORY_DAY_MILLIS))}",
                        style = MaterialTheme.typography.bodySmall, color = PoliceColors.LightBlue)
                }
                if (workouts.any { it.id == UNKNOWN_WORKOUT_SOURCE }) Text(
                    "Some older sessions don’t have their source workouts recorded. Choose ‘Workout source unavailable’ to find them.",
                    style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            }
            Text("$matches of ${history.size} sessions", style = MaterialTheme.typography.labelSmall,
                color = PoliceColors.Muted, modifier = Modifier.testTag("history-filter-count"))
        }
    }
    if (dates) HistoryDateRangeDialog(filters.startDay, filters.endDay,
        onDismiss = { dates = false }, onApply = { start, end ->
            onChange(filters.copy(dateWindow = HistoryDateWindow.Custom, startDay = start, endDay = end)); dates = false
        })
}

@Composable
private fun HistoryFilterField(label: String, value: String, modifier: Modifier, tag: String,
    options: List<HistoryFilterOption>, selectedId: String?, allLabel: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PoliceColors.Muted)
        Box {
            OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("history-filter-$tag")) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄")
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                if (allLabel != null) DropdownMenuItem(text = { Text(allLabel) },
                    modifier = Modifier.testTag("history-filter-$tag-all"), onClick = { onSelect(null); open = false })
                options.forEach { option -> DropdownMenuItem(text = { Text(option.label, color = if (option.id == selectedId) PoliceColors.LightBlue else PoliceColors.Text) },
                    modifier = Modifier.testTag("history-filter-$tag-${option.id}"), onClick = { onSelect(option.id); open = false }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDateRangeDialog(start: Long?, end: Long?, onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    fun utcMillis(day: Long?) = day?.times(HISTORY_DAY_MILLIS)
    val picker = rememberDateRangePickerState(initialSelectedStartDateMillis = utcMillis(start), initialSelectedEndDateMillis = utcMillis(end))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("history-date-range"), color = PoliceColors.Background) {
            Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Session dates", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        onApply(picker.selectedStartDateMillis!! / HISTORY_DAY_MILLIS, picker.selectedEndDateMillis!! / HISTORY_DAY_MILLIS)
                    }, enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null) { Text("Apply") }
                }
                DateRangePicker(state = picker, modifier = Modifier.weight(1f), title = null,
                    headline = null, showModeToggle = true)
            }
        }
    }
}
