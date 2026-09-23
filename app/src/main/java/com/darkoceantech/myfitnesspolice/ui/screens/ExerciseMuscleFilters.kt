package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.data.MuscleGroup
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.ui.theme.*

@Composable
internal fun ExerciseMuscleFilters(selected: Set<MuscleGroup>, groups: List<MuscleGroup>,
    onChange: (Set<MuscleGroup>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedCount = groups.count { it in selected }
    val all = selectedCount == groups.size
    Surface(Modifier.fillMaxWidth().testTag("muscle-filter-matrix"), color = PoliceColors.Card,
        shape = PoliceCardShape, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle-muscle-filters")
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse filters" else "Expand filters") {
                    expanded = !expanded
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MUSCLE GROUPS", color = PoliceColors.LightBlue, style = MaterialTheme.typography.labelSmall)
                    Text(when {
                        all -> "All groups"
                        selectedCount == 0 -> "No groups selected"
                        else -> "$selectedCount of ${groups.size} groups selected"
                    }, color = PoliceColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(painterResource(R.drawable.ic_back), contentDescription = null,
                    tint = PoliceColors.LightBlue, modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else -90f))
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("select-all-muscles")
                    .toggleable(value = all, role = Role.Checkbox, onValueChange = {
                        onChange(if (all) emptySet() else MuscleGroup.entries.toSet())
                    }).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RadioButton(selected = all, onClick = null, modifier = Modifier.size(20.dp),
                        colors = RadioButtonDefaults.colors(selectedColor = PoliceColors.ButtonGlow, unselectedColor = PoliceColors.Muted))
                    Text("Select all", style = MaterialTheme.typography.labelMedium)
                }
                val labelStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val longestLabel = groups.maxOfOrNull {
                    textMeasurer.measure(AnnotatedString(it.label), style = labelStyle, softWrap = false, maxLines = 1).size.width
                } ?: 0
                // Reserve measured label width plus the radio control, spacing, padding, and a small rounding allowance.
                val minimumCellWidth = with(density) { longestLabel.toDp() } + 37.dp
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth + 6.dp) / (minimumCellWidth + 6.dp)).toInt().coerceIn(1, 3)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { group ->
                                    val checked = group in selected
                                    Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                                        color = if (checked) PoliceColors.Raised else PoliceColors.Background,
                                        border = BorderStroke(1.dp, if (checked) PoliceColors.ButtonBlue else PoliceColors.Border)) {
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("muscle-filter-${group.name}")
                                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                                                onChange(if (checked) selected - group else selected + group)
                                            }).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            RadioButton(checked, onClick = null, modifier = Modifier.size(16.dp),
                                                colors = RadioButtonDefaults.colors(selectedColor = PoliceColors.ButtonGlow, unselectedColor = PoliceColors.Muted))
                                            Text(group.label, Modifier.weight(1f), style = labelStyle,
                                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Text("Choose multiple groups. Deselect groups to hide them.", style = MaterialTheme.typography.bodySmall,
                    color = PoliceColors.Muted)
            }
        }
    }
}
