package com.example.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfitnesspolice.data.*

private enum class GymSection(val title: String, val testTag: String) {
    Exercises("Exercises", "gym-section-exercises"),
    Workouts("Workouts", "workout-section-0"),
    Plan("Plan", "workout-section-1"),
}

@Composable
fun WorkoutHomeRoute(model: SessionViewModel, exercisesModel: ExercisesViewModel, modifier: Modifier = Modifier,
    onFinished: (String) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val action by model.action.collectAsStateWithLifecycle()
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var training by rememberSaveable { mutableStateOf(false) }
    var logger by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(GymSection.Exercises) }
    var trainingPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var trainingEditor by rememberSaveable { mutableStateOf(false) }
    var requestedStart by rememberSaveable { mutableStateOf(false) }
    fun back() {
        if (action.saving) return
        model.clearError()
        when {
            training -> training = false
            savedId != null -> savedId = null
            logger -> logger = false
            trainingEditor -> trainingEditor = false
            else -> trainingPlanId = null
        }
    }
    BackHandler(logger || savedId != null || training || trainingEditor || trainingPlanId != null) { back() }
    LaunchedEffect(action.revision) {
        if (requestedStart && action.completedAction == "start-training") {
            requestedStart = false; training = true
        }
        if (action.completedAction == "delete-training-plan") { trainingEditor = false; trainingPlanId = null }
    }
    val selectedTraining = state.trainingPlans.firstOrNull { it.plan.id == trainingPlanId }
    if (training) ActiveWorkoutScreen(model, modifier,
        onBack = { training = false }, onFinished = { id -> training = false; onFinished(id) })
    else if (savedId != null) key(savedId) {
        WorkoutBuilderScreen(model, exercisesModel, modifier, planId = savedId, onBack = { back() },
            onSaved = { savedId = null }, onDeleted = { savedId = null })
    }
    else if (logger) WorkoutBuilderScreen(model, exercisesModel, modifier, onBack = { back() }, onSaved = { logger = false })
    else if ((trainingEditor || trainingPlanId != null) && (state.loading || state.failed || (trainingPlanId != null && selectedTraining == null))) {
        Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { trainingEditor = false; trainingPlanId = null }) { Text("Back to Plan") }
            if (state.loading) CircularProgressIndicator()
            else if (state.failed) { Text("Could not load training plans."); TextButton(onClick = model::retry) { Text("Retry") } }
            else Text("This training plan is no longer available.")
        }
    }
    else if (trainingEditor) TrainingPlanEditorScreen(selectedTraining, state.sessions, action, modifier,
        onBack = { back() }, onSaved = { trainingEditor = false; section = GymSection.Plan },
        onWorkouts = { trainingEditor = false; trainingPlanId = null; section = GymSection.Workouts }, onSave = model::saveTrainingPlan)
    else if (selectedTraining != null) TrainingPlanDetailScreen(selectedTraining, state.sessions, action, modifier,
        onBack = { back() }, onEdit = { model.clearError(); trainingEditor = true },
        onStart = { model.clearError(); requestedStart = true; model.startTraining(selectedTraining.plan.id) },
        onWorkout = { model.clearError(); savedId = it }, onDelete = { model.deleteTrainingPlan(selectedTraining.plan.id) })
    else if (section == GymSection.Exercises) {
        val exercises by exercisesModel.state.collectAsStateWithLifecycle()
        val form by exercisesModel.form.collectAsStateWithLifecycle()
        ExercisesScreen(exercises, form, exercisesModel::retry, exercisesModel::addExercise,
            exercisesModel::resetForm, modifier, header = {
                GymSectionTabs(section, onSection = { section = it },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    enabled = !form.saving)
            })
    } else {
        WorkoutHomeScreen(modifier, onAddWorkout = { logger = true },
        section = section, onSection = { section = it }, trainingPlans = state.trainingPlans,
        onAddTraining = { model.clearError(); trainingPlanId = null; trainingEditor = true },
        onTrainingPlan = { model.clearError(); trainingPlanId = it },
        loading = state.loading, failed = state.failed, onRetry = model::retry,
        saved = state.sessions.filter { it.workout.kind == "plan" }, onSavedWorkout = { savedId = it },
        hasActiveSession = state.sessions.any { it.workout.kind == "session" && it.workout.finishedAt == null },
        onResume = { training = true })
    }
}

