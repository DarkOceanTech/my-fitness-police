package com.darkoceantech.myfitnesspolice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.WeeklyProgress
import com.darkoceantech.myfitnesspolice.data.WeeklyProgressPoint
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardScreen
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import com.darkoceantech.myfitnesspolice.ui.theme.policeBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

class DashboardChartsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullWidthChartsSwipeShowInsightsAndRefreshFromNewData() {
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 20, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weeks = listOf(20, 28, 22, 0, 34, 30, 42, 24).map { sets ->
            val start = monday.timeInMillis
            monday.add(Calendar.DAY_OF_MONTH, 7)
            WeeklyProgressPoint(start, monday.timeInMillis, sets.toLong(), sets * 10L,
                BigDecimal(sets * 650), sets * 180_000L)
        }
        val current = weeks.last()
        val progress = mutableStateOf<WeeklyProgress?>(WeeklyProgress(current.weekStartMillis, current.weekEndMillis,
            current.sets, current.reps, current.weightPounds, current.activityMillis, weeks))
        compose.setContent {
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop(), color = Color.Transparent, contentColor = PoliceColors.Text) {
                    DashboardScreen(progress.value, Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithTag("weekly-Sets-value").assertTextEquals("24")
        val pagerWidth = compose.onNodeWithTag("weekly-progress-cards").fetchSemanticsNode().boundsInRoot.width
        val cardWidth = compose.onNodeWithTag("weekly-Sets-card").fetchSemanticsNode().boundsInRoot.width
        assertEquals(pagerWidth, cardWidth, 1f)
        screenshot("sets-chart")
        compose.onNodeWithTag("weekly-Sets-insights").performScrollTo().performClick()
        compose.onNodeWithTag("weekly-Sets-info").assertTextEquals(
            "Recorded sets in workouts started this week, Monday–Sunday. Includes warmup and working sets.")
        screenshot("sets-insights")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("weekly-progress-cards").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("weekly-Reps-value").assertIsDisplayed().assertTextEquals("240")
        compose.onNodeWithTag("weekly-Reps-chart").assertContentDescriptionContains("Week of Jul 20, 2026: 200 Reps", substring = true)
        screenshot("reps-chart")
        compose.onNodeWithContentDescription("Show Weight chart").performScrollTo().performClick()
        compose.onNodeWithTag("weekly-Weight-value").assertIsDisplayed().assertTextEquals("15,600")
        screenshot("weight-chart")
        compose.onNodeWithContentDescription("Show Activity chart").performClick()
        compose.onNodeWithTag("weekly-Activity-value").assertIsDisplayed().assertTextEquals("72")
        screenshot("activity-chart")
        compose.onNodeWithTag("weekly-Activity-insights").performScrollTo().performClick()
        compose.onNodeWithTag("weekly-Activity-info").assertTextEquals(
            "Time spent investing into your sexy body in workouts started this week. Includes sets and breaks, excludes pauses, and updates during an active workout.")
        screenshot("activity-insights")
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { progress.value = progress.value!!.let { p -> p.copy(reps = 241,
            weeks = p.weeks.dropLast(1) + p.weeks.last().copy(reps = 241)) } }
        compose.onNodeWithContentDescription("Show Reps chart").performScrollTo().performClick()
        compose.onNodeWithTag("weekly-Reps-value").assertTextEquals("241")
        compose.onNodeWithTag("weekly-Reps-chart").assertContentDescriptionContains("Week of Sep 7, 2026: 241 Reps", substring = true)
        compose.runOnIdle { progress.value = progress.value!!.let { p -> p.copy(sets = 0, reps = 0,
            weightPounds = BigDecimal.ZERO, activityMillis = 0,
            weeks = p.weeks.map { it.copy(sets = 0, reps = 0, weightPounds = BigDecimal.ZERO, activityMillis = 0) }) } }
        compose.onNodeWithTag("weekly-Reps-value").assertTextEquals("0")
        screenshot("empty-chart")
        compose.runOnIdle { progress.value = null }
        compose.onNodeWithTag("weekly-Reps-value").assertTextEquals("—")
        compose.onNodeWithTag("weekly-Reps-chart").assertContentDescriptionEquals("Loading Reps chart")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Wait for SurfaceFlinger to present the initial frame before the system screenshot.
        android.os.SystemClock.sleep(250)
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "dashboard-chart-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
