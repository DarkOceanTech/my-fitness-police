package com.darkoceantech.myfitnesspolice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class EquipmentLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun orientation() {
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.setRotation(if (InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape")
            android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
    }
    @After fun restoreRotation() { InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }
    @Test fun createGymMachineNamedSetupThenReloadAndCancelChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "equipment-ui-" + UUID.randomUUID() + ".db"
        val db = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        val repo = FitnessRepository(db)
        val model = EquipmentLibraryViewModel(repo)
        val w = Workout(kind = "plan", name = "Back")
        val exercise = Exercise(name = "Cable row", equipment = "Cable; handles")
        val entry = WorkoutExercise(workoutId = w.id, exerciseId = exercise.id, position = 0)
        runBlocking { db.exerciseDao().insert(exercise); db.workoutDao().insert(w); db.workoutExerciseDao().insert(entry) }
        try {
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
                var open by remember { mutableStateOf(true) }
                Button(onClick = { model.reset(); open = true }, modifier = Modifier.testTag("open-equipment")) { Text("Equipment setup") }
                if (open) {
                    val current = runBlocking { db.workoutExerciseDao().getDetails(entry.id)!! }
                    EquipmentPositionEditor(current, SessionAction(), onDismiss = { open = false }, onEdit = {}, onSave = {})
                }
            } } }
            compose.onNodeWithTag("manage-equipment-library").performScrollTo().performClick()
            compose.onNodeWithTag("library-name").performTextInput("Test gym")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-library-record").performClick()
            compose.waitUntil(10000) { runBlocking { repo.equipmentLibrary.observe().first().gyms.size == 1 } }
            compose.onNodeWithTag("library-category-1").performClick()
            compose.onNodeWithTag("library-name").performTextInput("Row station")
            compose.onNodeWithTag("library-type").performScrollTo().performTextInput("Cable machine")
            compose.onNodeWithTag("library-model").performScrollTo().performTextInput("Model A")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("library-gym").performScrollTo().performClick()
            compose.onNodeWithText("Test gym").performClick()
            compose.onNodeWithTag("save-library-record").performClick()
            compose.waitUntil(10000) { runBlocking { repo.equipmentLibrary.observe().first().equipment.size == 1 } }
            compose.onNodeWithText("Close").performClick()
            val machine = runBlocking { repo.equipmentLibrary.observe().first().equipment.single() }
            compose.onNodeWithTag("equipment-selector").performScrollTo().performClick()
            compose.onNodeWithTag("equipment-selector-option-" + machine.id).performClick()
            compose.onNodeWithTag("add-equipment-position").performScrollTo().performClick()
            compose.onNodeWithTag("position-name-0").performScrollTo().performTextInput("Seat")
            compose.onNodeWithTag("position-value-0").performScrollTo().performTextInput("4")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("new-setup-name").performScrollTo().performTextInput("My row setup")
            Espresso.closeSoftKeyboard()
            screenshot("equipment-library-setup-" + (InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"))
            compose.onNodeWithTag("save-equipment-positions").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("equipment-position-editor").fetchSemanticsNodes().isEmpty() }
            val saved = runBlocking { db.workoutExerciseDao().getDetails(entry.id)!!.workoutExercise.equipmentOverride!! }
            assertEquals("My row setup", saved.setupName); assertEquals("Test gym", saved.equipment.single().gymName)
            assertEquals(listOf(EquipmentPosition("Seat", "4")), saved.positions)
            compose.onNodeWithTag("open-equipment").performClick()
            compose.onNodeWithTag("position-value-0").performScrollTo().assertTextContains("4").performTextReplacement("9")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(saved, runBlocking { db.workoutExerciseDao().getDetails(entry.id)!!.workoutExercise.equipmentOverride })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private fun screenshot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val bitmap = i.uiAutomation.takeScreenshot()
        val file = java.io.File(i.targetContext.getExternalFilesDir(null), name + ".png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
