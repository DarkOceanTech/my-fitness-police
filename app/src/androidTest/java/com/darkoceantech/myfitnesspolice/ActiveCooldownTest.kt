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

class ActiveCooldownTest {
    @get:Rule val compose = createComposeRule()
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()

    @Test fun startButtonsFollowOrderAndDetailsOnlySwipeCompletedSets() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db, 3, secondExercise = true); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            assertButton("s1", true); assertButton("s2", false); assertButton("s3", false)
            screenshot("only-first-start-enabled")
            compose.onNodeWithTag("exercise-tab-we2").performClick()
            assertButton("t1", false); assertButton("t2", false)
            compose.onNodeWithTag("exercise-tab-we").performClick()
            startAndComplete("s1", db)
            assertButton("s2", true); assertButton("s3", false)
            compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
            waitTag("active-set-pager")
            compose.onNodeWithTag("active-set-position").assertTextEquals("Set 1 of 1")
            compose.onNodeWithTag("active-set-pager").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("active-set-position").assertTextEquals("Set 1 of 1")
            compose.onNodeWithContentDescription("Back to exercise").performClick()
            startAndComplete("s2", db)
            assertButton("s3", true)
            compose.onNodeWithTag("set-action-s3").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
            compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
            waitTag("active-set-pager")
            for (number in listOf(2, 1, 2)) {
                compose.onNodeWithTag("active-set-pager").performTouchInput { swipeLeft() }
                compose.onNodeWithTag("active-set-position").assertTextEquals("Set $number of 2 · Swipe left or right")
            }
            screenshot("completed-sets-only")
            compose.onNodeWithContentDescription("Back to exercise").performClick()
            completeAndSave("s3", db)
            compose.onNodeWithTag("exercise-tab-we2").performClick()
            assertButton("t1", true); assertButton("t2", false)
            screenshot("next-exercise-start")
        } finally { db.close() }
    }

    @Test fun expiredCooldownWaitsForFinalResultsThenAutomaticallyOpensHistory() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db, 1); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
            compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
            waitTag("after-set-cooldown")
            compose.onNodeWithTag("actual-reps-input").performTextReplacement("8")
            Espresso.closeSoftKeyboard()
            screenshot("final-results-cooldown")
            // Advance the persisted anchor instead of sleeping for a minute in a UI test.
            val deadline = runBlocking {
                val state = db.sessionStateDao().get("w")!!
                val start = System.currentTimeMillis() - 61_000
                db.workoutDao().update(db.workoutDao().getDetails("w")!!.workout.copy(startedAt = start - 1000))
                db.sessionStateDao().save(state.copy(phaseStartedAt = start, dutyStartedAt = start - 1000))
                start + COOL_DOWN_MILLIS
            }
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("Cool-down complete. Save reps to finish.").fetchSemanticsNodes().isNotEmpty()
            }
            assertNull(runBlocking { db.workoutDao().getDetails("w")!!.workout.finishedAt })
            compose.onNodeWithTag("actual-reps-input").assertTextContains("8")
            compose.onNodeWithText("Save reps").performClick()
            waitTag("history-detail")
            compose.onNode(hasText("Reports") and hasClickAction()).assertIsSelected()
            runBlocking {
                val result = db.workoutDao().getDetails("w")!!
                assertEquals(deadline, result.workout.finishedAt)
                assertEquals(8, result.orderedSets().single().actualReps)
                assertEquals(60_000L, result.orderedSets().single().restMillis)
            }
            screenshot("automatically-opened-history")
        } finally { db.close() }
    }

    @Test fun cooldownContinuesAcrossTabsAndDoesNotDiscardSetEdits() {
        val db = database()
        val repo = FitnessRepository(db)
        runBlocking { seed(db, 1); repo.sessionProgress.prepareSession("w") }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            openSession()
            startAndComplete("s1", db)
            waitTag("cooldown-banner")
            screenshot("cooldown-countdown")
            compose.onNodeWithTag("set-action-s1").performScrollTo().performClick()
            compose.onNodeWithTag("edit-active-set").performScrollTo().performClick()
            runBlocking {
                val state = db.sessionStateDao().get("w")!!
                val start = System.currentTimeMillis() - 61_000
                db.sessionStateDao().save(state.copy(phaseStartedAt = start, dutyStartedAt = start - 1000))
            }
            compose.onNodeWithTag("cooldown-remaining").performScrollTo()
            compose.waitUntil(5000) {
                compose.onAllNodes(hasTestTag("cooldown-remaining") and hasText("0:00")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("cancel-active-set").performScrollTo().assertIsDisplayed()
            assertNull(runBlocking { db.workoutDao().getDetails("w")!!.workout.finishedAt })
            // Leaving the editor removes its auto-finish deferral; the shared monitor continues on Field.
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            waitTag("history-detail")
            compose.onNode(hasText("Reports") and hasClickAction()).assertIsSelected()
            assertEquals(60_000L, runBlocking { db.workoutDao().getDetails("w")!!.orderedSets().single().restMillis })
        } finally { db.close() }
    }

    private fun assertButton(id: String, enabled: Boolean) {
        val button = compose.onNodeWithTag("set-action-$id").performScrollTo()
        if (enabled) button.assertIsEnabled() else button.assertIsNotEnabled()
    }
    private fun startAndComplete(id: String, db: FitnessDatabase) {
        compose.onNodeWithTag("set-action-$id").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("w")!!.phase == "active" } }
        completeAndSave(id, db)
    }
    private fun completeAndSave(id: String, db: FitnessDatabase) {
        compose.onNodeWithTag("set-action-$id").performScrollTo().performClick()
        waitTag("actual-reps-input")
        compose.onNodeWithText("Save reps").performClick()
        compose.waitUntil(5000) { runBlocking { !db.sessionStateDao().get("w")!!.awaitingActual } }
    }
    private fun openSession() {
        compose.onNode(hasText("Academy") and hasClickAction()).performClick()
        compose.onNodeWithTag("workout-section-0").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Resume workout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Resume workout").performScrollTo().performClick()
        waitTag("training-ready")
    }
    private suspend fun seed(db: FitnessDatabase, count: Int, secondExercise: Boolean = false) {
        db.exerciseDao().insert(Exercise(id = "e", name = "Cable row", equipment = "Cable machine"))
        db.workoutDao().insert(Workout(id = "w", name = "Back training"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "we", workoutId = "w", exerciseId = "e", position = 0))
        (1..count).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "we", position = it - 1, reps = 10, weightGrams = 10000)) }
        if (secondExercise) {
            db.exerciseDao().insert(Exercise(id = "e2", name = "Biceps curl", equipment = "Dumbbell"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "we2", workoutId = "w", exerciseId = "e2", position = 1))
            (1..2).forEach { db.workoutSetDao().insert(WorkoutSet(id = "t$it", workoutExerciseId = "we2", position = it - 1, reps = 12, weightGrams = 5000)) }
        }
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "active-cooldown-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
