package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfitnesspolice.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutRemovalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun removingCardsPersistsWithoutChangingCatalogOrSessions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "removal-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        val repo = FitnessRepository(db)
        var sessionId = ""
        var removedId = ""
        val planId = runBlocking {
            repo.addExercise("Dumbbell Curl", "Dumbbells")
            repo.addExercise("Cable Row", "Cable machine")
            val catalog = repo.observeExercises().first()
            repo.chooseExercise(catalog.single { it.name == "Dumbbell Curl" }.id)
            repo.chooseExercise(catalog.single { it.name == "Cable Row" }.id)
            val draft = repo.observeSessions().first().single()
            val curl = draft.orderedExercises().first()
            removedId = curl.workoutExercise.id
            repo.updateExerciseNote(draft.workout.id, removedId, "Keep elbows close and control each rep.")
            repo.updateSetFields(draft.workout.id, removedId, curl.sets.single().id, reps = 12, grams = 15876)
            repo.saveMetadata(draft.workout.id, "Back,Biceps", "Thursday", "Arm Focus")
            repo.savePlan(draft.workout.id)
            sessionId = repo.startPlan(draft.workout.id)
            repo.finishWorkout(sessionId)
            draft.workout.id
        }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            screenshot("home")
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            waitFor("Edit workout")
            compose.onNodeWithTag("toggle-all-exercises").performScrollTo().performClick()
            screenshot("edit-metadata")
            compose.onNodeWithContentDescription("Workout options").performClick()
            compose.onNodeWithText("Edit details").performClick()
            screenshot("metadata-dialog")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Add Set"))
            screenshot("exercise-card")
            compose.onNodeWithContentDescription("Exercise options for Dumbbell Curl").performClick()
            compose.onNodeWithText("Edit note").assertDoesNotExist()
            compose.onNodeWithText("Remove", substring = false).performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(planId).first()!!.exercises.size == 1 } }
            compose.onNodeWithTag("exercise-card-$removedId").assertDoesNotExist()
            runBlocking {
                val remaining = repo.observeWorkout(planId).first()!!.exercises.single()
                assertEquals("Cable Row", remaining.exercise.name)
                assertEquals(0, remaining.workoutExercise.position)
                assertTrue(db.workoutSetDao().getForExercise(removedId).isEmpty())
                assertEquals(2, repo.observeExercises().first().size)
                assertEquals(2, repo.observeWorkout(sessionId).first()!!.exercises.size)
                // The operation is scoped to the plan and rejects session IDs.
                val sessionEntry = repo.observeWorkout(sessionId).first()!!.exercises.first().workoutExercise.id
                assertTrue(runCatching { repo.removeWorkoutExercise(sessionId, sessionEntry) }.isFailure)
            }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasContentDescription("Exercise options for Cable Row"))
            compose.onNodeWithContentDescription("Exercise options for Cable Row").performClick()
            compose.onNodeWithText("Remove", substring = false).performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(planId).first()!!.exercises.isEmpty() } }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithText("Start My Workout").assertDoesNotExist()
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNode(hasText("DOR") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-workout-$sessionId").fetchSemanticsNodes().isNotEmpty() }
            screenshot("history")
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            waitFor("Cable Row")
            screenshot("exercises")
            runBlocking {
                db.close()
                db = open()
                assertTrue(db.workoutDao().getDetails(planId)!!.exercises.isEmpty())
                assertTrue(db.workoutSetDao().getForExercise(removedId).isEmpty())
                assertEquals(2, db.exerciseDao().getAll().size)
                assertEquals(2, db.workoutDao().getDetails(sessionId)!!.exercises.size)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose can be idle while Android is still fading in a dialog window.
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "police-theme-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
