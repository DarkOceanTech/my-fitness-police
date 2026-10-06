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

class HistoryExerciseNoteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelSaveAndClearExerciseNotesAffectOnlyTheSelectedGroupedHistoryEntry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "history-exercise-note-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        val db = open()
        seed(db)
        fun details(id: String) = runBlocking { db.workoutDao().getDetails(id)!! }
        val original = details("second-session")
        val untouched = listOf("first-session", "previous-week", "saved-workout").associateWith(::details)
        val membership = runBlocking { db.trainingPlanDao().get("training-plan")!!.orderedItems() }
        try {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            waitTag("history-list")
            compose.onNodeWithTag("history-sessions")
                .performScrollToNode(hasTestTag("history-workout-first-session"))
            compose.onNodeWithTag("history-workout-first-session").performClick()
            waitTag("history-exercises")

            // Tapping the note opens a draft; cancelling must leave the original intact.
            scrollTo("exercise-note-second-row")
            compose.onNodeWithTag("exercise-note-second-row").performClick()
            waitTag("history-exercise-note-dialog")
            compose.onNodeWithTag("history-exercise-note-input").assert(hasText("Temporary reminder"))
                .performTextReplacement("Discard this draft")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("history-exercise-note-cancel").performClick()
            waitGone("history-exercise-note-dialog")
            assertEquals(original, details("second-session"))

            // The same editor remains available from a collapsed card's menu.
            scrollTo("history-card-toggle-second-row")
            compose.onNodeWithTag("history-card-toggle-second-row").performClick()
            compose.onNodeWithTag("exercise-card-content-second-row").assertDoesNotExist()
            scrollTo("session-exercise-options-second-row")
            compose.onNodeWithTag("session-exercise-options-second-row").performClick()
            compose.onNodeWithTag("edit-history-exercise-note-second-row").performClick()
            waitTag("history-exercise-note-dialog")
            compose.onNodeWithTag("history-exercise-note-input").assert(hasText("Temporary reminder"))
                .performTextReplacement("Added after reviewing this session.")
            Espresso.closeSoftKeyboard()
            capture("history-exercise-note-editor")
            compose.onNodeWithTag("history-exercise-note-save").performClick()
            waitGone("history-exercise-note-dialog")
            compose.waitUntil(5000) { details("second-session").exercises
                .single { it.workoutExercise.id == "second-row" }.workoutExercise.notes == "Added after reviewing this session." }
            val saved = details("second-session")
            assertOnlyNoteChanged(original, saved, "Added after reviewing this session.")
            assertEquals(untouched, untouched.keys.associateWith(::details))
            assertEquals(membership, runBlocking { db.trainingPlanDao().get("training-plan")!!.orderedItems() })
            val reopened = open()
            try { assertEquals(saved, runBlocking { reopened.workoutDao().getDetails("second-session")!! }) }
            finally { reopened.close() }

            // Clearing a temporary exercise note is an intentional, durable update.
            scrollTo("history-card-toggle-second-row")
            compose.onNodeWithTag("history-card-toggle-second-row").performClick()
            scrollTo("exercise-note-second-row")
            compose.onNodeWithTag("exercise-note-second-row").performClick()
            waitTag("history-exercise-note-dialog")
            compose.onNodeWithTag("history-exercise-note-input").assert(hasText("Added after reviewing this session."))
                .performTextReplacement("")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("history-exercise-note-save").assertIsEnabled().performClick()
            waitGone("history-exercise-note-dialog")
            compose.waitUntil(5000) { details("second-session").exercises
                .single { it.workoutExercise.id == "second-row" }.workoutExercise.notes.isEmpty() }
            assertOnlyNoteChanged(original, details("second-session"), "")
            assertEquals(untouched, untouched.keys.associateWith(::details))
            assertEquals(membership, runBlocking { db.trainingPlanDao().get("training-plan")!!.orderedItems() })
            scrollTo("exercise-note-second-row")
            compose.onNodeWithTag("exercise-note-second-row").assertHasClickAction().performClick()
            waitTag("history-exercise-note-dialog")
            assertEquals("", compose.onNodeWithTag("history-exercise-note-input").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("history-exercise-note-cancel").performClick()
            val reopenedAfterClear = open()
            try { assertOnlyNoteChanged(original, runBlocking { reopenedAfterClear.workoutDao()
                .getDetails("second-session")!! }, "") }
            finally { reopenedAfterClear.close() }
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun assertOnlyNoteChanged(before: WorkoutDetails, after: WorkoutDetails, note: String) {
        val expected = before.copy(exercises = before.exercises.map { entry ->
            if (entry.workoutExercise.id == "second-row")
                entry.copy(workoutExercise = entry.workoutExercise.copy(notes = note))
            else entry
        })
        assertEquals(expected, after)
    }

    private fun seed(db: FitnessDatabase) = runBlocking {
        db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable"))
        db.exerciseDao().insert(Exercise(id = "curl", name = "Dumbbell curl", equipment = "Dumbbell"))
        db.workoutDao().insert(Workout(id = "saved-workout", kind = "plan", name = "Back light"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "template-row", workoutId = "saved-workout",
            exerciseId = "row", position = 0, notes = "Reusable template note"))
        db.workoutSetDao().insert(WorkoutSet(id = "template-set", workoutExerciseId = "template-row", position = 0,
            reps = 10, weightGrams = 10000))
        db.trainingPlanDao().insert(TrainingPlan(id = "training-plan", name = "Weekly upper body"))
        TrainingPlanRepository(db).save("training-plan", "Weekly upper body", listOf("saved-workout"), "Monday")
        val now = System.currentTimeMillis() - 86_400_000L
        suspend fun session(id: String, start: Long, group: String, entries: List<Pair<String, String>>) {
            db.workoutDao().insert(Workout(id = id, name = id, kind = "session", startedAt = start,
                finishedAt = start + 120_000, sourceTrainingPlanId = "training-plan", trainingPlan = "Weekly upper body",
                historyGroupId = group, notes = "Session notes: $id"))
            entries.forEachIndexed { position, (entryId, exerciseId) ->
                db.workoutExerciseDao().insert(WorkoutExercise(id = entryId, workoutId = id,
                    exerciseId = exerciseId, position = position,
                    notes = if (entryId == "second-row") "Temporary reminder" else "Preserve $entryId note",
                    sourceWorkoutId = "saved-workout", sourceWorkoutName = "Back light"))
                db.workoutSetDao().insert(WorkoutSet(id = "$entryId-set", workoutExerciseId = entryId,
                    position = 0, reps = 10, actualReps = 9, weightGrams = 12000, rpe = 7,
                    notes = "Preserve $entryId set note", activeMillis = 20000, restMillis = 30000,
                    completedAt = start + 45000 + position * 45000))
            }
        }
        session("first-session", now, "current-group", listOf("first-row" to "row"))
        session("second-session", now + 1_800_000, "current-group", listOf("second-row" to "row", "second-curl" to "curl"))
        session("previous-week", now - 7 * 86_400_000L, "previous-group", listOf("previous-row" to "row"))
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("history-exercises").performScrollToNode(hasTestTag(tag))
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitGone(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            assertTrue("The regression test must run in portrait", bitmap.height > bitmap.width)
            val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "view-last-session-review").apply { mkdirs() }
            java.io.File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
