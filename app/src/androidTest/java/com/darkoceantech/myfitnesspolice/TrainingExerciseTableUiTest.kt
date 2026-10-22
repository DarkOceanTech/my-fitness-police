package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.TrainingExerciseSetsDialog
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TrainingExerciseTableUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape"
    private val exercise = Exercise("row", "Seated cable row", "Cable machine", primaryMuscles = "Latissimus dorsi; Rhomboids")
    @Before fun orient() {
        instrument.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
    }
    @After fun restoreOrientation() { instrument.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }

    @Test fun backPromptsForSetsAndNotesAndSupportsAllThreeChoices() {
        var visible by mutableStateOf(true)
        var savedNote: String? = null
        var savedSets: List<TrainingPlanSet>? = null
        compose.setContent { MyFitnessPoliceTheme {
            if (visible) TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id), "Back patrol", exercise,
                false, null, onDismiss = { visible = false }, onSave = {},
                onSaveDetails = { sets, _, note -> savedSets = sets; savedNote = note; visible = false })
        } }
        compose.onNodeWithContentDescription("Close Edit exercise sets").performClick()
        compose.onNodeWithText("Save your changes?").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        pick("Reps set 1", "12")
        compose.onNodeWithContentDescription("Close Edit exercise sets").performClick()
        compose.onNodeWithText("Save your changes?").assertIsDisplayed()
        compose.onNodeWithTag("continue-editing-training-exercise").performClick()
        field("Reps set 1").assertTextEquals("12")
        compose.waitForIdle()
        instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("discard-training-exercise-changes").performClick()
        compose.runOnIdle { assertNull(savedSets); visible = true }
        field("Reps set 1").assertTextEquals("10")
        compose.onNodeWithTag("training-exercise-note").performScrollTo().performClick()
        compose.onNodeWithTag("training-exercise-note-editor-input").performTextReplacement("Seat position four")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("training-exercise-note-editor-save").performClick()
        compose.onNodeWithContentDescription("Close Edit exercise sets").performClick()
        compose.onNodeWithText("Save your changes?").assertIsDisplayed()
        capture("unsaved-exercise-confirmation")
        compose.onNodeWithTag("save-training-exercise-and-exit").performClick()
        compose.runOnIdle {
            assertEquals("Seat position four", savedNote)
            assertEquals(listOf(TrainingPlanSet()), savedSets)
            assertFalse(visible)
        }
    }
    @Test fun fullscreenTableCancelAndRestorationKeepChangesLocalUntilSave() {
        val original = listOf(TrainingPlanSet(10, 560170, true, "superset"))
        var visible by mutableStateOf(true)
        val saved = mutableListOf<List<TrainingPlanSet>>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MyFitnessPoliceTheme {
            if (visible) TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id, original), "Back patrol", exercise,
                false, null, onDismiss = { visible = false }, onSave = { saved += it; visible = false })
        } }
        val dialog = compose.onNodeWithTag("training-exercise-sets").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(dialog.width >= instrument.targetContext.resources.displayMetrics.widthPixels * .95)
        compose.onNodeWithText("Edit exercise sets").assertIsDisplayed()
        compose.onNodeWithText("TRAINING PLAN DETAILS").assertExists()
        compose.onNodeWithText("Name").assertExists()
        compose.onNodeWithText("Back patrol").assertExists()
        compose.onNodeWithText("Target muscles").assertExists()
        compose.onNodeWithText("Add Exercise").assertDoesNotExist()
        compose.onNodeWithText("Collapse all").assertDoesNotExist()
        compose.onNodeWithContentDescription("Exercise options for Seated cable row").assertExists()
        compose.onNodeWithContentDescription("Drag to reorder Seated cable row").assertDoesNotExist()
        compose.onNodeWithContentDescription("Collapse Seated cable row").assertDoesNotExist()
        compose.onNodeWithTag("save-training-exercise-sets").assertIsNotEnabled()
        capture("table-details")
        pick("Reps set 1", "12")
        compose.onNodeWithTag("cancel-training-exercise-sets").performClick()
        assertTrue(saved.isEmpty())
        compose.runOnIdle { visible = true }
        field("Reps set 1").assertTextEquals("10")
        pick("Reps set 1", "14")
        val beforeIds = rowIds()
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(beforeIds, rowIds())
        field("Reps set 1").assertTextEquals("14")
        field("Weight set 1").assertTextEquals("1,234.96")
        capture("table-prescriptions")
        compose.onNodeWithTag("save-training-exercise-sets").assertIsEnabled().performClick()
        assertEquals(listOf(original.single().copy(reps = 14)), saved.single())
    }

    @Test fun addCopyDeleteAndReorderKeepPrescriptionsTogetherAndRenumberRows() {
        val warmup = TrainingPlanSet(5, 4536, true, "none")
        val working = TrainingPlanSet(12, 560170, false, "drop_set")
        var result: List<TrainingPlanSet>? = null
        compose.setContent { MyFitnessPoliceTheme {
            TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id, listOf(warmup, working)), "Back patrol", exercise,
                false, null, onDismiss = {}, onSave = { result = it })
        } }
        compose.onNodeWithTag("copy-training-set").performScrollTo().performClick()
        compose.onNodeWithTag("add-training-set").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove set 4 of Seated cable row").performScrollTo().performClick()
        val firstRow = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Set 1"))
            .fetchSemanticsNode()
        compose.runOnIdle { assertTrue(firstRow.config[SemanticsActions.CustomActions].single { it.label == "Move set down" }.action()) }
        compose.waitForIdle()
        field("Weight set 1").assertTextEquals("1,234.96")
        field("Reps set 2").assertTextEquals("5")
        compose.onNodeWithContentDescription("Remove set 2 of Seated cable row").performScrollTo().performClick()
        compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasTestTag("training-direct-set-totals"))
        compose.onNodeWithTag("training-direct-set-totals").assertTextEquals("2 sets · 24 reps")
        field("Type set 2").assertTextEquals("Ws")
        capture("table-reordered")
        compose.onNodeWithTag("save-training-exercise-sets").performClick()
        assertEquals(listOf(working, working), result)
    }

    @Test fun sharedMenuViewsHistoryImportsLocallyAndConfirmsRemoval() {
        val entry = ExerciseWithSets(WorkoutExercise("old-entry", "old", exercise.id, 0), exercise,
            listOf(WorkoutSet("old-set", "old-entry", 0, 8, 4536, completedAt = 10, actualReps = 7, notes = "Do not import")))
        val history = WorkoutDetails(Workout("old", startedAt = 1, finishedAt = 20), listOf(entry))
        var removed = false
        var saved: List<TrainingPlanSet>? = null
        compose.setContent { MyFitnessPoliceTheme {
            TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id), "Back patrol", exercise,
                false, null, onDismiss = {}, onSave = { saved = it }, library = listOf(history), onRemove = { removed = true })
        } }
        fun menu() { compose.onNodeWithContentDescription("Exercise options for Seated cable row").performScrollTo().performClick() }
        menu()
        listOf("View exercise info", "Edit equipment setup", "View last session", "Remove").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("View last session").performClick()
        compose.onNodeWithTag("import-session").performClick()
        compose.onNodeWithTag("last-session-import-confirmation").assertExists()
        compose.onNodeWithTag("confirm-import-session").performClick()
        assertNull(saved)
        field("Reps set 1").assertTextEquals("8")
        compose.onNodeWithTag("save-training-exercise-sets").performClick()
        assertEquals(listOf(TrainingPlanSet(8, 4536)), saved)
        menu(); compose.onNodeWithText("Remove").performClick()
        assertFalse(removed)
        compose.onNodeWithTag("cancel-remove-exercise-training-direct-row").performClick()
        assertFalse(removed)
        menu(); compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithTag("confirm-remove-exercise-training-direct-row").performClick()
        assertTrue(removed)
    }
    @Test fun fullScreenNoteDraftCancelsAndSavesWithPlanExercise() {
        var visible by mutableStateOf(true)
        var saved: String? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MyFitnessPoliceTheme {
            if (visible) TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id, notes = "Original note"),
                "Back patrol", exercise, false, null, onDismiss = { visible = false }, onSave = {},
                onSaveDetails = { _, _, note -> saved = note })
        } }
        fun edit() { compose.onNodeWithTag("training-exercise-note").performScrollTo().performClick() }
        edit()
        val bounds = compose.onNodeWithTag("training-exercise-note-editor").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= instrument.targetContext.resources.displayMetrics.widthPixels * .95)
        compose.onNodeWithTag("training-exercise-note-editor-input").performTextReplacement("Discard this")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("training-exercise-note-editor-cancel").performClick()
        compose.onNodeWithTag("training-exercise-note").assertTextEquals("Original note")
        edit()
        compose.onNodeWithTag("training-exercise-note-editor-input").performTextReplacement("Seat 4; slow lowering")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("training-exercise-note-editor-input").assertTextContains("Seat 4; slow lowering")
        capture("exercise-note-fullscreen")
        compose.onNodeWithTag("training-exercise-note-editor-save").performClick()
        assertNull(saved)
        compose.onNodeWithTag("training-exercise-note").assertTextEquals("Seat 4; slow lowering")
        compose.onNodeWithTag("save-training-exercise-sets").performClick()
        assertEquals("Seat 4; slow lowering", saved)
    }
    private fun field(description: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasContentDescription(description))
        return compose.onNodeWithContentDescription(description).performScrollTo()
    }
    private fun pick(description: String, text: String) {
        field(description).performClick()
        compose.onNodeWithTag("$description options").performScrollToNode(hasText(text))
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag("$description options"))).performClick()
    }
    private fun rowIds() = compose.onAllNodes(SemanticsMatcher("Set rows") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("set-row-") == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    private fun capture(name: String) {
        compose.waitForIdle(); instrument.waitForIdleSync(); android.os.SystemClock.sleep(1000)
        val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "plan-editor-review").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot()
        try { java.io.File(directory, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
