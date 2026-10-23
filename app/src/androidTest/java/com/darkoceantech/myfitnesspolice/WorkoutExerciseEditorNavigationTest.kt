package com.darkoceantech.myfitnesspolice

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class WorkoutExerciseEditorNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun menusReuseEditorAndKeepWorkoutDraft() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val store = ViewModelStore()
        val override = EquipmentConfiguration(positions = listOf(EquipmentPosition("Seat", "7")))
        runBlocking {
            db.exerciseDao().insert(Exercise("curl", "Curl", "Dumbbell", description = "Curl steadily", primaryMuscles = "Biceps"))
            db.workoutDao().insert(Workout(id = "plan", name = "Arms", kind = "plan"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "entry", workoutId = "plan", exerciseId = "curl", position = 0, equipmentOverride = override))
            db.workoutSetDao().insert(WorkoutSet(id = "set", workoutExerciseId = "entry", position = 0, reps = 10, weightGrams = 4500))
        }
        val repo = FitnessRepository(db)
        val session = SessionViewModel(repo)
        val exercises = ExercisesViewModel(repo)
        val library = EquipmentLibraryViewModel(repo)
        store.put("session", session); store.put("exercises", exercises); store.put("library", library)
        fun draft() = runBlocking { db.workoutDao().getDetails(WorkoutEditorRepository.editId("plan"))!! }
        fun open(name: String, menu: String) {
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasContentDescription("Exercise options for $name"))
            compose.onNodeWithContentDescription("Exercise options for $name").performClick()
            compose.onNodeWithText(menu).performClick()
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides library) {
                WorkoutBuilderScreen(session, exercises, planId = "plan")
            } } }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Expand Curl").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Expand Curl").performScrollTo().performClick()
            val entry = draft().orderedExercises().single()
            compose.runOnIdle { session.addDefaultSet(draft().workout.id, entry.workoutExercise.id) }
            compose.waitUntil(5000) { draft().orderedExercises().single().sets.size == 2 }
            val before = draft()
            open("Curl", "View exercise info")
            compose.onNodeWithTag("exercise-tab-0").assertIsSelected()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Edited curl")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            (1..3).forEach { compose.onNodeWithTag("exercise-tab-$it").performScrollTo().performClick() }
            compose.onNodeWithTag("exercise-tab-0").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-name").performScrollTo().assertTextContains("Edited curl")
            compose.onNodeWithTag("save-exercise").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            assertEquals(before.orderedExercises().single().sets, draft().orderedExercises().single().sets)
            assertEquals(before.orderedExercises().single().workoutExercise, draft().orderedExercises().single().workoutExercise)
            compose.onNodeWithContentDescription("Collapse Edited curl").assertExists()
            open("Edited curl", "Edit equipment setup")
            compose.onNodeWithTag("exercise-tab-1").assertIsSelected()
            compose.onNodeWithTag("exercise-tab-0").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Discard me")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("continue-exercise-editing").performClick()
            compose.onNodeWithTag("cancel-exercise").performClick()
            compose.onNodeWithTag("discard-exercise-changes").performClick()
            assertEquals("Edited curl", runBlocking { db.exerciseDao().get("curl")!!.name })
            assertEquals(override, draft().orderedExercises().single().workoutExercise.equipmentOverride)
            open("Edited curl", "Edit equipment setup")
            compose.onNodeWithContentDescription("Close exercise form").performClick()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").assertIsEnabled()
            assertEquals(1, runBlocking { db.workoutDao().getDetails("plan")!!.orderedExercises().single().sets.size })
        } finally { store.clear(); db.close() }
    }
}