package com.darkoceantech.myfitnesspolice

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeightKeypadUiTest {
    @get:Rule val compose = createComposeRule()

    private fun type(value: String) = value.forEach {
        compose.onNodeWithTag("weight-key-$it").performScrollTo().performClick()
    }

    @Test fun decimalBackspaceCancelAndSaveFromRecordedField() {
        var value by mutableStateOf("65")
        compose.setContent { MyFitnessPoliceTheme {
            RecordedNumberField("Weight (Lbs)", value, emptyList(), true, "weight", Modifier,
                weightInput = true) { value = it }
        } }
        compose.onNodeWithTag("weight").performClick()
        type("12.55")
        compose.onNodeWithTag("weight-key-⌫").performScrollTo().performClick()
        compose.onNodeWithTag("weight-keypad-value").assertTextEquals("12.5")
        compose.onNodeWithTag("weight-keypad-cancel").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("weight-keypad").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals("65", value) }
        compose.onNodeWithTag("weight").performClick()
        type("12.5")
        compose.onNodeWithTag("weight-keypad-save").performScrollTo().performClick()
        compose.waitUntil(5000) { value == "12.5" }
        compose.onNodeWithTag("weight").performClick()
        compose.onNodeWithTag("weight-keypad-value").assertTextEquals("12.5")
        repeat(4) { compose.onNodeWithTag("weight-key-⌫").performScrollTo().performClick() }
        compose.onNodeWithTag("weight-keypad-save").assertIsNotEnabled()
        type("0")
        compose.onNodeWithTag("weight-keypad-save").performScrollTo().performClick()
        compose.waitUntil(5000) { value == "0" }
    }

    @Test fun decimalWeightCommitsToPlanOnlyWhenEditorIsSaved() {
        val exercise = Exercise("keypad", "Curl", "Dumbbell")
        var saved: List<TrainingPlanSet>? = null
        compose.setContent { MyFitnessPoliceTheme {
            TrainingExerciseSetsDialog(TrainingPlanItem.ExerciseItem(exercise.id), "Arms", exercise,
                false, null, onDismiss = {}, onSave = { saved = it })
        } }
        compose.onNodeWithContentDescription("Weight set 1").performScrollTo().performClick()
        type("12.5")
        compose.onNodeWithTag("weight-keypad-save").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("weight-keypad").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Weight set 1").assertTextEquals("12.5")
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("save-training-exercise-sets").performClick()
        compose.runOnIdle { assertEquals(5670L, saved!!.single().weightGrams) }
    }
}
