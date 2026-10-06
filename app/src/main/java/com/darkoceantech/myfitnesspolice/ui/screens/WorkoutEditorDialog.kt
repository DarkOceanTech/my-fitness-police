package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.data.Workout
import com.darkoceantech.myfitnesspolice.ui.theme.*

/** Measure inside the visible window after system bars and IME, keeping actions below scrolling content. */
@Composable
internal fun WorkoutEditorDialog(title: String, tag: String, enabled: Boolean, onDismiss: () -> Unit,
    fullScreen: Boolean = false,
    footer: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (enabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        if (fullScreen) {
            FullScreenWorkoutEditor(title, tag, enabled, onDismiss, footer, content)
        } else Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 680.dp).testTag(tag),
                shape = PoliceCardShape, color = PoliceColors.Background, border = BorderStroke(1.dp, PoliceColors.Border)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShieldMark(Modifier.size(24.dp))
                        Text(title, Modifier.weight(1f).testTag("$tag-title").semantics { heading() },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SirenRule(Modifier.width(24.dp))
                    }
                    content()
                    HorizontalDivider(color = PoliceColors.Border)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically, content = footer)
                }
            }
        }
    }
}

@Composable
private fun FullScreenWorkoutEditor(title: String, tag: String, enabled: Boolean, onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val currentContent by rememberUpdatedState(content)
    val formBody = remember {
        movableContentOf {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { currentContent() }
        }
    }
    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onDismiss, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Close $title")
            }
            Text(title, Modifier.weight(1f).testTag("$tag-title").semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Surface(Modifier.fillMaxSize().testTag(tag).policeBackdrop().safeDrawingPadding().imePadding(),
        shape = RectangleShape, color = Color.Transparent, contentColor = PoliceColors.Text) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Keep the form and actions side by side in landscape, leaving the scroll viewport
            // enough height for a complete field when the keyboard is open.
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth > 500.dp) {
                val compact = maxHeight < 160.dp
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        if (compact) {
                            // Cancel remains available below; omit the decorative header when the IME
                            // leaves too little height for both a full toolbar and the action buttons.
                            Text(title, Modifier.testTag("$tag-title").semantics { heading() },
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            header()
                            SirenRule(Modifier.fillMaxWidth())
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically, content = footer)
                    }
                    VerticalDivider(color = PoliceColors.Border)
                    Box(Modifier.weight(1f).fillMaxHeight()) { formBody() }
                }
            } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                SirenRule(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) { formBody() }
                HorizontalDivider(color = PoliceColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically, content = footer)
            }
        }
    }
}

@Composable
internal fun WorkoutDetailsEditor(workout: Workout, action: SessionAction, onCancel: () -> Unit,
    onSave: (String, String) -> Unit) {
    var name by rememberSaveable(workout.id) { mutableStateOf(workout.name) }
    var muscles by rememberSaveable(workout.id) { mutableStateOf(workout.targetMuscles) }
    val changed = name.trim() != workout.name || muscles != workout.targetMuscles
    WorkoutEditorDialog("Edit workout details", "workout-details-editor", !action.saving, onCancel, footer = {
        OutlinedButton(onClick = onCancel, enabled = !action.saving, modifier = Modifier.weight(1f)) { Text("Cancel") }
        PoliceButton(onClick = { onSave(name, muscles) }, enabled = !action.saving && changed,
            modifier = Modifier.weight(1f).testTag("save-workout-details")) { Text(if (action.saving) "Saving…" else "Save") }
    }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Name this workout and set its muscle focus. Choose more than one target muscle if needed.", color = PoliceColors.Muted,
                style = MaterialTheme.typography.bodySmall)
            WorkoutDetailsFields(workout.copy(name = name, targetMuscles = muscles), !action.saving) { field, value ->
                when (field) { "name" -> name = value; "muscles" -> muscles = value }
            }
        }
        action.error?.let { Text(it, color = PoliceColors.Error, style = MaterialTheme.typography.bodySmall) }
    }
}
