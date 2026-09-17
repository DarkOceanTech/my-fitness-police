package com.example.myfitnesspolice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myfitnesspolice.data.Exercise
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.data.MuscleGroup
import com.example.myfitnesspolice.data.mainMuscleGroup
import com.example.myfitnesspolice.data.filterByMuscleGroups
import com.example.myfitnesspolice.ui.theme.*

@Composable
fun ExercisesScreen(
    state: ListState<Exercise>, form: ExerciseFormState, onRetry: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
    onResetForm: () -> Unit, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null, openAddOnEntry: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    var showForm by rememberSaveable { mutableStateOf(openAddOnEntry) }
    BackHandler(onBack != null && !showForm) { if (!form.saving) onBack?.invoke() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedNames by rememberSaveable { mutableStateOf(MuscleGroup.entries.map { it.name }) }
    val selected = selectedMuscleGroups(selectedNames)
    LaunchedEffect(form.saved) {
        if (form.saved) { showForm = false; query = ""; selectedNames = MuscleGroup.entries.map { it.name }; onResetForm() }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (header != null) header()
        else SectionPageHeader("Armory", location = "Exercises", onBack = onBack,
            backLabel = "Back to Armory", enabled = !form.saving)
        LazyColumn(Modifier.weight(1f).testTag("exercise-catalog"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "search") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseSearch(query, { query = it }, "catalog-search", Modifier.weight(1f))
                    PoliceIconButton(onClick = { onResetForm(); showForm = true }, enabled = !form.saving,
                        modifier = Modifier.size(48.dp).testTag("add-catalog-exercise"), shape = RoundedCornerShape(12.dp)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Add exercise")
                    }
                }
            }
            when (state) {
                ListState.Loading -> item { CircularProgressIndicator() }
                ListState.Failed -> item {
                    Text("Could not load your exercises.")
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                is ListState.Ready -> {
                    val groups = availableMuscleGroups(state.items)
                    val matches = filterExercises(filterByMuscleGroups(state.items, selected), query)
                    item(key = "filters") {
                        ExerciseMuscleFilters(selected, groups, onChange = { selectedNames = it.map { group -> group.name } })
                    }
                    item(key = "count") {
                        Text("${matches.size} of ${state.items.size} exercises", color = PoliceColors.Muted,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("catalog-count"))
                    }
                    if (matches.isEmpty()) item(key = "empty") {
                        Text(when {
                            state.items.isEmpty() -> "No exercises yet. Add your first exercise."
                            selected.isEmpty() -> "Select at least one muscle group, or select all."
                            else -> "No matches. Try another group, name, equipment, or muscle."
                        }, color = PoliceColors.Muted)
                    }
                    items(matches, key = { it.id }) { exercise -> ExerciseCatalogCard(exercise) }
                }
            }
        }
    }
    if (showForm) AddExerciseDialog(form, onDismiss = { showForm = false }, onEdit = onResetForm, onAdd = onAdd)
}

private fun selectedMuscleGroups(names: List<String>): Set<MuscleGroup> = names.flatMap { name ->
    // Preserve a restored selection from versions that used one Core filter.
    if (name == "CORE") listOf(MuscleGroup.ABDOMINAL, MuscleGroup.LOWER_BACK)
    else listOfNotNull(MuscleGroup.entries.find { it.name == name })
}.toSet()

private fun availableMuscleGroups(exercises: List<Exercise>): List<MuscleGroup> = MuscleGroup.entries.filter {
    it != MuscleGroup.OTHER || exercises.any { exercise -> exercise.mainMuscleGroup() == it }
}

internal fun filterExercises(exercises: List<Exercise>, query: String): List<Exercise> {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return exercises.filter { exercise ->
        val searchable = listOf(exercise.name, exercise.equipment, exercise.description,
            exercise.primaryMuscles, exercise.secondaryMuscles).joinToString(" ")
        terms.all { searchable.contains(it, ignoreCase = true) }
    }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
}

@Composable
private fun ExerciseSearch(query: String, onChange: (String) -> Unit, tag: String, modifier: Modifier = Modifier) {
    OutlinedTextField(query, onChange, modifier = modifier.fillMaxWidth().testTag(tag), singleLine = true,
        label = { Text("Search exercises") }, placeholder = { Text("Name, equipment, or muscle") },
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Clear") } })
}

@Composable
private fun ExerciseCatalogCard(exercise: Exercise, enabled: Boolean = true, onSelect: (() -> Unit)? = null) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("catalog-exercise-${exercise.id}"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = if (onSelect == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShieldMark(Modifier.size(32.dp))
                Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                SirenRule(Modifier.width(24.dp))
            }
            Text(exercise.mainMuscleGroup().label.uppercase(java.util.Locale.ROOT),
                color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelSmall)
            ExerciseDetailField("EQUIPMENT", exercise.equipment.ifBlank { "Not specified" })
            ExerciseDetailField("PRIMARY MUSCLES", exercise.primaryMuscles.ifBlank { "Not specified" })
            if (expanded) {
                HorizontalDivider(color = PoliceColors.Border)
                ExerciseDetailField("DESCRIPTION", exercise.description.ifBlank { "No description added" })
                ExerciseDetailField("SECONDARY MUSCLES", exercise.secondaryMuscles.ifBlank { "Not specified" })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("exercise-details-${exercise.id}")) {
                    Text(if (expanded) "Hide details" else "Show details")
                }
                Spacer(Modifier.weight(1f))
                if (onSelect != null) PoliceButton(onClick = onSelect, enabled = enabled,
                    modifier = Modifier.testTag("select-exercise-${exercise.id}")) { Text("Add") }
            }
        }
    }
}

