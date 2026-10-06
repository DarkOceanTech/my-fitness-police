package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrecinctNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyToolOpensItsOwnConstructionNoticeAndReturnsToPrecinct() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            val navigation = listOf("Dispatch", "Academy", "Field", "Reports", "Precinct")
            val positions = navigation.map { compose.onNode(hasText(it) and hasClickAction()).fetchSemanticsNode().boundsInRoot.left }
            assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
            compose.onNodeWithText("Learning").assertDoesNotExist()
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithTag("progress-history-tile").assertIsDisplayed()
            screenshot("progress-library-icon")
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("No completed workouts yet").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to Reports").performClick()
            compose.onNodeWithTag("progress-history-grid").assertIsDisplayed()
            compose.onNode(hasText("Precinct") and hasClickAction()).performClick()
            compose.onNodeWithTag("precinct-home").assertIsDisplayed()
            compose.onNodeWithText("Support and community").assertIsDisplayed()
            compose.onNodeWithTag("precinct-category-support").assertIsDisplayed()
            compose.onNodeWithTag("precinct-contact-tile").assertIsDisplayed()
            compose.onNodeWithTag("precinct-support-request-tile").assertIsDisplayed()
            screenshot("precinct-grid-top")
            val features = listOf("contact" to "Contact", "support-request" to "New Request", "community" to "Community")
            features.forEachIndexed { index, (tag, title) ->
                compose.onNodeWithTag("precinct-grid").performScrollToNode(hasTestTag("precinct-$tag-tile"))
                compose.onNodeWithTag("precinct-$tag-tile").performClick()
                compose.onNodeWithTag("precinct-construction").assertIsDisplayed()
                compose.onNodeWithTag("precinct-construction-title").assertTextEquals(title)
                compose.onNodeWithTag("precinct-construction-image").assertExists()
                compose.onNodeWithText("UNDER CONSTRUCTION").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("precinct-construction-dismiss").assertIsDisplayed()
                if (tag == "support-request") screenshot("support-request-construction")
                if (tag == "community") screenshot("community-construction")
                if (index % 2 == 0) Espresso.pressBack()
                else compose.onNodeWithTag("precinct-construction-dismiss").performClick()
                compose.onNodeWithTag("precinct-construction").assertDoesNotExist()
                compose.onNodeWithTag("precinct-$tag-tile").assertIsDisplayed()
            }
            screenshot("precinct-grid-bottom")
            compose.onNodeWithTag("precinct-exercises-tile").assertDoesNotExist()
            compose.onNodeWithTag("precinct-mountain-biking-tile").assertDoesNotExist()
            compose.onNodeWithTag("precinct-category-distance").assertDoesNotExist()
            compose.onNodeWithTag("precinct-running-tile").assertDoesNotExist()
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
            compose.onNode(hasText("Precinct") and hasClickAction()).performClick()
            compose.onNodeWithTag("precinct-grid").performScrollToNode(hasTestTag("precinct-community-tile"))
            compose.onNodeWithTag("precinct-community-tile").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("precinct-construction-title").assertTextEquals("Community")
            compose.onNodeWithTag("precinct-construction-dismiss").performClick()
            compose.onNodeWithTag("precinct-grid").assertIsDisplayed()
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
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "precinct-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
