package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.LastSessionDialog
import com.darkoceantech.myfitnesspolice.ui.screens.LastSessionState
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LastSessionLandscapeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapePreviewAndConfirmationKeepActionsVisibleAndRequireConfirmation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val imported = CopyOnWriteArrayList<String>()
        val closed = AtomicInteger()
        val snapshot = sampleSession()
        try {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            android.os.SystemClock.sleep(500)
            compose.setContent {
                MyFitnessPoliceTheme {
                    LastSessionDialog(LastSessionState(snapshot = snapshot), importing = false, importError = null,
                        onClose = { closed.incrementAndGet() }, onRetry = {}, onImport = { imported.add(it) })
                }
            }
            compose.onNodeWithTag("last-session-dialog").assertIsDisplayed()
            assertFullScreen()
            val closePosition = footerBounds("close-last-session")
            val importPosition = footerBounds("import-session")
            capture("last-session-landscape-preview")
            compose.onNodeWithTag("last-session-exercises")
                .performScrollToNode(hasTestTag("last-session-import-description"))
            assertSameBounds(closePosition, footerBounds("close-last-session"))
            assertSameBounds(importPosition, footerBounds("import-session"))
            capture("last-session-landscape-preview-scrolled")

            compose.onNodeWithTag("import-session").performClick()
            compose.onNodeWithTag("last-session-import-confirmation").assertIsDisplayed()
            compose.onNodeWithText("Replace current sets?").assertIsDisplayed()
            assertTrue("Opening confirmation must not import any data", imported.isEmpty())
            assertEquals(0, closed.get())
            val backPosition = footerBounds("back-from-import-confirmation")
            val confirmPosition = footerBounds("confirm-import-session")
            capture("last-session-landscape-confirmation")
            compose.onNodeWithTag("last-session-import-confirmation").performScrollToNode(
                hasText("Choose Back to keep your current sets, or Import session to replace them. Then use Save on the workout screen to keep your changes."))
            assertSameBounds(backPosition, footerBounds("back-from-import-confirmation"))
            assertSameBounds(confirmPosition, footerBounds("confirm-import-session"))
            assertFullScreen()
            capture("last-session-landscape-confirmation-scrolled")

            compose.onNodeWithTag("back-from-import-confirmation").performClick()
            compose.onNodeWithTag("last-session-import-confirmation").assertDoesNotExist()
            compose.onNodeWithTag("last-session-exercises").assertIsDisplayed()
            assertTrue("Back must preserve the current workout", imported.isEmpty())
            assertEquals(sampleSession(), snapshot)
            assertSameBounds(closePosition, footerBounds("close-last-session"))
            assertSameBounds(importPosition, footerBounds("import-session"))

            compose.onNodeWithTag("import-session").performClick()
            assertTrue(imported.isEmpty())
            compose.onNodeWithTag("confirm-import-session").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf("recorded-row"), imported.toList()) }
            compose.onNodeWithTag("close-last-session").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(1, closed.get())
                assertEquals(listOf("recorded-row"), imported.toList())
            }
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun footerBounds(tag: String): Rect = compose.onNodeWithTag(tag)
        .assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot

    private fun assertSameBounds(before: Rect, after: Rect) {
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.right, after.right, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
    }

    private fun assertFullScreen() {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            assertTrue("This regression requires landscape", bitmap.width > bitmap.height)
            val bounds = compose.onNodeWithTag("last-session-dialog").fetchSemanticsNode().boundsInRoot
            assertTrue("The modal should span the landscape width", bounds.width >= bitmap.width * .90f)
            assertTrue("The modal should span the landscape height", bounds.height >= bitmap.height * .85f)
        } finally { bitmap.recycle() }
    }

    private fun sampleSession(): ExerciseSessionSnapshot {
        val exercise = Exercise(id = "row", name = "Seated close-grip cable row", equipment = "Cable")
        val entry = ExerciseWithSets(WorkoutExercise(id = "recorded-row", workoutId = "recorded-session",
            exerciseId = "row", position = 0, notes = "Keep the torso stable and shoulders controlled."), exercise,
            (0 until 8).map { index -> WorkoutSet(id = "recorded-row-set-$index", workoutExerciseId = "recorded-row",
                position = index, reps = 12, weightGrams = 20000, actualReps = 10, rpe = 7,
                completedAt = 20_000L + index * 20_000L, activeMillis = 20_000, restMillis = 30_000,
                notes = "Recorded set note ${index + 1}") })
        val workout = WorkoutDetails(Workout(id = "recorded-session", name = "Back strength training",
            startedAt = 1_000, finishedAt = 200_000, trainingPlan = "Back strength training"), listOf(entry))
        return ExerciseSessionSnapshot(workout, entry)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Compose can be idle before the emulator compositor presents the entire frame.
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            assertTrue(bitmap.width > bitmap.height)
            val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "view-last-session-review").apply { mkdirs() }
            java.io.File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
