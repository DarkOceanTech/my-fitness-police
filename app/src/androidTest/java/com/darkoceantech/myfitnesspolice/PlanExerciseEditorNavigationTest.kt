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

class PlanExerciseEditorNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nestedEditorPreservesUnsavedSetsNotesAndOverride() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val store = ViewModelStore()
        val exercise = Exercise("curl", "Curl", "Dumbbell", description = "Curl steadily", primaryMuscles = "Biceps")
        runBlocking { db.exerciseDao().insert(exercise) }
        val repo = FitnessRepository(db)
        val model = EquipmentLibraryViewModel(repo)
        store.put("library", model)
        val override = EquipmentConfiguration(positions = listOf(EquipmentPosition("Seat", "7")))
        val item = TrainingPlanItem.ExerciseItem("curl", equipmentOverride = override)
        var result: List<TrainingPlanSet>? = null
        var resultNote: String? = null
        var resultConfig: EquipmentConfiguration? = null
        var dismissed = false
        fun menu(name: String, option: String) {
            compose.onNodeWithContentDescription("Exercise options for $name").performScrollTo().performClick()
            compose.onNodeWithText(option).performClick()
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
                val catalog by remember { repo.observeExercises() }.collectAsState(initial = listOf(exercise))
                TrainingExerciseSetsDialog(item, "My plan", catalog.single(), false, null,
                    onDismiss = { dismissed = true }, onSave = {},
                    onSaveDetails = { sets, config, note -> result = sets; resultConfig = config; resultNote = note })
            } } }
            compose.onNodeWithTag("add-training-set").performScrollTo().performClick()
            compose.onNodeWithTag("training-exercise-note").performScrollTo().performClick()
            compose.onNodeWithTag("training-exercise-note-editor-input").performTextReplacement("Keep this plan note")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("training-exercise-note-editor-save").performClick()
            menu("Curl", "View exercise info")
            compose.onNodeWithTag("exercise-tab-0").assertIsSelected()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Updated curl")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            (1..3).forEach { compose.onNodeWithTag("exercise-tab-$it").performScrollTo().performClick() }
            compose.onNodeWithTag("save-exercise").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            menu("Updated curl", "Edit equipment setup")
            compose.onNodeWithTag("exercise-tab-1").assertIsSelected()
            compose.onNodeWithTag("exercise-tab-0").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Discarded")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("continue-exercise-editing").performClick()
            compose.onNodeWithTag("cancel-exercise").performClick()
            compose.onNodeWithTag("discard-exercise-changes").performClick()
            menu("Updated curl", "Edit equipment setup")
            compose.onNodeWithContentDescription("Close exercise form").performClick()
            compose.onNodeWithTag("training-exercise-note").performScrollTo().assertTextEquals("Keep this plan note")
            assertFalse(dismissed)
            assertNull(result)
            compose.onNodeWithTag("save-training-exercise-sets").performClick()
            assertEquals(listOf(TrainingPlanSet(), TrainingPlanSet()), result)
            assertEquals("Keep this plan note", resultNote)
            assertEquals(override, resultConfig)
            assertEquals("Updated curl", runBlocking { db.exerciseDao().get("curl")!!.name })
        } finally { store.clear(); db.close() }
    }
}