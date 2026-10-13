package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import com.darkoceantech.myfitnesspolice.ui.screens.DailyBlotterCalendarSection
import com.darkoceantech.myfitnesspolice.ui.theme.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Isolated calendar state: these tests never open, clear or modify the user's workout database. */
class DailyBlotterCalendarUiTest {
    @get:Rule val compose = createComposeRule()
    private val calculator = DashboardMetricsCalculator()

    @Before fun freezeRequestedOrientation() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedOrientation") ?: return
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
            if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
        android.os.SystemClock.sleep(500)
    }

    @After fun restoreRotation() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    private fun report(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY) = calculator.calculate(
        DashboardInput(emptyList(), emptyList()), System.currentTimeMillis(), day, weekStartsOn = first)

    private fun show(state: MutableState<DashboardReport>, compact: Boolean = false) {
        compose.setContent {
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().safeDrawingPadding(),
                    color = Color.Transparent, contentColor = PoliceColors.Text) {
                    Column((if (compact) Modifier.requiredWidth(360.dp) else Modifier)
                        .fillMaxSize().padding(16.dp)) {
                        DailyBlotterCalendarSection(state.value) { day ->
                            state.value = report(day, state.value.weekStartsOn)
                        }
                    }
                }
            }
        }
    }

    @Test fun compactCalendarKeepsSevenDaysOnOneRowAndTodayKeepsBothHighlightMeanings() {
        val today = LocalDate.now()
        val state = mutableStateOf(report(today))
        show(state, compact = true)
        compose.onNodeWithText("Daily Blotter").assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val calendarBounds = compose.onNodeWithTag("launchpad-calendar").fetchSemanticsNode().boundsInRoot
        val firstDayTop = compose.onNodeWithTag("calendar-${state.value.calendar.first().date}").fetchSemanticsNode().boundsInRoot.top
        val titleBounds = compose.onNodeWithTag("dashboard-launchpad").fetchSemanticsNode().boundsInRoot
        val infoBounds = compose.onNodeWithTag("daily-blotter-info").fetchSemanticsNode().boundsInRoot
        val todayBounds = compose.onNodeWithTag("daily-blotter-today").fetchSemanticsNode().boundsInRoot
        assertTrue("Info belongs immediately after the title", infoBounds.left >= titleBounds.right - 1 && infoBounds.left - titleBounds.right < 8 * density)
        assertEquals("Today stays on the right edge", calendarBounds.right, todayBounds.right, 1f)
        state.value.calendar.forEach { day ->
            val bounds = compose.onNodeWithTag("calendar-${day.date}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("${day.date} stays on the same row", firstDayTop, bounds.top, 1f)
            assertTrue("${day.date} remains visible in an equal-width cell", bounds.width >= calendarBounds.width / 8)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNode(hasText(day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE"))) and
                hasAnyAncestor(hasTestTag("calendar-${day.date}")), useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("${day.date} label fits without wrapping or clipping", layouts.single().lineCount == 1 && !layouts.single().hasVisualOverflow)
            assertTrue("${day.date} needs an accessible height", bounds.height >= 48 * density - 1)
        }
        compose.onNodeWithTag("calendar-$today").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today and selected"))
        screenshot("today-selected")
        val other = state.value.calendar.first { it.date != today }.date
        compose.onNodeWithTag("calendar-$other").performClick().assertIsSelected()
        compose.onNodeWithTag("calendar-$today").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today"))
        screenshot("today-and-another-selection")
        compose.onNodeWithTag("daily-blotter-today").performClick()
        compose.onNodeWithTag("calendar-$today").assertIsSelected()
    }

    @Test fun weeklySwipesCrossYearBoundariesAndTodayResetsAnUnrestrictedCalendar() {
        val start = LocalDate.of(2026, 12, 30)
        val state = mutableStateOf(report(start))
        show(state)
        repeat(3) { index ->
            compose.onNodeWithTag("daily-blotter-weeks").performTouchInput { swipeLeft() }
            val expected = start.plusWeeks((index + 1).toLong())
            compose.waitUntil(5000) { state.value.selectedDay == expected }
            compose.onNodeWithTag("calendar-$expected").assertIsDisplayed().assertIsSelected()
            assertEquals(start.dayOfWeek, state.value.selectedDay.dayOfWeek)
        }
        assertEquals(2027, state.value.selectedDay.year)
        screenshot("next-year-week")
        compose.onNodeWithTag("daily-blotter-weeks").performTouchInput { swipeRight() }
        compose.waitUntil(5000) { state.value.selectedDay == start.plusWeeks(2) }
        compose.onNodeWithTag("daily-blotter-today").performClick()
        val today = LocalDate.now()
        compose.waitUntil(5000) { state.value.selectedDay == today }
        compose.onNodeWithTag("calendar-$today").assertIsDisplayed().assertIsSelected()
        assertEquals(startOfWeek(today, DayOfWeek.MONDAY), state.value.calendar.first().date)
        screenshot("today-reset")
    }

    @Test fun weekStartChangesReorderAllDaysAndExternalDateSelectionUpdatesDisplayedWeek() {
        val chosen = LocalDate.of(2026, 10, 14)
        val state = mutableStateOf(report(chosen))
        show(state)
        assertEquals(DayOfWeek.MONDAY, state.value.calendar.first().date.dayOfWeek)
        compose.runOnIdle { state.value = report(chosen, DayOfWeek.SUNDAY) }
        val sunday = startOfWeek(chosen, DayOfWeek.SUNDAY)
        compose.onNodeWithTag("calendar-$sunday").assertIsDisplayed()
        compose.onNodeWithTag("calendar-$chosen").assertIsSelected()
        assertEquals(DayOfWeek.SUNDAY, state.value.calendar.first().date.dayOfWeek)
        val sundayBounds = compose.onNodeWithTag("calendar-$sunday").fetchSemanticsNode().boundsInRoot
        val mondayBounds = compose.onNodeWithTag("calendar-${sunday.plusDays(1)}").fetchSemanticsNode().boundsInRoot
        assertTrue("Sunday should appear before Monday", sundayBounds.left < mondayBounds.left)
        screenshot("sunday-start")
        compose.runOnIdle { state.value = report(LocalDate.of(2027, 1, 1), DayOfWeek.SUNDAY) }
        compose.onNodeWithTag("calendar-2027-01-01").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("daily-blotter-weeks").performTouchInput { swipeRight() }
        compose.waitUntil(5000) { state.value.selectedDay == LocalDate.of(2026, 12, 25) }
        compose.onNodeWithTag("calendar-2026-12-25").assertIsDisplayed().assertIsSelected()
    }

    @Test fun instructionsOpenFullScreenAndBackKeepsTheSelectedDay() {
        val day = LocalDate.now().minusDays(14)
        val state = mutableStateOf(report(day))
        show(state)
        compose.onNodeWithTag("daily-blotter-info").performClick()
        compose.onNodeWithTag("daily-blotter-instructions").assertIsDisplayed()
        compose.onNodeWithText("Select a day").assertIsDisplayed()
        compose.onNodeWithTag("daily-blotter-instructions-content").performScrollToNode(hasText("Week starts on"))
        compose.onNodeWithText("Week starts on").assertIsDisplayed()
        screenshot("instructions")
        compose.onNodeWithTag("daily-blotter-instructions-back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("daily-blotter-instructions").assertDoesNotExist()
        compose.onNodeWithTag("calendar-$day").assertIsDisplayed().assertIsSelected()
        assertEquals(day, state.value.selectedDay)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val orientation = if (instrumentation.targetContext.resources.configuration.orientation == 2) "landscape" else "portrait"
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it, orientation) }
        val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "daily-blotter-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            java.io.File(folder, "$orientation-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
