package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class WeightPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape"
    private fun orient() {
        instrument.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
    }
    @Test fun formattedWeightKeepsPrecisionWhenSavingRepsAndPickerSavesRawNumbers() {
        orient()
        val db = Room.inMemoryDatabaseBuilder(instrument.targetContext, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            db.exerciseDao().insert(Exercise("row", "Row", "Cable"))
            db.workoutDao().insert(Workout(id = "log", startedAt = 1, finishedAt = 100))
            db.workoutExerciseDao().insert(WorkoutExercise("entry", "log", "row", 0))
            db.workoutSetDao().insert(WorkoutSet("set", "entry", 0, 10, 560170, completedAt = 50, actualReps = 9))
        }
        var set by mutableStateOf(runBlocking { db.workoutSetDao().getForExercise("entry").single() })
        var action by mutableStateOf(SessionAction())
        val submittedWeights = mutableListOf<String?>()
        try {
            compose.setContent { MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(), color = Color.Transparent, contentColor = PoliceColors.Text) {
                    RecordedSetInfoPanel("Row", set, action, onEdit = {}, onSaveNote = {}, totals = {}, onSave = { pounds, planned, actual, rpe, warmup ->
                        submittedWeights += pounds
                        runBlocking { repo.correctHistorySet("log", "set", pounds?.toBigDecimal()?.multiply(BigDecimal("453.59237"))?.setScale(0, RoundingMode.HALF_UP)?.longValueExact(),
                            planned.toInt(), actual.toInt(), rpe, warmup) }
                        set = runBlocking { db.workoutSetDao().getForExercise("entry").single() }
                        action = action.copy(revision = action.revision + 1, completedAction = "correct-history-set")
                    })
                }
            } }
            compose.onNodeWithTag("history-set-weight").assert(hasAnyDescendant(hasText("1,235")))
            capture("set-details-rounded-weight")
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            pick("history-set-planned", "12")
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { submittedWeights.size == 1 }
            assertNull(submittedWeights.single()); assertEquals(560170L, set.weightGrams); assertEquals(12, set.reps)
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            pick("history-set-weight", "1,250")
            capture("set-details-edit-rounded-weight")
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { submittedWeights.size == 2 }
            assertEquals("1250", submittedWeights.last()); assertEquals(566990L, set.weightGrams)
        } finally { instrument.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE); db.close() }
    }
    @Test fun largeChartWeightsUseWholeNumbersAndCommasInDataReadout() {
        orient()
        val chart = DashboardChart("weekly-volume", "Weekly volume progress", "1,234,568 Lbs-reps", "Last 8 weeks", ChartKind.LINE,
            listOf("Sep 14", "Sep 21", "Sep 28"), listOf(ChartSeries("Volume", listOf(560170.2, 800000.5, 1234567.5))), "Lbs-reps")
        try {
            compose.setContent { MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(), color = Color.Transparent, contentColor = PoliceColors.Text) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DashboardChartCard(chart, onInsights = {}) }
                }
            } }
            compose.onNodeWithTag("weekly-volume-chart").assertContentDescriptionContains("Sep 28 1,234,568", substring = true)
            compose.onNodeWithTag("weekly-volume-chart").performScrollTo().performTouchInput { click(percentOffset(.95f, .5f)) }
            compose.onNodeWithTag("weekly-volume-point").assertTextContains("1,234,568", substring = true)
            capture("chart-comma-weight-axis")
        } finally { instrument.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
    private fun pick(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("$tag-options").performScrollToNode(hasText(text))
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag("$tag-options"))).performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); instrument.waitForIdleSync(); android.os.SystemClock.sleep(1000)
        val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "weight-swap-review").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot()
        try { java.io.File(directory, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
