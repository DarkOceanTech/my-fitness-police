package com.example.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

internal fun openTrainingPlanFromWorkout(compose: ComposeContentTestRule, trainingId: String) {
    compose.onNodeWithContentDescription("Back to workout home").performClick()
    compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("workout-section-1"))
    compose.onNodeWithTag("workout-section-1").performClick()
    compose.onNodeWithTag("workout-home").performScrollToNode(hasTestTag("training-plan-$trainingId"))
    compose.onNodeWithTag("training-plan-$trainingId").performClick()
    compose.onNodeWithTag("start-training").performClick()
}
