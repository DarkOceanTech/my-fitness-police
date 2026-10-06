package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActiveExerciseSwapUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape"
    private fun fixture(block: (FitnessDatabase, FitnessRepository) -> Unit) {
        instrument.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
        val db = Room.inMemoryDatabaseBuilder(instrument.targetContext, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking {
            db.exerciseDao().insert(Exercise("row", "Original row", "Cable", primaryMuscles = "Latissimus dorsi"))
            db.exerciseDao().insert(Exercise("related", "Related cable row", "Cable", primaryMuscles = "Rhomboids"))
            db.exerciseDao().insert(Exercise("bench", "Bench press", "Barbell", primaryMuscles = "Pectoralis major"))
            for (id in listOf("live", "history")) {
                db.workoutDao().insert(Workout(id = id, name = "Back patrol", kind = "session", startedAt = 1, finishedAt = if (id == "history") 100 else null))
                db.workoutExerciseDao().insert(WorkoutExercise("$id-e", id, if (id == "history") "related" else "row", 0, notes = "$id note"))
                for (index in 0..1) db.workoutSetDao().insert(WorkoutSet("$id-$index", "$id-e", index,
                    if (id == "history") 8 else 10, if (id == "history") 560170 else 20000,
                    completedAt = if (id == "history") 50 else null, actualReps = if (id == "history") 7 else null,
                    rpe = if (id == "history") 8 else null, notes = if (id == "history") "Past set note" else ""))
            }
            repo.sessionProgress.prepareSession("live")
        }
        try {
            val model = SessionViewModel(repo)
            compose.setContent { MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(), color = Color.Transparent, contentColor = PoliceColors.Text) {
                    ActiveWorkoutScreen(model, Modifier.fillMaxSize(), onBack = {}, onFinished = {})
                }
            } }
            waitTag("session-set-count")
            compose.onNodeWithTag("session-set-count").assertTextEquals("0 / 2 sets")
            compose.onNodeWithText("CURRENT").assertDoesNotExist()
            block(db, repo)
        } finally { instrument.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE); db.close() }
    }
    @Test fun currentSourceCanCancelAndSwapThenStartedAndPausedExerciseIsLocked() = fixture { db, repo ->
        fun entry() = runBlocking { db.workoutDao().getDetails("live")!!.exercises.single() }
        val before = entry()
        capture("active-timer")
        openSwap()
        compose.onNodeWithTag("exercise-picker-screen").assertIsDisplayed()
        compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("recommended-exercise-related"))
        compose.onNodeWithTag("recommended-exercise-related").assertExists()
        capture("recommended-picker")
        chooseRelated()
        waitTag("swap-exercise-dialog")
        capture("swap-current-source")
        compose.onNodeWithContentDescription("Close Swap exercise").performClick()
        waitGone("swap-exercise-dialog")
        assertEquals(before, entry())
        openSwap(); chooseRelated()
        compose.onNodeWithTag("confirm-swap-exercise").performClick()
        waitGone("swap-exercise-dialog")
        assertEquals("related", entry().exercise.id)
        assertEquals(before.sets, entry().sets)
        runBlocking { repo.sessionProgress.startSet("live", "live-0") }
        compose.waitUntil(5000) { runBlocking { db.sessionStateDao().get("live")!!.phase == "active" } }
        openMenu()
        compose.onNodeWithTag("swap-exercise-live-e").assertIsNotEnabled()
        compose.onNodeWithTag("swap-exercise-live-e").performClick()
        compose.onNodeWithTag("exercise-picker-screen").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        runBlocking { repo.sessionProgress.pause("live") }
        openMenu()
        compose.onNodeWithTag("swap-exercise-live-e").assertIsNotEnabled()
    }
    @Test fun lastSessionCanBeViewedAndImportsOnlyPlannedData() = fixture { db, _ ->
        openSwap(); chooseRelated()
        waitTag("swap-view-last-session")
        compose.onNodeWithTag("swap-view-last-session").performScrollTo().performClick()
        waitTag("last-session-summary")
        compose.onNodeWithTag("import-session").assertDoesNotExist()
        capture("swap-last-session-preview")
        compose.onNodeWithTag("close-last-session").performClick()
        waitGone("last-session-dialog")
        compose.onNodeWithTag("swap-source-last_session").performScrollTo().performClick()
        capture("swap-last-source")
        compose.onNodeWithTag("confirm-swap-exercise").performClick()
        waitGone("swap-exercise-dialog")
        val sets = runBlocking { db.workoutSetDao().getForExercise("live-e") }
        assertEquals(listOf(8, 8), sets.map { it.reps })
        assertTrue(sets.all { it.weightGrams == 560170L && it.notes.isEmpty() && it.actualReps == null && it.rpe == null && it.completedAt == null })
        compose.onNode(hasText("1,235") and hasAnyAncestor(hasContentDescription("Session weight ${sets.first().id}")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("set-action-${sets.first().id}").assertIsEnabled()
    }
    @Test fun freshSourceCreatesOneDefaultSetAndCanStartIt() = fixture { db, _ ->
        openSwap(); chooseRelated()
        compose.onNodeWithTag("swap-source-new").performScrollTo().performClick()
        capture("swap-new-source")
        compose.onNodeWithTag("confirm-swap-exercise").performClick()
        waitGone("swap-exercise-dialog")
        val sets = runBlocking { db.workoutSetDao().getForExercise("live-e") }
        assertEquals(1, sets.size); assertEquals(10, sets.single().reps); assertEquals(0L, sets.single().weightGrams)
        compose.onNodeWithTag("session-set-count").assertTextEquals("0 / 1 sets")
        compose.onNodeWithTag("set-action-${sets.single().id}").performScrollTo().assertIsEnabled().performClick()
        waitTag("set-start-countdown")
        compose.onNodeWithTag("cancel-set-countdown").performClick()
        assertEquals("ready", runBlocking { db.sessionStateDao().get("live")!!.phase })
    }
    private fun openMenu() { compose.onNodeWithTag("session-exercise-options-live-e").performScrollTo().performClick() }
    private fun openSwap() { openMenu(); compose.onNodeWithTag("swap-exercise-live-e").assertIsEnabled().performClick(); waitTag("exercise-picker-screen") }
    private fun chooseRelated() {
        compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-related"))
        compose.onNodeWithTag("select-exercise-related").performClick(); waitTag("swap-exercise-dialog")
    }
    private fun waitTag(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitGone(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle(); instrument.waitForIdleSync(); android.os.SystemClock.sleep(1000)
        val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "weight-swap-review").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot()
        try { java.io.File(directory, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
