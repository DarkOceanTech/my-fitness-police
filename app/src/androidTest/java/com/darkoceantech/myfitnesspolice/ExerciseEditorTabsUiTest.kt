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

class ExerciseEditorTabsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FitnessDatabase
    private lateinit var model: EquipmentLibraryViewModel
    private val store = ViewModelStore()
    private val exercise = Exercise("row", "Cable row", "Cable or bench", description = "Row steadily", primaryMuscles = "Latissimus dorsi")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        runBlocking {
            db.exerciseDao().insert(exercise)
            db.equipmentLibraryDao().save(Gym("gym", "North gym"))
            (1..3).forEach { db.equipmentLibraryDao().save(Equipment("e$it", "Cable", "Station $it", gymId = "gym")) }
        }
        model = EquipmentLibraryViewModel(FitnessRepository(db))
        store.put("equipment", model)
    }
    @After fun close() { store.clear(); db.close() }
    private fun tab(index: Int) { compose.onNodeWithTag("exercise-tab-$index").performScrollTo().performClick() }
    private fun choose(tag: String, id: String) {
        if (tag == "exercise-equipment-options") {
            compose.onNodeWithTag("select-equipment").performScrollTo().performClick()
            compose.onNodeWithTag("selector-gym").performClick()
            compose.onNodeWithTag("selector-gym-option-gym").performClick()
            compose.onNodeWithTag("exercise-equipment-option-$id").performClick()
            compose.onNodeWithTag("done-equipment-selection").performClick()
            return
        }
        if (tag == "exercise-named-setup") {
            compose.onNodeWithTag("select-setup").performScrollTo().performClick()
            compose.onNodeWithTag(if (id.isEmpty()) "setup-choice-none" else "setup-choice-$id").performClick()
            compose.onNodeWithTag("done-setup-selection").performClick()
            return
        }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()
    }

    @Test fun tabsKeepDraftsIndependentSetupsSaveAndDiscard() {
        var visible by mutableStateOf(true)
        var editing by mutableStateOf(exercise)
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            if (visible) ExerciseFormDialog(ExerciseFormState(), { visible = false }, {}, editing, onSave = { _, _, _, _, _ -> })
        } } }
        compose.onNodeWithTag("save-exercise").assertIsNotEnabled()
        compose.onNodeWithTag("exercise-name").performTextReplacement("My row")

        tab(1)
        compose.waitUntil(5000) { model.library.value.equipment.size == 3 }
        (1..3).forEach { choose("exercise-equipment-options", "e$it") }
        tab(2)
        (1..3).forEach { i ->
            choose("exercise-setup-equipment", "e$i")
            compose.onNodeWithTag("add-equipment-position").performScrollTo().performClick()
            compose.onNodeWithTag("position-name-0").performScrollTo().performTextReplacement("Seat")
            compose.onNodeWithTag("position-value-0").performScrollTo().performTextReplacement("$i")
    
        }
        choose("exercise-setup-equipment", "e1")
        compose.onNodeWithTag("position-value-0").performScrollTo().assertTextEquals("Position *", "1")
        screenshot("setups")
        tab(3)
        compose.onNodeWithText("North gym", substring = true).assertExists()
        tab(0)
        compose.onNodeWithTag("exercise-name").performScrollTo().assertTextEquals("Exercise name *", "My row")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("continue-exercise-editing").performClick()
        compose.onNodeWithTag("save-exercise").performClick()
        compose.waitUntil(5000) { !visible }
        val saved = runBlocking { db.exerciseDao().get(exercise.id)!! }
        assertEquals(3, saved.equipmentDefault!!.compatibleEquipment.size)
        assertEquals(listOf("1", "2", "3"), saved.equipmentDefault!!.equipmentSetups.map { it.positions.single().position })
        compose.runOnIdle { editing = saved; visible = true }
        compose.onNodeWithTag("save-exercise").assertIsNotEnabled()
        tab(2)
        choose("exercise-setup-equipment", "e2")
        compose.onNodeWithTag("position-value-0").performScrollTo().assertTextEquals("Position *", "2")
        compose.onNodeWithTag("position-value-0").performTextReplacement("99")

        compose.onNodeWithContentDescription("Close exercise form").performClick()
        compose.onNodeWithTag("discard-exercise-changes").performClick()
        assertEquals(saved, runBlocking { db.exerciseDao().get(exercise.id) })
    }

    @Test fun catalogLastSessionIsViewOnlyAndSupportsProgress() {
        runBlocking {
            db.workoutDao().insert(Workout("history", startedAt = 1000, finishedAt = 2000, kind = "session", name = "Back"))
            db.workoutExerciseDao().insert(WorkoutExercise("entry", "history", exercise.id, 0))
            db.workoutSetDao().insert(WorkoutSet("set", "entry", 0, 10, 10000, completedAt = 1500, actualReps = 10))
        }
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            ExercisesScreen(ListState.Ready(listOf(exercise)), ExerciseFormState(), {}, { _,_,_,_,_ -> },
                {}, onUpdate = { _,_,_,_,_,_ -> })
        } } }
        compose.onNodeWithTag("exercise-options-row").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-last-session-row").performClick()
        compose.waitUntil(5000) { model.catalogHistory.value.snapshot != null }
        compose.onNodeWithTag("last-session-dialog").assertIsDisplayed()
        compose.onNodeWithText("Import session").assertDoesNotExist()
        compose.onNodeWithTag("view-exercise-progress").assertIsEnabled().performClick()
        compose.onNodeWithTag("exercise-progress-dialog").assertIsDisplayed()
    }

    @Test fun sharedRecordsSaveIndependentlyAndCanBeEditedWithoutDuplicates() {
        var visible by mutableStateOf(true)
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            if (visible) ExerciseFormDialog(ExerciseFormState(), { visible = false }, {}, exercise, onSave = { _, _, _, _, _ -> })
        } } }
        tab(3)
        compose.onNodeWithTag("gym-menu-gym").performScrollTo().performClick()
            compose.onNodeWithTag("edit-gym-gym").performClick()
        compose.onNodeWithTag("library-name").performScrollTo().performTextReplacement("Updated gym")

        compose.onNodeWithTag("save-library-record").performClick()
        compose.waitUntil(5000) { runBlocking { db.equipmentLibraryDao().gym("gym")?.name == "Updated gym" } }
        tab(1)
        choose("exercise-equipment-options", "e1")
        compose.onNodeWithTag("equipment-menu-e1").performScrollTo().performClick()
        compose.onNodeWithTag("edit-equipment-e1").performClick()
        compose.onNodeWithTag("library-name").performScrollTo().performTextReplacement("Updated station")
        compose.onNodeWithTag("library-model").performScrollTo().performTextReplacement("Model X")

        compose.onNodeWithTag("save-library-record").performClick()
        compose.waitUntil(5000) { runBlocking { db.equipmentLibraryDao().equipment("e1")?.makeModel == "Model X" } }
        choose("exercise-equipment-options", "e2")
        tab(2)
        choose("exercise-setup-equipment", "e1")
        compose.onNodeWithTag("edit-shared-setup").performScrollTo().performClick()
        compose.onNodeWithTag("library-name").performScrollTo().performTextReplacement("Row setup")

        choose("equipment-selector", "e1")
        compose.onNode(hasTestTag("add-equipment-position") and hasAnyAncestor(hasTestTag("equipment-library"))).performScrollTo().performClick()
        compose.onNode(hasTestTag("position-name-0") and hasAnyAncestor(hasTestTag("equipment-library"))).performScrollTo().performTextReplacement("Seat")
        compose.onNode(hasTestTag("position-value-0") and hasAnyAncestor(hasTestTag("equipment-library"))).performScrollTo().performTextReplacement("4")

        compose.onNodeWithTag("save-library-record").performClick()
        compose.waitUntil(5000) { model.library.value.setups.size == 1 }
        val setupId = model.library.value.setups.single().id
        compose.onNodeWithTag("assigned-setup-e1").assertDoesNotExist()
        choose("exercise-named-setup", setupId)
        compose.onNodeWithTag("setup-menu-$setupId").performScrollTo().performClick()
        compose.onNodeWithTag("assigned-setup-e1").assertExists()
        compose.onNodeWithTag("edit-setup-$setupId").performClick()
        compose.onNodeWithTag("library-record").assertDoesNotExist()
        compose.onNode(hasTestTag("position-value-0") and hasAnyAncestor(hasTestTag("equipment-library"))).performScrollTo().performTextReplacement("5")

        compose.onNodeWithTag("save-library-record").performClick()
        compose.waitUntil(5000) { model.library.value.setups.single().configuration.positions.single().position == "5" }
        compose.onNodeWithTag("assigned-setup-e1").assertExists()
        choose("exercise-named-setup", "")
        choose("exercise-named-setup", setupId)
        compose.onNodeWithTag("assigned-setup-e1").assertExists()
        compose.onNodeWithTag("edit-shared-setup").performScrollTo().performClick()
        compose.onNodeWithTag("equipment-library-title").assertTextEquals("Create setup")
        compose.onNodeWithTag("library-name").assertTextEquals("Name *", "")
        compose.onNodeWithTag("library-record").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("assigned-setup-e1").assertExists()
        tab(0)
        compose.onNodeWithTag("exercise-selected-equipment").performScrollTo()
            .assertTextContains("Station 1 · Station 2")
        tab(1)
        compose.onNodeWithTag("equipment-setup-link-e1").performScrollTo()
            .assertTextEquals("Setup: Row setup").performClick()
        compose.onNodeWithTag("exercise-tab-2").assertIsSelected()
        compose.onNodeWithTag("exercise-setup-equipment").performScrollTo().assertTextContains("Station 1")
        compose.onNodeWithTag("assigned-setup-e1").assertExists()
        screenshot("setup-list")
        compose.onNodeWithTag("position-value-0").performScrollTo().assertTextEquals("Position *", "5")
        compose.onNodeWithTag("cancel-exercise").performClick()
        compose.onNodeWithTag("discard-exercise-changes").performClick()
        assertEquals(exercise, runBlocking { db.exerciseDao().get(exercise.id) })
        assertEquals("Updated gym", runBlocking { db.equipmentLibraryDao().gym("gym")!!.name })
        assertEquals("Updated station", runBlocking { db.equipmentLibraryDao().equipment("e1")!!.name })
        assertEquals(3, model.library.value.equipment.size)
        assertEquals(1, model.library.value.setups.size)
    }

    @Test fun catalogEmptyHistoryDoesNotOfferImport() {
        compose.setContent { MyFitnessPoliceTheme { CompositionLocalProvider(LocalEquipmentLibraryModel provides model) {
            ExercisesScreen(ListState.Ready(listOf(exercise)), ExerciseFormState(), {}, { _,_,_,_,_ -> },
                {}, onUpdate = { _,_,_,_,_,_ -> })
        } } }
        compose.onNodeWithTag("exercise-options-row").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-last-session-row").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("last-session-empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Import session").assertDoesNotExist()
        compose.onNodeWithTag("view-exercise-progress").assertIsNotEnabled()
    }

    private fun screenshot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val bitmap = i.uiAutomation.takeScreenshot()
        val orientation = if (bitmap.width > bitmap.height) "landscape" else "portrait"
        val folder = java.io.File(i.targetContext.getExternalFilesDir(null), "exercise-tabs-review").apply { mkdirs() }
        java.io.File(folder, "$name-$orientation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
