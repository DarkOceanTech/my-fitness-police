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

class WorkoutLibraryToolsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickerCanCreateValidateCancelAndSelectAnExerciseWithoutLeavingEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "picker-create-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            val repo = FitnessRepository(db)
            val planId = runBlocking {
                repo.addExercise("Curl", "Dumbbell", "Flex the elbow.", "Biceps brachii")
                repo.chooseExercise(repo.observeExercises().first().single().id)
                repo.observeSessions().first().single().workout.id.also { repo.savePlan(it, "Arm light") }
            }
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            openWorkout(planId)
            builderScroll("add-workout-exercise")
            compose.onNodeWithTag("add-workout-exercise").performClick()
            compose.onNodeWithTag("exercise-picker-search").performTextInput("missing movement")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("toggle-muscle-filters").performClick()
            compose.onNodeWithTag("select-all-muscles").performClick()
            compose.onNodeWithTag("toggle-muscle-filters").performClick()
            screenshot("picker-plus")
            val search = compose.onNodeWithTag("exercise-picker-search").fetchSemanticsNode().boundsInRoot
            val plus = compose.onNodeWithTag("add-picker-exercise").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(plus.left >= search.right)
            assertTrue(plus.center.y in search.top..search.bottom)
            compose.onNodeWithTag("add-picker-exercise").performClick()
            compose.onNodeWithTag("exercise-name").performTextInput("Discard me")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("exercise-picker-search").assertTextContains("missing movement")
            assertEquals(1, runBlocking { db.exerciseDao().getAll().size })
            compose.onNodeWithTag("add-picker-exercise").performClick()
            compose.onNodeWithTag("save-exercise").performClick()
            compose.onNodeWithText("Enter an exercise name.").assertIsDisplayed()
            compose.onNodeWithTag("exercise-name").performTextInput("Cable row")
            compose.onNodeWithTag("save-exercise").performClick()
            compose.onNodeWithText("Add equipment, a description, and primary muscles. Use Bodyweight when no load is needed.").assertIsDisplayed()
            compose.onNodeWithTag("exercise-description").performScrollTo().performTextInput("Pull the handle toward the abdomen.")
            compose.onNodeWithTag("exercise-equipment").performScrollTo().performTextInput("Cable machine")
            compose.onNodeWithTag("exercise-primary").performScrollTo().performTextInput("Latissimus dorsi")
            compose.onNodeWithTag("exercise-secondary").performScrollTo().performTextInput("Biceps brachii")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-exercise").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("exercise-picker-search").fetchSemanticsNodes().isNotEmpty() }
            val added = runBlocking { db.exerciseDao().getAll().single { it.name == "Cable row" } }
            compose.onNodeWithTag("exercise-picker-count").assertTextEquals("2 of 2 exercises · select Add to use a movement")
            compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-${added.id}"))
            screenshot("picker-new-exercise")
            compose.onNodeWithTag("select-exercise-${added.id}").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("exercise-picker-search").fetchSemanticsNodes().isEmpty() }
            builderScroll("save-workout")
            compose.onNodeWithTag("save-workout").assertIsEnabled()
            compose.onNodeWithText("Edit workout").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.onNodeWithTag("exercise-catalog").performScrollToNode(hasText("Cable row"))
            compose.onNodeWithText("Cable row").assertIsDisplayed()
            db.close()
            db = open()
            runBlocking {
                assertEquals(added, db.exerciseDao().getAll().single { it.id == added.id })
                assertEquals("Pull the handle toward the abdomen.", added.description)
                assertEquals("Cable machine", added.equipment)
                assertEquals("Latissimus dorsi", added.primaryMuscles)
                assertEquals("Biceps brachii", added.secondaryMuscles)
                assertEquals(2, db.workoutDao().getDetails(planId)!!.exercises.size)
                assertEquals(10, db.workoutDao().getDetails(planId)!!.exercises.single { it.exercise.id == added.id }.sets.single().reps)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun copyButtonAppendsLastPrescriptionAndEnablesSaveWithPersistentIndependentRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "copy-set-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            val repo = FitnessRepository(db)
            val plan = runBlocking {
                repo.addExercise("Curl", "Dumbbell")
                repo.chooseExercise(repo.observeExercises().first().single().id)
                val draft = repo.observeSessions().first().single()
                val entry = draft.exercises.single().workoutExercise.id
                repo.saveSet(draft.workout.id, entry, null, 12, 29484)
                val last = repo.observeWorkout(draft.workout.id).first()!!.orderedSets().last()
                repo.updateSetFields(draft.workout.id, entry, last.id, modifier = "superset", warmup = true)
                repo.savePlan(draft.workout.id, "Biceps light")
                repo.observeWorkout(draft.workout.id).first()!!
            }
            val planId = plan.workout.id
            val entryId = plan.exercises.single().workoutExercise.id
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            openWorkout(planId)
            builderScroll("save-workout")
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Expand Curl").performScrollTo().performClick()
            builderScroll("copy-last-set-$entryId")
            val add = compose.onNodeWithTag("add-set-$entryId").fetchSemanticsNode().boundsInRoot
            val copy = compose.onNodeWithTag("copy-last-set-$entryId").fetchSemanticsNode().boundsInRoot
            assertTrue(copy.left > add.right)
            assertEquals(add.top, copy.top, 1f)
            compose.onNodeWithTag("copy-last-set-$entryId").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(planId).first()!!.orderedSets().size == 3 } }
            builderScroll("copy-last-set-$entryId")
            compose.onNodeWithContentDescription("Weight set 3").assertTextEquals("65")
            compose.onNodeWithContentDescription("Reps set 3").assertTextEquals("12")
            compose.onNodeWithContentDescription("Modifier set 3").assertTextEquals("Ss")
            compose.onNodeWithContentDescription("Type set 3").assertTextEquals("Wu")
            screenshot("copied-set")
            builderScroll("save-workout")
            compose.onNodeWithTag("save-workout").assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("plan-saved").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("save-workout").assertIsNotEnabled()
            db.close()
            db = open()
            runBlocking {
                val sets = db.workoutDao().getDetails(planId)!!.orderedSets()
                assertEquals(plan.orderedSets(), sets.take(2))
                assertEquals(3, sets.map { it.id }.distinct().size)
                assertEquals(listOf(0, 1, 2), sets.map { it.position })
                assertEquals(12, sets.last().reps)
                assertEquals(29484L, sets.last().weightGrams)
                assertEquals("superset", sets.last().modifier)
                assertTrue(sets.last().isWarmup)
                assertNull(sets.last().actualReps)
                assertNull(sets.last().completedAt)
                assertEquals(0L, sets.last().activeMillis)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun openWorkout(id: String) {
        compose.onNode(hasText("Academy") and hasClickAction()).performClick()
        compose.onNodeWithTag("workout-section-0").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithTag("home-plan-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-plan-$id").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Edit workout").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun builderScroll(tag: String) {
        compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag(tag))
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "workout-library-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
