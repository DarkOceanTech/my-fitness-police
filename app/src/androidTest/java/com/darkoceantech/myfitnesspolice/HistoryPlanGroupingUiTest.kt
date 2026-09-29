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

class HistoryPlanGroupingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportsMenuGroupsSelectedHistoryIntoExistingPlanWithoutChangingPrescriptions() {
        val (db, repo) = fixture()
        val before = runBlocking { listOf("a", "b", "c").associateWith { db.workoutDao().getDetails(it)!! } }
        val members = runBlocking { db.trainingPlanDao().get("existing")!!.members }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            waitTag("reports-options")
            compose.onNodeWithTag("reports-options").performClick()
            compose.onNodeWithTag("group-history-workouts-menu").performClick()
            waitTag("history-group-dialog")
            compose.onNodeWithTag("confirm-history-group").assertIsNotEnabled()
            scroll("history-group-select-all")
            compose.onNodeWithTag("history-group-select-all").performClick()
            compose.onNodeWithTag("history-group-selection-count").assert(hasText("3 selected"))
            compose.onNodeWithTag("history-group-clear").performClick()
            compose.onNodeWithTag("history-group-selection-count").assert(hasText("0 selected"))
            scroll("history-group-workout-a")
            compose.onNodeWithTag("history-group-workout-a").performClick()
            compose.onNodeWithTag("cancel-history-group").performClick()
            waitGone("history-group-dialog")
            assertEquals(before, runBlocking { before.keys.associateWith { db.workoutDao().getDetails(it)!! } })

