package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun WorkoutSessionSummaryScreen(workout: WorkoutDetails, now: Long, action: SessionAction,
    modifier: Modifier = Modifier, onBack: () -> Unit, onReviewSets: () -> Unit,
    onPauseResume: () -> Unit, onHistory: () -> Unit) {
    val progress = workout.sessionState
    val finished = workout.workout.finishedAt != null
    val performed = workout.performedSets()
    val finalSet = performed.lastOrNull()
    val finalRest = if (!finished && progress?.phase == "cooldown") progress.phaseMillis(now) else finalSet?.restMillis ?: 0L
    val remaining = if (!finished && progress != null) progress.cooldownRemainingMillis(now) else 0L
    val activeMillis = performed.sumOf { it.activeMillis }
    val breakMillis = performed.sumOf { it.restMillis } + if (!finished) finalRest else 0L
    val pounds = performed.fold(BigDecimal.ZERO) { sum, set ->
        sum + BigDecimal.valueOf(set.weightGrams).multiply(BigDecimal.valueOf((set.actualReps ?: set.reps).toLong()))
    }.divide(BigDecimal("453.59237"), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(modifier.fillMaxSize().testTag("workout-session-summary")) {
        SectionPageHeader("Workout summary", location = workout.workout.displayName(), onBack = onBack,
            backLabel = "Back to Academy", enabled = !action.saving)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (landscape) 76.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (finished) "Shift complete. Strong work, officer." else "Sets complete. Take your final breather.",
                style = MaterialTheme.typography.titleMedium, color = PoliceColors.LightBlue)
            WorkoutSummary(workout, Modifier, durationMillis = progress?.dutyMillis(now), tagPrefix = "summary")
            Surface(Modifier.fillMaxWidth().testTag("summary-final-rest"), shape = PoliceCardShape,
                color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Blue)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("FINAL REST", style = MaterialTheme.typography.labelLarge, color = PoliceColors.LightBlue)
                            Text(when { finished -> "Saved with your last set"
                                progress?.isPaused == true -> "Cool-down paused"
                                remaining == 0L -> "Saving your workout…"
                                else -> "One-minute cool-down · counts as break time" }, style = MaterialTheme.typography.bodySmall,
                                color = PoliceColors.Muted)
                        }
                        Text(sessionTime(if (finished) finalRest else ((remaining + 999) / 1000) * 1000),
                            style = StatTypography.copy(fontSize = 28.sp), modifier = Modifier.testTag("summary-rest-countdown"))
                    }
                    Text("Recorded break: ${sessionTime(finalRest)}", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("summary-rest-recorded"))
                    if (!finished) PoliceOutlinedButton(onClick = onPauseResume, enabled = !action.saving,
                        modifier = Modifier.fillMaxWidth().testTag("summary-pause-resume")) {
                        Text(if (progress?.isPaused == true) "Resume cool-down" else "Pause cool-down")
                    }
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = PoliceCardShape, color = PoliceColors.Card,
                border = BorderStroke(1.dp, PoliceColors.Border)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Lbs lifted" to pounds, "Active" to sessionTime(activeMillis), "Break" to sessionTime(breakMillis))
                        .forEach { (label, value) -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
                            Text(value, style = StatTypography.copy(fontSize = 18.sp))
                        } }
                }
            }
            workout.orderedExercises().forEach { entry ->
                val sets = entry.sets.filter { it.completedAt != null }
                if (sets.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${sets.size} sets · ${sets.sumOf { (it.actualReps ?: it.reps).toLong() }} reps",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
            }
            if (!finished) PoliceOutlinedButton(onClick = onReviewSets, enabled = !action.saving,
                modifier = Modifier.fillMaxWidth().testTag("summary-review-sets")) { Text("Review completed sets") }
            PoliceButton(onClick = onHistory, enabled = finished && !action.saving,
                modifier = Modifier.fillMaxWidth().testTag("summary-view-history")) { Text("View Workout Log") }
            action.error?.let { Text(it, color = PoliceColors.Error) }
        }
    }
}
