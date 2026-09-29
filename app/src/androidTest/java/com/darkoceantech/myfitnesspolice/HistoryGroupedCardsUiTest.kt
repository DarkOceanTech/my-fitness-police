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

class HistoryGroupedCardsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletingOneGroupMemberRequiresConfirmationAndPreservesTheOtherWorkouts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        seed(db)
        val before = runBlocking { listOf("session-first", "session-second", "previous-week")
            .associateWith { db.workoutDao().getDetails(it)!! } }
        fun chooseSecondMember() {
            compose.onNodeWithContentDescription("Workout Log options").performClick()
            compose.onNodeWithText("Delete a workout…").performClick()
            waitTag("history-delete-member-list")
            compose.onNodeWithTag("history-delete-member-list").performScrollToNode(hasTestTag("delete-history-member-session-second"))
            compose.onNodeWithTag("delete-history-member-session-second").performClick()
            waitTag("confirm-delete-history")
            compose.onNodeWithText("This permanently deletes “Biceps rehab”, including its sets, notes, and timing. This cannot be undone.")
                .assertIsDisplayed()
            compose.onNodeWithText("The other workouts in this recorded group will stay available.").assertIsDisplayed()
        }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            waitTag("history-list")
            scrollSessions("history-workout-session-first")
            compose.onNodeWithTag("history-workout-session-first").performClick()
            waitTag("history-exercises")
            chooseSecondMember()
            compose.onNodeWithText("Cancel").performClick()
            waitGone("confirm-delete-history")
            assertEquals(before, runBlocking { before.keys.associateWith { db.workoutDao().getDetails(it)!! } })

            chooseSecondMember()
            capture("confirm-delete-one-group-member")
            compose.onNodeWithTag("confirm-delete-history").performClick()
            waitGone("confirm-delete-history")
            waitTag("history-list")
            assertNull(runBlocking { db.workoutDao().getDetails("session-second") })
            assertEquals(before.getValue("session-first"), runBlocking { db.workoutDao().getDetails("session-first")!! })
            assertEquals(before.getValue("previous-week"), runBlocking { db.workoutDao().getDetails("previous-week")!! })
            scrollSessions("history-workout-session-first")
            compose.onNodeWithTag("history-workout-session-first")
                .assert(hasText("Strength rotation")).assert(hasText("2 sets · 16 reps"))
            scrollSessions("history-workout-previous-week")
            compose.onNodeWithTag("history-workout-previous-week").assert(hasText("Strength rotation"))
            assertEquals(4, runBlocking { db.exerciseDao().getAll().size })
            assertNotNull(runBlocking { db.trainingPlanDao().get("shared-plan") })
        } finally { restoreOrientation(); db.close() }
    }

    @Test fun separateWeeksStaySeparateAndCombinedDetailsKeepOrderAndCorrectTheOwningSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "history-group-cards-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        val db = open()
        seed(db)
        val before = runBlocking { listOf("session-first", "session-second", "previous-week")
            .associateWith { db.workoutDao().getDetails(it)!! } }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            waitTag("history-list")
            listOf("session-first", "previous-week").forEach { id ->
                scrollSessions("history-workout-$id")
                compose.onNodeWithTag("history-workout-$id").assert(hasText("Strength rotation"))
            }
            compose.onNodeWithTag("history-workout-session-second").assertDoesNotExist()
            compose.onAllNodesWithText("Plan ·", substring = true).assertCountEquals(0)
            capture("separate-history-group-cards")
            scrollSessions("history-workout-session-first")
            compose.onNodeWithTag("history-workout-session-first").performClick()
            waitTag("history-exercises")
            scrollExercises("history-workout-totals")
            compose.onNodeWithTag("history-workout-totals").assert(hasAnyDescendant(hasText("4 sets · 32 reps")))
            scrollExercises("history-collapse-all")
            compose.onNodeWithTag("history-collapse-all").performClick()
            val orderedEntries = listOf("first-row", "first-curl", "second-row", "second-press")
            orderedEntries.forEachIndexed { index, id ->
                // Assert the rendered chronological position, including both separate Row entries.
                scrollExercises("history-group-entry-$index-$id")
                compose.onNodeWithTag("history-group-entry-$index-$id").assertExists()
                compose.onNodeWithTag("exercise-card-content-$id").assertDoesNotExist()
            }
            capture("grouped-exercises-chronological")

            scrollExercises("history-card-toggle-second-row")
            compose.onNodeWithTag("history-card-toggle-second-row").performClick()
            compose.onNodeWithTag("session-set-second-row-set").performScrollTo().performClick()
            waitTag("history-set-info-panel")
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-actual").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-actual-options").performScrollToNode(hasText("9"))
            compose.onNode(hasText("9") and hasClickAction()).performClick()
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { db.workoutDao().getDetails("session-second")!!
                .orderedSets().single { it.id == "second-row-set" }.actualReps == 9 } }
            compose.onNodeWithTag("history-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-note-input").performTextReplacement("Corrected the second workout's row set.")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-history-set-note").performClick()
            waitGone("history-set-note-input")
            compose.onNodeWithContentDescription("Close set info").performClick()
            scrollExercises("history-workout-totals")
            compose.onNodeWithTag("history-workout-totals").assert(hasAnyDescendant(hasText("4 sets · 33 reps")))

            scrollExercises("session-exercise-options-second-row")
            compose.onNodeWithTag("session-exercise-options-second-row").performClick()
            compose.onNodeWithTag("change-history-exercise-second-row").performClick()
            waitTag("exercise-picker-list")
            compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-pulldown"))
            compose.onNodeWithTag("select-exercise-pulldown").performClick()
            waitGone("exercise-picker-list")
            val afterSecond = runBlocking { db.workoutDao().getDetails("session-second")!! }
            val oldRow = before.getValue("session-second").exercises.single { it.workoutExercise.id == "second-row" }
            val changedRow = afterSecond.exercises.single { it.workoutExercise.id == "second-row" }
            assertEquals(oldRow.workoutExercise.copy(exerciseId = "pulldown"), changedRow.workoutExercise)
            assertEquals(oldRow.sets.single().copy(actualReps = 9, notes = "Corrected the second workout's row set."), changedRow.sets.single())
            assertEquals(before.getValue("session-second").workout, afterSecond.workout)
            assertEquals(before.getValue("session-second").exercises.single { it.workoutExercise.id == "second-press" },
                afterSecond.exercises.single { it.workoutExercise.id == "second-press" })
            assertEquals(before.getValue("session-first"), runBlocking { db.workoutDao().getDetails("session-first")!! })
            assertEquals(before.getValue("previous-week"), runBlocking { db.workoutDao().getDetails("previous-week")!! })
            scrollExercises("history-group-entry-2-second-row")
            compose.onNodeWithTag("history-group-entry-2-second-row").assertExists()
            capture("grouped-second-session-corrected")

            // A separate Room connection reads the durable grouping and corrections from disk.
            val reopened = open()
            try {
                assertEquals(afterSecond, runBlocking { reopened.workoutDao().getDetails("session-second")!! })
                assertEquals("current-group", runBlocking { reopened.workoutDao().getDetails("session-first")!!.workout.historyGroupId })
                assertEquals("older-group", runBlocking { reopened.workoutDao().getDetails("previous-week")!!.workout.historyGroupId })
            } finally { reopened.close() }
            compose.onNodeWithContentDescription("Back to Workout Log").performClick()
            scrollSessions("history-workout-previous-week")
            compose.onNodeWithTag("history-workout-previous-week").performClick()
            waitTag("history-exercises")
            scrollExercises("history-workout-totals")
            compose.onNodeWithTag("history-workout-totals").assert(hasAnyDescendant(hasText("1 sets · 8 reps")))
            compose.onNodeWithTag("active-card-second-row").assertDoesNotExist()
        } finally { restoreOrientation(); db.close(); context.deleteDatabase(databaseName) }
    }

    private fun seed(db: FitnessDatabase) = runBlocking {
        listOf(Exercise(id = "row", name = "Cable row", equipment = "Cable", primaryMuscles = "Latissimus dorsi"),
            Exercise(id = "curl", name = "Dumbbell curl", equipment = "Dumbbell", primaryMuscles = "Biceps brachii"),
            Exercise(id = "press", name = "Bench press", equipment = "Barbell", primaryMuscles = "Pectoralis major"),
            Exercise(id = "pulldown", name = "Lat pulldown", equipment = "Cable", primaryMuscles = "Latissimus dorsi"))
            .forEach { db.exerciseDao().insert(it) }
        db.trainingPlanDao().insert(TrainingPlan(id = "shared-plan", name = "Strength rotation"))
        val firstStart = System.currentTimeMillis() - 86_400_000L
        suspend fun workout(id: String, name: String, start: Long, groupId: String, entries: List<Pair<String, String>>) {
            db.workoutDao().insert(Workout(id = id, name = name, startedAt = start, finishedAt = start + 120000,
                sourceTrainingPlanId = "shared-plan", trainingPlan = "Strength rotation", historyGroupId = groupId,
                notes = "Session notes for $id"))
            // Insert reverse order so the displayed position must come from the recorded sequence.
            entries.withIndex().toList().reversed().forEach { (position, pair) ->
                val (entryId, exerciseId) = pair
                db.workoutExerciseDao().insert(WorkoutExercise(id = entryId, workoutId = id, exerciseId = exerciseId,
                    position = position, notes = "Exercise note for $entryId", sourceWorkoutId = "template-$id", sourceWorkoutName = name))
                db.workoutSetDao().insert(WorkoutSet(id = "$entryId-set", workoutExerciseId = entryId, position = 0,
                    reps = 10, actualReps = 8, weightGrams = 20000, completedAt = start + 30000 + position * 30000,
                    activeMillis = 10000, restMillis = 20000, rpe = 7, notes = "Set note for $entryId"))
            }
        }
        workout("session-second", "Biceps rehab", firstStart + 1_800_000, "current-group", listOf("second-row" to "row", "second-press" to "press"))
        workout("previous-week", "Earlier training", firstStart - 7 * 86_400_000L, "older-group", listOf("older-row" to "row"))
        workout("session-first", "Back light", firstStart, "current-group", listOf("first-row" to "row", "first-curl" to "curl"))
    }

    private fun scrollSessions(tag: String) = compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag(tag))
    private fun scrollExercises(tag: String) = compose.onNodeWithTag("history-exercises").performScrollToNode(hasTestTag(tag))
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitGone(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
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
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "history-plan-cards-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val landscape = bitmap.width > bitmap.height
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it == "landscape", landscape) }
            java.io.File(folder, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
