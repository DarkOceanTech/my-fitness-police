package com.darkoceantech.myfitnesspolice

import androidx.compose.foundation.layout.*
import android.app.UiAutomation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.runBlocking
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardScreen
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardUiState
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.time.*

class DashboardChartsTest {
    @get:Rule val compose = createComposeRule()
    @Before fun freezeRequestedOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
        android.os.SystemClock.sleep(500)
    }
    @After fun restoreRotation() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
    private val day = LocalDate.of(2026, 9, 30)
    private val zone = ZoneId.systemDefault()
    private fun time(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val calculator = DashboardMetricsCalculator()
    private fun input(): DashboardInput {
        val sessions = (7 downTo 0).map { week ->
            val d = day.minusWeeks(week.toLong())
            val rows = listOf(5, 10, 15).mapIndexed { i, reps -> DashboardSet("$week-$i", "row", "Cable row", "Back",
                i, time(d) + i * 1000, reps, (100 - week * 4).toDouble(), false, 30_000 + i * 5000L, 60_000 + i * 10000L) }
            DashboardSession("session-$week", "Back patrol", time(d), time(d) + 600_000, "plan", null, rows)
        }
        return DashboardInput(sessions, listOf(DashboardPlan("plan", "Back patrol", 3, time(day.minusMonths(3)), 3, 30)),
            (7 downTo 0).map { DashboardSchedule("plan", day.minusWeeks(it.toLong())) })
    }
    private fun report(input: DashboardInput = input(), selected: LocalDate = day) = calculator.calculate(input, time(day) + 3600_000, selected, zone = zone)
    private fun show(state: androidx.compose.runtime.MutableState<DashboardUiState>, onDate: (LocalDate) -> Unit = {},
        onPlan: (String) -> Unit = {}, onLog: (String) -> Unit = {}) {
        compose.setContent { MyFitnessPoliceTheme {
            Surface(Modifier.fillMaxSize().policeBackdrop().windowInsetsPadding(WindowInsets.safeDrawing), color = Color.Transparent, contentColor = PoliceColors.Text) {
                DashboardScreen(state.value, Modifier.fillMaxSize(), onDaySelected = onDate, onPlan = onPlan, onLog = onLog)
            }
        } }
    }

    @Test fun fiveDayCalendarIsBoundedAndDaySelectionOpensCorrectLaunchItems() {
        val state = mutableStateOf(DashboardUiState(report(), false))
        var plan: String? = null; var log: String? = null
        show(state, onDate = { state.value = DashboardUiState(report(selected = it), false) },
            onPlan = { plan = it }, onLog = { log = it })
        compose.onNodeWithTag("calendar-$day").assertIsSelected()
        val strip = compose.onNodeWithTag("launchpad-calendar").fetchSemanticsNode().boundsInRoot
        val width = compose.onNodeWithTag("calendar-$day").fetchSemanticsNode().boundsInRoot.width
        // Five equal pills plus four equal gaps fill the strip.
        assertTrue(width > strip.width / 6 && width < strip.width / 5)
        screenshot("launchpad")
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-plan"))
        compose.onNodeWithTag("launch-plan").performClick()
        compose.runOnIdle { assertEquals("plan", plan) }
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-session-0"))
        compose.onNodeWithTag("launch-session-0").performClick()
        compose.runOnIdle { assertEquals("session-0", log) }
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-calendar"))
        compose.onNodeWithTag("launchpad-calendar").performScrollToIndex(0)
        compose.onNodeWithTag("calendar-${day.minusDays(10)}").performClick().assertIsSelected()
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-empty"))
        compose.onNodeWithTag("launchpad-empty").assertIsDisplayed()
        compose.onNodeWithTag("calendar-${day.minusDays(11)}").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-calendar"))
        compose.onNodeWithTag("launchpad-calendar").performScrollToIndex(20)
        compose.onNodeWithTag("calendar-${day.plusDays(10)}").performClick().assertIsSelected()
        compose.onNodeWithTag("calendar-${day.plusDays(11)}").assertDoesNotExist()
        screenshot("calendar-future-empty")
    }

    @Test fun allTwelveChartsHaveFullScreenScienceSpecsAndReadableData() {
        val r = report()
        val state = mutableStateOf(DashboardUiState(r, false))
        show(state)
        val charts = r.weekly + r.daily + r.monthly
        for (chart in charts) {
            selectChart(chart.id)
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("${chart.id}-chart"))
            compose.onNodeWithTag("${chart.id}-chart").assertIsDisplayed()
            screenshot(chart.id)
            if (chart.kind !in listOf(ChartKind.DONUT, ChartKind.GAUGE, ChartKind.RADAR)) {
                compose.onNodeWithTag("${chart.id}-chart").performTouchInput { click(center) }
                compose.onNodeWithTag("${chart.id}-point").assertExists()
            }
            // Align the carousel's header with the viewport. Finding a descendant of an
            // already visible, oversized lazy item does not scroll that child into view.
            val sectionIndex = listOf("weekly", "daily", "monthly").indexOf(chart.id.substringBefore('-'))
            compose.onNodeWithTag("dashboard-home").performScrollToIndex(3 + r.launches.size.coerceAtLeast(1) + sectionIndex * 2)
            compose.onNodeWithTag("${chart.id}-insights").assertIsDisplayed().performClick()
            compose.onNodeWithTag("dashboard-insights-dialog").assertIsDisplayed()
            compose.onNodeWithText("Calculation").assertIsDisplayed()
            compose.onNodeWithTag("dashboard-insights-content").performScrollToNode(hasText("Science baseline"))
            compose.onNodeWithText("Science baseline").assertIsDisplayed()
            if (chart.id == "weekly-volume") screenshot("science-baseline")
            compose.onNodeWithTag("dashboard-insights-content").performScrollToNode(hasText("Chart data ·", substring = true))
            compose.onNodeWithText("Chart data ·", substring = true).assertIsDisplayed()
            compose.onNodeWithTag("close-dashboard-insights").performClick()
            compose.onNodeWithTag("dashboard-insights-dialog").assertDoesNotExist()
        }
    }

    @Test fun changedRecordedDataRefreshesChartsAndUnknownBaselinesStayVisible() {
        val rows = listOf(DashboardSet("one", "row", "Cable row", "Back", 0, time(day), 10, 100.0, false, 30_000, 60_000))
        val original = DashboardInput(listOf(DashboardSession("s", "Back", time(day), time(day) + 600_000, null, null, rows)), emptyList())
        val state = mutableStateOf(DashboardUiState(report(original), false))
        show(state)
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("weekly-volume-value"))
        compose.onNodeWithTag("weekly-volume-value").assertTextEquals("1,000 Lbs-reps")
        compose.runOnIdle { state.value = DashboardUiState(report(original.copy(sessions = original.sessions.map {
            it.copy(sets = it.sets.map { set -> set.copy(reps = 11) }) })), false) }
        compose.onNodeWithTag("weekly-volume-value").assertTextEquals("1,100 Lbs-reps")
        selectChart("weekly-intensity")
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("weekly-intensity-chart"))
        compose.onNodeWithTag("weekly-intensity-chart").assertContentDescriptionContains("Unknown baseline: Sets 1", substring = true)
        compose.runOnIdle { state.value = DashboardUiState(report(DashboardInput(emptyList(), emptyList())), false) }
        compose.onNodeWithTag("weekly-intensity-empty").assertExists()
        compose.onNodeWithTag("weekly-intensity-chart").assertDoesNotExist()
        screenshot("empty-metrics")
    }

    @Test fun launchpadOpensSavedPlanAndRecordedLogThroughMainNavigation() {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        runBlocking {
            db.exerciseDao().insert(Exercise("e", "Cable row", "Cable", primaryMuscles = "Latissimus dorsi"))
            db.trainingPlanDao().insert(TrainingPlan("p", "Back patrol", now - 7 * 86_400_000L, trainingWeekdays[today.dayOfWeek.value - 1]))
            db.trainingPlanDao().insertExerciseMembers(listOf(TrainingPlanExercise("p", "e", 0)))
            db.trainingScheduleDao().insert(listOf(TrainingSchedule("p", today.toString())))
            db.workoutDao().insert(Workout("s", startedAt = now - 5000, finishedAt = now - 1000, sourceTrainingPlanId = "p", trainingPlan = "Back patrol"))
            db.workoutExerciseDao().insert(WorkoutExercise("se", "s", "e", 0))
            db.workoutSetDao().insert(WorkoutSet("ss", "se", 0, 10, 10000, completedAt = now - 2000, actualReps = 9))
        }
        try {
            compose.setContent { MyFitnessPoliceApp(FitnessRepository(db)) }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-launchpad").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-p"))
            compose.onNodeWithTag("launch-p").performClick()
            compose.onNodeWithTag("training-plan-detail").assertIsDisplayed()
            compose.onNodeWithText("Back patrol").assertIsDisplayed()
            compose.onNodeWithTag("schedule-training").assertDoesNotExist()
            compose.onNode(hasText("Academy") and hasClickAction()).assertIsSelected()
            compose.onNodeWithContentDescription("Back to Launchpad").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-launchpad").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-s"))
            compose.onNodeWithTag("launch-s").performClick()
            compose.onNodeWithTag("history-detail").assertIsDisplayed()
            compose.onNode(hasText("Reports") and hasClickAction()).assertIsSelected()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-launchpad").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-s"))
            compose.onNodeWithTag("launch-s").performClick()
            compose.onNodeWithTag("history-detail").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to Launchpad").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-launchpad").fetchSemanticsNodes().isNotEmpty() }
        } finally { db.close() }
    }

    @Test fun carouselSupportsSwipesAndFourTextButtonsPerSection() {
        show(mutableStateOf(DashboardUiState(report(), false)))
        for (section in listOf("weekly", "daily", "monthly")) {
            val charts = when (section) { "weekly" -> report().weekly; "daily" -> report().daily; else -> report().monthly }
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("$section-chart-carousel"))
            compose.onNodeWithTag("${charts.first().id}-tab").assertIsSelected()
            compose.onNodeWithTag("$section-chart-carousel").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("${charts[1].id}-tab").assertIsSelected()
            compose.onNodeWithTag("$section-chart-carousel").performTouchInput { swipeRight() }
            compose.onNodeWithTag("${charts.first().id}-tab").assertIsSelected()
            selectChart(charts.last().id)
            compose.onNodeWithTag("${charts.last().id}-tab").assertIsSelected()
        }
    }

    private fun selectChart(id: String) {
        val section = id.substringBefore('-')
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("$section-chart-tabs"))
        compose.onNodeWithTag("$id-tab").performClick()
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val orientation = if (instrumentation.targetContext.resources.configuration.orientation == 2) "landscape" else "portrait"
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals("Actual screen orientation", it, orientation) }
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "launchpad-schedule-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$orientation-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
