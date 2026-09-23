package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

enum class PtoActivity(val title: String, val description: String, val icon: Int, val tag: String, val constructionMessage: String) {
    RUNNING("Running", "Gear up for foot patrol.", R.drawable.ic_running, "running",
        "Our foot-pursuit unit is lacing up. The only thing running here today is the coffee machine."),
    HIKING("Hiking", "Take your patrol to the trails.", R.drawable.ic_hiking, "hiking",
        "Trail patrol is investigating a suspiciously steep hill. Your hiking tools are still being assembled."),
    KAYAKING("Kayaking", "Report for paddle duty.", R.drawable.ic_kayaking, "kayaking",
        "Marine patrol is checking whether the donuts float. Your paddling tools are still in dry dock."),
    WALKING("Walking", "One beat, one step at a time.", R.drawable.ic_walking, "walking",
        "Beat patrol is warming up. This page is taking its first steps through the academy."),
    ROAD_CYCLING("Road cycling", "Get ready for road patrol.", R.drawable.ic_road_cycling, "road-cycling",
        "Traffic division is fitting a siren to a bicycle. Your ride tools are still in the workshop."),
    SNOWBOARDING("Snowboarding", "Take your patrol to the slopes.", R.drawable.ic_snowboarding, "snowboarding",
        "Snow patrol is teaching a traffic cone to carve. Your slope tools are still in the workshop."),
}

@Composable
fun PtoRoute(selected: PtoActivity?, onSelect: (PtoActivity?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag("pto-home")) {
        SectionPageHeader("Field Training")
        LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = Modifier.weight(1f).testTag("pto-grid"),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("My Police Training Officer", style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue,
                        modifier = Modifier.semantics { heading() })
                    Text("Indoor and outdoor activity tracking for distance, time, and elevation.",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
            }
            item(key = "activities", span = { GridItemSpan(maxLineSpan) }) {
                Text("Activities", style = MaterialTheme.typography.titleLarge, color = PoliceColors.LightBlue,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            }
            items(PtoActivity.entries, key = { it.tag }) { activity ->
                SectionLaunchCard(activity.title, activity.description, activity.icon, "pto-${activity.tag}-tile") { onSelect(activity) }
            }
        }
    }
    if (selected != null) FeatureConstructionDialog(selected.title, selected.icon, selected.constructionMessage,
        homeTitle = "Field Training", tagPrefix = "pto", onDismiss = { onSelect(null) })
}
