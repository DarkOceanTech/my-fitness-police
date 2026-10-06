package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
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

class MixedTrainingPlanUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mixedPlanPreservesItemTypesOrderAndEditableSetsAcrossSaveAndManualStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "mixed-training-ui-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        val db = open()
        seedCatalog(db)
        val originalModule = runBlocking {
            db.workoutDao().insert(Workout(id = "module", kind = "plan", name = "Biceps strength"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "module-curl", workoutId = "module", exerciseId = "curl", position = 0))
            db.workoutSetDao().insert(WorkoutSet(id = "module-set-1", workoutExerciseId = "module-curl", position = 0, reps = 8, weightGrams = 9000))
            db.workoutSetDao().insert(WorkoutSet(id = "module-set-2", workoutExerciseId = "module-curl", position = 1, reps = 6, weightGrams = 11000))
            db.workoutDao().getDetails("module")!!
        }
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            createPlan("Mixed arm training")
            available("available-workout-module")
            compose.onNodeWithTag("available-workout-module").performClick()
            compose.onNodeWithTag("include-selected-workouts").assertIsEnabled()
            source("exercises")
            compose.onNodeWithTag("include-selected-workouts").assertIsNotEnabled()
            compose.onNodeWithTag("included-count").assertTextEquals("(0)")
            available("available-exercise-curl")
            compose.onNodeWithTag("available-exercise-curl").performClick()
            compose.onNodeWithTag("include-selected-workouts").performClick()
            included("included-exercise-curl")
            compose.onNodeWithTag("included-exercise-curl").assertExists()
            available("available-exercise-row")
            compose.onNodeWithTag("available-exercise-row").performTouchInput { longClick() }
            included("included-exercise-row")
            compose.onNodeWithTag("included-exercise-row").assertIsSelected()
            compose.onNodeWithTag("remove-selected-workout").assertIsEnabled().performClick()
            compose.onNodeWithTag("included-count").assertTextEquals("(1)")
            source("workouts")
            available("available-workout-module")
            compose.onNodeWithTag("available-workout-module").performClick()
            compose.onNodeWithTag("move-included-up").assertIsNotEnabled()
            compose.onNodeWithTag("include-selected-workouts").performClick()
            included("included-workout-module")
            compose.onNodeWithTag("included-workout-module").assertIsSelected()
            compose.onNodeWithTag("move-included-up").assertIsEnabled().performClick()
            compose.onNodeWithTag("move-included-up").assertIsNotEnabled()
            compose.onNodeWithTag("move-included-down").assertIsEnabled()
            capture("mixed-included-items")

            editDirect("curl")
            assertValue("training-set-weight-0", "0")
            assertValue("training-set-reps-0", "10")
            pick("training-set-reps-0", "13")
            compose.onNodeWithTag("cancel-training-exercise-sets").performClick()
            waitGone("training-exercise-sets")
            editDirect("curl")
            assertValue("training-set-reps-0", "10")
            pick("training-set-weight-0", "25")
            pick("training-set-reps-0", "12")
            compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasTestTag("add-training-set"))
            compose.onNodeWithTag("add-training-set").performScrollTo().performClick()
            pick("training-set-weight-1", "30")
            pick("training-set-reps-1", "8")
            capture("mixed-direct-set-editor")
            compose.onNodeWithTag("save-training-exercise-sets").performClick()
            waitGone("training-exercise-sets")
            compose.onNodeWithTag("save-training-plan").assertIsEnabled().performClick()
            waitTag("workout-home")
            val created = runBlocking { db.trainingPlanDao().getAll().single() }
            val initialItems = listOf(TrainingPlanItem.WorkoutItem("module"), TrainingPlanItem.ExerciseItem("curl", listOf(
                TrainingPlanSet(reps = 12, weightGrams = 11340), TrainingPlanSet(reps = 8, weightGrams = 13608))))
            assertEquals(initialItems, runBlocking { db.trainingPlanDao().get(created.id)!!.orderedItems() })
            assertEquals(originalModule.exercises, runBlocking { db.workoutDao().getDetails("module")!!.exercises })
            homePlan(created.id)
            compose.onNodeWithTag("training-plan-${created.id}").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithContentDescription("Training plan options").performClick()
            compose.onNodeWithTag("edit-training-plan").performClick()
            waitTag("training-plan-editor")
            editDirect("curl")
            assertValue("training-set-weight-0", "25")
            assertValue("training-set-reps-0", "12")
            assertValue("training-set-weight-1", "30")
            assertValue("training-set-reps-1", "8")
            pick("training-set-reps-1", "9")
            compose.onNodeWithTag("save-training-exercise-sets").performClick()
            waitGone("training-exercise-sets")
            compose.onNodeWithTag("save-training-plan").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("training-plan-workouts").performScrollToNode(hasTestTag("training-exercise-curl"))
            compose.onNodeWithTag("training-exercise-curl").performClick()
            waitTag("training-exercise-sets")
            assertValue("training-set-reps-1", "9")
            pick("training-set-reps-1", "11")
            compose.onNodeWithTag("save-training-exercise-sets").performClick()
            waitGone("training-exercise-sets")
            val expected = listOf(TrainingPlanItem.WorkoutItem("module"), TrainingPlanItem.ExerciseItem("curl", listOf(
                TrainingPlanSet(reps = 12, weightGrams = 11340), TrainingPlanSet(reps = 11, weightGrams = 13608))))
            val reopened = open()
            try { assertEquals(expected, runBlocking { reopened.trainingPlanDao().get(created.id)!!.orderedItems() }) }
            finally { reopened.close() }
            capture("mixed-plan-saved-detail")
            compose.onNodeWithTag("start-training").assertIsEnabled().performClick()
            waitTag("training-ready")
            val session = runBlocking { db.workoutDao().observeAllDetails().first().single { it.workout.kind == "session" } }
            assertEquals(created.id, session.workout.sourceTrainingPlanId)
            assertEquals(listOf("curl", "curl"), session.orderedExercises().map { it.exercise.id })
            assertEquals(listOf(0, 1), session.orderedExercises().map { it.workoutExercise.position })
            assertEquals(listOf(8, 6), session.orderedExercises()[0].sets.sortedBy { it.position }.map { it.reps })
            assertEquals(listOf(12, 11), session.orderedExercises()[1].sets.sortedBy { it.position }.map { it.reps })
            assertEquals(listOf(11340L, 13608L), session.orderedExercises()[1].sets.sortedBy { it.position }.map { it.weightGrams })
            assertEquals(4, session.orderedSets().map { it.id }.distinct().size)
            assertEquals("ready", session.sessionState!!.phase)
            assertFalse(session.sessionState!!.hasStarted)
            assertTrue(session.orderedSets().all { it.completedAt == null && it.activeMillis == 0L && it.restMillis == 0L })
            assertEquals(originalModule.exercises, runBlocking { db.workoutDao().getDetails("module")!!.exercises })
            capture("mixed-session-ready")
        } finally { restoreOrientation(); db.close(); context.deleteDatabase(databaseName) }
    }

    @Test fun exerciseOnlyPlanUsesDefaultSetAndWaitsForTheFirstManualStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        seedCatalog(db)
        try {
            freezeOrientation()
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            createPlan("Quick curls")
            source("exercises")
            available("available-exercise-curl")
            compose.onNodeWithTag("available-exercise-curl").performTouchInput { longClick() }
            included("included-exercise-curl")
            compose.onNodeWithTag("save-training-plan").assertIsEnabled().performClick()
            waitTag("workout-home")
            val plan = runBlocking { db.trainingPlanDao().getAll().single() }
            assertEquals(listOf(TrainingPlanItem.ExerciseItem("curl", listOf(TrainingPlanSet()))),
                runBlocking { db.trainingPlanDao().get(plan.id)!!.orderedItems() })
            assertTrue(runBlocking { db.workoutDao().observeAllDetails().first().isEmpty() })
            homePlan(plan.id)
            compose.onNodeWithTag("training-plan-${plan.id}").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("training-plan-workouts").performScrollToNode(hasTestTag("training-exercise-curl"))
            compose.onNodeWithTag("training-exercise-curl").assertIsDisplayed()
            capture("exercise-only-plan")
            compose.onNodeWithTag("start-training").assertIsEnabled().performClick()
            waitTag("training-ready")
            val session = runBlocking { db.workoutDao().observeAllDetails().first().single() }
            val set = session.orderedSets().single()
            assertEquals("session", session.workout.kind)
            assertEquals(10, set.reps)
            assertEquals(0L, set.weightGrams)
            assertEquals("ready", session.sessionState!!.phase)
            assertFalse(session.sessionState!!.hasStarted)
            compose.onNodeWithTag("set-action-${set.id}").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(12000) { runBlocking { db.sessionStateDao().get(session.workout.id)?.phase == "active" } }
            val started = runBlocking { db.sessionStateDao().get(session.workout.id)!! }
            assertEquals(set.id, started.currentSetId)
            assertTrue(started.hasStarted)
        } finally { restoreOrientation(); db.close() }
    }

    private fun seedCatalog(db: FitnessDatabase) = runBlocking {
        db.exerciseDao().insert(Exercise(id = "curl", name = "Curl", equipment = "Dumbbell", description = "Flex the elbow.", primaryMuscles = "Biceps brachii"))
        db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Cable", description = "Pull toward the torso.", primaryMuscles = "Latissimus dorsi"))
    }
    private fun createPlan(name: String) {
        compose.onNode(hasText("Academy") and hasClickAction()).performClick()
        compose.onNodeWithTag("workout-section-1").performClick()
        waitTag("create-first-training-plan")
        compose.onNodeWithTag("create-first-training-plan").performScrollTo().performClick()
        waitTag("training-plan-editor")
        compose.onNodeWithTag("training-plan-name").performTextInput(name)
        compose.waitForIdle(); android.os.SystemClock.sleep(250)
        Espresso.closeSoftKeyboard()
        compose.waitForIdle(); android.os.SystemClock.sleep(250)
    }
    private fun source(value: String) {
        compose.onNodeWithTag("training-excluded-source").performClick()
        compose.onNodeWithTag("training-source-$value").performClick()
    }
    private fun available(tag: String) = compose.onNodeWithTag("available-workouts").performScrollToNode(hasTestTag(tag))
    private fun included(tag: String) = compose.onNodeWithTag("included-workouts").performScrollToNode(hasTestTag(tag))
    private fun editDirect(id: String) {
        included("edit-included-exercise-$id")
        compose.onNodeWithTag("edit-included-exercise-$id").performClick()
        waitTag("training-exercise-sets")
    }
    private fun assertValue(tag: String, value: String) {
        val description = fieldDescription(tag)
        compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performScrollTo().assert(hasText(value) or hasAnyDescendant(hasText(value)))
    }
    private fun pick(tag: String, value: String) {
        val description = fieldDescription(tag)
        compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose.onNodeWithTag("$description options").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(hasTestTag("$description options"))).performClick()
    }
    private fun fieldDescription(tag: String) = (if ("-weight-" in tag) "Weight" else "Reps") + " set " + (tag.substringAfterLast('-').toInt() + 1)
    private fun homePlan(id: String) = compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("training-plan-$id"))
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitGone(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() } }
    private fun freezeOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
    }
    private fun restoreOrientation() {
        if (InstrumentationRegistry.getArguments().getString("expectedOrientation") != null)
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "mixed-training-plan-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val landscape = bitmap.width > bitmap.height
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it == "landscape", landscape) }
            java.io.File(folder, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
