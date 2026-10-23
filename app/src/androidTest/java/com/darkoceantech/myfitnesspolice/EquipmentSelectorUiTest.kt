package com.darkoceantech.myfitnesspolice

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import com.darkoceantech.myfitnesspolice.ui.theme.policeBackdrop
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class EquipmentSelectorUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: FitnessDatabase
    private lateinit var model: EquipmentLibraryViewModel
    private val store = ViewModelStore()
    private val a = EquipmentIdentity("a", "Cable", "Long cable station name", gymId = "g", gymName = "North gym")
    private val b = EquipmentIdentity("b", "Bench", "Bench", gymId = "g", gymName = "North gym")
    private val other = EquipmentIdentity("other", "Barbell", "Other gym barbell", gymId = "other-gym")
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        runBlocking {
            listOf(Gym("g", "North gym"), Gym("other-gym", "South gym"), Gym("empty", "Empty gym")).forEach { db.equipmentLibraryDao().save(it) }
            listOf(a,b,other).forEach { db.equipmentLibraryDao().save(Equipment(it.id,it.type,it.name,gymId=it.gymId)) }
        }
        model = EquipmentLibraryViewModel(FitnessRepository(db))
        store.put("model", model)
    }
    @After fun close() { store.clear(); db.close() }
    private fun open() = compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
    private fun option(id: String) = compose.onNodeWithTag("exercise-equipment-option-" + id)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = i.uiAutomation.takeScreenshot()
        val folder = java.io.File(i.targetContext.getExternalFilesDir(null), "equipment-selector-review").apply { mkdirs() }
        val orientation = if (bitmap.width > bitmap.height) "landscape" else "portrait"
        java.io.File(folder, name + "-" + orientation + ".png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun selectionIsLocalAndHiddenGymsArePreserved() {
        val setup = EquipmentConfiguration(equipment=listOf(a),setupName="Seat setup",positions=listOf(EquipmentPosition("Seat","4")))
        var config by mutableStateOf(EquipmentConfiguration(compatibleEquipment=listOf(a,other),
            equipmentSetups=listOf(setup),gymId="g",gymName="North gym"))
        val initial = config
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            Column(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ExerciseLibraryTab(1,config,true,"",{}, {}, {}, { config=it })
            }
        } } }
        compose.waitUntil(5000) { model.library.value.equipment.size == 3 }
        compose.onNodeWithTag("selected-equipment-card-a").assertExists()
        compose.onNodeWithTag("selected-equipment-card-other").assertExists()
        compose.onNodeWithTag("selected-equipment-card-b").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        screenshot("cards")
        open()
        option("a").assertIsOn()
        option("b").assertIsOff()
        option("other").assertDoesNotExist()
        option("b").performClick()
        assertEquals(initial,config)
        compose.onNodeWithTag("cancel-equipment-selection").assertIsDisplayed().performClick()
        assertEquals(initial,config)
        open()
        option("b").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Back to Equipment").performClick()
        assertEquals(initial,config)
        open()
        option("b").assertIsOff().performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("equipment-selector-screen").assertDoesNotExist()
        assertEquals(initial,config)
        open()
        option("b").assertIsOff().performClick()
        screenshot("selector")
        compose.onNodeWithTag("done-equipment-selection").assertIsDisplayed().performClick()
        assertEquals(setOf("a","b","other"),config.compatibleEquipment.map { it.id }.toSet())
        assertEquals(listOf(setup), config.equipmentSetups)
        open()
        option("a").performClick()
        compose.onNodeWithTag("done-equipment-selection").performClick()
        assertEquals(setOf("b","other"),config.compatibleEquipment.map { it.id }.toSet())
        assertEquals(3,model.library.value.equipment.size)
    }
    @Test fun noGymAndEmptyGymAreExplicit() {
        var config by mutableStateOf(EquipmentConfiguration())
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            Column(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ExerciseLibraryTab(1,config,true,"",{}, {}, {}, { config=it })
            }
        } } }
        compose.waitUntil(5000) { model.library.value.gyms.size == 3 }
        compose.onNodeWithText("No equipment selected.").assertExists()
        open()
        compose.onNodeWithText("Select a gym to view its equipment.").assertExists()
        option("a").assertDoesNotExist()
        compose.onNodeWithTag("done-equipment-selection").assertIsNotEnabled()
        compose.onNodeWithTag("selector-gym").performClick()
        compose.onNodeWithTag("selector-gym-option-empty").performClick()
        compose.onNodeWithText("No equipment assigned to this gym.").assertExists()
        compose.onNodeWithTag("selector-gym").performClick()
        compose.onNodeWithTag("selector-gym-option-g").performClick()
        option("a").performClick()
        compose.onNodeWithTag("done-equipment-selection").performClick()
        assertEquals("g",config.gymId)
        assertEquals(listOf("a"),config.compatibleEquipment.map { it.id })
    }
}