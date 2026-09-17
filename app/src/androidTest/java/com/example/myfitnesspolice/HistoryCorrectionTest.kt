package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfitnesspolice.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryCorrectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renameCorrectAndAnnotateRecordedSetWithoutChangingTemplate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "history-corrections-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        var db = open()
        val repo = FitnessRepository(db)
        var planId = ""
        var workoutId = ""
        var otherId = ""
        lateinit var recorded: WorkoutSet
        runBlocking {
            repo.addExercise("Bench press", "Barbell")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            planId = repo.observeSessions().first().single().workout.id
            repo.savePlan(planId, "Original template")
            workoutId = repo.startPlan(planId)
            recorded = repo.observeWorkout(workoutId).first()!!.orderedSets().single()
            repo.sessionProgress.completeSet(workoutId, recorded.id)
            repo.sessionProgress.recordActual(workoutId, recorded.id, 8)
            repo.finishWorkout(workoutId)
            recorded = repo.observeWorkout(workoutId).first()!!.orderedSets().single()
            otherId = repo.startPlan(planId)
            repo.finishWorkout(otherId)
        }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("DOR") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            waitTag("history-workout-$workoutId")
            compose.onNodeWithTag("history-workout-$workoutId").performClick()
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete all").assertDoesNotExist()
            compose.onNodeWithText("Edit workout name").performClick()
            compose.onNodeWithTag("history-workout-name").performTextReplacement("")
            compose.onNodeWithTag("save-history-workout-name").assertIsNotEnabled()
            compose.onNodeWithTag("history-workout-name").performTextInput("Corrected session")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-history-workout-name").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-workout-name").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("session-set-${recorded.id}").performScrollTo().performClick()
            waitTag("history-set-info-panel")
            compose.onNodeWithTag("history-set-actual").assertHasNoClickAction()
            compose.onNodeWithTag("history-set-rpe").assertHasNoClickAction().assert(hasText("—") or hasAnyDescendant(hasText("—")))
            compose.onNodeWithTag("save-history-set").assertDoesNotExist()
            compose.onNodeWithText("Cancel").assertDoesNotExist()
            val header = compose.onNodeWithTag("history-set-info-header").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("history-set-info-header")
                .assert(hasAnyAncestor(hasTestTag("history-workout-totals")))
                .assert(hasAnyAncestor(hasTestTag("history-set-info-scroll")))
            val back = compose.onNodeWithContentDescription("Close set info").fetchSemanticsNode().boundsInRoot
            assertTrue(back.bottom <= header.top)
            val weightBounds = compose.onNodeWithTag("history-set-weight").fetchSemanticsNode().boundsInRoot
            val plannedBounds = compose.onNodeWithTag("history-set-planned").fetchSemanticsNode().boundsInRoot
            val actualBounds = compose.onNodeWithTag("history-set-actual").fetchSemanticsNode().boundsInRoot
            val rpeBounds = compose.onNodeWithTag("history-set-rpe").fetchSemanticsNode().boundsInRoot
            assertEquals(weightBounds.left, plannedBounds.left, 1f)
            assertEquals(rpeBounds.left, actualBounds.left, 1f)
            assertTrue(plannedBounds.top >= weightBounds.bottom && actualBounds.top >= rpeBounds.bottom)
            screenshot("history-set-read-only")
            compose.onNodeWithTag("history-set-info-scroll").performTouchInput { swipeUp() }
            assertEquals(back.top, compose.onNodeWithContentDescription("Close set info").fetchSemanticsNode().boundsInRoot.top, 1f)
            // Cancel works before changes, and discards every pending numeric value after changes.
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("cancel-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-rpe").assertHasNoClickAction()
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            pick("history-set-weight", "100")
            pick("history-set-planned", "15")
            pick("history-set-actual", "5")
            pick("history-set-rpe", "10")
            compose.onNodeWithTag("save-history-set").assertIsEnabled()
            compose.onNodeWithTag("cancel-history-set").performScrollTo().performClick()
            assertEquals(recorded, runBlocking { db.workoutDao().getDetails(workoutId)!!.orderedSets().single() })
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-weight").assert(hasText("0") or hasAnyDescendant(hasText("0")))
            compose.onNodeWithTag("history-set-planned").assert(hasText("10") or hasAnyDescendant(hasText("10")))
            compose.onNodeWithTag("history-set-actual").assert(hasText("8") or hasAnyDescendant(hasText("8")))
            compose.onNodeWithTag("history-set-rpe").assert(hasText("—") or hasAnyDescendant(hasText("—")))
            compose.onNodeWithTag("cancel-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-note-input").performTextInput("Discard this note")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("", runBlocking { db.workoutDao().getDetails(workoutId)!!.orderedSets().single().notes })
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            pick("history-set-planned", "12")
            pick("history-set-rpe", "7")
            compose.onNodeWithTag("save-history-set").assertIsEnabled()
            compose.onNodeWithTag("history-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-note-input").performTextInput("Corrected from my paper log.")
            Espresso.closeSoftKeyboard()
            screenshot("history-set-note-dialog")
            compose.onNodeWithTag("save-history-set-note").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-set-note-input").fetchSemanticsNodes().isEmpty() }
            // Saving a note must not discard the pending numeric corrections.
            compose.onNodeWithTag("save-history-set").assertIsEnabled()
            pick("history-set-weight", "65")
            pick("history-set-actual", "9")
            screenshot("history-set-edit")
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("edit-history-set").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-set-info-panel").assertExists()
            compose.onNodeWithTag("history-set-actual").assertHasNoClickAction()
            compose.onNodeWithTag("history-set-rpe").assertHasNoClickAction().assert(hasText("7") or hasAnyDescendant(hasText("7")))
            compose.onNodeWithTag("cancel-history-set").assertDoesNotExist()
            // A second canceled edit restores the saved rating, not the initial unrecorded value.
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            pick("history-set-rpe", "1")
            compose.onNodeWithTag("cancel-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-rpe").assert(hasText("7") or hasAnyDescendant(hasText("7")))
            compose.onNodeWithTag("history-workout-totals").performScrollTo().assert(hasAnyDescendant(hasText("1 sets · 9 reps")))
            compose.onNodeWithContentDescription("Close set info").performClick()
            compose.onNodeWithContentDescription("Back to history").performClick()
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Edit workout names").performClick()
            compose.onNodeWithTag("rename-workout-selector").performClick()
            compose.onNodeWithTag("rename-workout-$workoutId").performClick()
            compose.onNodeWithTag("history-workout-name").performTextReplacement("Renamed from history list")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-history-workout-name").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-workout-name").fetchSemanticsNodes().isEmpty() }
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("weekly-Reps-value") and hasText("9")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("weekly-Weight-value").performScrollTo().assertTextEquals("585")
            runBlocking {
                db.close(); db = open()
                val workout = db.workoutDao().getDetails(workoutId)!!
                val result = workout.orderedSets().single()
                assertEquals("Renamed from history list", workout.workout.name)
                assertEquals(29484L, result.weightGrams)
                assertEquals(12, result.reps)
                assertEquals(9, result.actualReps)
                assertEquals(7, result.rpe)
                assertEquals("Corrected from my paper log.", result.notes)
                assertEquals(recorded.completedAt, result.completedAt)
                assertEquals(recorded.activeMillis, result.activeMillis)
                assertEquals(recorded.restMillis, result.restMillis)
                val template = db.workoutDao().getDetails(planId)!!
                assertEquals("Original template", template.workout.name)
                assertEquals(10, template.orderedSets().single().reps)
                assertEquals(0L, template.orderedSets().single().weightGrams)
                assertNull(template.orderedSets().single().rpe)
                assertEquals("Original template", db.workoutDao().getDetails(otherId)!!.workout.name)
            }
        } finally { db.close(); context.deleteDatabase(databaseName) }
    }

    @Test fun rejectInvalidHistoryCorrectionsAndPreserveExactUnchangedWeight() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        try {
            repo.addExercise("Row", "Cable")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            val plan = repo.observeSessions().first().single()
            repo.savePlan(plan.workout.id, "Row template")
            val session = repo.startPlan(plan.workout.id)
            val set = repo.observeWorkout(session).first()!!.orderedSets().single()
            suspend fun rejected(operation: suspend () -> Unit) {
                try { operation(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
            }
            rejected { repo.renameHistoryWorkout(plan.workout.id, "Wrong target") }
            rejected { repo.correctHistorySet(session, set.id, 100L, 12, 8, null) }
            repo.sessionProgress.completeSet(session, set.id)
            repo.sessionProgress.recordActual(session, set.id, 8)
            repo.finishWorkout(session)
            val before = db.workoutDao().getDetails(session)!!
            rejected { repo.renameHistoryWorkout(session, " ") }
            rejected { repo.correctHistorySet(session, plan.orderedSets().single().id, 100L, 12, 8, null) }
            rejected { repo.correctHistorySet(session, set.id, -1L, 12, 8, null) }
            rejected { repo.correctHistorySet(session, set.id, 100L, 0, 8, null) }
            rejected { repo.correctHistorySet(session, set.id, 100L, 12, -1, null) }
            rejected { repo.correctHistorySet(session, set.id, 100L, 12, 8, 0) }
            rejected { repo.correctHistorySet(session, set.id, 100L, 12, 8, 11) }
            assertEquals(before, db.workoutDao().getDetails(session))
            repo.correctHistorySet(session, set.id, 12345L, 12, 0, null)
            repo.correctHistorySet(session, set.id, null, 13, 1, null)
            repo.saveHistorySetNote(session, set.id, " Note only ")
            val corrected = db.workoutDao().getDetails(session)!!.orderedSets().single()
            assertEquals(12345L, corrected.weightGrams)
            assertEquals(13, corrected.reps)
            assertEquals(1, corrected.actualReps)
            assertEquals("Note only", corrected.notes)
            assertEquals(before.orderedSets().single().completedAt, corrected.completedAt)
        } finally { db.close() }
    }

    private fun pick(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("$tag-options").performTouchInput { swipeUp() }
        compose.onNodeWithTag("$tag-options").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasClickAction()).performClick()
    }
    private fun waitTag(tag: String) { compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Let the display compositor present the frame before capturing it.
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "history-correction-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
