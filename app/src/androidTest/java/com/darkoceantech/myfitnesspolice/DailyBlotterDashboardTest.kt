package com.darkoceantech.myfitnesspolice

import android.app.UiAutomation
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.dashboard.DashboardReport
import com.darkoceantech.myfitnesspolice.domain.dashboard.startOfWeek
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardRoute
import com.darkoceantech.myfitnesspolice.ui.screens.DashboardViewModel
import com.darkoceantech.myfitnesspolice.ui.screens.DemoSettingsScreen
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors
import com.darkoceantech.myfitnesspolice.ui.theme.policeBackdrop
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Tests the actual Room -> dashboard -> calendar/settings integration using isolated data. */
class DailyBlotterDashboardTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FitnessDatabase
    private lateinit var settings: AppSettingsRepository
    private lateinit var model: DashboardViewModel
    private val store = ViewModelStore()
    private val preferenceName = "daily-blotter-test-${UUID.randomUUID()}"
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val today: LocalDate get() = LocalDate.now()

    @Before fun prepareIsolatedSources() {
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { expected ->
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(
                if (expected == "landscape") UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
            android.os.SystemClock.sleep(500)
        }
        database = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        settings = AppSettingsRepository(context, preferenceName)
        compose.runOnUiThread {
            model = DashboardViewModel(DashboardRepository(FitnessRepository(database)), settings)
            store.put("dashboard", model)
        }
    }

    @After fun releaseIsolatedSources() {
        compose.runOnUiThread { store.clear() }
        settings.close()
        database.close()
        context.deleteSharedPreferences(preferenceName)
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    @Test fun weeklySwipeRebasesRecordedDataAndTodayRestoresAllPeriods() {
        seedRecordedTraining()
        show()
        awaitReport(today)
        assertDailyReps(10.0)
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-current"))
        compose.onNodeWithTag("launch-current").assertExists()

        calendar().performTouchInput { swipeRight() }
        val previous = today.minusWeeks(1)
        awaitReport(previous)
        compose.onNodeWithTag("calendar-$previous").assertIsSelected()
        assertDailyReps(6.0)
        val previousReport = report()
        assertEquals(startOfWeek(previous, DayOfWeek.MONDAY), previousReport.weekStart)
        assertEquals(600.0, previousReport.weekly.first().series.single().values.last()!!, .1)
        assertTrue(previousReport.monthly.first().caption.startsWith(
            previous.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))))
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launch-previous"))
        compose.onNodeWithTag("launch-previous").assertExists()
        capture("previous-week-recorded")

        calendar().performTouchInput { swipeLeft() }
        awaitReport(today)
        calendar().performTouchInput { swipeLeft() }
        awaitReport(today.plusWeeks(1))
        assertNotNull(report().daily.first().emptyReason)
        assertEquals(0, report().daily.first().series.first().values.size)

        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("daily-blotter-today"))
        compose.onNodeWithTag("daily-blotter-today").performClick()
        awaitReport(today)
        compose.onNodeWithTag("calendar-$today").assertIsSelected()
        assertDailyReps(10.0)
        assertEquals(1000.0, report().weekly.first().series.single().values.last()!!, .1)
        assertEquals(startOfWeek(today, DayOfWeek.MONDAY), report().weekStart)
        assertEquals(today.withDayOfMonth(1).lengthOfMonth(), report().monthly.first().labels.size)
        assertEquals("current", report().launches.single().id)
        capture("today-recorded-reset")
    }

    @Test fun weekSwipeCrossesYearBoundaryAndTodayReturnsToDeviceDate() {
        val december = LocalDate.of(today.year - 1, 12, 31)
        show(december)
        awaitReport(december)
        val january = december.plusWeeks(1)
        calendar().performTouchInput { swipeLeft() }
        awaitReport(january)
        assertEquals(december.dayOfWeek, report().selectedDay.dayOfWeek)
        assertEquals(YearMonth.of(today.year, 1), YearMonth.from(report().selectedDay))
        assertEquals(startOfWeek(january, DayOfWeek.MONDAY), report().weekStart)
        assertEquals(7, report().calendar.size)
        assertEquals(31, report().monthly.first().labels.size)
        assertTrue(report().daily.all { it.emptyReason != null })
        capture("year-boundary-empty")

        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("daily-blotter-today"))
        compose.onNodeWithTag("daily-blotter-today").performClick()
        awaitReport(today)
        assertEquals(today, report().today)
        assertEquals(YearMonth.from(today), YearMonth.from(report().selectedDay))
        compose.onNodeWithTag("calendar-$today").assertIsSelected()
    }

    @Test fun savedWeekStartChangesCalendarOrderAndWeeklyDashboardBoundaries() {
        show()
        awaitReport(today)
        assertEquals(DayOfWeek.MONDAY, report().calendar.first().date.dayOfWeek)
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("settings-week-start").performScrollTo().performClick()
        compose.onNodeWithText("Sunday", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { settings.weekStartsOn.value == DayOfWeek.SUNDAY }
        val reloaded = AppSettingsRepository(context, preferenceName)
        try { assertEquals(DayOfWeek.SUNDAY, reloaded.weekStartsOn.value) }
        finally { reloaded.close() }
        compose.onNodeWithContentDescription("Back to Dispatch").performClick()
        compose.waitUntil(5000) { model.state.value.report?.weekStartsOn == DayOfWeek.SUNDAY }
        assertEquals(today, report().selectedDay)
        assertEquals(DayOfWeek.SUNDAY, report().calendar.first().date.dayOfWeek)
        assertEquals(startOfWeek(today, DayOfWeek.SUNDAY), report().weekStart)
        for (day in report().calendar) compose.onNodeWithTag("calendar-${day.date}").assertExists()
        capture("sunday-week-start")
    }

    private fun show(initialDate: LocalDate = today) {
        compose.setContent {
            var selectedDay by remember { mutableStateOf(initialDate) }
            var settingsOpen by remember { mutableStateOf(false) }
            val weekStartsOn by settings.weekStartsOn.collectAsState()
            MyFitnessPoliceTheme {
                Surface(Modifier.fillMaxSize().policeBackdrop().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = Color.Transparent, contentColor = PoliceColors.Text) {
                    if (settingsOpen) DemoSettingsScreen(onBack = { settingsOpen = false },
                        weekStartsOn = weekStartsOn, onWeekStartChanged = settings::setWeekStartsOn)
                    else DashboardRoute(model, Modifier.fillMaxSize(), onSettings = { settingsOpen = true },
                        onPlan = {}, onLog = {}, selectedDay = selectedDay, onDayChanged = { selectedDay = it })
                }
            }
        }
    }

    private fun calendar(): SemanticsNodeInteraction {
        compose.onNodeWithTag("dashboard-home").performScrollToNode(hasTestTag("launchpad-calendar"))
        return compose.onNodeWithTag("launchpad-calendar")
    }

    private fun awaitReport(date: LocalDate) {
        compose.waitUntil(8000) { model.state.value.report?.selectedDay == date }
        compose.waitForIdle()
    }

    private fun report(): DashboardReport = requireNotNull(model.state.value.report)

    private fun assertDailyReps(reps: Double) {
        assertEquals(listOf(reps), report().daily.first().series.first { it.name == "Reps" }.values)
    }

    private fun seedRecordedTraining() = runBlocking {
        database.exerciseDao().insert(Exercise("row", "Cable row", "Cable", primaryMuscles = "Latissimus dorsi"))
        database.trainingPlanDao().insert(TrainingPlan("plan", "Back patrol", 100))
        val dates = listOf("current" to today, "previous" to today.minusWeeks(1),
            "older" to today.minusMonths(1).withDayOfMonth(1))
        for ((id, date) in dates) {
            val finished = if (date == today) System.currentTimeMillis() - 1000
                else date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            database.workoutDao().insert(Workout(id, startedAt = finished - 90_000,
                finishedAt = finished, sourceTrainingPlanId = "plan", trainingPlan = "Back patrol"))
            database.workoutExerciseDao().insert(WorkoutExercise("exercise-$id", id, "row", 0))
            val reps = if (id == "current") 10 else 6
            database.workoutSetDao().insert(WorkoutSet("set-$id", "exercise-$id", 0, reps, 45359,
                completedAt = finished - 1000, actualReps = reps, activeMillis = 30_000, restMillis = 60_000))
            database.trainingScheduleDao().insert(listOf(TrainingSchedule("plan", date.toString())))
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val orientation = if (context.resources.configuration.orientation == 2) "landscape" else "portrait"
        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { assertEquals(it, orientation) }
        val folder = java.io.File(context.getExternalFilesDir(null), "daily-blotter-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(folder, "$orientation-integration-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }
}
