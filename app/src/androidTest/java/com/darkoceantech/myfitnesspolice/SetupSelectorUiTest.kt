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

class SetupSelectorUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun draftSelectionCancelBackAndDonePreserveOtherEquipment() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val db=Room.inMemoryDatabaseBuilder(instrument.targetContext,FitnessDatabase::class.java).build()
        val store=ViewModelStore()
        val model=EquipmentLibraryViewModel(FitnessRepository(db))
        store.put("model",model)
        val a=EquipmentIdentity("a","Cable","Cable station")
        val b=EquipmentIdentity("b","Bench","Bench")
        fun setup(id:String,e:EquipmentIdentity)=EquipmentConfiguration(equipment=listOf(e),setupId=id,setupName="Setup "+id,
            positions=listOf(EquipmentPosition("Position",id)))
        val first=setup("1",a); val second=setup("2",a); val other=setup("3",b)
        runBlocking {
            listOf(a,b).forEach { db.equipmentLibraryDao().save(Equipment(it.id,it.type,it.name)) }
            listOf(first,second,other).forEach { db.equipmentLibraryDao().save(NamedEquipmentSetup(it.setupId!!,it.setupName,it.equipment.single().type,it)) }
        }
        val original=EquipmentConfiguration(compatibleEquipment=listOf(a,b),equipmentSetups=listOf(first,other))
        var config by mutableStateOf(original)
        fun open() { compose.onNodeWithTag("select-setup").performScrollTo().performClick() }
        try {
            compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
                Column(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    ExerciseLibraryTab(2,config,true,"a",{}, {}, {}, { config=it })
                }
            } } }
            compose.waitUntil(5000) { model.library.value.setups.size==3 }
            compose.onNodeWithTag("assigned-setup-a").assertExists()
            compose.onNodeWithText("Setup 2").assertDoesNotExist()
            compose.onNodeWithText("Setup 3").assertDoesNotExist()
            compose.onAllNodes(isToggleable()).assertCountEquals(0)
            open()
            compose.onNodeWithTag("setup-choice-1").assertIsSelected()
            compose.onNodeWithTag("setup-choice-3").assertDoesNotExist()
            compose.onNodeWithTag("setup-choice-2").performClick()
            assertEquals(original,config)
            compose.onNodeWithTag("cancel-setup-selection").assertIsDisplayed().performClick()
            assertEquals(original,config)
            open()
            compose.onNodeWithTag("setup-choice-1").assertIsSelected()
            compose.onNodeWithTag("setup-choice-2").performClick()
            compose.onNodeWithContentDescription("Back to Setups").performClick()
            assertEquals(original,config)
            open()
            compose.onNodeWithTag("setup-choice-2").performClick()
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithTag("setup-selector-screen").assertDoesNotExist()
            assertEquals(original,config)
            open()
            compose.onNodeWithTag("setup-choice-2").performClick()
            compose.onNodeWithTag("done-setup-selection").assertIsDisplayed().performClick()
            assertEquals(second,config.equipmentSetups.first { it.equipment.single().id=="a" })
            assertEquals(other,config.equipmentSetups.first { it.equipment.single().id=="b" })
            assertEquals(3,model.library.value.setups.size)
            open()
            compose.onNodeWithTag("setup-choice-2").assertIsSelected()
            compose.onNodeWithTag("setup-choice-none").performClick()
            compose.onNodeWithTag("done-setup-selection").performClick()
            val custom=config.equipmentSetups.first { it.equipment.single().id=="a" }
            assertNull(custom.setupId)
            assertEquals(second.positions,custom.positions)
        } finally { store.clear(); db.close() }
    }
}