            compose.onNodeWithTag("reports-options").performClick()
            compose.onNodeWithTag("group-history-workouts-menu").performClick()
            waitTag("history-group-dialog")
            listOf("a", "b").forEach { id -> scroll("history-group-workout-$id"); compose.onNodeWithTag("history-group-workout-$id").performClick() }
            compose.onNodeWithTag("confirm-history-group").assertIsNotEnabled()
            scroll("history-group-plan")
            compose.onNodeWithTag("history-group-plan").performClick()
            compose.onNodeWithTag("history-group-plan-empty").assertExists()
            compose.onNodeWithTag("history-group-plan-existing").performClick()
            capture("group-existing-plan")
            compose.onNodeWithTag("confirm-history-group").assertIsEnabled().performClick()
            waitGone("history-group-dialog")
            waitTag("history-list")
            val groupId = runBlocking { db.workoutDao().getDetails("a")!!.workout.historyGroupId }
            assertFalse(groupId.isNullOrBlank())
            listOf("a", "b").forEach { id ->
                val after = runBlocking { db.workoutDao().getDetails(id)!! }
                assertEquals(before.getValue(id).copy(workout = before.getValue(id).workout.copy(
                    sourceTrainingPlanId = "existing", trainingPlan = "Existing training", historyGroupId = groupId)), after)
            }
            assertEquals(before.getValue("c"), runBlocking { db.workoutDao().getDetails("c")!! })
            assertEquals(members, runBlocking { db.trainingPlanDao().get("existing")!!.members })
            // The earlier session is the stable route for one combined card containing A and B.
            compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag("history-workout-b"))
            compose.onNodeWithTag("history-workout-b").assert(hasText("Existing training"))
            compose.onNodeWithTag("history-workout-a").assertDoesNotExist()
            compose.onAllNodesWithText("Plan ·", substring = true).assertCountEquals(0)
            capture("history-grouped-plan-card")
        } finally { restoreOrientation(); db.close() }
    }

    @Test fun selectedHistoryCanCreateANewPlanWhileKeepingNotesAndTimes() {
        val (db, repo) = fixture()
        val before = runBlocking { db.workoutDao().getDetails("a")!! }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            waitTag("history-sessions")
            compose.onNodeWithContentDescription("Workout Log options").performClick()
            compose.onNodeWithTag("group-history-workouts-menu").performClick()
            waitTag("history-group-dialog")
            scroll("history-group-workout-a")
            compose.onNodeWithTag("history-group-workout-a").performClick()
            scroll("history-group-selection-count")
            compose.onNodeWithTag("history-group-selection-count").assert(hasText("1 selected"))
            scroll("history-group-workout-a")
            compose.onNodeWithTag("history-group-workout-a").assertIsOn()
            scroll("history-group-new-mode")
            compose.onNodeWithTag("history-group-new-mode").performClick()
            compose.onNodeWithTag("confirm-history-group").assertIsNotEnabled()
            scroll("history-group-plan-name")
            compose.onNodeWithTag("history-group-plan-name").performTextInput("Recovery week")
            compose.waitForIdle()
            android.os.SystemClock.sleep(350)
            compose.onNodeWithText("Group workouts into plan").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close workout grouping").assertIsDisplayed()
            compose.onNodeWithTag("history-group-plan-name").assertIsDisplayed()
            compose.onNodeWithTag("confirm-history-group").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("cancel-history-group").assertIsDisplayed()
            capture("group-new-plan-keyboard")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("confirm-history-group").performClick()
            waitGone("history-group-dialog")
            val after = runBlocking { db.workoutDao().getDetails("a")!! }
            val newPlan = runBlocking { db.trainingPlanDao().get(after.workout.sourceTrainingPlanId!!)!! }
            assertEquals("Recovery week", newPlan.plan.name)
            assertTrue(newPlan.members.isEmpty())
            assertFalse(after.workout.historyGroupId.isNullOrBlank())
            assertEquals(before.copy(workout = before.workout.copy(sourceTrainingPlanId = newPlan.plan.id,
                trainingPlan = "Recovery week", historyGroupId = after.workout.historyGroupId)), after)
            assertNull(runBlocking { db.workoutDao().getDetails("b")!!.workout.sourceTrainingPlanId })
            compose.onNodeWithTag("history-sessions").performScrollToNode(hasTestTag("history-workout-a"))
            compose.onNodeWithTag("history-workout-a").assert(hasText("Recovery week"))
            compose.onAllNodesWithText("Plan ·", substring = true).assertCountEquals(0)
        } finally { restoreOrientation(); db.close() }
    }

    private fun fixture(): Pair<FitnessDatabase, FitnessRepository> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        runBlocking {
            db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable machine"))
            val now = System.currentTimeMillis()
            listOf("a", "b", "c").forEachIndexed { index, id ->
                val start = now - (index + 1) * 86_400_000L
                db.workoutDao().insert(Workout(id = id, name = "Recorded workout ${id.uppercase()}", startedAt = start,
                    finishedAt = start + 90000, notes = "Session note $id"))
                db.workoutExerciseDao().insert(WorkoutExercise(id = "entry-$id", workoutId = id, exerciseId = "row", position = 0,
                    notes = "Exercise note $id", sourceWorkoutId = "template", sourceWorkoutName = "Back template"))
                db.workoutSetDao().insert(WorkoutSet(id = "set-$id", workoutExerciseId = "entry-$id", position = 0,
                    reps = 10, actualReps = 8, weightGrams = 15000, completedAt = start + 30000,
                    activeMillis = 30000, restMillis = 60000, rpe = 8, notes = "Set note $id", isWarmup = index == 0))
            }
            db.workoutDao().insert(Workout(id = "unfinished", name = "Unfinished session"))
            db.workoutDao().insert(Workout(id = "template", kind = "plan", name = "Back template"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "template-entry", workoutId = "template", exerciseId = "row", position = 0))
            db.workoutSetDao().insert(WorkoutSet(id = "template-set", workoutExerciseId = "template-entry", position = 0, reps = 12, weightGrams = 12000))
            db.trainingPlanDao().insert(TrainingPlan(id = "existing", name = "Existing training"))
            db.trainingPlanDao().insertMembers(listOf(TrainingPlanWorkout("existing", "template", 0)))
            db.trainingPlanDao().insert(TrainingPlan(id = "empty", name = "Empty plan"))
        }
        return db to FitnessRepository(db)
    }
    private fun scroll(tag: String) = compose.onNodeWithTag("history-group-list").performScrollToNode(hasTestTag(tag))
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
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "history-picker-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val landscape = bitmap.width > bitmap.height
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it == "landscape", landscape) }
            java.io.File(folder, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
