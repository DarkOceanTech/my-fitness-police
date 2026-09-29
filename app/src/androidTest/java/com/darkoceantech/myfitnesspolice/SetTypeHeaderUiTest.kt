package com.darkoceantech.myfitnesspolice

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.ui.screens.RecordedSetInfoPanel
import com.darkoceantech.myfitnesspolice.ui.screens.SessionAction
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SetTypeHeaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeSetTypeEditsInTheExistingHeading() = verifyHeading("active")
    @Test fun historySetTypeEditsInTheExistingHeading() = verifyHeading("history")

    private fun verifyHeading(prefix: String) {
        val recorded = mutableStateOf(WorkoutSet(id = "set", workoutExerciseId = "entry",
            position = 0, reps = 10, weightGrams = 20000, completedAt = 1L, actualReps = 8, rpe = 7))
        val action = mutableStateOf(SessionAction())
        compose.setContent {
            MyFitnessPoliceTheme {
                RecordedSetInfoPanel("Cable row", recorded.value, action.value,
                    onEdit = {}, onSave = { _, _, _, _, warmup ->
                        recorded.value = recorded.value.copy(isWarmup = warmup)
                        action.value = SessionAction(revision = action.value.revision + 1,
                            completedAction = "correct-$prefix-set")
                    }, onSaveNote = {}, totals = {}, tagPrefix = prefix,
                    correctionAction = "correct-$prefix-set")
            }
        }
        val tag = "$prefix-set-type"
        val originalTop = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(tag).assertHasNoClickAction()
        compose.onNodeWithTag("edit-$prefix-set").performClick()
        compose.onAllNodesWithTag(tag).assertCountEquals(1)
        val heading = compose.onNodeWithTag(tag).assertHasClickAction().fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithTag("cancel-$prefix-set").fetchSemanticsNode().boundsInRoot
        assertEquals(originalTop, heading.top, 2f)
        assertTrue("Set type stays beside the edit controls", heading.top < cancel.bottom && heading.bottom > cancel.top)
        compose.onNodeWithText("Set type", substring = false).assertDoesNotExist()
        choose(tag, "Warm-up")
        compose.onNodeWithTag("cancel-$prefix-set").performClick()
        compose.onNodeWithText("Working Set").assertIsDisplayed()
        assertFalse(recorded.value.isWarmup)
        compose.onNodeWithTag("edit-$prefix-set").performClick()
        choose(tag, "Warm-up")
        compose.onNodeWithTag("save-$prefix-set").performClick()
        compose.onNodeWithText("Warm-up Set").assertIsDisplayed()
        compose.onNodeWithTag(tag).assertHasNoClickAction()
        assertTrue(recorded.value.isWarmup)
    }

    private fun choose(tag: String, text: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag("$tag-options"))).performClick()
    }
}
