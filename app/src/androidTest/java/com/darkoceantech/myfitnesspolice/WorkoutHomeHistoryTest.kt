package com.darkoceantech.myfitnesspolice

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

class WorkoutHomeHistoryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun homeNavigationAndConfirmedDeletionPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-" + java.util.UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        val repo = FitnessRepository(db)
        var id = ""
        var entry = ""
        runBlocking {
            repo.addExercise("Test row", "Cable")
            repo.startWorkout()
            id = repo.observeUnfinished().first().single().id
            repo.addWorkoutExercise(id, repo.observeExercises().first().single().id)
            entry = repo.observeWorkout(id).first()!!.exercises.single().workoutExercise.id
            repo.saveSet(id, entry, null, 8, 20000)
            repo.finishWorkout(id)
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNodeWithText("This week ·", substring = true).assertIsDisplayed()
            compose.onNodeWithText("MyFitnessPolice").assertIsDisplayed()
            val navigation = listOf("Dispatch", "Academy", "Field", "Reports", "Armory")
            val positions = navigation.map { label ->
                compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
            }
            assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
            compose.onNode(hasText("Dispatch") and hasClickAction()).assertIsSelected()
            compose.onNodeWithContentDescription("Add workout").assertDoesNotExist()
            screenshot("dashboard-home")
            compose.onNodeWithTag("weekly-Weight-value").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("weekly-Activity-value").performScrollTo().assertIsDisplayed()
            screenshot("dashboard-scrolled")
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithTag("weekly-progress-cards").assertDoesNotExist()
            compose.onNodeWithText("MyFitnessPolice").assertDoesNotExist()
            compose.onNodeWithContentDescription("Add workout").performClick()
            waitFor("Create your Workout")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            waitFor("Create My First Workout")
            compose.onNodeWithText("Monday").assertDoesNotExist()
            compose.onNodeWithTag("create-first-workout").performScrollTo()
            screenshot("empty-workout-home")
            compose.onNodeWithTag("create-first-workout").performClick()
            waitFor("Create your Workout")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            // Input order and legacy weekdays must not override muscle/name grouping.
            val planIds = runBlocking {
                val exercise = repo.observeExercises().first().single().id
                listOf(Triple("Back", "Cable rows", "Thursday"), Triple("Back", "Wide rows", "Monday"),
                    Triple("Biceps", "Light curls", "")).map { (muscles, workoutName, day) ->
                    repo.chooseExercise(exercise)
                    val draft = repo.observeSessions().first().single { it.workout.kind == "draft" }.workout.id
                    repo.saveMetadata(draft, muscles, day, if (day.isEmpty()) "Unscheduled Plan" else "$day Plan")
                    repo.savePlan(draft, workoutName)
                    draft
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-${planIds[0]}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("create-first-workout").assertDoesNotExist()
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("workout-group-Back"))
            compose.onNodeWithTag("workout-group-Back").assertIsDisplayed()
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("home-plan-${planIds[0]}"))
            compose.onNodeWithTag("home-plan-${planIds[0]}").assert(hasText("Back"))
            compose.onNodeWithText("Monday").assertDoesNotExist()
            compose.onNodeWithText("Monday Plan").assertDoesNotExist()
            compose.onNodeWithText("→").assertDoesNotExist()
            assertTrue(compose.onNodeWithTag("home-plan-${planIds[0]}").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag("home-plan-${planIds[1]}").fetchSemanticsNode().boundsInRoot.top)
            screenshot("ordered-workout-plans")
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("workout-group-Biceps"))
            compose.onNodeWithTag("workout-group-Biceps").assertIsDisplayed()
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("home-plan-${planIds[2]}"))
            compose.onNodeWithTag("home-plan-${planIds[2]}").assert(hasText("Biceps"))
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("home-plan-${planIds[0]}"))
            compose.onNodeWithTag("home-plan-${planIds[0]}").performClick()
            waitFor("Edit workout")
            compose.onNodeWithText("Monday").assertDoesNotExist()
            screenshot("dark-workout-editor")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-home").assertIsDisplayed()
            screenshot("progress-grid")
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.onNodeWithContentDescription("Back to Reports").performClick()
            compose.onNodeWithTag("progress-history-grid").assertIsDisplayed()
            compose.onNodeWithTag("progress-history-tile").performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("progress-history-grid").assertIsDisplayed()
            compose.onNodeWithTag("progress-history-tile").performClick()
            screenshot("history-list")
            waitFor("1 sets · 8 reps")
            compose.onNodeWithTag("history-workout-$id").performClick()
            compose.onNodeWithText("Delete workout").assertDoesNotExist()
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete workout").performClick()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertNotNull(db.workoutDao().getDetails(id)) }
            compose.onNodeWithContentDescription("History options").performClick()
            compose.onNodeWithText("Delete workout").performClick()
            compose.onNodeWithText("Delete", substring = false).performClick()
            waitFor("No completed workouts yet")
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.onNodeWithTag("exercise-catalog").assertIsDisplayed()
            screenshot("learning-tab")
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            compose.onNodeWithTag("weekly-Sets-value").performScrollTo()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("weekly-Sets-value") and hasText("0")).fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                db.close()
                db = open()
                assertNull(db.workoutDao().getDetails(id))
                assertTrue(db.workoutSetDao().getForExercise(entry).isEmpty())
                assertEquals(1, db.exerciseDao().getAll().size)
                assertTrue(planIds.all { db.workoutDao().getDetails(it)?.workout?.kind == "plan" })
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "dashboard-navigation-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}


