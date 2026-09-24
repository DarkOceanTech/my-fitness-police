package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActiveWorkoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun carouselActualRepsPauseResumeAndPartialFinishKeepPlanIntact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        val planId = runBlocking {
            repo.addExercise("Dumbbell Row", "Dumbbells")
            repo.addExercise("Lat Pulldown", "Cable machine")
            val catalog = repo.observeExercises().first()
            repo.chooseExercise(catalog.single { it.name == "Dumbbell Row" }.id)
            val draft = repo.observeSessions().first().single()
            val first = draft.exercises.single()
            repo.updateSetFields(draft.workout.id, first.workoutExercise.id, first.sets.single().id, grams = 29484, warmup = true)
            repo.saveSet(draft.workout.id, first.workoutExercise.id, null, 10, 29484)
            repo.saveSet(draft.workout.id, first.workoutExercise.id, null, 10, 29484)
            repo.updateExerciseNote(draft.workout.id, first.workoutExercise.id, "Control the lowering phase on every rep.")
            repo.chooseExercise(catalog.single { it.name == "Lat Pulldown" }.id)
            repo.saveMetadata(draft.workout.id, "Back,Biceps", "Thursday", "Arm Focus")
            repo.savePlan(draft.workout.id)
            draft.workout.id
        }
        try {
            compose.setContent { MyFitnessPoliceApp(repo) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("home-plan-$planId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-plan-$planId").performScrollTo().performClick()
            val trainingId = runBlocking { repo.trainingPlans.observeAll().first().single().plan.id }
            openTrainingPlanFromWorkout(compose, trainingId)
            waitFor("ON DUTY")
            val session = runBlocking { repo.observeSessions().first().single { it.workout.kind == "session" } }
            val id = session.workout.id
            val entries = session.orderedExercises()
            val sets = session.orderedSets()
            assertEquals(sets[0].id, session.sessionState!!.currentSetId)
            assertEquals("ready", session.sessionState.phase)
            compose.onNodeWithTag("training-ready").assertIsDisplayed()
            compose.onNodeWithTag("duty-time").assertTextEquals("0:00")
            compose.onNodeWithTag("active-time").assertTextEquals("0:00")
            compose.onNodeWithTag("set-action-${sets[0].id}").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.sessionState!!.phase == "active" } }
            compose.onNodeWithText("In progress • saved automatically").assertDoesNotExist()
            compose.onNodeWithText("1 Wu").assertIsDisplayed()
            compose.onNodeWithContentDescription("Set type ${sets[0].id}").assertHasNoClickAction()
            compose.onNodeWithContentDescription("Session weight ${sets[0].id}").assertHasNoClickAction()
            compose.onNodeWithContentDescription("Session reps ${sets[0].id}").assertHasNoClickAction()
            compose.onNodeWithContentDescription("Actual reps ${sets[0].id}").assertHasNoClickAction()
            screenshot("active-first-set")
            compose.onNodeWithTag("active-exercise-pager").performTouchInput { swipeLeft() }
            compose.onNodeWithText("Lat Pulldown", substring = false).assertIsDisplayed()
            compose.onNodeWithTag("exercise-tab-${entries[1].workoutExercise.id}").assertIsSelected()
            compose.onNodeWithTag("exercise-tab-${entries[0].workoutExercise.id}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active exercise")).assertIsNotSelected()
            screenshot("viewing-next-exercise")
            runBlocking { assertEquals(sets[0].id, repo.observeWorkout(id).first()!!.sessionState!!.currentSetId) }
            compose.onNodeWithTag("exercise-tab-${entries[0].workoutExercise.id}").performClick()
            compose.onNodeWithTag("set-action-${sets[0].id}").performScrollTo().performClick()
            waitFor("Actual reps performed")
            compose.onNodeWithTag("actual-reps-input").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("10")))
            screenshot("actual-reps")
            compose.onNodeWithTag("actual-reps-input").performTextReplacement("8")
            compose.onNodeWithText("Save reps").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.orderedSets()[0].actualReps == 8 } }
            compose.onNodeWithTag("actual-${sets[0].id}").assert(hasAnyDescendant(hasText("8")))
            compose.onNodeWithContentDescription("Actual reps ${sets[0].id}").assertHasNoClickAction()
            compose.onNodeWithTag("session-set-${sets[0].id}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Recorded, read only"))
            screenshot("break-timer")
            compose.onNodeWithContentDescription("Info for set 1 of Dumbbell Row").performClick()
            compose.onNodeWithTag("duty-time").assertIsDisplayed()
            compose.onNodeWithTag("active-exercise-pager").assertDoesNotExist()
            compose.onNodeWithTag("active-set-actual").assertHasNoClickAction()
            compose.onNodeWithTag("active-set-weight").assertHasNoClickAction()
            compose.onNodeWithTag("active-set-info-header").assert(hasAnyAncestor(hasTestTag("active-workout-totals")))
                .assert(hasAnyAncestor(hasTestTag("active-set-info-scroll")))
            fun pick(field: String, value: String) {
                compose.onNodeWithTag("active-set-$field").performScrollTo().performClick()
                compose.onNodeWithTag("active-set-$field-options").performScrollToNode(hasText(value))
                compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(hasTestTag("active-set-$field-options"))).performClick()
            }
            compose.onNodeWithTag("edit-active-set").performScrollTo().performClick()
            pick("actual", "2")
            pick("rpe", "10")
            compose.onNodeWithTag("cancel-active-set").performScrollTo().performClick()
            runBlocking { assertEquals(8, repo.observeWorkout(id).first()!!.orderedSets()[0].actualReps) }
            compose.onNodeWithTag("active-set-rpe").assert(hasText("—") or hasAnyDescendant(hasText("—")))
            compose.onNodeWithTag("edit-active-set").performScrollTo().performClick()
            pick("actual", "9")
            pick("rpe", "8")
            compose.onNodeWithTag("save-active-set").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.orderedSets()[0].actualReps == 9 } }
            compose.onNodeWithTag("active-set-info-panel").assertExists()
            compose.onNodeWithTag("active-set-actual").assertHasNoClickAction()
            compose.onNodeWithTag("active-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("active-set-note-input").performTextInput("Discard this note")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking { assertEquals("", repo.observeWorkout(id).first()!!.orderedSets()[0].notes) }
            compose.onNodeWithTag("active-set-note").performScrollTo().performClick()
            compose.onNodeWithTag("active-set-note-input").performTextReplacement("Better control; corrected my rep count.")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-active-set-note").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.orderedSets()[0].notes.isNotEmpty() } }
            compose.onNodeWithTag("active-set-info-scroll").performTouchInput { swipeDown() }
            screenshot("set-info")
            compose.onNodeWithContentDescription("Back to exercise").performClick()
            compose.onNodeWithTag("set-action-${sets[1].id}").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.sessionState!!.currentSetId == sets[1].id } }
            compose.onNodeWithTag("pause-resume-workout").performScrollTo().performClick()
            waitFor("Workout paused")
            compose.onNodeWithTag("pause-reason-input").performTextReplacement("Phone call")
            compose.onNodeWithText("Save reason").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.sessionState!!.pauseReason == "Phone call" } }
            screenshot("paused-workout")
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("weekly-Reps-value") and hasText("9")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("weekly-Sets-value").assertTextEquals("1")
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithText("Resume workout").performScrollTo().performClick()
            waitFor("ON DUTY")
            compose.onNodeWithText("PAUSED · Phone call").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("pause-resume-workout").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { !repo.observeWorkout(id).first()!!.sessionState!!.isPaused } }
            compose.onNodeWithText("Finish workout").performScrollTo().performClick()
            compose.onNodeWithText("You can pause your workout if you need to come back to it later.").assertIsDisplayed()
            screenshot("finish-confirmation")
            compose.onNodeWithText("Finish", substring = false).performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("history-detail").fetchSemanticsNodes().isNotEmpty() }
            waitFor("Dumbbell Row")
            compose.onNodeWithText("Completed").assertDoesNotExist()
            compose.onNodeWithText("Delete workout").assertDoesNotExist()
            runBlocking {
                val result = repo.observeWorkout(id).first()!!
                assertNotNull(result.workout.finishedAt)
                assertEquals(1, result.performedSets().size)
                assertEquals(9, result.performedSets().single().actualReps)
                assertEquals(8, result.performedSets().single().rpe)
                assertEquals("Better control; corrected my rep count.", result.performedSets().single().notes)
                assertEquals(10, result.performedSets().single().reps)
                val plan = repo.observeWorkout(planId).first()!!
                assertTrue(plan.orderedSets().all { it.actualReps == null && it.completedAt == null && it.activeMillis == 0L })
            }
            compose.onNodeWithContentDescription("Info for set 1 of Dumbbell Row").performClick()
            compose.onNodeWithTag("edit-history-set").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-actual").performScrollTo().performClick()
            compose.onNodeWithTag("history-set-actual-options").performScrollToNode(hasText("8"))
            compose.onNode(hasText("8") and hasClickAction() and hasAnyAncestor(hasTestTag("history-set-actual-options"))).performClick()
            compose.onNodeWithTag("save-history-set").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeWorkout(id).first()!!.performedSets().single().actualReps == 8 } }
            compose.onNodeWithContentDescription("Close set info").performClick()
            compose.onNodeWithTag("actual-${sets[0].id}", useUnmergedTree = true).assert(hasAnyDescendant(hasText("8")))
            compose.onAllNodesWithText("Lat Pulldown", substring = false).assertCountEquals(0)
            screenshot("completed-history")
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("weekly-Reps-value") and hasText("8")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("weekly-Sets-value").assertTextEquals("1")
            compose.onNodeWithText("THIS WEEK · SAMPLE DATA").assertDoesNotExist()
            screenshot("real-weekly-progress")
            compose.onNodeWithTag("weekly-Weight-value").performScrollTo().assertTextEquals("520")
        } finally { db.close() }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(8000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "active-session-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