@Composable
private fun ExerciseDetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelSmall)
        Text(value, color = PoliceColors.Text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExerciseDialogFrame(
    title: String, enabled: Boolean, onDismiss: () -> Unit, fullScreen: Boolean = false,
    footer: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = { if (enabled) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = !fullScreen)) {
        val frame = if (fullScreen) Modifier.fillMaxSize().testTag("exercise-picker-screen")
            .policeBackdrop().safeDrawingPadding().imePadding()
        else Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding().heightIn(max = 680.dp)
        Surface(frame, shape = if (fullScreen) RectangleShape else PoliceCardShape,
            color = if (fullScreen) Color.Transparent else PoliceColors.Background,
            border = if (fullScreen) null else BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.padding(if (fullScreen) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (fullScreen) {
                        IconButton(onClick = onDismiss, enabled = enabled) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to workout")
                        }
                    } else ShieldMark(Modifier.size(34.dp))
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    SirenRule(Modifier.width(30.dp))
                }
                content()
                HorizontalDivider(color = PoliceColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically, content = footer)
            }
        }
    }
}

@Composable
private fun AddExerciseDialog(form: ExerciseFormState, onDismiss: () -> Unit, onEdit: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var primary by rememberSaveable { mutableStateOf("") }
    var secondary by rememberSaveable { mutableStateOf("") }
    ExerciseDialogFrame("Add exercise", !form.saving, onDismiss, footer = {
        TextButton(onClick = onDismiss, enabled = !form.saving) { Text("Cancel") }
        Spacer(Modifier.width(12.dp))
        PoliceButton(onClick = { onAdd(name, equipment, description, primary, secondary) }, enabled = !form.saving,
            modifier = Modifier.testTag("save-exercise")) {
            Text(if (form.saving) "Saving…" else "Save")
        }
    }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("exercise-form"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add a movement to your exercise library. All fields except secondary muscles are required.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            OutlinedTextField(name, { name = it; onEdit() }, label = { Text("Exercise name") }, singleLine = true,
                enabled = !form.saving, modifier = Modifier.fillMaxWidth().testTag("exercise-name"))
            OutlinedTextField(description, { description = it; onEdit() }, label = { Text("Description") }, minLines = 2, maxLines = 4,
                enabled = !form.saving, modifier = Modifier.fillMaxWidth().testTag("exercise-description"))
            OutlinedTextField(equipment, { equipment = it; onEdit() }, label = { Text("Equipment") }, maxLines = 3,
                placeholder = { Text("Bodyweight; bench; dumbbell") }, enabled = !form.saving,
                modifier = Modifier.fillMaxWidth().testTag("exercise-equipment"))
            OutlinedTextField(primary, { primary = it; onEdit() }, label = { Text("Primary muscles") }, maxLines = 4,
                placeholder = { Text("Latissimus dorsi; trapezius (middle part)") }, enabled = !form.saving,
                modifier = Modifier.fillMaxWidth().testTag("exercise-primary"))
            OutlinedTextField(secondary, { secondary = it; onEdit() }, label = { Text("Secondary muscles (optional)") }, maxLines = 4,
                placeholder = { Text("Biceps brachii; brachialis") }, enabled = !form.saving,
                modifier = Modifier.fillMaxWidth().testTag("exercise-secondary"))
            Text("Primary: main movers. Secondary: assisting muscles and stabilizers. Separate multiple entries with semicolons.",
                style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
        }
        // Keep validation visible even when the keyboard is open and lower fields are scrolled away.
        form.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun ExercisePickerDialog(exercises: List<Exercise>, saving: Boolean, error: String?,
    form: ExerciseFormState, onResetForm: () -> Unit, onAddExercise: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedNames by rememberSaveable { mutableStateOf(MuscleGroup.entries.map { it.name }) }
    val selected = selectedMuscleGroups(selectedNames)
    val groups = remember(exercises) { availableMuscleGroups(exercises) }
    val matches = remember(exercises, query, selected) { filterExercises(filterByMuscleGroups(exercises, selected), query) }
    LaunchedEffect(form.saved) {
        if (showForm && form.saved) {
            showForm = false
            query = ""
            selectedNames = MuscleGroup.entries.map { it.name }
            onResetForm()
        }
    }
    if (showForm) AddExerciseDialog(form,
        onDismiss = { showForm = false; onResetForm() }, onEdit = onResetForm, onAdd = onAddExercise)
    else ExerciseDialogFrame("Add Exercise", !saving, onDismiss, fullScreen = true,
        footer = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseSearch(query, { query = it }, "exercise-picker-search", Modifier.weight(1f))
            PoliceIconButton(onClick = { onResetForm(); showForm = true }, enabled = !saving && !form.saving,
                modifier = Modifier.size(48.dp).testTag("add-picker-exercise"), shape = RoundedCornerShape(12.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add exercise")
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("exercise-picker-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "filters") {
                ExerciseMuscleFilters(selected, groups, onChange = { selectedNames = it.map { group -> group.name } })
            }
            item(key = "count") {
                Text("${matches.size} of ${exercises.size} exercises · select Add to use a movement", color = PoliceColors.Muted,
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("exercise-picker-count"))
            }
            if (matches.isEmpty()) item(key = "empty") {
                Text(when {
                    exercises.isEmpty() -> "Use + above to add your first exercise."
                    selected.isEmpty() -> "Select at least one muscle group, or select all."
                    else -> "No matches. Try another group, name, equipment, or muscle."
                }, color = PoliceColors.Muted)
            }
            items(matches, key = { it.id }) { exercise ->
                ExerciseCatalogCard(exercise, enabled = !saving, onSelect = { onSelect(exercise.id) })
            }
        }
        error?.let { Text(it, color = PoliceColors.Error) }
    }
}
