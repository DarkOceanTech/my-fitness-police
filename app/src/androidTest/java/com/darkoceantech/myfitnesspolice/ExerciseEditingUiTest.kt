package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExerciseEditingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogEditorPrefillsCancelsValidatesAndUpdatesOneExerciseWhileAddStartsBlank() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedOrientation = InstrumentationRegistry.getArguments().getString("expectedOrientation")
        if (expectedOrientation != null) {
            assertTrue(instrumentation.uiAutomation.setRotation(if (expectedOrientation == "landscape")
                UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
            android.os.SystemClock.sleep(500)
            instrumentation.waitForIdleSync()
        }
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        val original = Exercise(id = "editable-catalog-exercise", name = "Dumbbell curl",
            equipment = "Dumbbell", description = "Flex the elbow with a steady upper arm.",
            primaryMuscles = "Biceps brachii", secondaryMuscles = "Brachialis")
        val updated = original.copy(name = "Seated cable row", equipment = "Cable machine; bench",
            description = "Pull the handle toward the torso while keeping the spine neutral.",
            primaryMuscles = "Latissimus dorsi; trapezius (middle part)",
            secondaryMuscles = "Biceps brachii; brachialis; posterior deltoid")
        runBlocking { db.exerciseDao().insert(original) }
        fun records() = runBlocking { db.exerciseDao().getAll() }
        fun scrollCatalog(matcher: SemanticsMatcher) = compose.onNodeWithTag("exercise-catalog").performScrollToNode(matcher)
        fun openEdit() {
            scrollCatalog(hasTestTag("exercise-options-${original.id}"))
            compose.onNodeWithTag("exercise-options-${original.id}").performClick()
            compose.onNodeWithTag("edit-exercise-${original.id}").performClick()
            compose.onNodeWithTag("exercise-editor-screen").assertIsDisplayed()
        }
        fun assertField(tag: String, expected: String) {
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
        }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repository) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("gym-section-exercises").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("catalog-count").fetchSemanticsNodes().isNotEmpty() }
            openEdit()
            assertField("exercise-name", original.name)
            assertField("exercise-description", original.description)
            assertField("exercise-equipment", original.equipment)
            assertField("exercise-primary", original.primaryMuscles)
            assertField("exercise-secondary", original.secondaryMuscles)
            val (screenWidth, screenHeight) = screenshot("edit-prefilled-fullscreen")
            expectedOrientation?.let { assertEquals("Run in the requested orientation", it == "landscape", screenWidth > screenHeight) }
            val formBounds = compose.onNodeWithTag("exercise-editor-screen").fetchSemanticsNode().boundsInRoot
            assertTrue("The exercise editor should use the full screen width", formBounds.width >= screenWidth * .90f)
            assertTrue("The exercise editor should use the full screen height", formBounds.height >= screenHeight * .90f)
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("Discard this change")
            screenshot("edit-cancel-keyboard")
            compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(original), records())

            openEdit()
            assertField("exercise-name", original.name)
            compose.onNodeWithTag("exercise-name").performScrollTo().performTextReplacement("")
            compose.onNodeWithTag("save-exercise").assertIsDisplayed().performClick()
            compose.onNodeWithText("Enter an exercise name.").assertIsDisplayed()
            assertEquals(listOf(original), records())
            listOf(
                "exercise-name" to updated.name,
                "exercise-description" to updated.description,
                "exercise-equipment" to updated.equipment,
                "exercise-primary" to updated.primaryMuscles,
                "exercise-secondary" to updated.secondaryMuscles,
            ).forEach { (tag, value) -> compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
            compose.onNodeWithTag("exercise-secondary").performScrollTo().performClick().assertIsFocused()
            compose.onNodeWithTag("save-exercise").assertIsDisplayed()
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            screenshot("edit-keyboard-footer")
            assertEquals(listOf(original), records())
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-exercise").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(updated), records())
            scrollCatalog(hasText(updated.name))
            compose.onNodeWithText(updated.name).assertIsDisplayed()
            scrollCatalog(hasTestTag("exercise-details-${updated.id}"))
            compose.onNodeWithTag("exercise-details-${updated.id}").performClick()
            scrollCatalog(hasText(updated.description))
            compose.onNodeWithText(updated.description).assertIsDisplayed()
            scrollCatalog(hasText(updated.secondaryMuscles))
            compose.onNodeWithText(updated.secondaryMuscles).assertIsDisplayed()
            screenshot("edit-saved-catalog")

            // The same full-screen form can add a new exercise without reusing the edit's field values.
            scrollCatalog(hasTestTag("add-catalog-exercise"))
            compose.onNodeWithTag("add-catalog-exercise").performClick()
            compose.onNodeWithTag("exercise-editor-screen").assertIsDisplayed()
            compose.onNodeWithText("Create New Exercise").assertIsDisplayed()
            listOf("exercise-name", "exercise-description", "exercise-equipment", "exercise-primary", "exercise-secondary")
                .forEach { assertField(it, "") }
            compose.onNodeWithContentDescription("Close exercise form").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("exercise-editor-screen").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(updated), records())
        } finally {
            db.close()
            if (expectedOrientation != null) instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun screenshot(name: String): Pair<Int, Int> {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "exercise-edit-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val orientation = if (bitmap.width > bitmap.height) "landscape" else "portrait"
        try {
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let {
                assertEquals("Keep the requested orientation throughout editing", it, orientation)
            }
            java.io.File(folder, "$name-$orientation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap.width to bitmap.height
        } finally { bitmap.recycle() }
    }
}
