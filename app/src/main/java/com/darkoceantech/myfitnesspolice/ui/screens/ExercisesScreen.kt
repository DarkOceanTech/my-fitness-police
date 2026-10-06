package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.data.Exercise
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.MuscleGroup
import com.darkoceantech.myfitnesspolice.data.mainMuscleGroup
import com.darkoceantech.myfitnesspolice.data.filterByMuscleGroups
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
fun ExercisesScreen(
    state: ListState<Exercise>, form: ExerciseFormState, onRetry: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
    onResetForm: () -> Unit, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null, openAddOnEntry: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    onUpdate: (String, String, String, String, String, String) -> Unit,
) {
    var showForm by rememberSaveable { mutableStateOf(openAddOnEntry) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingExercise = (state as? ListState.Ready<Exercise>)?.items?.firstOrNull { it.id == editingId }
    BackHandler(onBack != null && !showForm) { if (!form.saving) onBack?.invoke() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedNames by rememberSaveable { mutableStateOf(MuscleGroup.entries.map { it.name }) }
    val selected = selectedMuscleGroups(selectedNames)
    LaunchedEffect(form.saved) {
        if (form.saved) { showForm = false; editingId = null; query = ""; selectedNames = MuscleGroup.entries.map { it.name }; onResetForm() }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (header != null) header()
        else SectionPageHeader("Precinct", location = "Exercises", onBack = onBack,
            backLabel = "Back to Precinct", enabled = !form.saving)
        LazyColumn(Modifier.weight(1f).testTag("exercise-catalog"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "search") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseSearch(query, { query = it }, "catalog-search", Modifier.weight(1f))
                    PoliceIconButton(onClick = { onResetForm(); editingId = null; showForm = true }, enabled = !form.saving,
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
                    items(matches, key = { it.id }) { exercise ->
                        ExerciseCatalogCard(exercise, enabled = !form.saving, onEdit = {
                            onResetForm(); editingId = exercise.id; showForm = true
                        })
                    }
                }
            }
        }
    }
    if (showForm && (editingId == null || editingExercise != null)) ExerciseFormDialog(form,
        exercise = editingExercise,
        onDismiss = { showForm = false; editingId = null; onResetForm() }, onEdit = onResetForm,
        onSave = { name, equipment, description, primary, secondary ->
            if (editingId == null) onAdd(name, equipment, description, primary, secondary)
            else onUpdate(requireNotNull(editingId), name, equipment, description, primary, secondary)
        })
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
private fun ExerciseCatalogCard(exercise: Exercise, enabled: Boolean = true, onSelect: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null, selected: Boolean = false, selectLabel: String = "Add", recommended: Boolean = false) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("catalog-exercise-${exercise.id}"), shape = PoliceCardShape,
        color = PoliceColors.Card, border = BorderStroke(1.dp, if (selected) PoliceColors.Red else PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = if (onSelect == null || selected) Modifier else Modifier.clickable(enabled = enabled, onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShieldMark(Modifier.size(32.dp))
                Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (onEdit != null) Box {
                    IconButton(onClick = { menu = true }, enabled = enabled,
                        modifier = Modifier.testTag("exercise-options-${exercise.id}")) {
                        Text("⋮", fontSize = 26.sp, modifier = Modifier.semantics {
                            contentDescription = "Exercise options for ${exercise.name}"
                        })
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit exercise details") },
                            onClick = { menu = false; onEdit() }, enabled = enabled,
                            modifier = Modifier.testTag("edit-exercise-${exercise.id}"))
                    }
                } else SirenRule(Modifier.width(24.dp))
            }
            if (recommended) Text("Recommended · related muscle group", modifier = Modifier.testTag("recommended-exercise-${exercise.id}"),
                color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelMedium)
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
                if (onSelect != null) {
                    if (selected) Button(onClick = onSelect, enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = PoliceColors.Red.copy(alpha = .18f), contentColor = PoliceColors.Error),
                        border = BorderStroke(1.dp, PoliceColors.Red),
                        modifier = Modifier.testTag("remove-exercise-${exercise.id}")) { Text("Remove") }
                    else PoliceButton(onClick = onSelect, enabled = enabled,
                        modifier = Modifier.testTag("select-exercise-${exercise.id}")) { Text(selectLabel) }
                }
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
    title: String, enabled: Boolean, onDismiss: () -> Unit,
    screenTag: String = "exercise-picker-screen", backLabel: String = "Back to workout",
    footer: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val formBody = remember {
        movableContentOf {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) { currentContent() }
        }
    }
    Dialog(onDismissRequest = { if (enabled) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val frame = Modifier.fillMaxSize().testTag(screenTag)
            .policeBackdrop().safeDrawingPadding().imePadding()
        Surface(frame, shape = RectangleShape, color = Color.Transparent, contentColor = PoliceColors.Text) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Keep this arrangement stable as the landscape keyboard opens, preserving
                // focus and leaving the available height for the editable fields.
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth > 500.dp) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onDismiss, enabled = enabled) {
                                    Icon(painterResource(R.drawable.ic_back), contentDescription = backLabel)
                                }
                                Text(title, Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.titleMedium, color = PoliceColors.Text)
                            }
                            footer?.let { controls -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically, content = controls) }
                        }
                        VerticalDivider(color = PoliceColors.Border)
                        Box(Modifier.weight(1f).fillMaxHeight()) { formBody() }
                    }
                } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = onDismiss, enabled = enabled) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = backLabel)
                        }
                        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        SirenRule(Modifier.width(30.dp))
                    }
                    Box(Modifier.weight(1f)) { formBody() }
                    footer?.let { controls ->
                        HorizontalDivider(color = PoliceColors.Border)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically, content = controls)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseFormDialog(form: ExerciseFormState, onDismiss: () -> Unit, onEdit: () -> Unit,
    exercise: Exercise? = null, onSave: (String, String, String, String, String) -> Unit) {
    var name by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.name.orEmpty()) }
    var equipment by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.equipment.orEmpty()) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var primary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.primaryMuscles.orEmpty()) }
    var secondary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.secondaryMuscles.orEmpty()) }
    ExerciseDialogFrame(if (exercise == null) "Create New Exercise" else "Edit exercise details", !form.saving, onDismiss,
        screenTag = "exercise-editor-screen", backLabel = "Close exercise form", footer = {
        TextButton(onClick = onDismiss, enabled = !form.saving) { Text("Cancel") }
        Spacer(Modifier.width(12.dp))
        PoliceButton(onClick = { onSave(name, equipment, description, primary, secondary) }, enabled = !form.saving,
            modifier = Modifier.testTag("save-exercise")) {
            Text(if (form.saving) "Saving…" else "Save")
        }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("exercise-form"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text((if (exercise == null) "Add a movement to your exercise library." else "Update this movement in your exercise library.") +
                " All fields except secondary muscles are required.",
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
    onDismiss: () -> Unit, onSelect: (String) -> Unit,
    selectedExerciseIds: Set<String> = emptySet(), onRemove: ((String) -> Unit)? = null,
    title: String = "Add Exercise", selectLabel: String = "Add", backLabel: String = "Back to workout",
    allowCreate: Boolean = true, recommendedExerciseIds: List<String> = emptyList(),
    intro: (@Composable () -> Unit)? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedNames by rememberSaveable { mutableStateOf(MuscleGroup.entries.map { it.name }) }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    val multiple = onRemove != null
    val selected = selectedMuscleGroups(selectedNames)
    val groups = remember(exercises) { availableMuscleGroups(exercises) }
    val matches = remember(exercises, query, selected, onlySelected, selectedExerciseIds, multiple, recommendedExerciseIds) {
        filterExercises(filterByMuscleGroups(exercises, selected), query).filter {
            !multiple || !onlySelected || it.id in selectedExerciseIds
        }.sortedWith(compareBy<Exercise> { recommendedExerciseIds.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .thenBy { it.name.lowercase(java.util.Locale.ROOT) })
    }
    LaunchedEffect(form.saved) {
        if (showForm && form.saved) {
            showForm = false
            query = ""
            onlySelected = false
            selectedNames = MuscleGroup.entries.map { it.name }
            onResetForm()
        }
    }
    if (showForm) ExerciseFormDialog(form,
        onDismiss = { showForm = false; onResetForm() }, onEdit = onResetForm, onSave = onAddExercise)
    else ExerciseDialogFrame(title, !saving, onDismiss, backLabel = backLabel) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseSearch(query, { query = it }, "exercise-picker-search", Modifier.weight(1f))
            if (allowCreate) PoliceIconButton(onClick = { onResetForm(); showForm = true }, enabled = !saving && !form.saving,
                modifier = Modifier.size(48.dp).testTag("add-picker-exercise"), shape = RoundedCornerShape(12.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add exercise")
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("exercise-picker-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (intro != null) item(key = "intro") { intro() }
            if (multiple) item(key = "selection") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilterChip(selected = onlySelected, onClick = {
                        onlySelected = !onlySelected
                        if (onlySelected) {
                            query = ""
                            selectedNames = MuscleGroup.entries.map { it.name }
                        }
                    }, enabled = !saving, label = { Text("${selectedExerciseIds.size} selected") },
                        modifier = Modifier.testTag("exercise-picker-selected-count").semantics {
                            contentDescription = if (onlySelected) "Show all exercises" else "Show selected exercises"
                        })
                }
            }
            item(key = "filters") {
                ExerciseMuscleFilters(selected, groups, onChange = { selectedNames = it.map { group -> group.name } })
            }
            item(key = "count") {
                val hint = if (selectLabel == "Select") "choose an exercise" else "select $selectLabel to use a movement"
                Text("${matches.size} of ${exercises.size} exercises · $hint", color = PoliceColors.Muted,
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("exercise-picker-count"))
            }
            if (matches.isEmpty()) item(key = "empty") {
                Text(when {
                    exercises.isEmpty() -> if (allowCreate) "Use + above to add your first exercise." else "No exercises are available to select."
                    multiple && onlySelected && selectedExerciseIds.isEmpty() -> "Nothing selected yet. Tap 0 selected to see all exercises."
                    selected.isEmpty() -> "Select at least one muscle group, or select all."
                    else -> "No matches. Try another group, name, equipment, or muscle."
                }, color = PoliceColors.Muted)
            }
            items(matches, key = { it.id }) { exercise ->
                val included = multiple && exercise.id in selectedExerciseIds
                ExerciseCatalogCard(exercise, enabled = !saving, selected = included, selectLabel = selectLabel,
                    recommended = exercise.id in recommendedExerciseIds,
                    onSelect = { if (included) onRemove?.invoke(exercise.id) else onSelect(exercise.id) })
            }
        }
        error?.let { Text(it, color = PoliceColors.Error) }
    }
}
