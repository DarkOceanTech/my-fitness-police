package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

class TimeCorrectionUiTest {
    @get:Rule(order = 0) val orientation = object : ExternalResource() {
        override fun before() { InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0) }
        override fun after() { InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
    @get:Rule(order = 1) val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FitnessDatabase
    private lateinit var model: SessionViewModel
    private val store = ViewModelStore()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        db.exerciseDao().insert(Exercise("e", "Cable row", "Cable"))
        db.workoutDao().insert(Workout("w", startedAt = 1000, finishedAt = 301000))
        db.workoutExerciseDao().insert(WorkoutExercise("entry", "w", "e", 0))
        db.workoutSetDao().insert(WorkoutSet("a", "entry", 0, 10, 45000, completedAt = 31000, actualReps = 12, activeMillis = 30000, restMillis = 60000))
        db.workoutSetDao().insert(WorkoutSet("b", "entry", 1, 10, 45000, completedAt = 93000, actualReps = 8, activeMillis = 2000, restMillis = 60000, notes = "#TIME forgot to start"))
        model = SessionViewModel(FitnessRepository(db)); store.put("model", model)
    }
    @After fun close() { store.clear(); db.close() }
    private fun waitFor(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun reviewPreservesDraftCancellationThenSavesBothSidesAndResolvesReport() {
        var open by mutableStateOf(true)
        compose.setContent { MyFitnessPoliceTheme { if (open) TimeCorrectionDialog(model, "w", "b") { open = false } } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Next: review intervals").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Next: review intervals").performScrollTo().performClick()
        compose.onNodeWithText("Previous Rest", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Next: Work duration").performScrollTo().performClick()
        compose.onNodeWithTag("corrected-work-seconds").performScrollTo().performTextInput("42")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("review-time-transfer").performScrollTo().performClick()
        compose.onNodeWithText("Combined duration stays", substring = true).performScrollTo().assertExists()
        capture("review")
        compose.onNodeWithTag("cancel-time-correction").performClick()
        assertTrue(runBlocking { db.workoutDao().getDetails("w")!!.timeCorrections.isEmpty() })
        compose.runOnIdle { open = true }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Next: review intervals").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Next: review intervals").performScrollTo().performClick()
        compose.onNodeWithText("Previous Rest", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Next: Work duration").performScrollTo().performClick()
        compose.onNodeWithTag("corrected-work-seconds").performScrollTo().performTextInput("42")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("review-time-transfer").performScrollTo().performClick()
        compose.onNodeWithText("Back", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("corrected-work-seconds").assertTextContains("42")
        compose.onNodeWithTag("review-time-transfer").performScrollTo().performClick()
        compose.onNodeWithTag("save-time-correction").performScrollTo().performClick()
        compose.waitUntil(10000) { !open }
        val w = runBlocking { db.workoutDao().getDetails("w")!! }
        assertEquals(42000L, w.withEffectiveTimes().orderedSets()[1].activeMillis)
        assertEquals(20000L, w.withEffectiveTimes().orderedSets()[0].restMillis)
        assertEquals(2000L, w.orderedSets()[1].activeMillis)
    }
    @Test fun chevronsNotesTimingBadgeAndRepColorsAppearInCompletedSetTable() {
        val w = runBlocking { db.workoutDao().getDetails("w")!! }
        compose.setContent { MyFitnessPoliceTheme { Surface(color = Color.Transparent) {
            Column(Modifier.fillMaxSize().policeBackdrop().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ActiveExerciseCard(w.exercises.single(), w, null, true, onInfo = {}, timeModel = model)
            }
        } } }
        compose.onNodeWithTag("set-details-chevron-b", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("set-note-indicator-b", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("set-time-indicator-b").assertExists()
        fun color(value: String, expected: Color) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(value) and hasAnyAncestor(hasContentDescription("Actual reps ${if (value == "12") "a" else "b"}")), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(expected, layouts.single().layoutInput.style.color)
        }
        color("12", PoliceColors.Red); color("8", PoliceColors.LightBlue)
        compose.onNodeWithTag("set-action-b").performScrollTo()
        capture("set-table")
        compose.onNodeWithTag("set-time-indicator-b").performScrollTo().performClick()
        waitFor("time-correction-dialog")
        compose.onNodeWithText("Correct time").performScrollTo().assertExists()
    }
    @Test fun reportDismissesFlagWithoutDeletingNoteAndFiltersResolvedEntries() {
        compose.setContent { MyFitnessPoliceTheme { TimeReviewReport(model) {} } }
        waitFor("time-review-b")
        compose.onNodeWithTag("time-review-b").performClick()
        compose.onNodeWithText("Mark as accurate").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("time-correction-dialog").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("time-review-b").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Dismissed").performClick()
        waitFor("time-review-b")
        compose.onNodeWithText("#TIME forgot to start").assertExists()
        capture("report")
        val w = runBlocking { db.workoutDao().getDetails("w")!! }
        assertEquals("dismissed", w.timeReviewStatus(w.orderedSets()[1]))
        assertEquals("#TIME forgot to start", w.orderedSets()[1].notes)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val label = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        val dir = java.io.File(context.getExternalFilesDir(null), "time-review-checks").apply { mkdirs() }
        val image = (if (compose.onAllNodesWithTag("time-correction-dialog").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("time-correction-dialog") else if (compose.onAllNodesWithTag("time-review-report").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("time-review-report") else compose.onRoot()).captureToImage().asAndroidBitmap()
        try { java.io.File(dir, "$label-$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
