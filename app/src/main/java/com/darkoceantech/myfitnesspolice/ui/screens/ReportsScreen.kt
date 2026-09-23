package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.R

@Composable
fun ReportsScreen(onHistory: () -> Unit, modifier: Modifier = Modifier) {
    var showDor by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().testTag("progress-history-home")) {
        SectionPageHeader("Progress Reports",
            location = "Pain = Progress\nInspired by Ray Dalio’s ‘Pain + Reflection = Progress’")
        LazyVerticalGrid(columns = GridCells.Adaptive(152.dp),
            modifier = Modifier.weight(1f).testTag("progress-history-grid"),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "history") {
                SectionLaunchCard("Workout History", "Review completed workouts, sets, reps, and notes.",
                    R.drawable.ic_library, "progress-history-tile", onHistory)
            }
            item(key = "dor") {
                SectionLaunchCard("DOR", "Daily Observation Report", R.drawable.ic_report, "reports-dor-tile") {
                    showDor = true
                }
            }
        }
    }
    if (showDor) FeatureConstructionDialog("DOR", R.drawable.ic_report,
        "The Daily Observation Report desk is still sorting its paperwork. Our officers are investigating who used the last pen.",
        homeTitle = "Reports", tagPrefix = "dor", onDismiss = { showDor = false })
}
