package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanDeletionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletePlanReturnsHomeAndPreservesLiveSessionHistoryAndCatalogAfterReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "plan-delete-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        val repo = FitnessRepository(db)
        var planId = ""
        var planEntry = ""
        var historyId = ""
        var activeId = ""
        try {
            runBlocking {
                repo.addExercise("Dumbbell row", "Dumbbells")
                repo.chooseExercise(repo.observeExercises().first().single().id)
                val draft = repo.observeSessions().first().single()
                planId = draft.workout.id
                planEntry = draft.exercises.single().workoutExercise.id
                repo.saveMetadata(planId, "Back", "Monday", "Arm Focus")
                repo.savePlan(planId)
                historyId = repo.startPlan(planId)
                val set = repo.observeWorkout(historyId).first()!!.orderedSets().single()
                repo.sessionProgress.completeSet(historyId, set.id)
                repo.sessionProgress.recordActual(historyId, set.id, 8)
                repo.finishWorkout(historyId)
                activeId = repo.startPlan(planId)
            }
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            waitFor("Edit workout")
            compose.onNodeWithContentDescription("Workout options").performClick()
            compose.onNodeWithText("Delete workout").performClick()
            compose.onNodeWithText("Delete this workout?").assertIsDisplayed()
            screenshot("delete-plan-warning")
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertNotNull(repo.observeWorkout(planId).first()) }
            compose.onNodeWithContentDescription("Workout options").performClick()
            compose.onNodeWithText("Delete workout").performClick()
            compose.onNodeWithTag("confirm-delete-plan").performClick()
            waitFor("Create My First Workout")
            compose.onNodeWithText("Edit workout").assertDoesNotExist()
            compose.onNodeWithText("Arm Focus").assertDoesNotExist()
            compose.onNodeWithText("Resume workout").performScrollTo().performClick()
            waitFor("ON DUTY")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.waitForIdle()
            runBlocking {
                assertTrue(runCatching { repo.deletePlan(activeId) }.isFailure)
                assertTrue(runCatching { repo.deletePlan(historyId) }.isFailure)
                db.close()
                db = open()
                assertNull(db.workoutDao().getDetails(planId))
                assertTrue(db.workoutSetDao().getForExercise(planEntry).isEmpty())
                val active = db.workoutDao().getDetails(activeId)!!
                assertNull(active.workout.finishedAt)
                assertEquals(1, active.orderedSets().size)
                assertNotNull(active.sessionState)
                assertEquals(8, db.workoutDao().getDetails(historyId)!!.performedSets().single().actualReps)
                assertEquals(1, db.exerciseDao().getAll().size)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(8000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "weekly-info-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
