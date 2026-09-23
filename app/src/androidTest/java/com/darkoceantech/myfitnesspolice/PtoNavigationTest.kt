package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PtoNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activitiesMoveToPtoAndKeepTheirConstructionWindows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNode(hasText("Dispatch") and hasClickAction()).assertIsSelected()
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithText("My Police Training Officer").assertIsDisplayed()
            compose.onNodeWithText("Indoor and outdoor activity tracking for distance, time, and elevation.").assertIsDisplayed()
            screenshot("pto-home")
            val activities = listOf("running" to "Running", "hiking" to "Hiking", "kayaking" to "Kayaking",
                "walking" to "Walking", "road-cycling" to "Road cycling", "snowboarding" to "Snowboarding")
            activities.forEachIndexed { index, (tag, title) ->
                compose.onNodeWithTag("pto-grid").performScrollToNode(hasTestTag("pto-$tag-tile"))
                compose.onNodeWithTag("pto-$tag-tile").performClick()
                compose.onNodeWithTag("pto-construction-title").assertTextEquals(title)
                compose.onNodeWithTag("pto-construction-image").assertIsDisplayed()
                compose.onNodeWithText("UNDER CONSTRUCTION").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Back to Field Training").assertIsDisplayed()
                if (index == 0) screenshot("pto-running-construction")
                if (index % 2 == 0) Espresso.pressBack()
                else compose.onNodeWithTag("pto-construction-dismiss").performClick()
                compose.onNodeWithTag("pto-$tag-tile").assertIsDisplayed()
            }
            screenshot("pto-last-activities")
            compose.onNode(hasText("Armory") and hasClickAction()).performClick()
            compose.onNodeWithText("Support and tools").assertIsDisplayed()
            compose.onNodeWithText("Call for backup and gear up.").assertIsDisplayed()
            compose.onNodeWithTag("armory-grid").performScrollToNode(hasTestTag("armory-timer-tile"))
            compose.onNodeWithTag("armory-timer-tile").assertIsDisplayed()
            activities.forEach { (tag, title) ->
                compose.onNodeWithTag("armory-$tag-tile").assertDoesNotExist()
                compose.onNodeWithText(title).assertDoesNotExist()
            }
            compose.onNodeWithTag("armory-category-distance").assertDoesNotExist()
            runBlocking {
                assertTrue(db.workoutDao().observeAllDetails().first().isEmpty())
                assertTrue(db.exerciseDao().getAll().isEmpty())
            }
        } finally { db.close() }
    }

    @Test fun ptoSelectionRestoresAndStaysIndependentOfArmory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithTag("pto-running-tile").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("pto-construction-title").assertTextEquals("Running")
            compose.onNodeWithTag("pto-construction-dismiss").performClick()
            compose.onNode(hasText("Armory") and hasClickAction()).performClick()
            compose.onNodeWithTag("armory-contact-tile").performClick()
            compose.onNodeWithTag("armory-construction-title").assertTextEquals("Contact")
            compose.onNodeWithTag("armory-construction-dismiss").performClick()
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithTag("pto-home").assertIsDisplayed()
            compose.onNodeWithTag("pto-construction").assertDoesNotExist()
            compose.onNodeWithTag("armory-construction").assertDoesNotExist()
            compose.onNode(hasText("Reports") and hasClickAction()).performClick()
            compose.onNodeWithText("Progress Reports").assertIsDisplayed()
            screenshot("dor-home")
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.onNodeWithContentDescription("Back to Reports").performClick()
            compose.onNodeWithTag("progress-history-grid").assertIsDisplayed()
        } finally { db.close() }
    }

    @Test fun finishingAnAcademyWorkoutOpensItsDorHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            val sessionId = runBlocking {
                repo.addExercise("Cable row", "Cable machine")
                repo.chooseExercise(repo.observeExercises().first().single().id)
                val planId = repo.observeSessions().first().single().workout.id
                repo.savePlan(planId, "Back patrol")
                val id = repo.startPlan(planId)
                val set = repo.observeWorkout(id).first()!!.orderedSets().single()
                repo.sessionProgress.completeSet(id, set.id)
                repo.sessionProgress.recordActual(id, set.id, 10)
                id
            }
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Resume workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Resume workout").performScrollTo().performClick()
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("Finish", substring = false).performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("history-detail").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Reports") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("Cable row").assertIsDisplayed()
            assertNotNull(runBlocking { repo.observeWorkout(sessionId).first()!!.workout.finishedAt })
            screenshot("academy-finished-in-dor")
        } finally { db.close() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "pto-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
