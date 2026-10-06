package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImportLastSessionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importThroughPlanPreservesHistoryAndMembershipsAndRequiresExplicitSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "import-last-session-ui-${java.util.UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        val repository = FitnessRepository(db)
        fun details(id: String) = runBlocking { db.workoutDao().getDetails(id)!! }
        val trainingId = seed(db)
        val original = details("new-workout")
        val other = details("other-workout")
        val history = details("recorded-session")
        val membership = runBlocking { db.trainingPlanDao().get(trainingId)!!.orderedItems() }
        val editId = WorkoutEditorRepository.editId(original.workout.id)
        try {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
            compose.setContent { MyFitnessPoliceApp(repository) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-1").performClick()
            waitTag("workout-home")
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("training-plan-$trainingId"))
            compose.onNodeWithTag("training-plan-$trainingId").performClick()
            openWorkout("new-workout", "Curl")
            saveButton().assertIsNotEnabled()
            expand("Curl")
            compose.onNodeWithContentDescription("Reps set 1").assertTextEquals("10")
            compose.onNodeWithContentDescription("Weight set 1").assertTextEquals("0")
            compose.onNodeWithContentDescription("Reps set 2").assertDoesNotExist()

            viewLast("Curl", details(editId).exercises.single().workoutExercise.id)
            assertPreview()
            capturePortrait("last-session-preview")
            compose.onNodeWithTag("set-action-recorded-set-0").performScrollTo().performClick()
            waitTag("last-session-set-details")
            compose.onNodeWithTag("last-session-set-rpe").assertTextEquals("5").assertHasNoClickAction()
            compose.onNodeWithTag("last-session-set-actual").assertTextEquals("11").assertHasNoClickAction()
            compose.onNodeWithTag("last-session-set-details")
                .performScrollToNode(hasTestTag("last-session-set-notes"))
            compose.onNodeWithTag("last-session-set-notes").assertTextEquals("Historical set note 0").assertHasNoClickAction()
            compose.onNodeWithTag("edit-history-set").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back to last session").performClick()
            waitGone("last-session-set-details")
            compose.onNodeWithTag("close-last-session").performClick()
            waitGone("last-session-dialog")
            assertEquals(listOf(10), details(editId).orderedSets().map { it.reps })
            assertEquals("Default note", details(editId).exercises.single().workoutExercise.notes)
            saveButton().assertIsNotEnabled()
            assertEquals(original, details("new-workout"))
            assertEquals(history, details("recorded-session"))

            viewLast("Curl", details(editId).exercises.single().workoutExercise.id)
            val beforeConfirmation = details(editId)
            compose.onNodeWithTag("import-session").assertIsEnabled().performClick()
            waitTag("last-session-import-confirmation")
            compose.onNodeWithText("Replace current sets?").assertIsDisplayed()
            compose.onNodeWithText("All current sets for this exercise will be overwritten with 3 sets from the session you viewed.")
                .assertIsDisplayed()
            assertEquals(beforeConfirmation, details(editId))
            assertEquals(original, details("new-workout"))
            assertEquals(history, details("recorded-session"))
            capturePortrait("last-session-import-confirmation")
            compose.onNodeWithTag("back-from-import-confirmation").performClick()
            waitGone("last-session-import-confirmation")
            waitTag("last-session-exercises")
            assertEquals(beforeConfirmation, details(editId))
            compose.onNodeWithTag("import-session").assertIsEnabled().performClick()
            waitTag("last-session-import-confirmation")
            compose.onNodeWithTag("confirm-import-session").assertIsEnabled().performClick()
            waitGone("last-session-dialog")
            waitTag("workout-imported")
            compose.waitUntil(5000) { details(editId).orderedSets().size == 3 }
            assertImported(details(editId), history)
            assertEquals(original, details("new-workout"))
            assertEquals(history, details("recorded-session"))
            assertVisiblePrescriptions()
            saveButton().assertIsEnabled()

            // Import changes only the working copy; the ordinary unsaved-changes flow still applies.
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNodeWithTag("discard-workout-changes").performClick()
            waitTag("training-plan-detail")
            assertNull(runBlocking { db.workoutDao().getDetails(editId) })
            assertEquals(original, details("new-workout"))
            assertEquals(membership, runBlocking { db.trainingPlanDao().get(trainingId)!!.orderedItems() })

            openWorkout("new-workout", "Curl")
            expand("Curl")
            compose.onNodeWithContentDescription("Reps set 2").assertDoesNotExist()
            viewLast("Curl", details(editId).exercises.single().workoutExercise.id)
            compose.onNodeWithTag("import-session").assertIsEnabled().performClick()
            waitTag("last-session-import-confirmation")
            compose.onNodeWithTag("confirm-import-session").assertIsEnabled().performClick()
            waitGone("last-session-dialog")
            waitTag("workout-imported")
            saveButton().assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitTag("plan-saved")
            saveButton().assertIsNotEnabled()
            assertImported(details("new-workout"), history)
            assertEquals(original.workout.id, details("new-workout").workout.id)
            assertEquals(membership, runBlocking { db.trainingPlanDao().get(trainingId)!!.orderedItems() })
            assertEquals(history, details("recorded-session"))
            assertEquals(other, details("other-workout"))

            compose.onNodeWithContentDescription("Back to workout home").performClick()
            waitTag("training-plan-detail")
            openWorkout("new-workout", "Curl")
            expand("Curl")
            assertVisiblePrescriptions()
            capturePortrait("imported-prescriptions")
            saveButton().assertIsNotEnabled()
            // Verify persistence through a second database connection, not just the observed UI state.
            val reopened = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
            try {
                assertImported(runBlocking { reopened.workoutDao().getDetails("new-workout")!! }, history)
                assertEquals(membership, runBlocking { reopened.trainingPlanDao().get(trainingId)!!.orderedItems() })
            } finally { reopened.close() }

            // An exercise without matching history opens a dismissible preview and cannot import.
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            waitTag("training-plan-detail")
            openWorkout("other-workout", "Row")
            expand("Row")
            val otherEditId = WorkoutEditorRepository.editId("other-workout")
            val untouchedEdit = details(otherEditId)
            viewLast("Row", untouchedEdit.exercises.single().workoutExercise.id)
            compose.onNodeWithTag("import-session").assertIsNotEnabled()
            capturePortrait("last-session-empty")
            compose.onNodeWithTag("close-last-session").performClick()
            waitGone("last-session-dialog")
            assertEquals(untouchedEdit, details(otherEditId))
            assertEquals(other, details("other-workout"))
            saveButton().assertIsNotEnabled()
            assertEquals(history, details("recorded-session"))
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun seed(db: FitnessDatabase): String = runBlocking {
        db.exerciseDao().insert(Exercise(id = "curl", name = "Curl", equipment = "Dumbbell"))
        db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Cable"))
        db.exerciseDao().insert(Exercise(id = "press", name = "Unrelated press", equipment = "Barbell"))
        listOf("new-workout" to "curl", "other-workout" to "row").forEach { (id, exercise) ->
            db.workoutDao().insert(Workout(id = id, kind = "plan", name = if (exercise == "curl") "Biceps light" else "Back rows"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id,
                exerciseId = exercise, position = 0, notes = "Default note"))
            db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry",
                position = 0, reps = 10, weightGrams = 0))
        }
        db.workoutDao().insert(Workout(id = "recorded-session", name = "Last arm session", kind = "session",
            startedAt = 100_000, finishedAt = 400_000))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "recorded-curl", workoutId = "recorded-session",
            exerciseId = "curl", position = 0, notes = "Keep elbows tucked."))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "recorded-press", workoutId = "recorded-session",
            exerciseId = "press", position = 1, notes = "Unrelated exercise note"))
        db.workoutSetDao().insert(WorkoutSet(id = "recorded-press-set", workoutExerciseId = "recorded-press",
            position = 0, reps = 15, actualReps = 13, weightGrams = 10000, completedAt = 250_000))
        listOf(
            Triple(12, 4536L, "none"),
            Triple(8, 9072L, "superset"),
            Triple(6, 6804L, "drop_set"),
        ).forEachIndexed { index, (reps, weight, modifier) ->
            db.workoutSetDao().insert(WorkoutSet(id = "recorded-set-$index", workoutExerciseId = "recorded-curl",
                position = index, reps = reps, weightGrams = weight, modifier = modifier, isWarmup = index == 0,
                actualReps = reps - 1, rpe = index + 5, notes = "Historical set note $index",
                activeMillis = 20_000L + index, restMillis = 30_000L + index, completedAt = 180_000L + index))
        }
        TrainingPlanRepository(db).save(null, "Upper body training", listOf("new-workout", "other-workout"), "Monday")
    }

    private fun assertImported(target: WorkoutDetails, source: WorkoutDetails) {
        val copied = target.exercises.single()
        val sourceEntry = source.exercises.single { it.exercise.id == "curl" }
        val sets = copied.sets.sortedBy { it.position }
        val originalSets = sourceEntry.sets.sortedBy { it.position }
        assertEquals("Default note", copied.workoutExercise.notes)
        assertEquals(originalSets.map { it.reps }, sets.map { it.reps })
        assertEquals(originalSets.map { it.weightGrams }, sets.map { it.weightGrams })
        assertEquals(originalSets.map { it.modifier }, sets.map { it.modifier })
        assertEquals(originalSets.map { it.isWarmup }, sets.map { it.isWarmup })
        assertEquals(listOf(0, 1, 2), sets.map { it.position })
        assertEquals(3, sets.map { it.id }.distinct().size)
        assertTrue(sets.none { set -> originalSets.any { it.id == set.id } })
        assertTrue(sets.all { it.actualReps == null && it.rpe == null && it.notes.isEmpty() &&
            it.completedAt == null && it.activeMillis == 0L && it.restMillis == 0L })
    }

    private fun openWorkout(id: String, exercise: String) {
        waitTag("training-plan-detail")
        compose.onNodeWithTag("training-plan-workouts").performScrollToNode(hasTestTag("training-workout-$id"))
        compose.onNodeWithTag("training-workout-$id").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Expand $exercise").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun expand(exercise: String) = compose.onNodeWithContentDescription("Expand $exercise").performScrollTo().performClick()
    private fun viewLast(exercise: String, entryId: String) {
        compose.onNodeWithContentDescription("Exercise options for $exercise").performScrollTo().performClick()
        compose.onNodeWithTag("view-last-session-$entryId").assertIsEnabled().performClick()
        waitTag("last-session-dialog")
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("last-session-exercises").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("last-session-empty").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun assertPreview() {
        waitTag("active-card-recorded-curl")
        compose.onNodeWithTag("active-card-recorded-curl").assertExists()
        compose.onNodeWithText("Keep elbows tucked.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("actual-recorded-set-0", useUnmergedTree = true).performScrollTo()
            .assert(hasText("11") or hasAnyDescendant(hasText("11"))).assertHasNoClickAction()
        compose.onNodeWithTag("active-card-recorded-press").assertDoesNotExist()
        compose.onNodeWithText("Unrelated press").assertDoesNotExist()
        compose.onNodeWithText("Unrelated exercise note").assertDoesNotExist()
        compose.onNodeWithTag("import-session").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("close-last-session").assertIsDisplayed()
    }
    private fun saveButton(): SemanticsNodeInteraction {
        compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
        return compose.onNodeWithTag("save-workout")
    }
    private fun assertVisiblePrescriptions() {
        compose.onNodeWithText("Default note").performScrollTo().assertIsDisplayed()
        listOf("12", "8", "6").forEachIndexed { index, reps ->
            compose.onNodeWithContentDescription("Reps set ${index + 1}").performScrollTo().assertTextEquals(reps)
        }
        compose.onNodeWithContentDescription("Weight set 1").assertTextEquals("10")
        compose.onNodeWithContentDescription("Weight set 2").assertTextEquals("20")
        compose.onNodeWithContentDescription("Weight set 3").assertTextEquals("15")
        compose.onNodeWithContentDescription("Modifier set 2").assertTextEquals("S")
        compose.onNodeWithContentDescription("Modifier set 3").assertTextEquals("D")
        compose.onNodeWithContentDescription("Type set 1").assertTextEquals("Wu")
        compose.onNodeWithContentDescription("Type set 2").assertTextEquals("Ws")
    }
    private fun waitTag(tag: String) {
        compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitGone(tag: String) {
        compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }
    private fun capturePortrait(name: String) {
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
