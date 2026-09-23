package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DispatchSessionUpdateTest {
    @get:Rule val compose = createComposeRule()
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()

    @Test fun settingsPreviewAndRenamedSectionsAreNavigable() {
        val db = database()
        try {
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNodeWithText("This week ·", substring = true).assertIsDisplayed()
            compose.onNodeWithText("WEEKLY PROGRESS").assertDoesNotExist()
            compose.onNodeWithText("Sets Completed").assertIsDisplayed()
            screenshot("dispatch")
            compose.onNodeWithTag("open-settings").performClick()
            compose.onNodeWithTag("settings-name").performTextInput("Officer Strong")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Build muscle").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Pounds (lbs)").performScrollTo().performClick()
            compose.onNodeWithText("Kilograms (kg)").performClick()
            compose.onNodeWithTag("settings-Timer sounds").performScrollTo().performClick().assertIsOff()
            screenshot("settings-controls")
            compose.onNodeWithTag("settings-name").performScrollTo()
            screenshot("settings-profile")
            compose.onNodeWithContentDescription("Back to Dispatch").performClick()
            compose.onNodeWithTag("open-settings").assertIsDisplayed()
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithText("Field Training").assertIsDisplayed()
            compose.onNodeWithText("My Police Training Officer").assertIsDisplayed()
            screenshot("field")
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithText("Progress Reports").assertIsDisplayed()
            compose.onNodeWithText("Pain = Progress", substring = true).assertIsDisplayed()
            screenshot("reports")
        } finally { db.close() }
    }

    @Test fun unstartedWorkoutCanBeCancelledAndExerciseInfoOpensFromBuilder() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            compose.onNodeWithText("Cancel workout").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { runBlocking { db.workoutDao().getDetails("w") == null } }
            compose.onNodeWithText("Resume workout").assertDoesNotExist()
            runBlocking {
                db.workoutDao().insert(Workout(id = "plan", kind = "plan", name = "Back strength"))
                db.workoutExerciseDao().insert(WorkoutExercise(id = "pe", workoutId = "plan", exerciseId = "e", position = 0))
                db.workoutSetDao().insert(WorkoutSet(id = "ps", workoutExerciseId = "pe", position = 0, reps = 10, weightGrams = 10000))
            }
            waitTag("home-plan-plan")
            compose.onNodeWithTag("home-plan-plan").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Expand Cable row").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Exercise options for Cable row").performScrollTo().performClick()
            compose.onNodeWithText("View exercise info").performClick()
            compose.onNodeWithTag("exercise-info-dialog").assertIsDisplayed()
            compose.onNodeWithText("Pull the handles toward your ribs.").assertIsDisplayed()
            compose.onNodeWithText("Latissimus dorsi").assertIsDisplayed()
            screenshot("exercise-info")
        } finally { db.close() }
    }

    @Test fun afterSetFormRecordsEffortAndNotesAndDetailsWrapWithinExercise() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            compose.onNodeWithText("Cancel workout").assertIsDisplayed()
            compose.onNodeWithTag("set-action-s1").performClick()
            compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
            compose.onNodeWithText("Pause workout").assertIsDisplayed()
            compose.onNodeWithContentDescription("Actual reps s1").assertHasNoClickAction()
            compose.onNodeWithTag("set-action-s1").performClick()
            waitTag("actual-reps-input")
            val breakStart = runBlocking { db.sessionStateDao().get("w")!!.phaseStartedAt!! }
            compose.onNodeWithTag("actual-reps-input").performTextReplacement("8")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("after-set-rpe").performClick()
            compose.onNodeWithTag("Set RPE options").performScrollToNode(hasText("7"))
            compose.onNode(hasText("7") and hasClickAction()).performClick()
            compose.onNodeWithTag("after-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("after-set-note-input").performTextInput("Controlled tempo")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Save note").performClick()
            screenshot("after-set-form")
            compose.onNodeWithText("Save reps").performClick()
            compose.waitUntil(5000) { runBlocking { !db.sessionStateDao().get("w")!!.awaitingActual } }
            runBlocking {
                val set = db.workoutSetDao().getForExercise("we").first()
                assertEquals(8, set.actualReps); assertEquals(7, set.rpe); assertEquals("Controlled tempo", set.notes)
                assertEquals(breakStart, db.sessionStateDao().get("w")!!.phaseStartedAt)
                assertTrue(db.sessionStateDao().get("w")!!.phaseMillis(System.currentTimeMillis()) > 0)
                for (id in listOf("s2", "s3")) {
                    repo.sessionProgress.startSet("w", id)
                    repo.sessionProgress.completeSet("w", id)
                    repo.sessionProgress.recordActual("w", id, 10)
                }
            }
            compose.onNodeWithContentDescription("Info for set 1 of Cable row").performScrollTo().performClick()
            verifySwiping("active")
            compose.onNodeWithTag("active-set-times").performScrollTo().assertIsDisplayed()
            screenshot("active-set-details")
            compose.onNodeWithContentDescription("Back to exercise").performClick()
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("Finish", substring = false).performClick()
            waitTag("history-detail")
            compose.onNode(hasText("Reports") and hasClickAction()).assertIsSelected()
            compose.onNodeWithContentDescription("Info for set 1 of Cable row").performScrollTo().performClick()
            verifySwiping("history")
            compose.onNodeWithTag("history-set-times").performScrollTo().assertIsDisplayed()
            screenshot("history-set-details")
        } finally { db.close() }
    }

    private fun verifySwiping(prefix: String) {
        waitTag("$prefix-set-pager")
        compose.onNodeWithTag("$prefix-set-position").assertTextEquals("Set 1 of 3 · Swipe left or right")
        for (number in listOf(2, 3, 1)) {
            compose.onNodeWithTag("$prefix-set-pager").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("$prefix-set-position").assertTextEquals("Set $number of 3 · Swipe left or right")
        }
        compose.onNodeWithTag("$prefix-set-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("$prefix-set-position").assertTextEquals("Set 3 of 3 · Swipe left or right")
        compose.onNodeWithTag("edit-$prefix-set").performScrollTo().performClick()
        compose.onNodeWithTag("$prefix-set-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("$prefix-set-position").assertTextEquals("Set 3 of 3 · Swipe left or right")
        compose.onNodeWithTag("cancel-$prefix-set").performScrollTo().performClick()
        compose.onNodeWithTag("$prefix-set-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("$prefix-set-position").assertTextEquals("Set 1 of 3 · Swipe left or right")
    }

    private fun openSession() {
        compose.onNode(hasText("Academy") and hasClickAction()).performClick()
        compose.onNodeWithTag("workout-section-0").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Resume workout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Resume workout").performScrollTo().performClick()
        waitTag("training-ready")
    }
    private suspend fun seed(db: FitnessDatabase) {
        db.exerciseDao().insert(Exercise(id = "e", name = "Cable row", equipment = "Cable machine",
            description = "Pull the handles toward your ribs.", primaryMuscles = "Latissimus dorsi", secondaryMuscles = "Biceps brachii"))
        db.workoutDao().insert(Workout(id = "w", name = "Back training"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "we", workoutId = "w", exerciseId = "e", position = 0))
        (1..3).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "we", position = it - 1, reps = 10, weightGrams = 10000)) }
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(250)
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "dispatch-session-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
