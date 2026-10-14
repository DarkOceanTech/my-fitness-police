package com.darkoceantech.myfitnesspolice

import com.darkoceantech.myfitnesspolice.domain.muscles.MuscleLabels
import com.darkoceantech.myfitnesspolice.ui.screens.muscleGroupColor
import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardChartCard
import com.darkoceantech.myfitnesspolice.ui.screens.chartMaximum
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** Isolated presentation fixtures; no workout database is opened. */
class WeeklyMuscleLegendTest {
    @Test fun legendMarkersMatchRenderedStackColors() {
        val names = listOf("Upper Back", "Trapezius", "Lower Back")
        show(chart(names).copy(series = names.map { ChartSeries(it, List(8) { 10.0 }) }))
        val pixels = compose.onNodeWithTag("weekly-muscles-chart").captureToImage().toPixelMap()
        names.forEachIndexed { index, name ->
            val expected = muscleGroupColor(name)
            val marker = compose.onNodeWithTag("muscle-legend-marker-$index").performScrollTo().captureToImage().toPixelMap()
            fun matches(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) < .01f &&
                kotlin.math.abs(a.green - b.green) < .01f && kotlin.math.abs(a.blue - b.blue) < .01f
            assertTrue(matches(expected, marker[marker.width / 2, marker.height / 2]))
            assertTrue("Stack segment uses the legend color for $name", (0 until pixels.width step 2).any { x ->
                (0 until pixels.height step 2).any { y -> matches(expected, pixels[x, y]) }
            })
        }
    }
    @Test fun muscleColorsFollowCanonicalOrderAndLegacyAlias() {
        val names = MuscleLabels.canonical
        names.forEachIndexed { index, name ->
            assertEquals(androidx.compose.ui.graphics.lerp(PoliceColors.Blue, PoliceColors.Red,
                index.toFloat() / (names.size - 1)), muscleGroupColor(name))
        }
        assertEquals(muscleGroupColor("Upper Back"), muscleGroupColor("Back"))
    }

    @Test fun weeklyTitleAndInsightsShareTheTextBaseline() {
        show(chart(listOf("Upper Back", "Trapezius", "Lower Back")).copy(title = "Muscle Set Count"))
        val title = compose.onNodeWithTag("weekly-muscles-title")
        val insights = compose.onNodeWithTag("weekly-muscles-insights")
        fun baseline(node: SemanticsNodeInteraction): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        assertEquals(baseline(title), baseline(insights), 1f)
        capture("baseline-alignment")
    }
    @Test fun muscleAxisRoundsUpToTenAndContainsStackedTotals() {
        for ((total, expected) in listOf(0.0 to 10.0, 9.0 to 10.0, 10.0 to 10.0,
            11.0 to 20.0, 20.0 to 20.0, 21.0 to 30.0)) {
            val fixture = chart(listOf("Back", "Chest")).copy(series = listOf(
                ChartSeries("Back", List(8) { total / 2 }), ChartSeries("Chest", List(8) { total / 2 })))
            assertEquals(expected, chartMaximum(fixture), .001)
        }
    }

