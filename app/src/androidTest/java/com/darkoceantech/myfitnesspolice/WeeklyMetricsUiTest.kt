package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardChartCard
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardScreen
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardUiState
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pure synthetic recorded-session input: never opens or modifies the user's workout database. */
class WeeklyMetricsUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 13)
    private val zone = ZoneId.systemDefault()
    private fun time(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val density: Float get() = InstrumentationRegistry.getInstrumentation()
        .targetContext.resources.displayMetrics.density

    @Before fun freezeRequestedOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
        android.os.SystemClock.sleep(500)
    }

    @After fun restoreRotation() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    private fun report(): DashboardReport {
        val sessions = (7 downTo 0).map { week ->
            val date = day.minusWeeks(week.toLong())
            val sets = listOf("row" to "Back", "press" to "Chest", "curl" to "Biceps").mapIndexed { index, (id, muscle) ->
                DashboardSet("$week-$id", id, "$muscle patrol", muscle, index, time(date) + index * 1000,
                    10, (140 - week * 3 - index * 20).toDouble(), false, 45_000, 60_000)
            }
            DashboardSession("session-$week", "Training patrol", time(date), time(date) + 600_000,
                "plan", null, sets)
        }
        // This completed session precedes the eight visible weeks and remains available for references.
        val oldDate = day.minusDays(180)
        val oldSet = DashboardSet("old-row", "row", "Back patrol", "Back", 0, time(oldDate),
            5, 200.0, false, 45_000, 60_000)
        val old = DashboardSession("older-session", "Earlier patrol", time(oldDate), time(oldDate) + 600_000,
            "plan", null, listOf(oldSet))
        val input = DashboardInput(sessions + old,
            listOf(DashboardPlan("plan", "Training patrol", null, time(oldDate), 3, 30)),
            listOf(DashboardSchedule("plan", day)))
        return DashboardMetricsCalculator().calculate(input, time(day) + 3_600_000, day, zone = zone)
    }

    private fun showCard(state: MutableState<DashboardChart>) {
        compose.setContent {
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(),
                    color = Color.Transparent, contentColor = PoliceColors.Text) {
                    // Exercise narrow phone headers in portrait, and the actual available width in landscape.
                    val width = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT)
                        Modifier.requiredWidth(360.dp) else Modifier
                    key(state.value.id) {
                        Column(width.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(16.dp).testTag("weekly-metrics-review-host")) {
                            DashboardChartCard(state.value, onInsights = {})
                        }
                    }
                }
            }
        }
    }

    @Test fun weeklyCardsKeepTextOnlyInsightsAtTopRightWithFittingHeadingsAndFullWidthPlots() {
        val charts = report().weekly
        val current = mutableStateOf(charts.first())
        showCard(current)
        charts.forEach { chart ->
            compose.runOnIdle { current.value = chart }
            val header = bounds("${chart.id}-header")
            val title = bounds("${chart.id}-title")
            val value = bounds("${chart.id}-value")
            val insights = bounds("${chart.id}-insights")
            assertTrue("${chart.id}: Insights belongs to the header's top right", insights.top <= title.top + 4 * density)
            assertEquals("${chart.id}: Insights stays on the padded right edge", header.right, insights.right, 1f)
            assertTrue("${chart.id}: title has its own space beside Insights", title.right <= insights.left + 1f)
            assertTrue("${chart.id}: subtitle has its own space beside Insights", value.right <= insights.left + 1f)
            assertTrue("${chart.id}: title and subtitle do not overlap", title.bottom <= value.top + 1f)
            textFits("${chart.id}-title")
            textFits("${chart.id}-value")
            compose.onNode(hasText("Insights") and hasAnyAncestor(hasTestTag("${chart.id}-insights")),
                useUnmergedTree = true).assertExists()
            compose.onAllNodes(hasText("i") and hasAnyAncestor(hasTestTag(chart.id)),
                useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodes(hasText(chart.caption) and hasAnyAncestor(hasTestTag(chart.id)),
                useUnmergedTree = true).assertCountEquals(0)
            screenshot("${chart.id}-header")

            val graph = compose.onNodeWithTag("${chart.id}-chart").performScrollTo()
            val card = bounds(chart.id)
            val graphBounds = graph.fetchSemanticsNode().boundsInRoot
            assertEquals("${chart.id}: canvas uses all available padded width", card.width - 32 * density,
                graphBounds.width, 2f)
            assertTrue("${chart.id}: graph remains within the card", graphBounds.left >= card.left && graphBounds.right <= card.right)
            if (chart.kind != ChartKind.DONUT) {
                val axis = bounds("${chart.id}-y-axis-label")
                val plot = bounds("${chart.id}-plot")
                assertEquals("${chart.id}: horizontal unit is centered over the y-axis", plot.left, axis.center.x, 2f)
                assertTrue("${chart.id}: axis label stays inside the graph width", axis.left >= graphBounds.left - 1 && axis.right <= graphBounds.right + 1)
                assertTrue("${chart.id}: axis label sits above the plot", axis.bottom <= plot.top + 1f)
                textFits("${chart.id}-y-axis-label", oneLine = true)
                chart.rightUnit?.let {
                    val rightAxis = bounds("${chart.id}-right-axis-label")
                    assertTrue("Secondary unit stays inside the card", rightAxis.left >= graphBounds.left && rightAxis.right <= graphBounds.right + 1)
                    textFits("${chart.id}-right-axis-label", oneLine = true)
                }
            }
            screenshot("${chart.id}-plot")
        }
    }

    @Test fun weeklyCarouselAndTextInsightsKeepTheExistingFullScreenNavigation() {
        val r = report()
        compose.setContent {
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(),
                    color = Color.Transparent, contentColor = PoliceColors.Text) {
                    DashboardScreen(DashboardUiState(r, false), Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("weekly-chart-carousel"))
        compose.onNodeWithTag("weekly-chart-carousel").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("weekly-muscles-tab").assertIsSelected()
        compose.onNodeWithTag("weekly-chart-carousel").performTouchInput { swipeRight() }
        compose.onNodeWithTag("weekly-volume-tab").assertIsSelected()
        r.weekly.forEach { chart ->
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("weekly-chart-tabs"))
            compose.onNodeWithTag("${chart.id}-tab").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("${chart.id}-tab").assertIsSelected()
            compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("${chart.id}-insights"))
            compose.onNodeWithTag("${chart.id}-insights").assertIsDisplayed().performClick()
            val modal = compose.onNodeWithTag("dashboard-insights-dialog").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val config = InstrumentationRegistry.getInstrumentation().targetContext.resources
            assertTrue("Insights remains a full-screen page", modal.width >= config.displayMetrics.widthPixels * .9f)
            compose.onNode(hasText(chart.title) and hasAnyAncestor(hasTestTag("dashboard-insights-dialog"))).assertIsDisplayed()
            compose.onNodeWithTag("dashboard-insights-content").performScrollToNode(hasText("Calculation"))
            compose.onNodeWithText("Calculation").assertIsDisplayed()
            screenshot("${chart.id}-insights")
            compose.onNodeWithTag("close-dashboard-insights").performClick()
            compose.onNodeWithTag("dashboard-insights-dialog").assertDoesNotExist()
        }
    }

    @Test fun dailyAndMonthlyCardsRetainTheirHelperCaptionsAndCircledInfo() {
        val r = report()
        val charts = r.daily + r.monthly
        val current = mutableStateOf(charts.first())
        showCard(current)
        charts.forEach { chart ->
            compose.runOnIdle { current.value = chart }
            compose.onNode(hasText(chart.caption) and hasAnyAncestor(hasTestTag(chart.id)),
                useUnmergedTree = true).assertExists()
            compose.onNode(hasText("i") and hasAnyAncestor(hasTestTag("${chart.id}-insights")),
                useUnmergedTree = true).assertExists()
            compose.onNode(hasText("Insights") and hasAnyAncestor(hasTestTag("${chart.id}-insights")),
                useUnmergedTree = true).assertExists()
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun textFits(tag: String, oneLine: Boolean = false) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals("$tag needs measurable text", 1, results.size)
        assertFalse("$tag must not clip or overflow", results.single().hasVisualOverflow)
        if (oneLine) assertEquals("$tag is horizontal on one line", 1, results.single().lineCount)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = if (instrumentation.targetContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            "landscape" else "portrait"
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it, orientation) }
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "weekly-metrics-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            java.io.File(folder, "$orientation-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
