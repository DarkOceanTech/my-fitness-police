package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfitnesspolice.data.*
import com.example.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArmoryNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyToolOpensItsOwnConstructionNoticeAndReturnsToArmory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            val navigation = listOf("Dispatch", "Academy", "PTO", "DOR", "Armory")
            val positions = navigation.map { compose.onNode(hasText(it) and hasClickAction()).fetchSemanticsNode().boundsInRoot.left }
            assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
            compose.onNodeWithText("Learning").assertDoesNotExist()
            compose.onNode(hasText("DOR") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").assertIsDisplayed()
            screenshot("progress-library-icon")
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("No completed workouts yet").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to DOR").performClick()
            compose.onNodeWithTag("progress-history-grid").assertIsDisplayed()
            compose.onNode(hasText("Armory") and hasClickAction()).performClick()
            compose.onNodeWithTag("armory-home").assertIsDisplayed()
            compose.onNodeWithText("Support and tools").assertIsDisplayed()
            compose.onNodeWithTag("armory-category-support").assertIsDisplayed()
            compose.onNodeWithTag("armory-contact-tile").assertIsDisplayed()
            compose.onNodeWithTag("armory-support-request-tile").assertIsDisplayed()
            screenshot("armory-grid-top")
            val features = listOf("contact" to "Contact", "support-request" to "New Request", "community" to "Community",
                "timer" to "Timer")
            features.forEachIndexed { index, (tag, title) ->
                compose.onNodeWithTag("armory-grid").performScrollToNode(hasTestTag("armory-$tag-tile"))
                if (tag == "timer") compose.onNodeWithTag("armory-category-tools").assertExists()
                compose.onNodeWithTag("armory-$tag-tile").performClick()
                compose.onNodeWithTag("armory-construction").assertIsDisplayed()
                compose.onNodeWithTag("armory-construction-title").assertTextEquals(title)
                compose.onNodeWithTag("armory-construction-image").assertExists()
                compose.onNodeWithText("UNDER CONSTRUCTION").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("armory-construction-dismiss").assertIsDisplayed()
                if (tag == "support-request") screenshot("support-request-construction")
                if (tag == "community") screenshot("community-construction")
                if (index % 2 == 0) Espresso.pressBack()
                else compose.onNodeWithTag("armory-construction-dismiss").performClick()
                compose.onNodeWithTag("armory-construction").assertDoesNotExist()
                compose.onNodeWithTag("armory-$tag-tile").assertIsDisplayed()
            }
            screenshot("armory-grid-bottom")
            compose.onNodeWithTag("armory-exercises-tile").assertDoesNotExist()
            compose.onNodeWithTag("armory-mountain-biking-tile").assertDoesNotExist()
            compose.onNodeWithTag("armory-category-distance").assertDoesNotExist()
            compose.onNodeWithTag("armory-running-tile").assertDoesNotExist()
            runBlocking {
                assertTrue(db.workoutDao().observeAllDetails().first().isEmpty())
                assertTrue(db.exerciseDao().getAll().isEmpty())
            }
        } finally { db.close() }
    }

    @Test fun constructionSelectionRestoresAndGymExercisesStillWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            runBlocking { repo.addExercise("Cable row", "Cable machine", "Pull toward the abdomen.", "Latissimus dorsi") }
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Armory") and hasClickAction()).performClick()
            compose.onNodeWithTag("armory-grid").performScrollToNode(hasTestTag("armory-community-tile"))
            compose.onNodeWithTag("armory-community-tile").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("armory-construction-title").assertTextEquals("Community")
            compose.onNodeWithTag("armory-construction-dismiss").performClick()
            compose.onNodeWithTag("armory-grid").assertIsDisplayed()
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").assertIsSelected()
            compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasText("Cable row"))
            compose.onNodeWithText("Cable row").assertIsDisplayed()
        } finally { db.close() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "armory-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
