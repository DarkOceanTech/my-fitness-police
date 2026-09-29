package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
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

class HistoryBrowseUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyFiltersCombineAndClearWithoutGuessingOlderWorkoutSources() {
        val (db, repo) = fixture()
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(repo) }
            openHistory()
            compose.onNodeWithTag("toggle-history-filters").performClick()
            filter("date", "Custom")
            waitTag("history-date-range")
            compose.onNodeWithText("Cancel").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("history-date-range").fetchSemanticsNodes().isEmpty() }
            assertCount("4 of 4 sessions")
            compose.onNodeWithTag("history-filter-date").assert(hasText("All time"))
            filter("date", "Last7Days")
            assertCount("3 of 4 sessions")
            filter("exercise", "row")
            assertCount("2 of 4 sessions")
            filter("workout", "back")
            assertCount("1 of 4 sessions")
            filter("plan", "day-a")
            assertCount("1 of 4 sessions")
            capture("combined-history-filters")
            filter("plan", "day-b")
            assertCount("0 of 4 sessions")
            compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag("history-no-matches"))
            compose.onNodeWithTag("clear-empty-history-filters").performClick()
            assertCount("4 of 4 sessions")
            filter("workout", "__unknown_workout_source__")
            assertCount("1 of 4 sessions")
            compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag("history-workout-legacy"))
            compose.onNodeWithTag("history-workout-legacy").assertIsDisplayed()
            compose.onNodeWithTag("clear-history-filters").performScrollTo().performClick()
            assertCount("4 of 4 sessions")
        } finally { restoreOrientation(); db.close() }
    }

    @Test fun historyCardsCollapseAndReplacementKeepsRecordedSetsAndNotes() {
        val (db, repo) = fixture()
        val before = runBlocking { db.workoutDao().getDetails("recent")!! }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(repo) }
            openHistory()
            compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag("history-workout-recent"))
            compose.onNodeWithTag("history-workout-recent").performClick()
            waitTag("history-exercises")
            scrollExercise("history-collapse-all")
            compose.onNodeWithTag("history-collapse-all").performClick()
            compose.onNodeWithTag("history-collapse-all").assert(hasText("Expand all"))
            listOf("recent-row", "recent-curl").forEach { id ->
                scrollExercise("active-card-$id")
                compose.onNodeWithTag("exercise-card-content-$id").assertDoesNotExist()
            }
            capture("collapsed-history-cards")
            scrollExercise("history-collapse-all")
            compose.onNodeWithTag("history-collapse-all").performClick()
            compose.onNodeWithTag("session-set-recent-row-set").performScrollTo().assertIsDisplayed()
            scrollExercise("history-card-toggle-recent-row")
            compose.onNodeWithTag("history-card-toggle-recent-row").performClick()
            compose.onNodeWithTag("exercise-card-content-recent-row").assertDoesNotExist()
            openReplacement()
            compose.onNodeWithText("Change exercise").assertIsDisplayed()
            compose.onNodeWithTag("add-picker-exercise").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back to Workout Log").performClick()
            assertEquals(before, runBlocking { db.workoutDao().getDetails("recent")!! })
            openReplacement()
            compose.onNodeWithTag("exercise-picker-search").performTextReplacement("Bench")
            compose.waitForIdle()
            android.os.SystemClock.sleep(350)
            Espresso.closeSoftKeyboard()
            compose.waitForIdle()
            android.os.SystemClock.sleep(350)
            compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-bench"))
            capture("history-change-exercise")
            compose.onNodeWithTag("select-exercise-bench").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-picker-list").fetchSemanticsNodes().isEmpty() }
            val after = runBlocking { db.workoutDao().getDetails("recent")!! }
            val oldEntry = before.exercises.single { it.workoutExercise.id == "recent-row" }
            val newEntry = after.exercises.single { it.workoutExercise.id == "recent-row" }
            assertEquals(oldEntry.workoutExercise.copy(exerciseId = "bench"), newEntry.workoutExercise)
            assertEquals(oldEntry.sets, newEntry.sets)
            assertEquals(before.workout, after.workout)
            assertEquals("row", runBlocking { db.workoutDao().getDetails("old")!!.exercises.single().exercise.id })
            compose.onNodeWithTag("exercise-card-content-recent-row").assertDoesNotExist()
            scrollExercise("history-card-toggle-recent-row")
            compose.onNodeWithTag("history-card-toggle-recent-row").performClick()
            compose.onNodeWithTag("session-set-recent-row-set").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("set-note-indicator-recent-row-set", useUnmergedTree = true).assertIsDisplayed()
            capture("history-replaced-exercise")
        } finally { restoreOrientation(); db.close() }
    }

    private fun fixture(): Pair<FitnessDatabase, FitnessRepository> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val now = System.currentTimeMillis()
        runBlocking {
            listOf(Exercise(id = "row", name = "Cable row", equipment = "Cable machine", primaryMuscles = "Latissimus dorsi"),
                Exercise(id = "curl", name = "Dumbbell curl", equipment = "Dumbbell", primaryMuscles = "Biceps brachii"),
                Exercise(id = "bench", name = "Bench press", equipment = "Barbell; bench", primaryMuscles = "Pectoralis major"))
                .forEach { db.exerciseDao().insert(it) }
            suspend fun add(id: String, ago: Int, exercise: String, source: String?, plan: String?) {
                val start = now - ago * 86_400_000L
                db.workoutDao().insert(Workout(id = id, name = "Session $id", startedAt = start, finishedAt = start + 180000,
                    sourceTrainingPlanId = plan, trainingPlan = if (plan == "day-a") "Monday strength" else if (plan == "day-b") "Tuesday strength" else ""))
                val entry = "$id-$exercise"
                db.workoutExerciseDao().insert(WorkoutExercise(id = entry, workoutId = id, exerciseId = exercise, position = 0,
                    notes = "Recorded exercise note", sourceWorkoutId = source,
                    sourceWorkoutName = when (source) { "back" -> "Back — Light"; "chest" -> "Chest — Strength"; else -> "" }))
                db.workoutSetDao().insert(WorkoutSet(id = "$entry-set", workoutExerciseId = entry, position = 0,
                    reps = 10, actualReps = 8, weightGrams = 20000, completedAt = start + 30000,
                    activeMillis = 30000, restMillis = 60000, notes = "Keep this set note", rpe = 7))
            }
            add("recent", 1, "row", "back", "day-a")
            add("other", 2, "bench", "chest", "day-b")
            add("old", 60, "row", "back", "day-a")
            add("legacy", 3, "row", null, "day-a")
            db.workoutExerciseDao().insert(WorkoutExercise(id = "recent-curl", workoutId = "recent", exerciseId = "curl", position = 1,
                sourceWorkoutId = "arms", sourceWorkoutName = "Biceps — Light"))
            db.workoutSetDao().insert(WorkoutSet(id = "recent-curl-set", workoutExerciseId = "recent-curl", position = 0,
                reps = 12, actualReps = 12, weightGrams = 5000, completedAt = now - 86_400_000L + 90000))
        }
        return db to FitnessRepository(db)
    }

    private fun openHistory() {
        compose.onNode(hasText("Reports") and hasClickAction()).performClick()
        compose.onNodeWithTag("progress-history-tile").performClick()
        waitTag("history-filter-count")
    }
    private fun filter(kind: String, value: String) {
        compose.onNodeWithTag("history-filter-$kind").performScrollTo().performClick()
        compose.onNodeWithTag("history-filter-$kind-$value").performClick()
    }
    private fun assertCount(expected: String) {
        compose.onNodeWithTag("history-filter-count").performScrollTo().assert(hasText(expected))
    }
    private fun scrollExercise(tag: String) = compose.onNodeWithTag("history-exercises").performScrollToNode(hasTestTag(tag))
    private fun openReplacement() {
        scrollExercise("session-exercise-options-recent-row")
        compose.onNodeWithTag("session-exercise-options-recent-row").performClick()
        compose.onNodeWithTag("change-history-exercise-recent-row").performClick()
        waitTag("exercise-picker-list")
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun freezeOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
    }
    private fun restoreOrientation() {
        if (InstrumentationRegistry.getArguments().getString("expectedOrientation") != null)
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "history-picker-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val landscape = bitmap.width > bitmap.height
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it == "landscape", landscape) }
            val orientation = if (landscape) "landscape" else "portrait"
            java.io.File(folder, "$name-$orientation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
