package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkoceantech.myfitnesspolice.ui.screens.*
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

class WorkoutFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordCorrectFinishAndReviewWorkout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        runBlocking { repository.addExercise("Test squat", "Barbell") }
        try {
            // Exercise the persisted recording screen independently of the sample home layout.
            compose.setContent {
                MyFitnessPoliceTheme {
                    val model: SessionViewModel = viewModel(factory = FitnessViewModelFactory(repository))
                    var history by remember { mutableStateOf(false) }
                    Column {
                        Row {
                            TextButton(onClick = { history = false }) { Text("Workout") }
                            TextButton(onClick = { history = true }) { Text("History") }
                        }
                        key(history) { WorkoutFlowScreen(model, history) }
                    }
                }
            }
            waitFor("Start workout")
            compose.onNodeWithText("Start workout").performClick()
            waitFor("Choose exercise")
            compose.onNodeWithText("Choose exercise").performClick()
            compose.onNodeWithText("Test squat").performClick()
            waitFor("Add set")
            compose.onNodeWithText("Add set").performClick()
            choose("Reps", "5")
            choose("Weight (lbs)", "60")
            compose.onNodeWithText("Save set").performClick()
            waitFor("5 reps × 60 lbs")
            compose.onNodeWithText("Edit set").performClick()
            choose("Reps", "6")
            compose.onNodeWithText("Save set").performClick()
            waitFor("6 reps × 60 lbs")
            compose.onNode(hasText("History") and hasClickAction()).performClick()
            compose.onNode(hasText("Workout") and hasClickAction()).performClick()
            waitFor("6 reps × 60 lbs")
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("Finish", substring = false).performClick()
            waitFor("Start workout")
            compose.onNode(hasText("History") and hasClickAction()).performClick()
            waitFor("1 sets · 6 reps")
            val workoutId = runBlocking { repository.observeHistory().first().single().id }
            compose.onNodeWithTag("history-workout-" + workoutId).performClick()
            waitFor("Test squat")
            compose.onNodeWithText("Completed").assertDoesNotExist()
            compose.onNodeWithContentDescription("Info for set 1 of Test squat").assertIsDisplayed()
            runBlocking {
                val session = repository.observeSessions().first().single()
                assertNotNull(session.workout.finishedAt)
                assertEquals(6, session.exercises.single().sets.single().reps)
            }
        } finally { db.close() }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun choose(label: String, value: String) {
        compose.onNodeWithContentDescription(label).performClick()
        compose.onNodeWithTag("$label options").performScrollToNode(hasText(value))
        compose.onNodeWithText(value, substring = false).performClick()
    }

    @Test fun unfinishedWorkoutResumesAfterDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "resume-" + java.util.UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            var repository = FitnessRepository(db)
            repository.addExercise("Row", "Cable")
            repository.startWorkout()
            val id = repository.observeUnfinished().first().single().id
            repository.addWorkoutExercise(id, repository.observeExercises().first().single().id)
            val entry = repository.observeWorkout(id).first()!!.exercises.single().workoutExercise.id
            repository.saveSet(id, entry, null, 8, 20000)
            db.close()
            db = open()
            repository = FitnessRepository(db)
            repository.startWorkout()
            assertEquals(id, repository.observeUnfinished().first().single().id)
            val set = repository.observeWorkout(id).first()!!.exercises.single().sets.single()
            assertEquals(8, set.reps)
            repository.deleteSet(id, entry, set.id)
            assertTrue(repository.observeWorkout(id).first()!!.exercises.single().sets.isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}

