package com.darkoceantech.myfitnesspolice

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CurrentSessionNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pausedWorkoutCanBeOpenedFromEveryMainTab() {
        val (db, repo) = fixture()
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            waitTag("return-to-active-workout")
            listOf("Dispatch", "Academy", "Field", "Reports", "Precinct").forEach { tab ->
                navigate(tab)
                compose.onNodeWithTag("return-to-active-workout").assertIsDisplayed()
                    .assert(hasText("Paused", substring = true))
                    .assert(hasText("Back training plan"))
                    .assert(hasText("Active 0:30"))
                    .assert(hasText("Break 0:00"))
                    .assert(hasText("Return to workout").not()).performClick()
                waitTag("active-workout-header")
                compose.onNodeWithText("Paused back training").assertIsDisplayed()
                compose.onNodeWithTag("return-to-active-workout").assertDoesNotExist()
                assertTrue(runBlocking { db.sessionStateDao().get("session")!!.isPaused })
                assertEquals(1, runBlocking { db.workoutDao().getDetails("session")!!.exercises.size })
            }
            capture("paused-session-return")
        } finally { db.close() }
    }

    @Test fun anotherTrainingPlanCannotStartUntilCurrentSessionEnds() {
        val (db, repo) = fixture()
        val planId = runBlocking { repo.trainingPlans.save(null, "Second training plan", listOf("plan"), "Monday") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            waitTag("return-to-active-workout")
            navigate("Academy")
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithText("Resume workout").assertDoesNotExist()
            compose.onNodeWithTag("workout-section-1").performClick()
            compose.onNodeWithText("Resume workout").assertDoesNotExist()
            waitTag("training-plan-$planId")
            compose.onNodeWithTag("training-plan-$planId").performScrollTo().performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("start-training").assertIsNotEnabled()
            compose.onNodeWithTag("return-to-current-session").assertDoesNotExist()
            compose.onNodeWithTag("return-to-active-workout").assertIsDisplayed().performClick()
            waitTag("active-workout-header")
            compose.onNodeWithText("Paused back training").assertIsDisplayed()
            assertTrue(runBlocking { db.sessionStateDao().get("session")!!.isPaused })
        } finally { db.close() }
    }

    @Test fun globalShortcutDisappearsAfterCancellationAndCompletion() {
        val (db, repo) = fixture(started = false)
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            waitTag("return-to-active-workout")
            compose.onNodeWithTag("return-to-active-workout")
                .assert(hasText("Back training plan"))
                .assert(hasText("Active 0:00"))
                .assert(hasText("Break 0:00"))
            runBlocking { repo.sessionProgress.cancelUnstartedSession("session") }
            waitTag("return-to-active-workout", present = false)
            val newSession = runBlocking { repo.startPlan("plan") }
            waitTag("return-to-active-workout")
            runBlocking { repo.finishWorkout(newSession) }
            waitTag("return-to-active-workout", present = false)
            listOf("Academy", "Field", "Reports", "Precinct").forEach { tab ->
                navigate(tab)
                compose.onNodeWithTag("return-to-active-workout").assertDoesNotExist()
            }
        } finally { db.close() }
    }

    @Test fun landscapeMenuRevealsNavigationWithoutReducingWorkoutHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val (db, repo) = fixture()
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            waitTag("return-to-active-workout")
            compose.onNodeWithTag("return-to-active-workout").performClick()
            waitTag("landscape-navigation-toggle")
            compose.onNodeWithTag("main-navigation").assertDoesNotExist()
            compose.onNodeWithTag("landscape-navigation-items").assertDoesNotExist()
            val header = compose.onNodeWithTag("active-workout-header").fetchSemanticsNode().boundsInRoot
            val pager = compose.onNodeWithTag("active-exercise-pager").fetchSemanticsNode().boundsInRoot
            capture("landscape-menu-collapsed")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("landscape-navigation-toggle").performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("navigation-donut-chase").assertExists()
                .assert(hasContentDescription("Officer chasing a dog with a donut"))
            capture("landscape-donut-open")
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("navigation-donut-chase").assertExists()
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithTag("navigation-donut-chase").assertDoesNotExist()
            compose.onNodeWithTag("landscape-navigation-items").assertIsDisplayed()
            assertEquals(header, compose.onNodeWithTag("active-workout-header").fetchSemanticsNode().boundsInRoot)
            assertEquals(pager, compose.onNodeWithTag("active-exercise-pager").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("landscape-navigation-toggle").performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("navigation-donut-chase").assertExists()
                .assert(hasContentDescription("Dog chasing an officer with a donut"))
            capture("landscape-donut-close")
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("navigation-donut-chase").assertExists()
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithTag("navigation-donut-chase").assertDoesNotExist()
            compose.onNodeWithTag("landscape-navigation-items").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
            navigate("Precinct")
            compose.onNodeWithTag("return-to-active-workout").assertIsDisplayed()
            compose.onNodeWithTag("main-navigation").assertIsDisplayed()
        } finally { compose.mainClock.autoAdvance = true; db.close() }
    }

    private fun fixture(started: Boolean = true): Pair<FitnessDatabase, FitnessRepository> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable machine"))
            db.workoutDao().insert(Workout(id = "session", name = "Paused back training", trainingPlan = "Back training plan"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "active-row", workoutId = "session", exerciseId = "row", position = 0))
            (1..3).forEach { number ->
                db.workoutSetDao().insert(WorkoutSet(id = "s$number", workoutExerciseId = "active-row", position = number - 1,
                    reps = 10, weightGrams = 20000))
            }
            repo.sessionProgress.prepareSession("session")
            if (started) {
                repo.sessionProgress.startSet("session", "s1")
                repo.sessionProgress.pause("session")
                db.sessionStateDao().save(db.sessionStateDao().get("session")!!.copy(phaseElapsedMillis = 30_000L))
            }
            db.workoutDao().insert(Workout(id = "plan", kind = "plan", name = "Back variation"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "plan-row", workoutId = "plan", exerciseId = "row", position = 0))
            db.workoutSetDao().insert(WorkoutSet(id = "planned-set", workoutExerciseId = "plan-row", position = 0,
                reps = 12, weightGrams = 15000))
        }
        return db to repo
    }

    private fun navigate(label: String) {
        if (compose.onAllNodesWithTag("landscape-navigation-toggle").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("landscape-navigation-toggle").performClick()
        }
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun waitTag(tag: String, present: Boolean = true) {
        compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "workout-flow-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
