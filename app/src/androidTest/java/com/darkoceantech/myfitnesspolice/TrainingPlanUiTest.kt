package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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

class TrainingPlanUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabsSelectionLongPressReorderRemovalAndMetadataUseReusableWorkouts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        val ids = runBlocking {
            repo.addExercise("Curl", "Dumbbell")
            val exercise = repo.observeExercises().first().single().id
            listOf("Back", "Biceps — Strength", "Biceps — Rehab", "Abdominal").map { name ->
                repo.chooseExercise(exercise)
                val draft = repo.observeSessions().first().single { it.workout.kind == "draft" }
                repo.savePlan(draft.workout.id, name)
                draft.workout.id
            }
        }
        val (back, strength, rehab, abs) = ids
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            waitTag("workout-section-1")
            homeScroll(hasTestTag("workout-section-1"))
            compose.onNodeWithTag("workout-section-1").performClick()
            waitTag("create-first-training-plan")
            capture("empty-plan-tab")
            compose.onNodeWithTag("create-first-training-plan").performClick()
            waitTag("training-plan-editor")
            Espresso.pressBack()
            waitTag("workout-home")
            compose.onNodeWithContentDescription("Add training plan").performClick()
            waitTag("training-plan-editor")
            compose.onNodeWithTag("save-training-plan").assertIsNotEnabled()
            compose.onNodeWithText("Training Plan").assertIsDisplayed()
            compose.onNodeWithTag("training-plan-name").performTextInput("Back + Arms + Abs")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("training-plan-day").performClick()
            compose.onNodeWithText("Mon", substring = false).assertIsDisplayed()
            capture("training-day-menu")
            compose.onNodeWithText("Monday").performClick()
            compose.onNodeWithTag("training-plan-day").assert(hasText("Mon"))
            val excludedBounds = compose.onNodeWithTag("available-workouts").fetchSemanticsNode().boundsInRoot
            val includedBounds = compose.onNodeWithTag("included-workouts").fetchSemanticsNode().boundsInRoot
            assertTrue(excludedBounds.right <= includedBounds.left)
            assertEquals(excludedBounds.top, includedBounds.top, 1f)
            fun available(id: String) = compose.onNodeWithTag("available-workouts")
                .performScrollToNode(hasTestTag("available-workout-$id"))
            available(back); compose.onNodeWithTag("available-workout-$back").performClick()
            available(strength); compose.onNodeWithTag("available-workout-$strength").performClick()
            compose.onNodeWithTag("include-selected-workouts").performClick()
            compose.onNodeWithTag("included-workouts").performScrollToNode(hasTestTag("included-workout-$back"))
            compose.onNodeWithTag("included-workout-$back").performClick()
            compose.onNodeWithTag("move-included-up").assertIsNotEnabled()
            compose.onNodeWithTag("move-included-down").assertIsEnabled().performClick()
            compose.onNodeWithTag("move-included-down").assertIsNotEnabled()
            compose.onNodeWithTag("move-included-up").assertIsEnabled()
            assertTrue(compose.onNodeWithTag("included-workout-$strength").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag("included-workout-$back").fetchSemanticsNode().boundsInRoot.top)
            compose.onNodeWithTag("move-included-up").performClick()
            assertTrue(compose.onNodeWithTag("included-workout-$back").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag("included-workout-$strength").fetchSemanticsNode().boundsInRoot.top)
            compose.onNodeWithTag("move-included-down").performClick()
            available(rehab); compose.onNodeWithTag("available-workout-$rehab").performClick()
            compose.onNodeWithTag("included-workout-$back").assertIsNotSelected()
            compose.onNodeWithTag("move-included-up").assertIsNotEnabled()
            compose.onNodeWithTag("move-included-down").assertIsNotEnabled()
            compose.onNodeWithTag("remove-selected-workout").assertIsNotEnabled()
            compose.onNodeWithTag("include-selected-workouts").assertIsEnabled()
            capture("training-excluded-selected")
            compose.onNodeWithTag("included-workout-$back").performClick()
            compose.onNodeWithTag("available-workout-$rehab").assertIsNotSelected()
            compose.onNodeWithTag("include-selected-workouts").assertIsNotEnabled()
            compose.onNodeWithTag("move-included-up").assertIsEnabled()
            compose.onNodeWithTag("remove-selected-workout").performClick()
            available(back); compose.onNodeWithTag("available-workout-$back").performClick()
            compose.onNodeWithTag("include-selected-workouts").performClick()
            available(abs); compose.onNodeWithTag("available-workout-$abs").performTouchInput { longClick() }
            capture("training-editor")
            compose.onNodeWithTag("save-training-plan").performClick()
            waitTag("workout-home")
            val plan = runBlocking { repo.trainingPlans.observeAll().first().single() }
            assertEquals(listOf(strength, back, abs), plan.workoutIds())
            assertEquals("Monday", plan.plan.dayOfWeek)
            assertEquals(4, runBlocking { repo.observeSessions().first().size })
            homeScroll(hasTestTag("training-plan-${plan.plan.id}"))
            capture("training-home")
            compose.onNodeWithTag("training-plan-${plan.plan.id}").performClick()
            waitTag("training-plan-detail")
            compose.onNodeWithTag("training-workout-$rehab").assertDoesNotExist()
            capture("training-detail")
            val recovery = runBlocking { repo.trainingPlans.save(null, "Recovery", listOf(rehab, strength)) }
            compose.onNodeWithTag("training-plan-workouts").performScrollToNode(hasTestTag("training-workout-$strength"))
            compose.onNodeWithTag("training-workout-$strength").performClick()
            waitTag("workout-metadata")
            fun editDetails() {
                compose.onNodeWithContentDescription("Workout options").performClick()
                compose.onNodeWithText("Edit details").performClick()
            }
            editDetails()
            compose.onNodeWithTag("workout-name").performTextReplacement("Biceps — Heavy")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("workout-training-plans").assertDoesNotExist()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("Biceps — Strength", runBlocking { db.workoutDao().getDetails(strength)!!.workout.name })
            assertEquals(2, runBlocking { db.trainingPlanDao().memberships(strength).size })
            editDetails()
            compose.onNodeWithTag("workout-name").performTextReplacement("Biceps — Heavy")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("workout-training-plans").assertDoesNotExist()
            compose.onNodeWithTag("save-workout-details").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("workout-details-editor").fetchSemanticsNodes().isEmpty() }
            assertEquals("Biceps — Heavy", runBlocking { db.workoutDao().getDetails(strength)!!.workout.name })
            assertEquals(2, runBlocking { db.trainingPlanDao().memberships(strength).size })
            assertTrue(runBlocking { db.trainingPlanDao().memberships(strength).any { it.trainingPlanId == recovery } })
            assertEquals("Biceps — Rehab", runBlocking { db.workoutDao().getDetails(rehab)!!.workout.name })
        } finally { db.close() }
    }

    @Test fun namedWorkoutDraftClearsAndSavesWithoutLosingTypedName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking { repo.addExercise("Curl", "Dumbbell") }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            waitTag("create-first-workout")
            capture("empty-workouts-tab")
            compose.onNodeWithTag("create-first-workout").performScrollTo().performClick()
            compose.onNodeWithTag("workout-name").performTextInput("Discard this name")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Clear"))
            compose.onNodeWithText("Clear").performClick()
            compose.onNodeWithText("Clear draft").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithText("Clear draft").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("workout-name"))
            compose.onNodeWithTag("workout-name").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            compose.onNodeWithTag("workout-name").performTextInput("Biceps — Rehab")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasText("Add Exercise"))
            compose.onNodeWithText("Add Exercise").performClick()
            val exercise = runBlocking { repo.observeExercises().first().single().id }
            compose.onNodeWithTag("exercise-picker-list").performScrollToNode(hasTestTag("select-exercise-$exercise"))
            compose.onNodeWithTag("select-exercise-$exercise").performClick()
            waitTag("workout-builder")
            compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
            compose.onNodeWithTag("save-workout").performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitTag("workout-home")
            val saved = runBlocking { repo.observeSessions().first().single() }
            assertEquals("Biceps — Rehab", saved.workout.name)
            assertEquals("plan", saved.workout.kind)
            homeScroll(hasTestTag("home-plan-${saved.workout.id}"))
            compose.onNodeWithText("Biceps — Rehab").assertIsDisplayed()
            capture("named-workout-home")
        } finally { db.close() }
    }

    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun homeScroll(matcher: SemanticsMatcher) { compose.onNodeWithTag("workout-home").performScrollToNode(matcher) }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "gym-plan-layout-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
