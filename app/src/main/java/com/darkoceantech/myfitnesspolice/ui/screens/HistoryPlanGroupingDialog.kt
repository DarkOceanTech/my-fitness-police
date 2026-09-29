package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.displayName
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun HistoryPlanGroupingDialog(model: SessionViewModel, onDismiss: () -> Unit,
    initialSelectedIds: Set<String> = emptySet(), onGrouped: () -> Unit = onDismiss) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    val completed = state.sessions.filter { it.workout.kind == "session" && it.workout.finishedAt != null }
        .sortedByDescending { it.workout.startedAt }
    var selectedIds by rememberSaveable(initialSelectedIds.sorted()) { mutableStateOf(initialSelectedIds.toList()) }
    val selected = selectedIds.filter { id -> completed.any { it.workout.id == id } }
    var newPlan by rememberSaveable { mutableStateOf(false) }
    var selectedPlan by rememberSaveable { mutableStateOf<String?>(null) }
    var planName by rememberSaveable { mutableStateOf("") }
    var planMenu by remember { mutableStateOf(false) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var revision by rememberSaveable { mutableIntStateOf(action.revision) }
    val existing = state.trainingPlans.firstOrNull { it.plan.id == selectedPlan }
    val valid = selected.isNotEmpty() && !state.loading && !state.failed &&
        if (newPlan) planName.isNotBlank() else existing != null
    LaunchedEffect(action.revision) {
        if (revision != action.revision) {
            if (requested && action.completedAction == "group-history-workouts") { requested = false; onGrouped() }
            revision = action.revision
        }
    }
    fun close() { if (!action.saving) { model.clearError(); onDismiss() } }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            action.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::close, enabled = !action.saving,
                contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.testTag("cancel-history-group")) { Text("Cancel") }
            PoliceButton(onClick = {
                requested = true
                model.groupHistoryWorkouts(selected, trainingPlanId = if (newPlan) null else existing!!.plan.id,
                    newPlanName = if (newPlan) planName.trim() else null)
            }, enabled = valid && !action.saving,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirm-history-group")) {
                Text(if (action.saving) "Grouping…" else "Group workouts", maxLines = 2)
            }
            }
        }
    }
    val heading: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = ::close, enabled = !action.saving) {
                Icon(painterResource(R.drawable.ic_back), "Close workout grouping")
            }
            Text("Group workouts into plan", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
    }
    val body: @Composable () -> Unit = {
        LazyColumn(Modifier.fillMaxSize().testTag("history-group-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "plan-choice") {
                Surface(Modifier.fillMaxWidth(), color = PoliceColors.Card, shape = PoliceCardShape,
                    border = BorderStroke(1.dp, PoliceColors.Border)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose a plan for your recorded workouts.", style = MaterialTheme.typography.bodyMedium)
                        Text("Each selection creates a separate Workout Log card, even when you reuse the same plan.",
                            style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !newPlan, onClick = { newPlan = false; model.clearError() }, enabled = !action.saving,
                                label = { Text("Existing plan") }, modifier = Modifier.testTag("history-group-existing-mode"))
                            FilterChip(selected = newPlan, onClick = { newPlan = true; model.clearError() }, enabled = !action.saving,
                                label = { Text("New plan") }, modifier = Modifier.testTag("history-group-new-mode"))
                        }
                        if (newPlan) {
                            OutlinedTextField(planName, { planName = it; model.clearError() }, singleLine = true,
                                label = { Text("Plan name") }, enabled = !action.saving,
                                modifier = Modifier.fillMaxWidth().testTag("history-group-plan-name"))
                            Text("Add future workout routines to your new plan later in Academy.",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                        } else Box {
                            OutlinedButton(onClick = { planMenu = true }, enabled = !action.saving && state.trainingPlans.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("history-group-plan")) {
                                Text(existing?.plan?.name ?: if (state.trainingPlans.isEmpty()) "No plans yet — choose New plan" else "Select a plan",
                                    Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("⌄")
                            }
                            DropdownMenu(planMenu, onDismissRequest = { planMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                state.trainingPlans.sortedBy { it.plan.name.lowercase() }.forEach { plan ->
                                    DropdownMenuItem(text = { Text(plan.plan.name) }, modifier = Modifier.testTag("history-group-plan-${plan.plan.id}"),
                                        onClick = { selectedPlan = plan.plan.id; planMenu = false; model.clearError() })
                                }
                            }
                        }
                        Text("Recorded sets, notes, and times stay with each workout.",
                            style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                    }
                }
            }
            item(key = "selection") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected", Modifier.weight(1f).testTag("history-group-selection-count"),
                        style = MaterialTheme.typography.titleSmall, color = PoliceColors.LightBlue)
                    TextButton(onClick = { selectedIds = completed.map { it.workout.id }; model.clearError() },
                        enabled = !action.saving && completed.isNotEmpty(), modifier = Modifier.testTag("history-group-select-all")) { Text("Select all") }
                    TextButton(onClick = { selectedIds = emptyList(); model.clearError() },
                        enabled = !action.saving && selected.isNotEmpty(), modifier = Modifier.testTag("history-group-clear")) { Text("Clear") }
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
            else if (state.failed) item {
                Text("Could not load recorded workouts.", color = PoliceColors.Error)
                TextButton(onClick = model::retry) { Text("Retry") }
            } else if (completed.isEmpty()) item { Text("Finish a workout to group it into a plan.", color = PoliceColors.Muted) }
            items(completed, key = { it.workout.id }) { workout ->
                val checked = workout.workout.id in selected
                Surface(Modifier.fillMaxWidth().testTag("history-group-workout-${workout.workout.id}")
                    .toggleable(value = checked, enabled = !action.saving, role = Role.Checkbox) {
                        selectedIds = if (checked) selectedIds - workout.workout.id else selectedIds + workout.workout.id
                        model.clearError()
                    }, color = if (checked) PoliceColors.Raised else PoliceColors.Card, shape = PoliceCardShape,
                    border = BorderStroke(1.dp, if (checked) PoliceColors.LightBlue else PoliceColors.Border)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !action.saving)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(workout.workout.displayName(), style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.workout.startedAt)),
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            Text("${workout.performedSets().size} sets · ${workout.performedSets().sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
                            Text(workout.workout.trainingPlan.ifBlank { "No plan" },
                                style = MaterialTheme.typography.labelSmall, color = PoliceColors.LightBlue)
                        }
                    }
                }
            }
        }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("history-group-dialog").policeBackdrop().safeDrawingPadding().imePadding(),
            color = Color.Transparent, contentColor = PoliceColors.Text) {
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) { heading(); controls() }
                    VerticalDivider(color = PoliceColors.Border)
                    Box(Modifier.weight(1f).fillMaxHeight()) { body() }
                }
            } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                heading()
                SirenRule(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) { body() }
                controls()
            }
        }
    }
}
