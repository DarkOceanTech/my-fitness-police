package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import java.util.UUID

class EquipmentPositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editCancelRemoveReopenAndSessionSnapshotPreserveEquipmentSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "equipment-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        val repo = FitnessRepository(db)
        val planId = runBlocking {
            repo.addExercise("Machine row", "Independent-arm row machine")
            val exerciseId = repo.observeExercises().first().single().id
            repo.chooseExercise(exerciseId)
            repo.setWorkoutDetails("day", "Monday")
            repo.setWorkoutDetails("muscles", "Back")
            val first = repo.observeSessions().first().single().workout.id
            repo.savePlan(first)
            repo.chooseExercise(exerciseId)
            val second = repo.observeSessions().first().single { it.workout.kind == "draft" }.workout.id
            repo.savePlan(second)
            first
        }
        val entryId = runBlocking { db.workoutDao().getDetails(planId)!!.orderedExercises().single().workoutExercise.id }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            screenshot("home-darker-gradient")
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Expand Machine row").performScrollTo().performClick()
            fun showEditor() {
                compose.onNodeWithTag("workout-builder").performScrollToNode(hasContentDescription("Exercise options for Machine row"))
                compose.onNodeWithContentDescription("Exercise options for Machine row").performClick()
                compose.onNodeWithText("Edit equipment setup").performClick()
            }
            showEditor()
            compose.onNodeWithTag("position-name-0").performTextInput("Seat")
            compose.onNodeWithTag("save-equipment-positions").performClick()
            compose.onNodeWithText("Enter both a name and position for each item, or remove the unfinished item.").assertIsDisplayed()
            compose.onNodeWithTag("position-value-0").performTextInput(" 4 ")
            compose.onNodeWithTag("equipment-position-list").performScrollToNode(hasTestTag("add-equipment-position"))
            compose.onNodeWithTag("add-equipment-position").performClick()
            compose.onNodeWithTag("equipment-position-list").performScrollToNode(hasTestTag("position-name-1"))
            compose.onNodeWithTag("position-name-1").performTextInput("Bench angle")
            compose.onNodeWithTag("position-value-1").performTextInput("30°")
            Espresso.closeSoftKeyboard()
            screenshot("equipment-position-modal")
            compose.onNodeWithTag("save-equipment-positions").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("equipment-position-editor").fetchSemanticsNodes().isEmpty() }
            val expected = listOf(EquipmentPosition("Seat", "4"), EquipmentPosition("Bench angle", "30°"))
            runBlocking {
                assertEquals(expected, db.workoutDao().getDetails(planId)!!.orderedExercises().single().workoutExercise.equipmentPositions)
                assertTrue(repo.observeSessions().first().single { it.workout.id != planId }.exercises.single().workoutExercise.equipmentPositions.isEmpty())
            }
            showEditor()
            compose.onNodeWithTag("position-value-0").performTextReplacement("9")
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertEquals(expected, db.workoutDao().getDetails(planId)!!.exercises.single().workoutExercise.equipmentPositions) }
            showEditor()
            compose.onNodeWithTag("remove-position-1").performClick()
            compose.onNodeWithTag("save-equipment-positions").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("equipment-position-editor").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("EQUIPMENT SETUP"))
            compose.onNodeWithText("Seat").assertIsDisplayed()
            screenshot("edit-workout-gradient-buttons")
            val trainingId = runBlocking { repo.trainingPlans.save(null, "Rows", listOf(planId), "Monday") }
            openTrainingPlanFromWorkout(compose, trainingId)
            compose.waitUntil(8000) { compose.onAllNodesWithText("EQUIPMENT SETUP").fetchSemanticsNodes().isNotEmpty() }
            screenshot("active-equipment-setup")
            val activeSession = runBlocking { repo.observeSessions().first().single { it.workout.kind == "session" } }
            fun showActiveSetup() {
                compose.onNodeWithContentDescription("Session exercise options for Machine row").performScrollTo().performClick()
                compose.onNodeWithText("Edit note").assertDoesNotExist()
                compose.onNodeWithText("Edit equipment setup").performClick()
            }
            showActiveSetup()
            compose.onNodeWithTag("position-value-0").performTextReplacement("9")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking {
                assertEquals(listOf(EquipmentPosition("Seat", "4")), repo.observeWorkout(activeSession.workout.id).first()!!
                    .exercises.single().workoutExercise.equipmentPositions)
            }
            showActiveSetup()
            compose.onNodeWithTag("position-value-0").performTextReplacement("6")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            screenshot("active-equipment-editor")
            compose.onNodeWithTag("save-equipment-positions").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("equipment-position-editor").fetchSemanticsNodes().isEmpty() }
            runBlocking {
                val session = repo.observeSessions().first().single { it.workout.kind == "session" }
                val copied = listOf(EquipmentPosition("Seat", "6"))
                assertEquals(copied, session.exercises.single().workoutExercise.equipmentPositions)
                assertEquals(listOf(EquipmentPosition("Seat", "4")), db.workoutDao().getDetails(planId)!!.exercises.single().workoutExercise.equipmentPositions)
                repo.saveEquipmentPositions(planId, entryId, emptyList())
                assertTrue(db.workoutDao().getDetails(planId)!!.exercises.single().workoutExercise.equipmentPositions.isEmpty())
                assertEquals(copied, db.workoutDao().getDetails(session.workout.id)!!.exercises.single().workoutExercise.equipmentPositions)
                // Session history retains the setup snapshot even if the source plan is changed later.
                repo.finishWorkout(session.workout.id)
                db.close()
                db = open()
                assertTrue(db.workoutDao().getDetails(planId)!!.exercises.single().workoutExercise.equipmentPositions.isEmpty())
                assertEquals(copied, db.workoutDao().getDetails(session.workout.id)!!.exercises.single().workoutExercise.equipmentPositions)
                assertNotNull(db.workoutDao().getDetails(session.workout.id)!!.workout.finishedAt)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "equipment-theme-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
