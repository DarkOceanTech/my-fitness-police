package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class WorkoutEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun metadataCancelSaveAndDragOrderPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "editor-" + java.util.UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        val repo = FitnessRepository(db)
        val planId = runBlocking {
            repo.addExercise("Curl", "Dumbbell")
            repo.addExercise("Row", "Cable")
            val catalog = repo.observeExercises().first()
            repo.chooseExercise(catalog.single { it.name == "Curl" }.id)
            repo.chooseExercise(catalog.single { it.name == "Row" }.id)
            repo.setWorkoutDetails("muscles", "Biceps")
            repo.setWorkoutDetails("day", "Monday")
            repo.setWorkoutDetails("plan", "Arm Focus")
            val id = repo.observeSessions().first().single().workout.id
            repo.savePlan(id)
            id
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            waitFor("Edit workout")
            fun saveControl() = compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            saveControl()
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            compose.onNodeWithText("Training plans").assertDoesNotExist()
            captureFilterReview(compose, "workout-save-disabled")
            compose.onNodeWithText("Day of the Week").assertDoesNotExist()
            compose.onNodeWithText("Start My Workout").assertDoesNotExist()
            fun editName() {
                compose.onNodeWithContentDescription("Workout options").performClick()
                compose.onNodeWithText("Edit details").performClick()
                compose.onNodeWithText("Day of week").assertDoesNotExist()
                compose.onNodeWithTag("workout-training-plans").assertDoesNotExist()
                compose.onNodeWithTag("save-workout-details").assertIsNotEnabled()
                compose.onNodeWithTag("workout-name").performTextReplacement("Arm strength")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
            }
            editName()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertEquals("", repo.observeWorkout(planId).first()!!.workout.name) }
            saveControl()
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            editName()
            captureFilterReview(compose, "edit-details-modal")
            compose.onNodeWithTag("save-workout-details").performClick()
            waitFor("Arm strength")
            runBlocking { assertEquals("Arm strength", repo.observeWorkout(planId).first()!!.workout.name) }
            saveControl()
            compose.onNodeWithTag("save-workout").assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitFor("Saved")
            compose.onNodeWithTag("plan-saved").assertIsDisplayed()
            assertTrue(compose.onNodeWithTag("plan-saved").fetchSemanticsNode().boundsInRoot.bottom <=
                compose.onNodeWithTag("workout-builder").fetchSemanticsNode().boundsInRoot.top)
            captureFilterReview(compose, "workout-saved-banner")
            compose.onNodeWithContentDescription("Dismiss saved notification").performClick()
            saveControl()
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()

            // Room updates must enable Save, while restoring the last saved value disables it again.
            val noteEntry = runBlocking { repo.observeWorkout(planId).first()!!.orderedExercises().first().workoutExercise.id }
            runBlocking { repo.updateExerciseNote(planId, noteEntry, "Keep elbows close") }
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("save-workout") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            runBlocking { repo.updateExerciseNote(planId, noteEntry, "") }
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("save-workout") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("toggle-all-exercises"))
            compose.onNodeWithTag("plan-totals").assertTextEquals("2 sets · 20 reps")
            compose.onNodeWithText("Expand all").assertExists()
            compose.onNodeWithContentDescription("Expand Curl").assertExists()
            compose.onNodeWithContentDescription("Expand Row").assertExists()
            compose.onNodeWithTag("toggle-all-exercises").performClick()
            compose.onNodeWithText("Collapse all").assertExists()
            compose.onNodeWithTag("toggle-all-exercises").performClick()
            compose.onAllNodesWithText("Add Set").assertCountEquals(0)
            saveControl()
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasContentDescription("Drag to reorder Curl"))
            val first = compose.onNodeWithContentDescription("Drag to reorder Curl").fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithContentDescription("Drag to reorder Row").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Drag to reorder Curl").performTouchInput {
                down(center)
                advanceEventTime(600)
                moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 300)
                advanceEventTime(100)
                up()
            }
            compose.waitUntil(5000) {
                runBlocking { repo.observeWorkout(planId).first()!!.orderedExercises().first().exercise.name == "Row" }
            }
            compose.onNodeWithContentDescription("Expand Curl").performClick()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Add Set"))
            compose.onNodeWithText("Add Set").assertIsDisplayed()

            runBlocking {
                db.close()
                db = open()
                val loaded = db.workoutDao().getDetails(planId)!!
                assertEquals("Monday", loaded.workout.dayOfWeek) // Legacy schedule remains stored, but belongs on training plans in the UI.
                assertEquals(listOf("Row", "Curl"), loaded.orderedExercises().map { it.exercise.name })
                assertEquals(listOf(0, 1), loaded.orderedExercises().map { it.workoutExercise.position })
                assertTrue(loaded.exercises.all { it.sets.size == 1 })
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}

