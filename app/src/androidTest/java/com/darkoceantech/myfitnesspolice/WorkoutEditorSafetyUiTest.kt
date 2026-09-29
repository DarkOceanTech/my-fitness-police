package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutEditorSafetyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backPromptsKeepEditsIsolatedAndIndividualDeletionRenumbersOnSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        val original = runBlocking {
            repository.addExercise("Curl", "Dumbbell")
            repository.chooseExercise(repository.observeExercises().first().single().id)
            val draft = repository.observeSessions().first().single()
            val entry = draft.exercises.single().workoutExercise.id
            repository.saveSet(draft.workout.id, entry, null, 11, 1000)
            repository.saveSet(draft.workout.id, entry, null, 12, 2000)
            repository.savePlan(draft.workout.id, "Arm technique")
            db.workoutDao().getDetails(draft.workout.id)!!
        }
        val planId = original.workout.id
        val editId = WorkoutEditorRepository.editId(planId)
        fun saved() = runBlocking { db.workoutDao().getDetails(planId)!! }
        fun edited() = runBlocking { db.workoutDao().getDetails(editId)!! }
        fun waitForEdit(predicate: (WorkoutDetails) -> Boolean) {
            compose.waitUntil(5000) { predicate(edited()) }
        }
        fun expand() = compose.onNodeWithContentDescription("Expand Curl").performScrollTo().performClick()
        fun chooseModifier(name: String) {
            compose.onNodeWithContentDescription("Modifier set 1").performScrollTo().performClick()
            compose.onNodeWithText("Regular").assertExists()
            compose.onNodeWithText("Superset").assertExists()
            compose.onNodeWithText("Drop set").assertExists()
            compose.onNodeWithText(name).performClick()
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repository) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            fun open() {
                compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Expand Curl").fetchSemanticsNodes().isNotEmpty() }
            }
            open()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            expand()
            compose.onNodeWithContentDescription("Modifier set 1").assertTextEquals("R")
            chooseModifier("Superset")
            waitForEdit { it.orderedSets().first().modifier == "superset" }
            assertEquals(original.orderedSets(), saved().orderedSets())

            // Android Back offers the same choices as the page's back arrow.
            Espresso.pressBack()
            compose.onNodeWithTag("save-workout-and-exit").assertExists()
            compose.onNodeWithTag("discard-workout-changes").assertExists()
            compose.onNodeWithTag("continue-editing-workout").performClick()
            compose.onNodeWithContentDescription("Modifier set 1").assertTextEquals("S")
            chooseModifier("Drop set")
            waitForEdit { it.orderedSets().first().modifier == "drop_set" }
            compose.onNodeWithContentDescription("Modifier set 1").assertTextEquals("D")
            compose.onNodeWithContentDescription("Remove set 2 of Curl").performScrollTo().performClick()
            waitForEdit { it.orderedSets().size == 2 }
            assertEquals(listOf(0, 1), edited().orderedSets().map { it.position })
            compose.onNodeWithContentDescription("Reps set 2").assertTextEquals("12")
            compose.onNodeWithContentDescription("Reps set 3").assertDoesNotExist()
            screenshot("workout-delete-and-modifiers")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            screenshot("workout-unsaved-changes")
            compose.onNodeWithTag("discard-workout-changes").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(original.orderedSets(), saved().orderedSets())
            assertNull(runBlocking { db.workoutDao().getDetails(editId) })

            open()
            expand()
            chooseModifier("Superset")
            waitForEdit { it.orderedSets().first().modifier == "superset" }
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNodeWithTag("save-workout-and-exit").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("superset", saved().orderedSets().first().modifier)
            assertEquals(3, saved().orderedSets().size)

            // Explicit Save commits deletion but keeps the user in the editor.
            open()
            expand()
            compose.onNodeWithContentDescription("Remove set 2 of Curl").performScrollTo().performClick()
            waitForEdit { it.orderedSets().size == 2 }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("plan-saved").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Edit workout").assertIsDisplayed()
            // The saved banner reduces the lazy list viewport; bring the bottom action back into composition.
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            assertEquals(listOf(0, 1), saved().orderedSets().map { it.position })
            assertEquals(listOf(10, 12), saved().orderedSets().map { it.reps })
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("discard-workout-changes").assertDoesNotExist()
        } finally { db.close() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "workout-flow-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
