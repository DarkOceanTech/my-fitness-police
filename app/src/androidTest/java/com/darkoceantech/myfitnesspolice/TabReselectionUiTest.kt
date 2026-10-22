package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TabReselectionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedReportsTabGoesBackOneLevelAndStopsAtLanding() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            runBlocking {
                db.exerciseDao().insert(Exercise("e", "Cable row", "Cable"))
                db.workoutDao().insert(Workout("w", trainingPlan = "Test plan", startedAt = System.currentTimeMillis() - 10000, finishedAt = System.currentTimeMillis()))
                db.workoutExerciseDao().insert(WorkoutExercise("entry", "w", "e", 0))
                db.workoutSetDao().insert(WorkoutSet("s", "entry", 0, 10, 4536, completedAt = 100, actualReps = 8))
            }
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("progress-history-tile").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("history-workout-w").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-workout-w").performScrollTo().performClick()
            compose.onNodeWithTag("set-action-s").performScrollTo().performClick()
            compose.onNodeWithTag("edit-history-set").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("edit-history-set").assertDoesNotExist()
            compose.onNodeWithTag("history-detail").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("history-list").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("progress-history-home").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("progress-history-home").assertExists()
            compose.onNodeWithText("Academy").performClick()
            compose.onNodeWithTag("progress-history-home").assertDoesNotExist()
            compose.onNodeWithText("Dispatch").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("launch-w").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("launch-w").performScrollTo().performClick()
            compose.onNodeWithTag("history-detail").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("history-list").assertExists()
            compose.onNodeWithText("Reports").performClick()
            compose.onNodeWithTag("progress-history-home").assertExists()
        } finally { db.close() }
    }
}