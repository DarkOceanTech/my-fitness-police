package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardChartCard
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** Isolated presentation fixtures; no workout database is opened. */
class WeeklyMuscleLegendTest {
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

    private fun chart(names: List<String>) = DashboardChart("weekly-muscles", "Weekly Muscle Set Count",
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
        val sorted = chart.series.withIndex().sortedBy { it.value.name }
        sorted.forEachIndexed { rank, entry ->
            val item = compose.onNodeWithTag("muscle-legend-item-${entry.index}").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("muscle-legend-item-${sorted[rank / 7 * 7].index}").fetchSemanticsNode().boundsInRoot
            assertEquals(first.left, item.left, 1f)
            if (rank % 7 != 0) {
                val previous = compose.onNodeWithTag("muscle-legend-item-${sorted[rank - 1].index}").fetchSemanticsNode().boundsInRoot
                assertTrue(item.top >= previous.bottom - 1)
            }
            if (rank >= 7) assertTrue(item.left > compose.onNodeWithTag("muscle-legend-item-${sorted[0].index}").fetchSemanticsNode().boundsInRoot.left)
        }
        for (week in listOf(7, 0, 3)) {
            if (week != 7) compose.onNodeWithTag("weekly-muscles-week-$week").performScrollTo().performTouchInput { click() }
            compose.onNodeWithTag("weekly-muscles-week-$week").assertIsSelected()
            compose.onNodeWithTag("weekly-muscles-selected-week").assertTextEquals("Week of ${chart.labels[week]}")
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
        val sorted = chart.series.withIndex().sortedBy { it.value.name }
        sorted.forEach { entry ->
                val label = compose.onNodeWithTag("muscle-legend-label-${entry.index}").performScrollTo()
                label.assertTextEquals(entry.value.name)
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
