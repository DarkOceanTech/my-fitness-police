package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExerciseProgressUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyShowsAllMatchingSessionsAndSortsWithoutChangingImportSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        val imported = mutableListOf<String>()
        val history = try {
            runBlocking {
                db.exerciseDao().insert(Exercise(id = "curl", name = "Dumbbell curl", equipment = "Dumbbell"))
                db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Barbell"))
                for ((id, time, kind, finished) in listOf(
                    listOf("older", "1000", "session", "2000"), listOf("newer", "3000", "session", "4000"),
                    listOf("active", "5000", "session", ""), listOf("plan", "6000", "plan", ""))) {
                    db.workoutDao().insert(Workout(id = id, name = "$id workout", startedAt = time.toLong(),
                        kind = kind, finishedAt = finished.toLongOrNull()))
                    db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-curl", workoutId = id, exerciseId = "curl", position = 0))
                    db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-curl", position = 0,
                        weightGrams = 4536, reps = 10, actualReps = 12, completedAt = 1500, activeMillis = 20000, restMillis = 30000))
                    db.workoutSetDao().insert(WorkoutSet(id = "$id-unperformed", workoutExerciseId = "$id-curl", position = 1,
                        reps = 99, weightGrams = 453600))
                }
                db.workoutExerciseDao().insert(WorkoutExercise(id = "newer-row", workoutId = "newer", exerciseId = "row", position = 1))
                repository.getExerciseSessionHistory("curl")
            }
        } finally { db.close() }
        assertEquals(listOf("newer-curl", "older-curl"), history.map { it.entry.workoutExercise.id })
        assertEquals(2, history.size)
        try {
            val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
            instrumentation.uiAutomation.setRotation(if (orientation == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
            compose.setContent { MyFitnessPoliceTheme {
                LastSessionDialog(LastSessionState(snapshot = history.first(), history = history), false, null,
                    onClose = {}, onRetry = {}, onImport = { imported.add(it) })
            } }
            compose.onAllNodesWithText("EXERCISE").assertCountEquals(0)
            compose.onNodeWithTag("history-card-toggle-newer-curl").assertDoesNotExist()
            compose.onNodeWithTag("last-session-summary").assertExists()
            compose.onNodeWithText("1 sets · 12 reps").assertExists()
            compose.onNodeWithTag("view-exercise-progress").assertIsDisplayed().performClick()
            compose.onNodeWithTag("exercise-progress-dialog").assertIsDisplayed()
            compose.onNodeWithTag("exercise-history-total-sessions").assertTextEquals("2")
            compose.onNodeWithTag("exercise-history-total-sets").assertTextEquals("2")
            compose.onNodeWithTag("exercise-history-total-reps").assertTextEquals("24")
            compose.onNodeWithTag("exercise-history-total-lbs-lifted").assertTextEquals("240")
            capture("overview-$orientation")
            list().performScrollToNode(hasTestTag("exercise-history-toggle-all"))
            compose.onNodeWithTag("exercise-history-toggle-all").performClick()
            list().performScrollToNode(hasTestTag("history-card-toggle-newer-curl"))
            compose.onNode(hasTestTag("exercise-card-content-newer-curl") and
                hasAnyAncestor(hasTestTag("exercise-progress-dialog"))).assertDoesNotExist()
            list().performScrollToIndex(2)
            compose.onNodeWithTag("exercise-history-session-newer-curl").assertIsDisplayed()
            list().performScrollToNode(hasTestTag("exercise-history-sort"))
            compose.onNodeWithTag("exercise-history-sort").performClick()
            compose.onNodeWithText("Date ascending (oldest first)").performClick()
            list().performScrollToNode(hasTestTag("history-card-toggle-older-curl"))
            list().performScrollToIndex(2)
            compose.onNodeWithTag("exercise-history-session-older-curl").assertIsDisplayed()
            capture("collapsed-$orientation")
            compose.onNodeWithTag("history-card-toggle-older-curl").performClick()
            list().performScrollToNode(hasTestTag("session-exercise-options-older-curl"))
            compose.onNodeWithTag("session-exercise-options-older-curl").assertIsDisplayed().performClick()
            compose.onNodeWithText("View exercise info").assertIsDisplayed()
            compose.onNodeWithText("View exercise info").performClick()
            compose.onNodeWithContentDescription("Close Exercise info").performClick()
            list().performScrollToNode(hasTestTag("set-action-older-set"))
            compose.onNodeWithTag("set-action-older-set").performClick()
            compose.onNodeWithTag("last-session-set-actual").assertTextEquals("12").assertHasNoClickAction()
            compose.onNodeWithContentDescription("Back to exercise progress").performClick()
            compose.onNodeWithContentDescription("Back to last session").performClick()
            compose.onNodeWithTag("exercise-progress-dialog").assertDoesNotExist()
            assertTrue(imported.isEmpty())
            compose.onNodeWithTag("import-session").performClick()
            compose.onNodeWithTag("confirm-import-session").performClick()
            compose.runOnIdle { assertEquals(listOf("newer-curl"), imported) }
        } finally { instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }

    private fun list() = compose.onNodeWithTag("exercise-progress-list")
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "exercise-progress-review").apply { mkdirs() }
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
