package com.darkoceantech.myfitnesspolice

import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assume.assumeTrue
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.EquipmentPositionEditor
import com.darkoceantech.myfitnesspolice.ui.screens.SessionAction
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EquipmentKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowPhoneKeepsFieldsAboveDockedKeyboardAndAddDoesNotOverlayThem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val entry = ExerciseWithSets(
            WorkoutExercise(workoutId = "plan", exerciseId = "row", position = 0, equipmentPositions = listOf(
                EquipmentPosition("Seat", "4"), EquipmentPosition("Back support", "2"), EquipmentPosition("Cable", "12"),
                EquipmentPosition("Bench angle", "30°"), EquipmentPosition("Rack bar rest", "8"))),
            Exercise(id = "row", name = "Seated independent-arm machine row", equipment = "Machine"), emptyList())
        var saved: List<EquipmentPosition>? = null
        lateinit var host: android.view.View
        try {
            compose.setContent { MyFitnessPoliceTheme {
                host = LocalView.current
                EquipmentPositionEditor(entry, SessionAction(), onDismiss = {}, onEdit = {}, onSave = { saved = it })
            } }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("equipment-position-editor-title").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("The title should fit at normal font size on a 360 dp phone", layouts.single().isLineEllipsized(0))
            fun scrollTo(tag: String) {
                compose.onNodeWithTag("equipment-position-list").performScrollToNode(hasTestTag(tag))
            }
            fun keyboardBounds(): Rect? = automation.windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }?.let { window -> Rect().also { window.getBoundsInScreen(it) } }
            fun verifyField(tag: String) {
                compose.waitForIdle()
                android.os.SystemClock.sleep(700)
                // Floating toolbars do not reserve keyboard space. They cannot validate a docked-IME layout.
                val imeHeight = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                assumeTrue("Enable a docked on-screen keyboard before running this device check", imeHeight > 200)
                compose.onNodeWithTag(tag).assertIsDisplayed()
                val field = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                val save = compose.onNodeWithTag("save-equipment-positions").fetchSemanticsNode().boundsInRoot
                val keyboard = keyboardBounds()!!
                assertTrue("Field must stay above Save and the docked keyboard: field=$field, save=$save, keyboard=$keyboard", field.bottom <= save.top && field.bottom <= keyboard.top)
                assertTrue("Save must stay above the keyboard", save.bottom <= keyboard.top)
                val density = instrumentation.targetContext.resources.displayMetrics.density
                assertTrue("The full text field must remain visible", field.height >= 50 * density)
                val add = compose.onAllNodesWithTag("add-equipment-position").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                if (add != null && add.height > 0) assertTrue("Add must not overlap a field", add.top >= field.bottom || add.bottom <= field.top)
            }
            scrollTo("position-value-4")
            compose.onNodeWithTag("position-value-4").performClick().performTextReplacement("9")
            verifyField("position-value-4")
            captureFilterReview(compose, "equipment-docked-keyboard")
            scrollTo("add-equipment-position")
            compose.onNodeWithTag("add-equipment-position").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("position-name-5").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("position-name-5").performClick().performTextInput("Safety bars")
            compose.onNodeWithTag("position-value-5").performClick().performTextInput("6")
            verifyField("position-value-5")
            captureFilterReview(compose, "new-position-docked-keyboard")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-equipment-positions").performClick()
            assertEquals(6, saved!!.size)
            assertEquals(EquipmentPosition("Safety bars", "6"), saved!!.last())
        } finally { automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags } }
    }
}