@Composable
private fun WorkoutHomeScreen(modifier: Modifier, onAddWorkout: () -> Unit, loading: Boolean, failed: Boolean, onRetry: () -> Unit,
    section: GymSection, onSection: (GymSection) -> Unit, trainingPlans: List<TrainingPlanDetails>, onAddTraining: () -> Unit, onTrainingPlan: (String) -> Unit,
    saved: List<WorkoutDetails>, onSavedWorkout: (String) -> Unit,
    hasActiveSession: Boolean, onResume: () -> Unit) {
    val workoutGroups = remember(saved) { groupWorkoutsByMuscle(saved) }
    LazyColumn(modifier.testTag("workout-home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "gym-tabs") {
            GymSectionTabs(section, onSection)
        }
        item(key = "gym-section-intro") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (section == GymSection.Workouts)
                    "Create workout variations\nfor a body part, like Biceps — Light\nor Biceps — Strength."
                    else "Build your day with workouts like\nBiceps — Light, Back — Rows Only,\nand Abs — Light Reps.",
                    modifier = Modifier.weight(1f), minLines = 3,
                    style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                PoliceIconButton(onClick = if (section == GymSection.Workouts) onAddWorkout else onAddTraining,
                    modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = if (section == GymSection.Workouts) "Add workout" else "Add training plan")
                }
            }
        }
        if (hasActiveSession) item {
            PoliceButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume workout") }
        }
        if (loading) item(key = "loading-plans") {
            Box(Modifier.fillMaxWidth().heightIn(min = 136.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (failed) item(key = "failed-plans") {
            Text("Could not load your workouts and training plans.", color = PoliceColors.Error)
            TextButton(onClick = onRetry) { Text("Retry") }
        } else if (section == GymSection.Plan) {
            if (trainingPlans.isEmpty()) item(key = "first-training-plan") {
                EmptyGymListButton("Create My First Plan", "create-first-training-plan", onAddTraining)
            }
            trainingPlans.forEach { details -> item(key = "training-${details.plan.id}") {
                TrainingPlanCard(details, saved) { onTrainingPlan(details.plan.id) }
            } }
        } else if (workoutGroups.isEmpty()) item(key = "first-workout") {
            EmptyGymListButton("Create My First Workout", "create-first-workout", onAddWorkout)
        }
        if (section == GymSection.Workouts && !loading && !failed) workoutGroups.forEach { group ->
            item(key = "muscle-group-${group.title}") {
                Row(Modifier.fillMaxWidth().testTag("workout-group-${group.title}").semantics { heading() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        color = PoliceColors.LightBlue)
                    Text(group.workouts.size.toString(), style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                }
            }
            group.workouts.forEachIndexed { index, session ->
                item(key = session.workout.id) {
                    val totalSets = session.exercises.sumOf { it.sets.size }
                    val totalReps = session.exercises.sumOf { entry -> entry.sets.sumOf { it.reps.toLong() } }
                    PlanCard(group.title, session.workout.displayName(),
                        "$totalSets sets · $totalReps reps", index,
                        modifier = Modifier.testTag("home-plan-${session.workout.id}")) {
                        onSavedWorkout(session.workout.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun GymSectionTabs(section: GymSection, onSection: (GymSection) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().selectableGroup().testTag("gym-sections"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GymSection.entries.forEach { tab ->
            Column(Modifier.weight(1f).testTag(tab.testTag)
                .selectable(selected = section == tab, enabled = enabled, role = Role.Tab, onClick = { onSection(tab) })
                .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tab.title, style = MaterialTheme.typography.titleMedium,
                    color = if (section == tab) PoliceColors.Text else PoliceColors.Muted)
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (section == tab) PoliceColors.Red else PoliceColors.Blue))
            }
        }
    }
}

@Composable
private fun EmptyGymListButton(label: String, tag: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 136.dp).testTag(tag),
        shape = PoliceCardShape, border = androidx.compose.foundation.BorderStroke(1.dp, PoliceColors.Blue),
        colors = ButtonDefaults.buttonColors(containerColor = PoliceColors.Card, contentColor = PoliceColors.Text)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(32.dp), tint = PoliceColors.LightBlue)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlanCard(targetMuscles: String, name: String, subtitle: String, variant: Int,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = if (variant % 2 == 0) PoliceColors.Blue else PoliceColors.Red
    Box(modifier.fillMaxWidth().clip(PoliceCardShape)
        .background(Brush.horizontalGradient(listOf(PoliceColors.Card, androidx.compose.ui.graphics.lerp(PoliceColors.Card, dimSurface(accent), .13f))))
        .border(1.dp, PoliceColors.Border, PoliceCardShape)
        .clickable(onClickLabel = "Open $name workout", onClick = onClick)) {
        ShieldMark(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(110.dp), tint = PoliceColors.Muted.copy(alpha = .09f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    Text(targetMuscles, color = PoliceColors.Text, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("workout-card-muscles")
                            .background(PoliceColors.Raised, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                SirenRule(Modifier.width(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, color = PoliceColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
