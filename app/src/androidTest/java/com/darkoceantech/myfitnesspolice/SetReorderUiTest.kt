package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.screens.ReorderableSetTable
import com.darkoceantech.myfitnesspolice.ui.screens.SessionAction
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SetReorderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggingNearViewportEdgeScrollsLongSetTableAndCommitsOnlyOnDrop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalSets = (0 until 20).map { index -> WorkoutSet(id = "long-set-$index", workoutExerciseId = "long-entry",
            position = index, reps = index + 5, weightGrams = (index + 1) * 1000L) }
        var entry by mutableStateOf(ExerciseWithSets(WorkoutExercise(id = "long-entry", workoutId = "long-workout",
            exerciseId = "curl", position = 0), Exercise(id = "curl", name = "Curl", equipment = "Dumbbell"), originalSets))
        var callbacks = 0
        lateinit var listState: LazyListState
        try {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
            compose.setContent {
                MyFitnessPoliceTheme {
                    var viewport by remember { mutableStateOf(Rect.Zero) }
                    listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().background(PoliceColors.Background).safeDrawingPadding().testTag("reorder-test-scroll")
                        .onGloballyPositioned { viewport = Rect(it.positionInWindow(), it.size.toSize()) },
                        state = listState, contentPadding = PaddingValues(12.dp)) {
                        item {
                            ReorderableSetTable(entry, enabled = true, action = SessionAction(), parentListState = listState,
                                parentViewport = viewport, onDragState = {}, onDeleteSet = {}, onChange = { _, _, _ -> },
                                onReorder = { ids ->
                                    callbacks++
                                    entry = entry.copy(sets = ids.mapIndexed { position, id ->
                                        originalSets.single { it.id == id }.copy(position = position)
                                    })
                                })
                        }
                    }
                }
            }
            val viewport = compose.onNodeWithTag("reorder-test-scroll").fetchSemanticsNode().boundsInRoot
            // The new handle must start the existing long-press drag gesture.
            val source = compose.onNodeWithTag("set-drag-handle-long-set-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("reorder-test-scroll").performTouchInput { down(source - viewport.topLeft) }
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("reorder-test-scroll").performTouchInput {
                advanceEventTime(700)
                moveTo(Offset(source.x - viewport.left, viewport.height - 8f), delayMillis = 300)
            }
            compose.mainClock.advanceTimeBy(2500)
            compose.runOnIdle {
                assertTrue("Dragging at the lower edge should scroll hidden sets into view", listState.firstVisibleItemScrollOffset > 0)
                assertEquals("The held preview must not commit", 0, callbacks)
                assertEquals(originalSets, entry.sets)
            }
            compose.onNodeWithTag("set-dragged-long-set-0").assertExists()
            compose.onNodeWithTag("set-insertion-long-entry").assertExists()
            capture("set-reorder-long-table-edge-scroll")
            compose.onNodeWithTag("reorder-test-scroll").performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(1, callbacks)
                assertEquals("long-set-0", entry.sets.last().id)
                assertReorderedOnly(originalSets, entry.sets, originalSets.drop(1).map { it.id } + "long-set-0")
            }
        } finally {
            compose.mainClock.autoAdvance = true
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test fun dragSetsPreservesDataAndExerciseOrderAndNeedsExplicitSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "set-reorder-${java.util.UUID.randomUUID()}.db"
        fun openDb() = Room.databaseBuilder(context, FitnessDatabase::class.java, databaseName).build()
        val db = openDb()
        seed(db)
        val editId = WorkoutEditorRepository.editId("saved-workout")
        fun details(id: String) = runBlocking { db.workoutDao().getDetails(id)!! }
        fun edited() = details(editId)
        fun curl() = edited().orderedExercises().first()
        val savedBefore = details("saved-workout")
        try {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            openWorkout()
            expand("Curl")
            val before = edited()
            val initialSets = curl().sets.sortedBy { it.position }
            val ids = initialSets.map { it.id }
            val curlEntryId = curl().workoutExercise.id
            val reorderedIds = listOf(ids[1], ids[2], ids[0])

            // Holding a row only previews the insertion; the database changes on release.
            beginDrag(ids[0], ids[2], sourceDescription = "Weight set 1")
            compose.onNodeWithTag("Weight set 1 options").assertDoesNotExist()
            compose.onNodeWithTag("set-dragged-${ids[0]}").assertExists()
            compose.onNodeWithTag("set-insertion-$curlEntryId").assertExists()
            assertEquals(before, edited())
            capture("set-reorder-held-red-outline-blue-insertion")
            finishDrag(cancelled = false)
            compose.waitUntil(5000) { curl().sets.sortedBy { it.position }.map { it.id } == reorderedIds }
            assertReorderedOnly(initialSets, curl().sets, reorderedIds)
            assertEquals(before.orderedExercises().map { it.workoutExercise }, edited().orderedExercises().map { it.workoutExercise })
            assertEquals(before.orderedExercises()[1], edited().orderedExercises()[1])
            assertEquals(savedBefore, details("saved-workout"))
            capture("set-reorder-dropped")

            // Cancelling the next upward gesture restores the preview without another write.
            val afterDown = edited()
            beginDrag(ids[0], ids[1])
            compose.onNodeWithTag("set-dragged-${ids[0]}").assertExists()
            finishDrag(cancelled = true)
            compose.onNodeWithTag("set-dragged-${ids[0]}").assertDoesNotExist()
            assertEquals(afterDown, edited())

            // Ordinary taps still open the existing number pickers after a drag.
            pick("Weight set 1", "20")
            pick("Reps set 1", "12")
            assertEquals(afterDown, edited())
            compose.onNodeWithContentDescription("Reps set 1").assertTextEquals("12")
            compose.onNodeWithContentDescription("Reps set 3").assertTextEquals("11")
            saveButton().assertIsEnabled().performClick()
            compose.onNodeWithTag("confirm-save-workout").performClick()
            waitTag("plan-saved")
            val savedAfter = details("saved-workout")
            assertEquals(listOf("curl", "row"), savedAfter.orderedExercises().map { it.exercise.id })
            assertEquals(payload(curl()), payload(savedAfter.orderedExercises().first()))
            assertEquals(payload(savedBefore.orderedExercises()[1]), payload(savedAfter.orderedExercises()[1]))
            saveButton().assertIsNotEnabled()
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            waitTag("home-plan-saved-workout")

            // Reopening uses the persisted order; a subsequent upward change can be discarded.
            openWorkout()
            expand("Curl")
            val reopenedDraft = edited()
            assertEquals(listOf(12, 13, 11), curl().sets.sortedBy { it.position }.map { it.reps })
            val reopenedSets = curl().sets.sortedBy { it.position }
            val upwardIds = listOf(reopenedSets[2].id, reopenedSets[0].id, reopenedSets[1].id)
            beginDrag(reopenedSets[2].id, reopenedSets[0].id, sourceDescription = "Reps set 3")
            compose.onNodeWithTag("Reps set 3 options").assertDoesNotExist()
            finishDrag(cancelled = false)
            compose.waitUntil(5000) { curl().sets.sortedBy { it.position }.map { it.id } == upwardIds }
            assertReorderedOnly(reopenedSets, curl().sets, upwardIds)
            assertEquals(reopenedDraft.orderedExercises()[1], edited().orderedExercises()[1])
            compose.onNodeWithContentDescription("Back to workout home").performClick()
            compose.onNodeWithTag("discard-workout-changes").performClick()
            waitTag("home-plan-saved-workout")
            assertNull(runBlocking { db.workoutDao().getDetails(editId) })
            assertEquals(savedAfter, details("saved-workout"))

            // A one-set exercise has nothing to reorder, even with the same long-press gesture.
            openWorkout()
            expand("Row")
            val beforeSingle = edited()
            val onlySet = beforeSingle.orderedExercises()[1].sets.single()
            compose.onNodeWithTag("set-number-${onlySet.id}").performScrollTo()
            val viewport = compose.onNodeWithTag("workout-builder").fetchSemanticsNode().boundsInRoot
            val number = compose.onNodeWithTag("set-number-${onlySet.id}").fetchSemanticsNode().boundsInRoot.center
            compose.onNodeWithTag("workout-builder").performTouchInput {
                down(number - viewport.topLeft)
                advanceEventTime(700)
                moveBy(Offset(0f, -80f), delayMillis = 300)
                up()
            }
            compose.waitForIdle()
            assertEquals(beforeSingle, edited())
            compose.onNodeWithTag("set-dragged-${onlySet.id}").assertDoesNotExist()
            saveButton().assertIsNotEnabled()
            val reopened = openDb()
            try { assertEquals(savedAfter, runBlocking { reopened.workoutDao().getDetails("saved-workout")!! }) }
            finally { reopened.close() }
        } finally {
            compose.mainClock.autoAdvance = true
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun openWorkout() {
        waitTag("home-plan-saved-workout")
        compose.onNodeWithTag("home-plan-saved-workout").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Expand Curl").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun expand(exercise: String) = compose.onNodeWithContentDescription("Expand $exercise").performScrollTo().performClick()

    private fun beginDrag(sourceId: String, targetId: String, sourceDescription: String? = null) {
        compose.onNodeWithTag("set-row-$targetId").performScrollTo()
        compose.onNodeWithTag("set-number-$sourceId").performScrollTo()
        val viewport = compose.onNodeWithTag("workout-builder").fetchSemanticsNode().boundsInRoot
        val source = (if (sourceDescription == null) compose.onNodeWithTag("set-number-$sourceId")
            else compose.onNodeWithContentDescription(sourceDescription)).fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("set-row-$targetId").fetchSemanticsNode().boundsInRoot.center
        // Hold time and sibling animations advance in bounded steps while the pointer is down.
        // Automatic clock advancement could otherwise keep an edge-scroll loop running forever.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("workout-builder").performTouchInput { down(source - viewport.topLeft) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("workout-builder").performTouchInput {
            advanceEventTime(700)
            moveBy(Offset(0f, target.y - source.y), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(350)
    }

    private fun finishDrag(cancelled: Boolean) {
        compose.onNodeWithTag("workout-builder").performTouchInput { if (cancelled) cancel() else up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun pick(description: String, value: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose.onNodeWithTag("$description options").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasClickAction() and hasAnyAncestor(hasTestTag("$description options"))).performClick()
    }

    private fun saveButton(): SemanticsNodeInteraction {
        compose.onNodeWithTag("workout-builder").performScrollToNode(hasTestTag("save-workout"))
        return compose.onNodeWithTag("save-workout")
    }

    private fun assertReorderedOnly(before: List<WorkoutSet>, after: List<WorkoutSet>, order: List<String>) {
        assertEquals(order.mapIndexed { position, id -> before.single { it.id == id }.copy(position = position) }, after.sortedBy { it.position })
    }

    private fun payload(entry: ExerciseWithSets) = entry.sets.sortedBy { it.position }.map { it.copy(id = "", workoutExerciseId = "") }

    private fun seed(db: FitnessDatabase) = runBlocking {
        db.exerciseDao().insert(Exercise(id = "curl", name = "Curl", equipment = "Dumbbell"))
        db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Cable"))
        db.workoutDao().insert(Workout(id = "saved-workout", name = "Arm technique", kind = "plan", targetMuscles = "Biceps"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "curl-entry", workoutId = "saved-workout", exerciseId = "curl",
            position = 0, notes = "Keep elbows stable"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "row-entry", workoutId = "saved-workout", exerciseId = "row",
            position = 1, notes = "Do not change the second exercise"))
        listOf(4536L, 9072L, 13608L).forEachIndexed { position, weight ->
            db.workoutSetDao().insert(WorkoutSet(id = "curl-set-$position", workoutExerciseId = "curl-entry",
                position = position, reps = 11 + position, weightGrams = weight, isWarmup = position == 0,
                modifier = listOf("none", "superset", "drop_set")[position], notes = "Set note $position"))
        }
        db.workoutSetDao().insert(WorkoutSet(id = "row-set", workoutExerciseId = "row-entry", position = 0,
            reps = 8, weightGrams = 20000, notes = "Preserve this set"))
    }

    private fun waitTag(tag: String) { compose.waitUntil(8000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            assertTrue(bitmap.height > bitmap.width)
            val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "set-reorder-review").apply { mkdirs() }
            java.io.File(folder, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
