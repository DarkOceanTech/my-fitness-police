package com.darkoceantech.myfitnesspolice

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run in both device orientations; a long set list reproduces the inaccessible-table bug. */
class ActiveWorkoutLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun timerStaysVisibleWhileSetsAndWorkoutButtonsScrollTogether() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            db.exerciseDao().insert(Exercise(id = "e", name = "Seated cable row", equipment = "Cable machine"))
            db.workoutDao().insert(Workout(id = "w", name = "Back training"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "we", workoutId = "w", exerciseId = "e", position = 0))
            (1..12).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "we",
                position = it - 1, reps = 10, weightGrams = 20000)) }
            repo.sessionProgress.prepareSession("w")
        }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let {
                assertEquals("Test must run in the requested device orientation", it == "landscape", landscape)
            }
            val orientation = if (landscape) "landscape" else "portrait"
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithText("Resume workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Resume workout").performScrollTo().performClick()
            waitTag("active-workout-header")

            val headerBefore = compose.onNodeWithTag("active-workout-header").fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithTag("active-workout-title").fetchSemanticsNode().boundsInRoot
            val timer = compose.onNodeWithTag("session-timers").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            if (landscape) {
                assertTrue("Timer sits to the right of the title", timer.left >= title.right)
                compose.onNodeWithTag("session-timers").assert(hasAnyAncestor(hasTestTag("active-workout-header")))
            } else assertTrue("Portrait keeps its full-width timer below the title", timer.top >= title.bottom)
            compose.onNodeWithTag("finish-workout").assertIsNotDisplayed()
            compose.onNodeWithTag("pause-resume-workout").assert(hasAnyAncestor(hasTestTag("active-exercise-scroll-we")))
            compose.onNodeWithTag("finish-workout").assert(hasAnyAncestor(hasTestTag("active-exercise-scroll-we")))

            compose.onNodeWithTag("set-action-s1").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
            compose.onNodeWithTag("set-action-s1").assertIsDisplayed()
            screenshot("$orientation-first-set")
            compose.onNodeWithTag("set-action-s12").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag("session-timers").assertIsDisplayed()
            assertEquals(headerBefore, compose.onNodeWithTag("active-workout-header").fetchSemanticsNode().boundsInRoot)
            screenshot("$orientation-last-set")

            compose.onNodeWithTag("pause-resume-workout").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("finish-workout").assertIsDisplayed().assertIsEnabled()
            val actions = compose.onNodeWithTag("session-actions").fetchSemanticsNode().boundsInRoot
            val lastSet = compose.onNodeWithTag("session-set-s12").fetchSemanticsNode().boundsInRoot
            assertTrue("Workout controls must sit below the set table", actions.top >= lastSet.bottom)
            screenshot("$orientation-scrollable-actions")
            compose.onNodeWithTag("pause-resume-workout").performClick()
            waitTag("pause-reason-input")
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("pause-resume-workout").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { !db.sessionStateDao().get("w")!!.isPaused } }
            compose.onNodeWithTag("finish-workout").performScrollTo().performClick()
            compose.onNodeWithText("Finish this workout?").assertIsDisplayed()
            compose.onNodeWithText("Cancel", substring = false).performClick()
            compose.onNodeWithTag("set-action-s1").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("finish-workout").assertIsNotDisplayed()
            compose.onNodeWithTag("duty-time").assertIsDisplayed()
            assertNull(runBlocking { db.workoutDao().getDetails("w")!!.workout.finishedAt })
        } finally { db.close() }
    }

    private fun waitTag(tag: String, present: Boolean = true) {
        compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "active-layout-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
