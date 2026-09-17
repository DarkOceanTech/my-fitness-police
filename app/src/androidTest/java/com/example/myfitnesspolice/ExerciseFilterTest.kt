package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfitnesspolice.data.*
import com.example.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ExerciseFilterTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleGroupsSelectAllAndSearchCanFilterInBothDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).addCallback(SeedExercises).build()
        try {
            val repo = FitnessRepository(db)
            runBlocking { assertEquals(48, db.exerciseDao().getAll().size) }
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("muscle-filter-matrix").fetchSemanticsNodes().isNotEmpty() }
            fun click(tag: String) {
                compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performClick()
            }
            fun count(value: Int) {
                compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasTestTag("catalog-count"))
                compose.onNodeWithTag("catalog-count").assertTextEquals("$value of 48 exercises")
            }
            compose.onNodeWithTag("select-all-muscles").assertDoesNotExist()
            click("toggle-muscle-filters")
            compose.onNodeWithTag("select-all-muscles").assertIsOn()
            compose.onNodeWithTag("muscle-filter-FOREARMS").assertIsOn()
            compose.onNodeWithTag("muscle-filter-ABDOMINAL").assertIsOn()
            compose.onNodeWithTag("muscle-filter-LOWER_BACK").assertIsOn()
            compose.onNodeWithTag("muscle-filter-CORE").assertDoesNotExist()
            captureFilterReview(compose, "muscle-filter-matrix")
            click("muscle-filter-CHEST")
            compose.onNodeWithTag("muscle-filter-CHEST").assertIsOff()
            count(41)
            click("toggle-muscle-filters")
            compose.onNodeWithTag("muscle-filter-CHEST").assertDoesNotExist()
            count(41)
            click("toggle-muscle-filters")
            compose.onNodeWithTag("muscle-filter-CHEST").assertIsOff()
            click("select-all-muscles")
            count(48)
            click("select-all-muscles")
            count(0)
            click("muscle-filter-BACK")
            click("muscle-filter-BICEPS")
            count(15)
            compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasTestTag("catalog-search"))
            compose.onNodeWithTag("catalog-search").performTextInput("barbell")
            Espresso.closeSoftKeyboard()
            count(2)
            compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasText("Bent-over barbell row"))
            compose.onNodeWithText("Bent-over barbell row").assertIsDisplayed()
            captureFilterReview(compose, "filtered-exercises")
            // Filtering only changes presentation, never catalog persistence.
            runBlocking { assertEquals(48, db.exerciseDao().getAll().size) }
        } finally { db.close() }
    }
}
