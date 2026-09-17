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

class WorkoutPlanLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun createEditStartFinishKeepsPlanSeparate() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            repo.addExercise("Curl", "Dumbbell")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            repo.setWorkoutDetails("plan", "Arm Focus")
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithContentDescription("Add workout").performClick()
            waitFor("Create your Workout")
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitFor("Workouts")
            runBlocking {
                assertTrue(repo.observeHistory().first().isEmpty())
                assertTrue(repo.observeUnfinished().first().isEmpty())
            }
            val savedId = runBlocking { repo.observeSessions().first().single().workout.id }
            compose.onNodeWithTag("home-plan-$savedId").performScrollTo().performClick()
            waitFor("Edit workout")
            compose.onNodeWithText("Clear").assertDoesNotExist()
            compose.onNodeWithText("Start My Workout").assertDoesNotExist()
            compose.onNodeWithContentDescription("Expand Curl").performScrollTo().performClick()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Add Set"))
            compose.onNodeWithText("Add Set").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeSessions().first().single().exercises.single().sets.size == 2 } }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitFor("Saved")
            compose.onNodeWithText("WEEKLY PROGRESS").assertDoesNotExist()
            val trainingId = runBlocking { repo.trainingPlans.observeAll().first().single().plan.id }
            openTrainingPlanFromWorkout(compose, trainingId)
            waitFor("Finish workout")
            runBlocking {
                val records = repo.observeSessions().first()
                val plan = records.single { it.workout.kind == "plan" }
                val session = records.single { it.workout.kind == "session" }
                assertEquals(trainingId, session.workout.sourceTrainingPlanId)
                assertNotEquals(plan.exercises.single().workoutExercise.id, session.exercises.single().workoutExercise.id)
                assertTrue(session.exercises.single().sets.all { it.completedAt == null })
                assertTrue(repo.observeHistory().first().isEmpty())
            }
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("Finish", substring = false).performClick()
            waitFor("No sets were recorded in this session.")
            compose.onNodeWithTag("history-detail").assertExists()
            runBlocking {
                assertEquals(1, repo.observeHistory().first().size)
                val plan = repo.observeSessions().first().single { it.workout.kind == "plan" }
                assertNull(plan.workout.finishedAt)
                assertEquals(2, plan.exercises.single().sets.size)
            }
        } finally { db.close() }
    }

    @Test fun sessionEditsDoNotChangePlanAndResumeSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "plan-" + java.util.UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            var repo = FitnessRepository(db)
            repo.addExercise("Squat", "Barbell")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            val draft = repo.observeSessions().first().single()
            val planId = draft.workout.id
            repo.savePlan(planId)
            val sessionId = repo.startPlan(planId)
            assertEquals(sessionId, repo.startPlan(planId))
            val session = repo.observeWorkout(sessionId).first()!!
            val entry = session.exercises.single()
            val set = entry.sets.single()
            repo.updateSetFields(sessionId, entry.workoutExercise.id, set.id, reps = 7, grams = 20000)
            assertEquals(10, repo.observeWorkout(planId).first()!!.exercises.single().sets.single().reps)
            repo.setWorkoutDetails("day", "Friday")
            repo.clearDraft()
            assertEquals(sessionId, repo.observeUnfinished().first().single().id)
            db.close()
            db = open()
            repo = FitnessRepository(db)
            assertEquals(sessionId, repo.startPlan(planId))
            assertEquals(7, repo.observeWorkout(sessionId).first()!!.exercises.single().sets.single().reps)
            repo.finishWorkout(sessionId)
            repo.deleteWorkout(sessionId)
            assertNotNull(repo.observeWorkout(planId).first())
            assertTrue(repo.observeHistory().first().isEmpty())
            assertNotEquals(sessionId, repo.startPlan(planId))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}