    @Test fun volumeSelectionUpdatesSummaryAndSevenDailyValues() {
        val first = java.time.LocalDate.of(2026, 9, 7)
        val days = (0..7).map { week -> (0..6).map { day ->
            DailyVolume(first.plusWeeks(week.toLong()).plusDays(day.toLong()), (week * 100 + day).toDouble())
        } }
        val fixture = chart(listOf("Volume")).copy(id = "weekly-volume", title = "Volume Progression",
            kind = ChartKind.LINE, unit = "Lbs lifted", weeklyDays = days,
            series = listOf(ChartSeries("Volume", days.map { week -> week.sumOf { it.volume } })))
        show(fixture)
        for (week in listOf(7, 0, 3)) {
            compose.onNodeWithTag("weekly-volume-week-$week").performScrollTo().performClick()
            compose.onNodeWithTag("weekly-volume-week-$week").assertIsSelected()
            compose.onNodeWithTag("weekly-volume-summary-total").assertTextEquals(
                com.darkoceantech.myfitnesspolice.domain.formatting.formatWeight(days[week].sumOf { it.volume }) + " Lbs lifted")
            days[week].forEachIndexed { index, day ->
                compose.onNodeWithTag("weekly-volume-day-value-$index").performScrollTo().assertTextEquals(
                    com.darkoceantech.myfitnesspolice.domain.formatting.formatWeight(day.volume))
            }
        }
        compose.onNodeWithTag("weekly-volume-value").assertDoesNotExist()
        capture("volume-summary")
    }
    // Rotate before the Compose rule launches its Activity, avoiding recreation during setContent.
    @get:Rule(order = 0) val orientation = object : ExternalResource() {
        override fun before() {
            val landscape = InstrumentationRegistry.getArguments().getString("expectedOrientation") == "landscape"
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
                if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
            android.os.SystemClock.sleep(500)
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun chart(names: List<String>) = DashboardChart("weekly-muscles", "Muscle Set Count",
        "Working sets", "Last 8 weeks", ChartKind.STACKED_BAR, (1..8).map { "9/$it" },
        names.mapIndexed { i, name -> ChartSeries(name, (0..7).map { week ->
            when { week == 0 -> 0.0; i == 1 && week == 7 -> 1234567.0; else -> (i * 10 + week).toDouble() }
        }) }, "sets")

    private fun show(chart: DashboardChart) {
        compose.setContent {
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding().policeBackdrop(), color = Color.Transparent) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        DashboardChartCard(chart, onInsights = {})
                    }
                }
            }
        }
    }

    @Test fun columnOrderAndValuesFollowSelectedWeeklyBarIncludingZero() {
        val chart = chart(listOf("Triceps", "Lower Back", "Chest", "Abdominal", "Shoulders",
            "Biceps", "Hamstrings", "Calves", "Glutes", "Forearms", "Quads", "Back", "Other"))
        show(chart)
        compose.onNodeWithTag("weekly-muscles-legend").performScrollTo()
        val sorted = chart.series.withIndex().sortedBy { MuscleLabels.full(it.value.name) }
        sorted.forEachIndexed { rank, entry ->
            val item = compose.onNodeWithTag("muscle-legend-item-${entry.index}").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("muscle-legend-item-${sorted[rank / 5 * 5].index}").fetchSemanticsNode().boundsInRoot
            assertEquals(first.left, item.left, 1f)
            if (rank % 5 != 0) {
                val previous = compose.onNodeWithTag("muscle-legend-item-${sorted[rank - 1].index}").fetchSemanticsNode().boundsInRoot
                assertTrue(item.top >= previous.bottom - 1)
            }
            if (rank >= 5) assertTrue(item.left > compose.onNodeWithTag("muscle-legend-item-${sorted[0].index}").fetchSemanticsNode().boundsInRoot.left)
        }
        for (week in listOf(7, 0, 3)) {
            if (week != 7) compose.onNodeWithTag("weekly-muscles-week-$week").performScrollTo().performTouchInput { click() }
            compose.onNodeWithTag("weekly-muscles-week-$week").assertIsSelected()
            compose.onNodeWithTag("weekly-muscles-selected-week").assertTextEquals("Week of 09/0${week + 1}")
            chart.series.forEachIndexed { index, series ->
                compose.onNodeWithTag("muscle-legend-count-$index").assertTextEquals(DashboardMetricsCalculator.number(series.values[week]!!))
            }
        }
        compose.onNodeWithTag("weekly-muscles-legend").performScrollTo()
        capture("selected-week")
    }

    @Test fun longLabelsLargeCountsAndAdditionalRowsKeepEveryMuscleVisible() {
        val names = listOf("Triceps", "Lower Back", "Chest", "Abdominal", "Shoulders", "Biceps",
            "Hamstrings", "Calves", "Glutes", "Forearms", "Quads", "Other", "Long anatomical muscle group label",
            "Back", "Neck", "Additional muscle group")
        val chart = chart(names)
        show(chart)
        val sorted = chart.series.withIndex().sortedBy { MuscleLabels.full(it.value.name) }
        sorted.forEach { entry ->
                val label = compose.onNodeWithTag("muscle-legend-label-${entry.index}").performScrollTo()
                label.assertTextEquals(MuscleLabels.abbreviated(entry.value.name))
                val texts = mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(texts) }
                assertFalse("Full label must fit", texts.single().hasVisualOverflow)
                val count = compose.onNodeWithTag("muscle-legend-count-${entry.index}")
                count.assertTextEquals(DashboardMetricsCalculator.number(entry.value.values.last()!!))
                texts.clear()
                count.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(texts) }
                assertFalse("Large count must fit", texts.single().hasVisualOverflow)
                assertEquals(1, texts.single().lineCount)
                val countBounds = count.fetchSemanticsNode().boundsInRoot
                val itemBounds = compose.onNodeWithTag("muscle-legend-item-${entry.index}").fetchSemanticsNode().boundsInRoot
                assertEquals("Counts align to column edge", itemBounds.right, countBounds.right, 1f)
                assertTrue(label.fetchSemanticsNode().boundsInRoot.right <= countBounds.left)
            }
        compose.onNodeWithTag("muscle-legend-next").assertDoesNotExist()
        compose.onNodeWithTag("muscle-legend-previous").assertDoesNotExist()
        capture("expanded-legend")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: "portrait"
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "weekly-muscle-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$orientation-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
