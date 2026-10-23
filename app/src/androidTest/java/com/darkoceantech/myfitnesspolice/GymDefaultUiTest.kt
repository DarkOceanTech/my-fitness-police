package com.darkoceantech.myfitnesspolice

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class GymDefaultUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FitnessDatabase
    private lateinit var settings: AppSettingsRepository
    private lateinit var model: EquipmentLibraryViewModel
    private val store = ViewModelStore()
    private val preferenceName = "gym-test-" + java.util.UUID.randomUUID()
    private val north = Gym("north", "North gym", addressLine1="12 Rue de la Paix", addressLine2="Etage 2",
        city="Paris", postalCode="75002", country="France")
    private val south = Gym("south", "South gym", country="日本")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context,FitnessDatabase::class.java).build()
        settings = AppSettingsRepository(context,preferenceName)
        runBlocking {
            db.equipmentLibraryDao().save(north)
            db.equipmentLibraryDao().save(south)
            db.equipmentLibraryDao().save(Equipment("a","Cable","Cable station", "Model A","north"))
            db.equipmentLibraryDao().save(Equipment("b","Bench","South bench",gymId="south"))
            db.equipmentLibraryDao().save(Gym("empty","Empty gym"))
        }
        model=EquipmentLibraryViewModel(FitnessRepository(db),settings)
        store.put("model",model)
    }
    @After fun close() {
        store.clear(); settings.close(); db.close()
        context.getSharedPreferences(preferenceName,0).edit().clear().commit()
    }
    private fun defaultAction(id:String) {
        compose.onNodeWithTag("gym-menu-"+id).performScrollTo().performClick()
        compose.onNodeWithTag("gym-default-action-"+id).performClick()
        compose.waitUntil(5000) { !model.action.value.saving }
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation()
        i.waitForIdleSync()
        Thread.sleep(300)
        val bitmap=i.uiAutomation.takeScreenshot()
        val folder=java.io.File(context.getExternalFilesDir(null),"gym-default-review").apply { mkdirs() }
        val suffix=if(bitmap.width>bitmap.height) "landscape" else "portrait"
        java.io.File(folder,name+"-"+suffix+".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun optionalDefaultAndEquipmentViewPreserveExerciseDraft() {
        val identity=EquipmentIdentity("a","Cable","Cable station",gymId="north")
        val setup=EquipmentConfiguration(equipment=listOf(identity),setupName="Seat 4",positions=listOf(EquipmentPosition("Seat","4")))
        val original=EquipmentConfiguration(compatibleEquipment=listOf(identity),equipmentSetups=listOf(setup))
        var config by mutableStateOf(original)
        var tab by mutableIntStateOf(3)
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            Column(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                ExerciseLibraryTab(tab,config,true,"",{}, {}, { tab=3 }, { config=it })
            }
        } } }
        compose.waitUntil(5000) { model.library.value.gyms.size==3 }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        assertNull(settings.defaultGymId.value)
        defaultAction("north")
        compose.waitUntil(5000) { settings.defaultGymId.value=="north" }
        compose.onNodeWithTag("default-gym-north").assertExists()
        compose.onNodeWithTag("default-gym-south").assertDoesNotExist()
        AppSettingsRepository(context,preferenceName).use { assertEquals("north",it.defaultGymId.value) }
        compose.onNodeWithText("12 Rue de la Paix; Etage 2").assertExists()
        compose.onNodeWithText("Paris, 75002, France").assertExists()
        capture("gyms")
        compose.onNodeWithTag("gym-menu-north").performScrollTo().performClick()
        compose.onNodeWithTag("view-gym-equipment-north").performClick()
        compose.onNodeWithTag("gym-equipment-a").assertExists()
        compose.onNodeWithTag("gym-equipment-b").assertDoesNotExist()
        compose.onNodeWithText("Model A").assertExists()
        capture("equipment")
        compose.onNodeWithContentDescription("Back to Gym").performClick()
        assertEquals(original,config)
        assertEquals("north",settings.defaultGymId.value)
        compose.onNodeWithTag("gym-menu-empty").performScrollTo().performClick()
        compose.onNodeWithTag("view-gym-equipment-empty").performClick()
        compose.onNodeWithText("No equipment assigned to this gym.").assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("gym-equipment-screen").assertDoesNotExist()
        defaultAction("south")
        compose.waitUntil(5000) { settings.defaultGymId.value=="south" }
        compose.onNodeWithTag("default-gym-north").assertDoesNotExist()
        compose.onNodeWithTag("default-gym-south").assertExists()
        assertEquals(original,config)
        compose.runOnIdle { tab=1 }
        compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-equipment-option-b").assertExists()
        compose.onNodeWithTag("exercise-equipment-option-a").assertDoesNotExist()
        compose.onNodeWithTag("cancel-equipment-selection").performClick()
        compose.runOnIdle { config=original.copy(gymId="north",gymName="North gym") }
        compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-equipment-option-a").assertExists()
        compose.onNodeWithTag("exercise-equipment-option-b").assertDoesNotExist()
        compose.onNodeWithTag("cancel-equipment-selection").performClick()
        compose.runOnIdle { config=original; tab=3 }
        defaultAction("south")
        compose.waitUntil(5000) { settings.defaultGymId.value==null }
        defaultAction("north")
        compose.waitUntil(5000) { settings.defaultGymId.value=="north" }
        runBlocking { db.equipmentLibraryDao().save(north.copy(isArchived=true)) }
        compose.waitUntil(5000) { settings.defaultGymId.value==null }
        compose.runOnIdle { tab=1 }
        compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
        compose.onNodeWithText("Select a gym to view its equipment.").assertExists()
        assertEquals(original,config)
    }

    @Test fun addressFormattingOmitsEmptyParts() {
        assertEquals(listOf("12 Rue de la Paix; Etage 2","Paris, 75002, France"), north.compactAddressLines())
        assertEquals(listOf("日本"),south.compactAddressLines())
        assertEquals(emptyList<String>(),Gym(name="Empty").compactAddressLines())
        assertEquals(listOf("Suite 3","BC, V6B 1A1, Canada"),
            Gym(name="Partial",addressLine2="Suite 3",region="BC",postalCode="V6B 1A1",country="Canada").compactAddressLines())
    }
}