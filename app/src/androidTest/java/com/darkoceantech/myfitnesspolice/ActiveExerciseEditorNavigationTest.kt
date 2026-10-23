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

class ActiveExerciseEditorNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sharedEditorKeepsRunningAndPausedSessionIntact() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val store = ViewModelStore()
        val repo = FitnessRepository(db)
        val override = EquipmentConfiguration(positions = listOf(EquipmentPosition("Seat", "7")))
        runBlocking {
            db.exerciseDao().insert(Exercise("curl", "Curl", "Dumbbell", description = "Curl steadily", primaryMuscles = "Biceps"))
            db.workoutDao().insert(Workout(id = "live", name = "Arms", kind = "session", startedAt = System.currentTimeMillis()))
            db.workoutExerciseDao().insert(WorkoutExercise("entry", "live", "curl", 0, equipmentOverride = override, equipmentSnapshot = override))
            db.workoutSetDao().insert(WorkoutSet("set", "entry", 0, 10, 4500))
            repo.sessionProgress.prepareSession("live")
            repo.sessionProgress.startSet("live", "set")
        }
        val model = SessionViewModel(repo)
        val library = EquipmentLibraryViewModel(repo)
        store.put("session", model); store.put("library", library)
        fun details() = runBlocking { db.workoutDao().getDetails("live")!! }
        fun timing() = runBlocking { db.sessionStateDao().get("live")!! }
        fun menu(option: String) {
            compose.onNodeWithTag("session-exercise-options-entry").performScrollTo().performClick()
            compose.onNodeWithText(option).performClick()
        }
        try {
            val before = details()
            val start = timing()
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides library) {
                ActiveWorkoutScreen(model, onBack = {}, onFinished = {})
            } } }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("session-exercise-options-entry").fetchSemanticsNodes().isNotEmpty() }
            menu("View exercise info")
            compose.onNodeWithTag("exercise-tab-0").assertIsSelected()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Updated curl")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            (1..3).forEach { compose.onNodeWithTag("exercise-tab-$it").performScrollTo().performClick() }
            compose.onNodeWithTag("save-exercise").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            assertEquals(start, timing())
            assertTrue(timing().phaseMillis(System.currentTimeMillis()) > start.phaseMillis(start.phaseStartedAt!!))
            assertEquals(before.exercises.single().sets, details().exercises.single().sets)
            assertEquals(before.exercises.single().workoutExercise, details().exercises.single().workoutExercise)
            runBlocking { repo.sessionProgress.pause("live") }
            val paused = timing()
            menu("Edit equipment setup")
            compose.onNodeWithTag("exercise-tab-1").assertIsSelected()
            compose.onNodeWithTag("exercise-tab-0").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Discarded")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("continue-exercise-editing").performClick()
            compose.onNodeWithTag("cancel-exercise").performClick()
            compose.onNodeWithTag("discard-exercise-changes").performClick()
            menu("Edit equipment setup")
            compose.onNodeWithContentDescription("Close exercise form").performClick()
            assertEquals(paused, timing())
            assertEquals(before.exercises.single().workoutExercise, details().exercises.single().workoutExercise)
            assertEquals(before.exercises.single().sets, details().exercises.single().sets)
            assertEquals("Updated curl", runBlocking { db.exerciseDao().get("curl")!!.name })
        } finally { store.clear(); db.close() }
    }
}