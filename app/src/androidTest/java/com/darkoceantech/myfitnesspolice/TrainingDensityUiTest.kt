package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** UI fixtures only: never opens the user's database. Wide viewport is supplied by the emulator runner. */
class TrainingDensityUiTest {
    @Test fun intensityMatchesMuscleCardHeightAndShowsRangeLabels() {
        val muscle = DashboardChart("weekly-muscles", "Muscle Set Count", "", "", ChartKind.STACKED_BAR,
            (1..8).map { "10/$it" }, com.darkoceantech.myfitnesspolice.domain.muscles.MuscleLabels.canonical.map {
                ChartSeries(it, List(8) { 10.0 }) }, "sets")
        val intensity = DashboardChart("weekly-intensity", "Intensity Distribution", "", "", ChartKind.HORIZONTAL_PERCENT_BAR,
            (1..8).map { "10/0$it" }, listOf("Low", "Moderate", "High", "Unknown").map {
                ChartSeries(it, List(8) { 10.0 }) }, "sets", weeklySetTotals = List(8) { 40 })
        compose.setContent { MyFitnessPoliceTheme {
            Surface(Modifier.fillMaxSize().safeDrawingPadding().policeBackdrop(), color = Color.Transparent) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DashboardChartCard(muscle, onInsights = {})
                    HeightMatchedIntensityCard(intensity, muscle, onInsights = {})
                }
            }
        } }
        val muscleHeight = compose.onNodeWithTag("weekly-muscles").fetchSemanticsNode().size.height
        compose.onNodeWithTag("weekly-intensity").performScrollTo()
        assertEquals(muscleHeight, compose.onNodeWithTag("weekly-intensity").fetchSemanticsNode().size.height)
        listOf("Low <60%", "Mid 60-85%", "High >85%", "Unknown 1RM").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("weekly-intensity-week-3").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("weekly-intensity-legend-3").performScrollTo().assertIsDisplayed()
        capture("intensity-height-matched")
    }
    @get:Rule(order = 0) val orientation = object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private val work = listOf(0.0, 1.25, 2.5, 0.5, 1.0, 3.0, 2.25, 1.25)
    private val rest = listOf(0.0, 2.5, 0.0, 1.0, 2.0, 1.0, 0.5, 2.5)
    private fun density() = DashboardChart("weekly-density", "Training Density", "3.75 min", "",
        ChartKind.COMBO, (1..8).map { "10/$it" }, listOf(ChartSeries("Work", work), ChartSeries("Rest", rest),
            ChartSeries("Work / Rest", work.zip(rest) { a, b -> if (b > 0) a / b else null })),
        "min", "ratio", barSeriesCount = 2)

    private fun show(chart: DashboardChart, onInsights: () -> Unit = {}) {
        compose.setContent { MyFitnessPoliceTheme {
            Surface(Modifier.fillMaxSize().safeDrawingPadding().policeBackdrop(), color = Color.Transparent) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DashboardChartCard(chart, onInsights = onInsights)
                }
            }
        } }
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val wide = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "wide"
        assertEquals("Verify actual viewport orientation", wide, bounds.width > bounds.height)
    }

    private fun assertBaseline(id: String) {
        fun baseline(tag: String): Float {
            val node = compose.onNodeWithTag(tag)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("Header is a single line", 1, layout.lineCount)
            assertFalse("Header must not be truncated", layout.isLineEllipsized(0))
            // Clickable Text reports paragraph constraints differently from its final visual bounds.
            // Check title fit here; the link's complete rendering is checked in captured screenshots.
            if (tag.endsWith("-title")) assertFalse("Title must fit", layout.hasVisualOverflow)
            return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        assertEquals(baseline("$id-title"), baseline("$id-insights"), 1f)
    }

    @Test fun densitySelectionSummaryLegendAndInsightsStaySynchronized() {
        var insights = 0
        show(density()) { insights++ }
        capture("density-header")
        assertBaseline("weekly-density")
        compose.onNodeWithTag("weekly-density-insights").performClick()
        compose.runOnIdle { assertEquals(1, insights) }
        compose.onNodeWithTag("weekly-density-y-axis-label").assertTextEquals("Min")
        compose.onNodeWithTag("weekly-density-right-axis-label").assertTextEquals("Ratio")
        compose.onNodeWithTag("weekly-density-label-2").assertTextEquals("Work-Rest")
        val chartBounds = compose.onNodeWithTag("weekly-density-chart").fetchSemanticsNode().boundsInRoot
        assertEquals(chartBounds.left, compose.onNodeWithTag("weekly-density-y-axis-label").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals(chartBounds.right, compose.onNodeWithTag("weekly-density-right-axis-label").fetchSemanticsNode().boundsInRoot.right, 1f)
        for (week in listOf(7, 0, 2, 6, 1)) {
            compose.onNodeWithTag("weekly-density-week-$week").performScrollTo().performClick()
            compose.onNodeWithTag("weekly-density-week-$week").assertIsSelected()
            compose.onNodeWithTag("weekly-density-selected-week").assertTextEquals("Week of 10/0${week + 1}")
            compose.onNodeWithTag("weekly-density-summary-total").assertTextEquals(
                listOf("0 minutes of work", "4 minutes of work", "3 minutes of work", "2 minutes of work",
                    "3 minutes of work", "4 minutes of work", "3 minutes of work", "4 minutes of work")[week])
            compose.onNodeWithTag("weekly-density-legend-0").assertTextEquals(java.text.DecimalFormat("#,##0.###").format(work[week]))
            compose.onNodeWithTag("weekly-density-legend-1").assertTextEquals(java.text.DecimalFormat("#,##0.###").format(rest[week]))
            compose.onNodeWithTag("weekly-density-legend-2").assertTextEquals(
                if (rest[week] > 0) density().displayNumber(work[week] / rest[week], 2) else "—")
        }
        assertEquals("2 minutes of work", densitySummary(2.49))
        assertEquals("3 minutes of work", densitySummary(2.5))
        compose.onNodeWithTag("weekly-density-summary-total").performScrollTo()
        capture("density")
    }

    @Test fun densityStackColorsAndRatioRenderWithCorrectScale() {
        show(density())
        assertTrue(chartMaximum(density()) >= 4.0)
        val pixels = compose.onNodeWithTag("weekly-density-chart").captureToImage().toPixelMap()
        fun matches(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) < .01 &&
            kotlin.math.abs(a.green - b.green) < .01 && kotlin.math.abs(a.blue - b.blue) < .01
        listOf(PoliceColors.Blue, PoliceColors.LightBlue, PoliceColors.Red).forEachIndexed { index, color ->
            assertTrue("Expected rendered chart color $index", (0 until pixels.width step 2).any { x ->
                (0 until pixels.height step 2).any { y -> matches(color, pixels[x, y]) }
            })
            if (index < 3) {
                val marker = compose.onNodeWithTag("weekly-density-marker-$index").performScrollTo().captureToImage().toPixelMap()
                assertTrue(matches(color, marker[marker.width / 2, marker.height / 2]))
            }
        }
    }

    @Test fun intensityHeaderAlignmentAndInsightsNavigation() {
        var opened = false
        val zones = listOf("Low", "Moderate", "High", "Unknown")
        val counts = listOf(1.0, 2.0, 3.0, 4.0)
        show(DashboardChart("weekly-intensity", "Intensity Distribution", "", "", ChartKind.HORIZONTAL_PERCENT_BAR,
            (1..8).map { "10/0$it" }, zones.mapIndexed { i, name ->
                ChartSeries(name, (0..7).map { if (it == 0) 0.0 else counts[i] }) }, "sets",
            weeklySetTotals = (0..7).map { if (it == 0) 2 else 12 })) { opened = true }
        capture("intensity-header")
        assertBaseline("weekly-intensity")
        compose.onNodeWithTag("weekly-intensity-value").assertDoesNotExist()
        compose.onNodeWithTag("weekly-intensity-week-7").assertIsSelected()
        compose.onNodeWithTag("weekly-intensity-summary-total").assertTextEquals("12 total sets")
        compose.onNodeWithTag("weekly-intensity-week-7").assertContentDescriptionContains("Unknown: 4, 40 percent", substring = true)
        compose.onNodeWithTag("weekly-intensity-insights").performClick()
        compose.runOnIdle { assertTrue(opened) }
        for (week in listOf(0, 3, 7)) {
            compose.onNodeWithTag("weekly-intensity-week-$week").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("weekly-intensity-selected-week").assertTextEquals("Week of 10/0${week + 1}")
            compose.onNodeWithTag("weekly-intensity-summary-total").assertTextEquals(if (week == 0) "2 total sets" else "12 total sets")
            counts.forEachIndexed { index, count ->
                compose.onNodeWithTag("weekly-intensity-legend-$index").assertTextEquals(if (week == 0) "0" else count.toInt().toString())
            }
        }
        compose.onNodeWithTag("weekly-intensity-axis-label").assertTextEquals("Percentage of Total Volume")
        compose.onNodeWithTag("weekly-intensity-summary-total").performScrollTo()
        capture("intensity")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "density-review").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { java.io.File(folder, "$orientation-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
