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

class HistoryRefinementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notesCorrectionsAndConfirmedDeleteAllSurviveReopenAndPreserveOtherData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-refinement-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        var repo = FitnessRepository(db)
        var firstId = ""
        var secondId = ""
        var activeId = ""
        var planId = ""
        var draftId = ""
        var setId = ""
        var entryId = ""
        try {
            runBlocking {
                repo.addExercise("Bench press", "Barbell")
                val exerciseId = repo.observeExercises().first().single().id
                repo.chooseExercise(exerciseId)
                planId = repo.observeSessions().first().single().workout.id
                repo.savePlan(planId)
                firstId = repo.startPlan(planId)
                val first = repo.observeWorkout(firstId).first()!!.orderedSets().single()
                setId = first.id
                entryId = first.workoutExerciseId
                repo.sessionProgress.completeSet(firstId, setId)
                repo.sessionProgress.recordActual(firstId, setId, 8)
                repo.saveSetInfo(firstId, setId, 9, "Kept my shoulders stable.")
                repo.finishWorkout(firstId)
                // Historical corrections must preserve the prescribed count and the saved plan.
                repo.saveSetInfo(firstId, setId, 7, "Correction: seven full repetitions.")
                secondId = repo.startPlan(planId)
                repo.finishWorkout(secondId)
                activeId = repo.startPlan(planId)
                repo.chooseExercise(exerciseId)
                draftId = repo.observeSessions().first().single { it.workout.kind == "draft" }.workout.id
                db.close()
                db = open()
                repo = FitnessRepository(db)
                val reopened = repo.observeWorkout(firstId).first()!!.performedSets().single()
                assertEquals(7, reopened.actualReps)
                assertEquals(10, reopened.reps)
                assertEquals("Correction: seven full repetitions.", reopened.notes)
                assertEquals("", repo.observeWorkout(planId).first()!!.orderedSets().single().notes)
            }
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("DOR") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-workout-$firstId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-workout-$firstId").performClick()
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete all", substring = false).assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("Back to history").performClick()
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete all", substring = false).performClick()
            compose.onNodeWithText("Delete all workout history?").assertIsDisplayed()
            compose.onNodeWithText("This permanently deletes all 2 logged workouts, including their sets, notes, and timing. This cannot be undone.")
                .assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertEquals(2, repo.observeHistory().first().size) }
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete all", substring = false).performClick()
            compose.onNodeWithTag("confirm-delete-history").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("No completed workouts yet").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.waitForIdle()
            runBlocking {
                db.close()
                db = open()
                assertNull(db.workoutDao().getDetails(firstId))
                assertNull(db.workoutDao().getDetails(secondId))
                assertNull(db.sessionStateDao().get(firstId))
                assertTrue(db.workoutSetDao().getForExercise(entryId).isEmpty())
                assertNotNull(db.workoutDao().getDetails(activeId))
                assertNotNull(db.sessionStateDao().get(activeId))
                assertNotNull(db.workoutDao().getDetails(planId))
                assertNotNull(db.workoutDao().getDetails(draftId))
                assertEquals(1, db.exerciseDao().getAll().size)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
