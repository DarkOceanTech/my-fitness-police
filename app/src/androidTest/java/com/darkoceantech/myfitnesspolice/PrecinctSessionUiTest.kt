package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrecinctSessionUiTest {
    @get:Rule val compose = createComposeRule()
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
    @Test fun hqSettingsAndFieldTimerAreAvailableUnderRenamedNavigation() {
        val db = database()
        try {
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNodeWithText("HQ").assertIsDisplayed()
            capture("dispatch-hq")
            compose.onNodeWithTag("open-settings").performClick()
            compose.onNodeWithTag("settings-name").assertExists()
            compose.onNodeWithContentDescription("Back to Dispatch").performClick()
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithTag("pto-grid").performScrollToNode(hasText("Circuit and Time Tracking"))
            compose.onNodeWithTag("pto-grid").performScrollToNode(hasTestTag("pto-timer-tile"))
            compose.onNodeWithTag("pto-timer-tile").performClick()
            compose.onNodeWithTag("pto-construction-title").assertTextEquals("Timer")
            compose.onNodeWithTag("pto-construction-dismiss").performClick()
            compose.onNodeWithTag("pto-kayaking-tile").assertDoesNotExist()
            compose.onNodeWithTag("pto-snowboarding-tile").assertDoesNotExist()
            capture("field-timer")
            compose.onNode(hasText("Precinct") and hasClickAction()).performClick()
            compose.onNodeWithTag("precinct-home").assertIsDisplayed()
            compose.onNodeWithTag("precinct-timer-tile").assertDoesNotExist()
            compose.onNodeWithTag("precinct-community-tile").assertExists()
            capture("precinct")
        } finally { db.close() }
    }
    @Test fun countdownCancelDoesNotStartAndPlannedEditsAreExplicitAndLastSessionIsReadOnly() {
        val db = database()
        val repo = FitnessRepository(db)
        val instrument = InstrumentationRegistry.getInstrumentation()
        val landscape = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape"
        instrument.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
        runBlocking {
            db.exerciseDao().insert(Exercise(id = "e", name = "Cable row", equipment = "Cable"))
            for (id in listOf("w", "history")) {
                db.workoutDao().insert(Workout(id = id, name = "Back patrol", kind = "session", startedAt = 1,
                    finishedAt = if (id == "history") 100 else null))
                db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-e", workoutId = id, exerciseId = "e", position = 0, notes = "Exercise note"))
                for (position in 0..1) db.workoutSetDao().insert(WorkoutSet(id = "$id-$position", workoutExerciseId = "$id-e", position = position,
                    reps = 10, weightGrams = 10000, completedAt = if (id == "history") 50 else null,
                    actualReps = if (id == "history") 12 else null, activeMillis = 1000, restMillis = 2000))
            }
            repo.sessionProgress.prepareSession("w")
        }
        try {
            val model = SessionViewModel(repo)
            compose.setContent { MyFitnessPoliceTheme { ActiveWorkoutScreen(model, onBack = {}, onFinished = {}) } }
            waitTag("set-action-w-0")
            compose.onNodeWithTag("set-action-w-0").performScrollTo().performClick()
            waitTag("set-start-countdown")
            assertEquals("ready", runBlocking { db.sessionStateDao().get("w")!!.phase })
            capture("countdown-${if (landscape) "landscape" else "portrait"}")
            compose.onNodeWithTag("cancel-set-countdown").performClick()
            waitGone("set-start-countdown")
            assertNull(runBlocking { db.sessionStateDao().get("w")!!.dutyStartedAt })
            openMenu()
            compose.onNodeWithText("Edit planned sets").performClick()
            waitTag("active-planned-sets-dialog")
            chooseReps("12")
            capture("planned-editor-${if (landscape) "landscape" else "portrait"}")
            compose.onNodeWithTag("cancel-planned-sets").performClick()
            assertEquals(10, runBlocking { db.workoutSetDao().getForExercise("w-e").first().reps })
            openMenu()
            compose.onNodeWithText("Edit planned sets").performClick()
            chooseReps("12")
            compose.onNodeWithTag("save-planned-sets").performClick()
            waitGone("active-planned-sets-dialog")
            assertEquals(12, runBlocking { db.workoutSetDao().getForExercise("w-e").first().reps })
            openMenu()
            compose.onNodeWithText("View last session").performClick()
            waitTag("last-session-summary")
            compose.onNodeWithTag("import-session").assertDoesNotExist()
            capture("active-last-session-${if (landscape) "landscape" else "portrait"}")
            compose.onNodeWithTag("close-last-session").performClick()
            waitGone("last-session-dialog")
            val clickTime = System.currentTimeMillis()
            compose.onNodeWithTag("set-action-w-0").performScrollTo().performClick()
            compose.waitUntil(15000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
            assertTrue(runBlocking { db.sessionStateDao().get("w")!!.phaseStartedAt!! } - clickTime >= 4500)
            waitGone("set-start-countdown")
            runBlocking { repo.sessionProgress.completeSet("w", "w-0"); repo.sessionProgress.recordActual("w", "w-0", 14) }
            compose.onNodeWithTag("set-action-w-0").performScrollTo().performClick()
            waitTag("active-set-planned")
            assertRepColor("active-set-planned", PoliceColors.Red)
            assertRepColor("active-set-actual", PoliceColors.Red)
            capture("rep-over-target-${if (landscape) "landscape" else "portrait"}")
            runBlocking { repo.correctActiveSet("w", "w-0", null, 12, 8, null, false) }
            compose.waitUntil(5000) { compose.onAllNodesWithText("8").fetchSemanticsNodes().isNotEmpty() }
            assertRepColor("active-set-planned", PoliceColors.LightBlue)
            assertRepColor("active-set-actual", PoliceColors.LightBlue)
        } finally { instrument.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE); db.close() }
    }
    private fun openMenu() { compose.onNodeWithTag("session-exercise-options-w-e").performScrollTo().performClick() }
    private fun chooseReps(value: String) {
        compose.onNodeWithTag("planned-reps-w-0").performScrollTo().performClick()
        compose.onNodeWithTag("planned-reps-w-0-options").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(hasTestTag("planned-reps-w-0-options"))).performClick()
    }
    private fun assertRepColor(tag: String, color: androidx.compose.ui.graphics.Color) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(if (tag.endsWith("planned")) "12" else if (color == PoliceColors.Red) "14" else "8") and
            hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(color, results.single().layoutInput.style.color)
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitGone(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync(); android.os.SystemClock.sleep(500)
        val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "precinct-session-review").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot()
        try { java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
