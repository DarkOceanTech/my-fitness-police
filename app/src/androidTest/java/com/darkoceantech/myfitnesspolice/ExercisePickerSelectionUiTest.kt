package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExercisePickerSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleSelectionsRemainOpenFilterRemoveAndCommitOnlyWhenWorkoutIsSaved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        val catalog = listOf(
            Exercise(id = "curl", name = "Curl", equipment = "Dumbbell", description = "Flex the elbow.", primaryMuscles = "Biceps brachii"),
            Exercise(id = "row", name = "Row", equipment = "Cable", description = "Pull toward the torso.", primaryMuscles = "Latissimus dorsi"),
            Exercise(id = "press", name = "Press", equipment = "Barbell", description = "Press upward.", primaryMuscles = "Pectoralis major"),
            Exercise(id = "squat", name = "Squat", equipment = "Barbell", description = "Bend the hips and knees.", primaryMuscles = "Quadriceps femoris"),
        )
        val original = runBlocking {
            catalog.forEach { db.exerciseDao().insert(it) }
            repo.chooseExercise("curl")
            val draft = repo.observeSessions().first().single()
            repo.saveSet(draft.workout.id, draft.exercises.single().workoutExercise.id, null, 12, 4500)
            repo.savePlan(draft.workout.id, "Arm strength")
            db.workoutDao().getDetails(draft.workout.id)!!
        }
        val planId = original.workout.id
        val editId = WorkoutEditorRepository.editId(planId)
        fun edited() = runBlocking { db.workoutDao().getDetails(editId)!! }
        fun scroll(tag: String) = compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag(tag))
        fun selectedCount(expected: Int) {
            compose.waitUntil(5000) { edited().exercises.size == expected }
            scroll("exercise-picker-selected-count")
            compose.onNodeWithTag("exercise-picker-selected-count").assertTextEquals("$expected selected")
            compose.onNodeWithTag("exercise-picker-screen").assertIsDisplayed()
        }
        fun add(id: String) {
            scroll("select-exercise-$id")
            compose.onNodeWithTag("select-exercise-$id").performClick()
        }
        fun remove(id: String) {
            scroll("remove-exercise-$id")
            compose.onNodeWithTag("remove-exercise-$id").performClick()
        }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            // The exercise card can initially be outside the shorter landscape viewport.
            compose.waitUntil(5000) { compose.onAllNodesWithTag("workout-metadata").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("add-workout-exercise"))
            compose.onNodeWithTag("add-workout-exercise").performClick()
            selectedCount(1)
            compose.onNodeWithText("Cancel").assertDoesNotExist()
            scroll("remove-exercise-curl")
            compose.onNodeWithTag("remove-exercise-curl").assertTextEquals("Remove")

            scroll("select-exercise-row")
            // Two callbacks in one frame must not create duplicate workout exercise rows.
            compose.onNodeWithTag("select-exercise-row").performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
            selectedCount(2)
            add("press")
            selectedCount(3)
            assertEquals(1, edited().exercises.count { it.exercise.id == "row" })
            assertEquals(original, runBlocking { db.workoutDao().getDetails(planId)!! })

            compose.onNodeWithTag("exercise-picker-selected-count").performClick().assertIsSelected()
            scroll("exercise-picker-count")
            compose.onNodeWithTag("exercise-picker-count").assertTextEquals("3 of 4 exercises · select Add to use a movement")
            screenshot("picker-selected-only")
            val rowEntry = edited().exercises.single { it.exercise.id == "row" }
            remove("curl")
            selectedCount(2)
            assertFalse(edited().exercises.any { it.exercise.id == "curl" })
            remove("press")
            selectedCount(1)
            assertEquals(rowEntry.copy(workoutExercise = rowEntry.workoutExercise.copy(position = 0)), edited().exercises.single())
            compose.onNodeWithTag("exercise-picker-selected-count").performClick().assertIsNotSelected()
            scroll("exercise-picker-count")
            compose.onNodeWithTag("exercise-picker-count").assertTextEquals("4 of 4 exercises · select Add to use a movement")
            add("curl")
            selectedCount(2)
            assertEquals(listOf(0, 1), edited().orderedExercises().map { it.workoutExercise.position })

            compose.onNodeWithTag("add-picker-exercise").performClick()
            compose.onNodeWithText("Create New Exercise").assertIsDisplayed()
            compose.onNodeWithTag("exercise-editor-screen").assertIsDisplayed()
            compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
            selectedCount(2)
            screenshot("picker-persisted-selections")
            compose.onNodeWithContentDescription("Back to workout").performClick()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("plan-saved").fetchSemanticsNodes().isNotEmpty() }
            val saved = runBlocking { db.workoutDao().getDetails(planId)!! }
            assertEquals(listOf("row", "curl"), saved.orderedExercises().map { it.exercise.id })
            assertTrue(saved.exercises.all { it.sets.size == 1 })
            assertEquals(4, runBlocking { db.exerciseDao().getAll().size })
        } finally { restoreOrientation(); db.close() }
    }

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

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "history-picker-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val landscape = bitmap.width > bitmap.height
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it == "landscape", landscape) }
            val orientation = if (landscape) "landscape" else "portrait"
            java.io.File(folder, "$name-$orientation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        finally { bitmap.recycle() }
    }
}
