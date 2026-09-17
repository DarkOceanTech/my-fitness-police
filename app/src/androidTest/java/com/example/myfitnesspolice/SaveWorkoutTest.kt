package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfitnesspolice.data.*
import com.example.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SaveWorkoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun saveCreatesHomeCardAndClearPreservesSavedWorkout() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            repo.addExercise("Curl", "Dumbbell")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            repo.setWorkoutDetails("muscles", "Biceps,Triceps")
            repo.setWorkoutDetails("day", "Friday")
            repo.setWorkoutDetails("plan", "Arm Focus")
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            waitFor("Create My First Workout")
            compose.onNodeWithTag("create-first-workout").performScrollTo().performClick()
            waitFor("Save")
            compose.onNodeWithTag("save-workout").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitFor("Workouts")
            compose.onNodeWithText("Biceps, Triceps").assertExists()
            compose.onNodeWithText("Arm Focus").assertDoesNotExist()
            compose.onNodeWithText("Friday").assertDoesNotExist()
            compose.onNodeWithTag("create-first-workout").assertDoesNotExist()
            runBlocking {
                assertTrue(repo.observeHistory().first().isEmpty())
                assertTrue(repo.observeUnfinished().first().isEmpty())
                val saved = repo.observeSessions().first().single().workout
                assertEquals("plan", saved.kind)
                assertNull(saved.finishedAt)
                assertEquals("Biceps,Triceps", saved.targetMuscles)
                assertEquals("Friday", saved.dayOfWeek)
                assertEquals("Arm Focus", saved.trainingPlan)
                repo.setWorkoutDetails("plan", "Arm Focus")
            }
            compose.onNodeWithContentDescription("Add workout").performClick()
            waitFor("Clear")
            compose.onNodeWithText("Clear").performScrollTo().performClick()
            compose.onNodeWithText("Clear draft").performClick()
            waitFor("Select muscles")
            compose.onNodeWithTag("workout-training-plans").assertDoesNotExist()
            compose.onNodeWithText("Select muscles").assertExists()
            compose.onNodeWithText("Select day").assertDoesNotExist()
            runBlocking {
                assertTrue(repo.observeUnfinished().first().isEmpty())
                assertTrue(repo.observeHistory().first().isEmpty())
                assertEquals(1, repo.observeSessions().first().size)
                assertEquals("plan", repo.observeSessions().first().single().workout.kind)
            }
        } finally { db.close() }
    }
    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}

