package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutSessionFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()

    @Test fun doneUsesRepPickerAndNoteBadgeAndTypeCorrectionsWorkInActiveAndHistory() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db, 2); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            startFirst(db)
            compose.onNodeWithTag("set-action-s1").assert(hasText("Done"))
            compose.onNodeWithTag("set-action-s1").performClick()
            waitTag("actual-reps-input")
            choose("actual-reps-input", "8")
            compose.onNodeWithTag("after-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("after-set-note-input").performTextInput("Controlled lowering")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Save note").performClick()
            compose.onNodeWithText("Save reps").performClick()
            compose.waitUntil(5000) { runBlocking { db.workoutSetDao().getForExercise("we").first().notes.isNotEmpty() } }
            compose.onNodeWithTag("set-action-s1").performScrollTo()
            compose.onNodeWithTag("set-note-indicator-s1", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("set-action-s1").performClick()
            compose.onNodeWithTag("edit-active-set").performScrollTo().performClick()
            screenshot("active-inline-type-edit")
            choose("active-set-type", "Warm-up")
            compose.onNodeWithTag("save-active-set").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { db.workoutSetDao().getForExercise("we").first().isWarmup } }
            compose.onNodeWithText("Warm-up Set").assertExists()
            screenshot("active-warm-up-details")
            compose.onNodeWithContentDescription("Back to exercise").performClick()
            compose.onNodeWithTag("finish-workout").performScrollTo().performClick()
            compose.onNodeWithText("Finish", substring = false).performClick()
            waitTag("history-detail")
            compose.onNodeWithTag("set-action-s1").performScrollTo()
            compose.onNodeWithTag("set-note-indicator-s1", useUnmergedTree = true).assertExists()
            screenshot("history-note-indicator")
            compose.onNodeWithTag("set-action-s1").performClick()
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            screenshot("history-inline-type-edit")
            choose("history-set-type", "Working set")
            compose.onNodeWithTag("cancel-history-set").performScrollTo().performClick()
            assertTrue(runBlocking { db.workoutSetDao().getForExercise("we").first().isWarmup })
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            choose("history-set-type", "Working set")
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { !db.workoutSetDao().getForExercise("we").first().isWarmup } }
            runBlocking {
                val set = db.workoutSetDao().getForExercise("we").first()
                assertEquals(8, set.actualReps); assertEquals(10, set.reps); assertEquals("Controlled lowering", set.notes)
            }
        } finally { db.close() }
    }

    @Test fun finalSaveOpensSummaryWithPausableFinalRestAndStaysAfterAutoFinish() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db, 1); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession(); startFirst(db)
            compose.onNodeWithTag("set-action-s1").performClick()
            waitTag("actual-reps-input")
            choose("actual-reps-input", "9")
            compose.onNodeWithText("Save reps").performClick()
            waitTag("workout-session-summary")
            compose.onNodeWithTag("summary-final-rest").assertIsDisplayed()
            compose.onNodeWithTag("summary-view-history").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("summary-pause-resume").performScrollTo().performClick()
            waitTag("pause-reason-input")
            compose.onNodeWithText("Close").performClick()
            assertTrue(runBlocking { db.sessionStateDao().get("w")!!.isPaused })
            compose.onNodeWithText("Cool-down paused").assertExists()
            compose.onNodeWithTag("summary-pause-resume").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { !db.sessionStateDao().get("w")!!.isPaused } }
            screenshot("final-rest-summary")
            runBlocking {
                val state = db.sessionStateDao().get("w")!!
                db.sessionStateDao().save(state.copy(phaseStartedAt = System.currentTimeMillis() - 61_000))
            }
            compose.waitUntil(8000) { runBlocking { db.workoutDao().getDetails("w")!!.workout.finishedAt != null } }
            compose.onNodeWithTag("workout-session-summary").assertIsDisplayed()
            compose.onNodeWithTag("summary-rest-recorded").performScrollTo().assertTextEquals("Recorded break: 1:00")
            screenshot("finished-summary")
            compose.onNodeWithTag("summary-view-history").performScrollTo().assertIsEnabled().performClick()
            waitTag("history-detail")
            assertEquals(60_000L, runBlocking { db.workoutSetDao().getForExercise("we").single().restMillis })
            assertEquals(9, runBlocking { db.workoutSetDao().getForExercise("we").single().actualReps })
        } finally { db.close() }
    }

    private fun choose(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("$tag-options").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(hasTestTag("$tag-options"))).performClick()
    }
    private fun startFirst(db: FitnessDatabase) {
        compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
    }
    private fun openSession() {
        compose.onNode(hasText("Academy") and hasClickAction()).performClick()
        compose.onNodeWithTag("workout-section-0").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("return-to-active-workout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("return-to-active-workout").performClick()
        waitTag("training-ready")
    }
    private suspend fun seed(db: FitnessDatabase, count: Int) {
        db.exerciseDao().insert(Exercise(id = "e", name = "Seated cable row", equipment = "Cable machine"))
        db.workoutDao().insert(Workout(id = "w", name = "Back training"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "we", workoutId = "w", exerciseId = "e", position = 0))
        (1..count).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "we", position = it - 1, reps = 10, weightGrams = 20000)) }
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "workout-flow-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
