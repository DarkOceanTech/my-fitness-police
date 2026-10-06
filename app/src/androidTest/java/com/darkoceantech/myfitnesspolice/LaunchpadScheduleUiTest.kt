package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
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
import org.junit.Before
import org.junit.After
import java.time.LocalDate
import java.time.YearMonth

class LaunchpadScheduleUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun freezeRequestedOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0))
        android.os.SystemClock.sleep(500)
    }
    @After fun restoreRotation() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
    }

    @Test fun scheduleCancelSaveAndLaunchpadBackPreserveSelectedDay() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        val today = LocalDate.now()
        val future = today.plusDays(2)
        runBlocking {
            db.exerciseDao().insert(Exercise("e", "Cable row", "Cable"))
            db.trainingPlanDao().insert(TrainingPlan("p", "Back patrol", 100, "Monday"))
            db.trainingPlanDao().insertExerciseMembers(listOf(TrainingPlanExercise("p", "e", 0)))
        }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            waitTag("dashboard-launchpad")
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-empty"))
            compose.onNodeWithTag("launch-p").assertDoesNotExist()
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            waitTag("gym-section-exercises")
            compose.onNodeWithTag("workout-section-1").performClick()
            waitTag("workout-home")
            compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("training-plan-p"))
            compose.onNodeWithTag("training-plan-p").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("training-plan-schedule").assertDoesNotExist()
            fun openCalendar() {
                val start = compose.onNodeWithTag("start-training").fetchSemanticsNode().boundsInRoot
                val schedule = compose.onNodeWithTag("schedule-training").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Scheduling belongs below Start training", schedule.top >= start.bottom)
                compose.onNodeWithTag("schedule-training").performClick()
                waitTag("training-schedule-dialog")
            }
            fun selectDate(date: LocalDate) {
                if (YearMonth.from(date) != YearMonth.from(today)) compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
                compose.onNodeWithTag("training-schedule-content").performScrollToNode(hasTestTag("schedule-date-$date"))
                compose.onNodeWithTag("schedule-date-$date").performClick().assertIsSelected()
            }
            openCalendar()
            compose.onNodeWithTag("save-training-schedule").assertIsNotEnabled()
            selectDate(today)
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(runBlocking { repo.trainingSchedule.observeAll().first().isEmpty() })
            openCalendar()
            selectDate(today)
            selectDate(future)
            capture("schedule-calendar")
            compose.onNodeWithTag("save-training-schedule").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("training-schedule-dialog").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(today.toString(), future.toString()), runBlocking { repo.trainingSchedule.observeAll().first().map { it.scheduledDate } })
            compose.onNodeWithContentDescription("Training plan options").performClick()
            compose.onNodeWithTag("edit-training-plan").performClick()
            waitTag("training-plan-editor")
            compose.onNodeWithTag("training-plan-day").assertDoesNotExist()
            compose.onNodeWithTag("training-plan-name").performTextReplacement("Back patrol updated")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-training-plan").performClick()
            waitTag("training-plan-detail")
            assertEquals("Monday", runBlocking { db.trainingPlanDao().get("p")!!.plan.dayOfWeek })
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            waitTag("dashboard-launchpad")
            compose.onNodeWithTag("launchpad-calendar").performScrollToIndex(12)
            compose.onNodeWithTag("calendar-$future").performClick()
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-p"))
            compose.onNodeWithTag("launch-p").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("schedule-training").assertIsDisplayed()
            openCalendar()
            val another = future.plusDays(1)
            selectDate(another)
            compose.onNodeWithTag("save-training-schedule").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("training-schedule-dialog").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(today.toString(), future.toString(), another.toString()),
                runBlocking { repo.trainingSchedule.observeAll().first().map { it.scheduledDate } })
            compose.onNodeWithContentDescription("Back to Launchpad").performClick()
            waitTag("dashboard-launchpad")
            compose.onNodeWithTag("calendar-$future").assertIsSelected()
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-p"))
            compose.onNodeWithTag("launch-p").performClick()
            waitTag("training-plan-detail")
            Espresso.pressBack()
            waitTag("dashboard-launchpad")
            compose.onNodeWithTag("calendar-$future").assertIsSelected()
            capture("launchpad-scheduled")
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-p"))
            compose.onNodeWithTag("launch-p").performClick()
            waitTag("training-plan-detail")
            fun deleteScheduleMenu() {
                compose.onNodeWithContentDescription("Training plan options").performClick()
                compose.onNodeWithTag("delete-scheduled-training").performClick()
                waitTag("delete-training-schedule-dialog")
            }
            deleteScheduleMenu()
            compose.onNode(hasAnyAncestor(hasTestTag("delete-schedule-date-$future")) and isSelected()).assertExists()
            compose.onNodeWithTag("cancel-delete-schedule").performClick()
            assertEquals(3, runBlocking { repo.trainingSchedule.observeAll().first().size })
            deleteScheduleMenu()
            capture("delete-schedule-confirmation")
            compose.onNodeWithTag("confirm-delete-schedule").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("delete-training-schedule-dialog").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(today.toString(), another.toString()),
                runBlocking { repo.trainingSchedule.observeAll().first().map { it.scheduledDate } })
            assertNotNull(runBlocking { db.trainingPlanDao().get("p") })
            compose.onNodeWithContentDescription("Back to Launchpad").performClick()
            waitTag("dashboard-launchpad")
            compose.onNodeWithTag("calendar-$future").assertIsSelected()
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-empty"))
            compose.onNodeWithTag("launch-p").assertDoesNotExist()
            // A finished plan replaces its pending launch, while other scheduled dates survive.
            runBlocking {
                val now = System.currentTimeMillis()
                db.workoutDao().insert(Workout("log", now - 1000, finishedAt = now, sourceTrainingPlanId = "p", trainingPlan = "Back patrol updated"))
                db.workoutExerciseDao().insert(WorkoutExercise("logged-row", "log", "e", 0))
                db.workoutSetDao().insert(WorkoutSet("logged-set", "logged-row", 0, 10, 10000, completedAt = now - 100, actualReps = 10))
            }
            compose.onNodeWithTag("launchpad-calendar").performScrollToIndex(10)
            compose.onNodeWithTag("calendar-$today").performClick()
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-log"))
            compose.onNodeWithTag("launch-p").assertDoesNotExist()
            compose.onNodeWithTag("launch-log").assertIsDisplayed()
            assertEquals(2, runBlocking { repo.trainingSchedule.observeAll().first().size })
            capture("launchpad-completed")
        } finally { db.close() }
    }

    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Wait for the native full-screen dialog transition before reviewing the frame.
        android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = if (instrumentation.targetContext.resources.configuration.orientation == 2) "landscape" else "portrait"
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it, orientation) }
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "launchpad-schedule-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$orientation-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
