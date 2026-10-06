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
        compose.onNodeWithText("⋮").assertDoesNotExist()
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
        field("Weight set 1").assertTextEquals("1,235")
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
        field("Weight set 1").assertTextEquals("1,235")
        field("Reps set 2").assertTextEquals("5")
        compose.onNodeWithContentDescription("Remove set 2 of Seated cable row").performScrollTo().performClick()
        compose.onNodeWithTag("training-direct-sets-list").performScrollToNode(hasTestTag("training-direct-set-totals"))
        compose.onNodeWithTag("training-direct-set-totals").assertTextEquals("2 sets · 24 reps")
        field("Type set 2").assertTextEquals("Ws")
        capture("table-reordered")
        compose.onNodeWithTag("save-training-exercise-sets").performClick()
        assertEquals(listOf(working, working), result)
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
