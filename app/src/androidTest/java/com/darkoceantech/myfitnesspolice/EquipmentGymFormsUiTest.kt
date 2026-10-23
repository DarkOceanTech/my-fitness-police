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
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class EquipmentGymFormsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun directEquipmentEditingIsIndependentAndGymAddressesReload() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrument.targetContext, FitnessDatabase::class.java).build()
        val store = ViewModelStore()
        val model = EquipmentLibraryViewModel(FitnessRepository(db))
        store.put("library", model)
        val exercise = Exercise("row", "Row", "Cable", description = "Pull", primaryMuscles = "Back")
        runBlocking {
            db.exerciseDao().insert(exercise)
            db.equipmentLibraryDao().save(Gym("gym", "Gym"))
            db.equipmentLibraryDao().save(Equipment("e1", "Cable", "Station", gymId = "gym"))
            db.equipmentLibraryDao().save(Equipment("e2", "Dumbbell", "Portable"))
        }
        fun tab(i: Int) { compose.onNodeWithTag("exercise-tab-$i").performScrollTo().performClick() }
        fun fill(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performClick().performTextReplacement(value) }
        fun waitClosed() { compose.waitUntil(5000) { compose.onAllNodesWithTag("equipment-library").fetchSemanticsNodes().isEmpty() } }
        fun capture(name: String) {
            compose.waitForIdle(); instrument.waitForIdleSync(); android.os.SystemClock.sleep(400)
            val bitmap = instrument.uiAutomation.takeScreenshot()
            val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "equipment-gym-review").apply { mkdirs() }
            val orientation = if (bitmap.width > bitmap.height) "landscape" else "portrait"
            java.io.File(directory, "$name-$orientation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
                ExerciseFormDialog(ExerciseFormState(), {}, {}, exercise, onSave = { _,_,_,_,_ -> })
            } } }
            compose.onNodeWithContentDescription("Exercise name, required").assertExists()
            tab(1)
            compose.waitUntil(5000) { model.library.value.equipment.size == 2 }
            compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
            compose.onNodeWithTag("selector-gym").performClick()
            compose.onNodeWithTag("selector-gym-option-gym").performClick()
            compose.onNodeWithTag("exercise-equipment-option-e1").assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("done-equipment-selection").performClick()
            compose.onNodeWithTag("equipment-menu-e1").performScrollTo().performClick()
        compose.onNodeWithTag("edit-equipment-e1").performClick()
            compose.onNodeWithText("Edit equipment").assertExists()
            compose.onNodeWithTag("library-record").assertDoesNotExist()
            compose.onNodeWithContentDescription("Name, required").assertExists()
            fill("library-name", "Updated station")
            capture("equipment-keyboard")
            compose.onNodeWithTag("save-library-record").assertIsDisplayed()

            compose.onNodeWithTag("save-library-record").performClick()
            waitClosed()
            compose.onNodeWithTag("selected-equipment-card-e1").performScrollTo().assertExists()
            compose.onNodeWithTag("selected-equipment-card-e2").assertDoesNotExist()
            compose.onNodeWithTag("edit-shared-equipment").performScrollTo().performClick()
            compose.onNodeWithTag("equipment-library-title").assertTextEquals("Create equipment")
            compose.onNodeWithTag("library-name").assertTextContains("Name *")
            compose.onNodeWithTag("save-library-record").performClick()
            compose.waitUntil(5000) { model.action.value.error != null }
            fill("library-name", "New bench")
            fill("library-type", "Bench")

            compose.onNodeWithTag("save-library-record").performClick()
            waitClosed()
            assertEquals(3, runBlocking { db.equipmentLibraryDao().equipment().first().size })
            assertEquals("Updated station", runBlocking { db.equipmentLibraryDao().equipment("e1")!!.name })
            tab(3)
            compose.onNodeWithTag("gym-card-gym").performScrollTo().assertExists()
            compose.onNodeWithTag("gym-menu-gym").performScrollTo().performClick()
            compose.onNodeWithTag("edit-gym-gym").performClick()
            compose.onNodeWithTag("equipment-library-title").assertTextEquals("Edit gym")
            compose.onNodeWithTag("library-record").assertDoesNotExist()
            val address = listOf("12 Rue de la Paix", "Etage 2", "Paris", "", "75002", "France")
            address.forEachIndexed { i, value -> fill("gym-address-$i", value) }
            capture("gym-keyboard")
            compose.onNodeWithTag("save-library-record").assertIsDisplayed()

            compose.onNodeWithTag("save-library-record").performClick()
            compose.waitUntil(5000) { runBlocking { db.equipmentLibraryDao().gym("gym")!!.country == "France" } }
            waitClosed()
            compose.onNodeWithTag("gym-card-gym").performScrollTo().assertExists()
            compose.onNodeWithTag("gym-menu-gym").performScrollTo().performClick()
            compose.onNodeWithTag("edit-gym-gym").performClick()
            compose.onNodeWithTag("gym-address-4").performScrollTo().assertTextContains("75002")
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("12 Rue de la Paix", substring = true).performScrollTo().assertIsDisplayed()
            capture("gym-details")
            compose.onNodeWithTag("edit-shared-gym").performScrollTo().performClick()
            compose.onNodeWithTag("equipment-library-title").assertTextEquals("Create gym")
            compose.onNodeWithTag("library-name").assertTextEquals("Name *", "")
            compose.onNodeWithTag("gym-address-0").performScrollTo().assertTextEquals("Address line 1", "")
            fill("library-name", "New gym")

            compose.onNodeWithTag("save-library-record").performClick()
            waitClosed()
            compose.onNodeWithTag("gym-card-gym").performScrollTo().assertExists()
            assertEquals(2, runBlocking { db.equipmentLibraryDao().gyms().first().size })
            assertEquals(exercise, runBlocking { db.exerciseDao().get("row")!! })
        } finally { store.clear(); db.close() }
    }
}