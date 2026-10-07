package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MobilityTrackerUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun longPressDragReordersSetValuesWithoutChangingTheirIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        instrumentation.uiAutomation.setRotation(if (orientation == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
        val routine = StretchRoutine(name = "Drag check", stretches = listOf(RoutineStretch(
            movement = StretchMovement(name = "Shoulder circles"),
            sets = listOf(StretchSet("set-a", work = 20), StretchSet("set-b", work = 40)))))
        var saved: StretchRoutine? = null
        try {
            compose.setContent { MyFitnessPoliceTheme {
                StretchRoutineEditor(routine, emptyList(), StretchPreferences(), SessionAction(), false,
                    onCancel = {}, onSave = { saved = it }, onCreated = {}, onStart = {})
            } }
            compose.onNodeWithTag("stretch-builder-list").performScrollToNode(hasTestTag("stretch-drag-set-b"))
            compose.onNodeWithTag("stretch-drag-set-a").performTouchInput {
                down(center); advanceEventTime(700)
                moveBy(androidx.compose.ui.geometry.Offset(0f, 140f)); advanceEventTime(100); up()
            }
            compose.onNodeWithTag("save-stretch-routine").assertIsEnabled().performClick()
            assertEquals(listOf("set-b", "set-a"), saved!!.stretches.single().sets.map { it.id })
            assertEquals(listOf(40, 20), saved!!.stretches.single().sets.map { it.work })
        } finally { instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
    @Test fun catalogCreateViewEditAndCancelPersistOnlySavedChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        instrumentation.uiAutomation.setRotation(if (orientation == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
        try {
            compose.setContent { MyFitnessPoliceApp(repository) }
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithTag("pto-grid").performScrollToNode(hasTestTag("pto-mobility-tracker-tile"))
            compose.onNodeWithTag("pto-mobility-tracker-tile").performClick()
            waitTag("stretch-tracker")
            compose.onNodeWithTag("create-stretch").performClick()
            compose.onNodeWithTag("save-stretch").assertIsNotEnabled()
            compose.onNodeWithTag("stretch-name").performTextReplacement("Custom mobility")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-stretch").performClick()
            compose.waitUntil(8000) { runBlocking { repository.stretches.catalog.first().isNotEmpty() } }
            val saved = runBlocking { repository.stretches.catalog.first().single() }
            compose.onNodeWithTag("stretch-catalog").performScrollToNode(hasTestTag("stretch-movement-" + saved.id))
            compose.onAllNodesWithText("⋮").onLast().performClick()
            compose.onNodeWithText("View stretch info").performClick()
            waitTag("stretch-info")
            capture("stretch-info-$orientation")
            compose.onNodeWithText("Close").performClick()
            compose.onAllNodesWithText("⋮").onLast().performClick()
            compose.onNodeWithText("Edit stretch details").performClick()
            compose.onNodeWithTag("save-stretch").assertIsNotEnabled()
            compose.onNodeWithTag("stretch-name").performTextReplacement("Discard this")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(saved, runBlocking { repository.stretches.catalog.first().single() })
            compose.onAllNodesWithText("⋮").onLast().performClick()
            compose.onNodeWithText("Edit stretch details").performClick()
            compose.onNodeWithTag("stretch-name").performTextReplacement("Edited mobility")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-stretch").performClick()
            compose.waitUntil(8000) { runBlocking { repository.stretches.catalog.first().single().name == "Edited mobility" } }
            assertEquals(saved.id, runBlocking { repository.stretches.catalog.first().single().id })
            assertTrue(runBlocking { db.stretchDao().sessions().first().isEmpty() })
        } finally { db.close(); instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
    @Test fun routineBuilderSaveDiscardAndPausedSessionAreReachableAcrossTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, FitnessDatabase::class.java).build()
        val repository = FitnessRepository(db)
        val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        instrumentation.uiAutomation.setRotation(if (orientation == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        android.os.SystemClock.sleep(500)
        runBlocking {
            repository.stretches.saveMovement(StretchMovement("both", "Shoulder circles", muscles = "Deltoid", movementType = "Moving mobility"))
            repository.stretches.saveMovement(StretchMovement("sides", "Calf stretch", muscles = "Gastrocnemius", separateSides = true))
        }
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MyFitnessPoliceApp(repository) }
            compose.onNode(hasText("Field") and hasClickAction()).performClick()
            compose.onNodeWithTag("pto-grid").performScrollToNode(hasTestTag("pto-mobility-tracker-tile"))
            compose.onNodeWithTag("pto-mobility-tracker-tile").performClick()
            waitTag("stretch-tracker")
            compose.onNodeWithTag("stretch-tab-1").performClick()
            compose.onNodeWithTag("create-stretch-routine").performClick()
            waitTag("stretch-routine-editor")
            compose.onNodeWithTag("save-stretch-routine").assertIsNotEnabled()
            compose.onNodeWithTag("routine-name").performTextReplacement("Morning mobility")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("add-stretch").performClick()
            compose.onNodeWithTag("select-stretch-both").performScrollTo().performClick()
            compose.onNodeWithTag("select-stretch-sides").performScrollTo().performClick()
            compose.onNodeWithText("2 selected").performClick()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithTag("stretch-builder-list").performScrollToNode(hasContentDescription("Work set 1"))
            compose.onAllNodesWithContentDescription("Work set 1").onFirst().performClick()
            compose.onNodeWithTag("Work set 1 options").performScrollToNode(hasText("31 s"))
            compose.onNodeWithText("31 s").performClick()
            compose.onAllNodesWithText("Copy last set").onFirst().performScrollTo().performClick()
            compose.onAllNodesWithText("×").onFirst().performScrollTo().performClick()
            compose.onNodeWithTag("stretch-builder-list").performScrollToNode(hasContentDescription("Drag to reorder"))
            val actions = compose.onAllNodesWithContentDescription("Drag to reorder").onFirst().fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!
            compose.runOnIdle { assertTrue(actions.single { it.label == "Move down" }.action()) }
            capture("routine-editor-$orientation")
            compose.onNodeWithTag("save-stretch-routine").performClick()
            waitTag("stretch-routines")
            val saved = runBlocking { repository.stretches.routines.first().single() }
            assertEquals(listOf("sides", "both"), saved.stretches.map { it.movement.id })
            assertEquals(31, saved.stretches.last().sets.single().work)
            compose.onNodeWithTag("stretch-routines").performScrollToNode(hasTestTag("stretch-routine-" + saved.id))
            compose.onNodeWithTag("stretch-routines").performScrollToNode(hasTestTag("stretch-routine-" + saved.id))
            compose.onNodeWithTag("stretch-routine-" + saved.id).performClick()
            compose.onNodeWithTag("save-stretch-routine").assertIsNotEnabled()
            compose.onNodeWithTag("routine-name").performTextReplacement("Unsaved name")
            Espresso.closeSoftKeyboard()
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitText("Continue editing")
            compose.onNodeWithText("Continue editing").performClick()
            compose.waitForIdle()
            android.os.SystemClock.sleep(500)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitText("Exit without saving")
            compose.onNodeWithText("Exit without saving").performClick()
            compose.onNodeWithTag("stretch-routines").performScrollToNode(hasTestTag("stretch-routine-" + saved.id))
            assertEquals("Morning mobility", runBlocking { repository.stretches.routines.first().single().name })
            compose.onNodeWithTag("stretch-routines").performScrollToNode(hasTestTag("stretch-routine-" + saved.id))
            compose.onNodeWithTag("stretch-routine-" + saved.id).performClick()
            compose.onNodeWithTag("routine-name").performTextReplacement("Morning patrol")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("save-stretch-routine").performClick()
            compose.waitUntil(8000) { runBlocking { repository.stretches.routines.first().single().name == "Morning patrol" } }
            compose.onNodeWithTag("stretch-routine-editor").assertExists()
            compose.onNodeWithTag("start-stretch-routine").performClick()
            waitTag("active-stretch-session")
            compose.onNodeWithTag("cancel-stretch-preparation").performClick()
            compose.waitUntil(8000) { runBlocking { db.stretchDao().active() == null } }
            waitTag("stretch-tracker")
            compose.onNodeWithTag("stretch-tab-1").performClick()
            compose.onNodeWithTag("stretch-routines").performScrollToNode(hasTestTag("stretch-routine-" + saved.id))
            compose.onNodeWithTag("stretch-routine-" + saved.id).performClick()
            compose.onNodeWithTag("start-stretch-routine").performClick()
            waitTag("active-stretch-session")
            compose.waitUntil(10000) { compose.onAllNodesWithText("LEFT SIDE").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("active-stretch-content").performScrollToNode(hasTestTag("pause-stretch"))
            compose.onNodeWithTag("pause-stretch").performClick()
            compose.waitUntil(5000) { runBlocking { StretchJson.run(db.stretchDao().active()!!.progress).status == "paused" } }
            val paused = runBlocking { db.stretchDao().active()!! }
            compose.onNode(hasText("Dispatch") and hasClickAction()).performClick()
            compose.onNodeWithTag("mobility-session-shortcut").assertIsDisplayed().performClick()
            waitTag("active-stretch-session")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("PAUSED").assertExists()
            assertEquals(StretchJson.run(paused.progress).elapsed, runBlocking { StretchJson.run(db.stretchDao().active()!!.progress).elapsed })
            capture("active-paused-$orientation")
            compose.onNodeWithTag("active-stretch-content").performScrollToNode(hasTestTag("pause-stretch"))
            compose.onNodeWithTag("pause-stretch").performClick()
            compose.onNodeWithTag("active-stretch-content").performScrollToNode(hasTestTag("skip-stretch-phase"))
            compose.onNodeWithTag("skip-stretch-phase").performClick()
            compose.onNodeWithTag("active-stretch-content").performScrollToNode(hasTestTag("finish-stretch"))
            compose.onNodeWithTag("finish-stretch").performClick()
            compose.onNodeWithText("Session notes (optional)").performClick()
            Espresso.closeSoftKeyboard()
            compose.onAllNodesWithText("Finish routine").onLast().performClick()
            waitTag("stretch-session-details")
            capture("session-details-$orientation")
            val session = runBlocking { db.stretchDao().sessions().first().single() }
            assertEquals("finished early", StretchJson.run(session.progress).status)
            assertTrue(StretchJson.run(session.progress).results.any { it.skipped })
            assertTrue(runBlocking { db.workoutDao().observeAllDetails().first().isEmpty() })
        } finally { db.close(); instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun waitText(text: String) { compose.waitUntil(8000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "mobility-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}



