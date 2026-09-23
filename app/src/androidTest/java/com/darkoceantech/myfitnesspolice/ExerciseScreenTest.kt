package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class ExerciseScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savingExerciseFromGymPersistsAllFieldsAcrossTabNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "exercise-form-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            val repository = FitnessRepository(db)
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repository) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            val tabs = listOf("gym-section-exercises", "workout-section-0", "workout-section-1")
            val leftEdges = tabs.map { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left }
            assertTrue(leftEdges.zipWithNext().all { (left, right) -> left < right })
            compose.onNodeWithTag("gym-section-exercises").assertIsSelected()
            compose.onNodeWithTag("exercise-catalog").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to Armory").assertDoesNotExist()
            screenshot("gym-exercises-tab")
            compose.onNodeWithTag("add-catalog-exercise").performClick()
            compose.onNodeWithTag("save-exercise").performClick()
            compose.onNodeWithText("Enter an exercise name.").assertIsDisplayed()
            compose.onNodeWithTag("exercise-name").performTextInput("Cable row")
            compose.onNodeWithTag("save-exercise").performClick()
            compose.onNodeWithText("Add equipment, a description, and primary muscles. Use Bodyweight when no load is needed.").assertIsDisplayed()
            compose.onNodeWithTag("exercise-description").performScrollTo().performTextInput("Pull the handle toward the abdomen with a steady torso.")
            compose.onNodeWithTag("exercise-equipment").performScrollTo().performTextInput("Cable machine; close-grip handle")
            compose.onNodeWithTag("exercise-primary").performScrollTo().performTextInput("Latissimus dorsi; trapezius (middle part)")
            compose.onNodeWithTag("exercise-secondary").performScrollTo().performTextInput("Biceps brachii; brachialis")
            screenshot("add-exercise-keyboard")
            Espresso.closeSoftKeyboard()
            screenshot("add-exercise-fields")
            compose.onNodeWithTag("save-exercise").performClick()
            scrollCatalog(hasText("Cable row"))
            scrollCatalog(hasText("Cable machine; close-grip handle"))
            compose.onNodeWithText("Cable machine; close-grip handle").assertIsDisplayed()
            val saved = runBlocking { db.exerciseDao().getAll().single() }
            assertEquals("Pull the handle toward the abdomen with a steady torso.", saved.description)
            assertEquals("Latissimus dorsi; trapezius (middle part)", saved.primaryMuscles)
            assertEquals("Biceps brachii; brachialis", saved.secondaryMuscles)
            scrollCatalog(hasTestTag("catalog-search"))
            compose.onNodeWithTag("catalog-search").performTextInput("brachialis")
            Espresso.closeSoftKeyboard()
            scrollCatalog(hasText("Cable row"))
            compose.onNodeWithText("Cable row").assertIsDisplayed()
            scrollCatalog(hasTestTag("exercise-details-${saved.id}"))
            compose.onNodeWithTag("exercise-details-${saved.id}").performClick()
            scrollCatalog(hasText(saved.description))
            compose.onNodeWithText(saved.description).assertIsDisplayed()
            scrollCatalog(hasText(saved.secondaryMuscles))
            compose.onNodeWithText(saved.secondaryMuscles).assertIsDisplayed()
            screenshot("custom-exercise-details")
            compose.onNode(hasText("Armory") and hasClickAction()).performClick()
            compose.onNodeWithTag("armory-home").assertIsDisplayed()
            compose.onNodeWithTag("armory-exercises-tile").assertDoesNotExist()
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            scrollCatalog(hasText("Cable row"))
            compose.onNodeWithText("Cable row").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to Armory").assertDoesNotExist()
            scrollCatalog(hasTestTag("add-catalog-exercise"))
            compose.onNodeWithTag("add-catalog-exercise").performClick()
            compose.onNodeWithTag("exercise-name").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            compose.onNodeWithTag("exercise-name").performTextInput("Discard this")
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(1, runBlocking { db.exerciseDao().getAll().size })
            db.close()
            db = open()
            assertEquals(saved, runBlocking { db.exerciseDao().getAll().single() })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun seededCatalogSearchAndWorkoutPickerUseFullExerciseDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).addCallback(SeedExercises).build()
        try {
            val repo = FitnessRepository(db)
            runBlocking {
                val catalog = db.exerciseDao().getAll()
                assertEquals(48, catalog.size)
                assertEquals(48, catalog.map { it.id }.distinct().size)
                assertTrue(catalog.all { it.description.isNotBlank() && it.equipment.isNotBlank() &&
                    it.primaryMuscles.isNotBlank() && it.secondaryMuscles.isNotBlank() })
            }
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            screenshot("exercise-library")
            scrollCatalog(hasTestTag("catalog-search"))
            compose.onNodeWithTag("catalog-search").performTextInput("bent-over barbell")
            Espresso.closeSoftKeyboard()
            scrollCatalog(hasText("Bent-over barbell row"))
            compose.onNodeWithText("Bent-over barbell row").assertIsDisplayed()
            scrollCatalog(hasText("Show details"))
            compose.onNodeWithText("Show details").performClick()
            screenshot("barbell-row-details")
            scrollCatalog(hasTestTag("catalog-search"))
            compose.onNodeWithTag("catalog-search").performTextReplacement("unilateral")
            Espresso.closeSoftKeyboard()
            scrollCatalog(hasText("Seated independent-arm machine row"))
            compose.onNodeWithText("Seated independent-arm machine row").assertIsDisplayed()
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithTag("create-first-workout").performClick()
            waitFor("Add Exercise")
            compose.onNodeWithText("Add Exercise").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-picker-search").performTextInput("pull-back")
            Espresso.closeSoftKeyboard()
            fun pickerClick(tag: String) {
                compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performClick()
            }
            fun pickerCount(value: Int) {
                compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("exercise-picker-count"))
                compose.onNodeWithTag("exercise-picker-count").assertTextEquals("$value of 48 exercises · select Add to use a movement")
            }
            compose.onNodeWithTag("select-all-muscles").assertDoesNotExist()
            pickerClick("toggle-muscle-filters")
            pickerClick("select-all-muscles")
            pickerCount(0)
            pickerClick("muscle-filter-BACK")
            pickerClick("muscle-filter-BICEPS")
            pickerCount(2)
            pickerClick("toggle-muscle-filters")
            compose.onNodeWithTag("muscle-filter-BACK").assertDoesNotExist()
            pickerCount(2)
            pickerClick("toggle-muscle-filters")
            compose.onNodeWithTag("muscle-filter-BACK").assertIsOn()
            compose.onNodeWithTag("muscle-filter-BICEPS").assertIsOn()
            pickerClick("toggle-muscle-filters")
            screenshot("choose-back-exercise")
            val id = "e12d7b3e-588b-4fc2-8d3f-000000000008"
            compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-$id"))
            compose.onNodeWithTag("select-exercise-$id").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("exercise-picker-search").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Seated independent-arm machine row"))
            compose.onNodeWithText("Seated independent-arm machine row").assertIsDisplayed()
        } finally { db.close() }
    }

    private fun scrollCatalog(matcher: SemanticsMatcher) {
        compose.waitUntil(8000) { compose.onAllNodesWithTag("muscle-filter-matrix").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("exercise-catalog").performScrollToNode(matcher)
    }
    private fun waitFor(text: String) {
        compose.waitUntil(8000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "exercise-catalog-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
