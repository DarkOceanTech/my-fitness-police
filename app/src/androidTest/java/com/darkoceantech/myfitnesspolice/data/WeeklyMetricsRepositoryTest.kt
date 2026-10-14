package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.dashboard.DashboardMetricsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Uses isolated Room data; never opens the installed app's workout database. */
class WeeklyMetricsRepositoryTest {
    @Test fun oldConfirmedExerciseHistoryReachesWeeklyReferencesWithoutExtendingChartRange() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java
        ).build()
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 10, 13)
        fun time(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        suspend fun seed(id: String, date: LocalDate, exercise: String, pounds: Double,
            reps: Int, confirmed: Boolean = true, completed: Boolean = true) {
            val start = time(date)
            db.workoutDao().insert(Workout(id, startedAt = start,
                finishedAt = if (confirmed && completed) start + 120_000 else null))
            db.workoutExerciseDao().insert(WorkoutExercise("entry-$id", id, exercise, 0))
            db.workoutSetDao().insert(WorkoutSet("set-$id", "entry-$id", 0, reps,
                (pounds * 453.59237).roundToLong(), completedAt = if (completed) start + 30_000 else null,
                actualReps = if (confirmed) reps else null))
            if (!confirmed) db.sessionStateDao().save(WorkoutSessionState(id, phase = "rest",
                currentSetId = "set-$id", awaitingActual = true))
        }
        try {
            db.exerciseDao().insert(Exercise("row", "Cable row", "Cable"))
            db.exerciseDao().insert(Exercise("press", "Bench press", "Barbell"))
            seed("old-baseline", day.minusYears(1), "row", 200.0, 1)
            seed("other-exercise", day.minusYears(1), "press", 1000.0, 1)
            seed("unfinished", day.minusDays(2), "row", 800.0, 1, completed = false)
            seed("awaiting-confirmation", day.minusDays(1), "row", 900.0, 1, confirmed = false)
            seed("current", day, "row", 150.0, 10)
            seed("future", day.plusDays(1), "row", 1000.0, 1)

            val input = withTimeout(5000) { DashboardRepository(FitnessRepository(db)).input.first() }
            val includedIds = input.sessions.flatMap { it.sets }.map { it.id }
            assertTrue("All historical records reach the calculator", "set-old-baseline" in includedIds)
            assertFalse("Unfinished sets are excluded", "set-unfinished" in includedIds)
            assertFalse("Unconfirmed after-set results are excluded", "set-awaiting-confirmation" in includedIds)

            val report = DashboardMetricsCalculator().calculate(input,
                time(day) + 10 * 3_600_000L, day, zone = zone)
            val intensity = report.weekly.single { it.id == "weekly-intensity" }
            assertEquals(listOf(0.0, 1.0, 0.0, 0.0), intensity.series.map { it.values.last() })
            val volume = report.weekly.single { it.id == "weekly-volume" }
            assertEquals(8, volume.labels.size)
            assertEquals(8, volume.series.single().values.size)
            assertTrue(volume.series.single().values.dropLast(1).all { it == 0.0 })
            assertEquals(1500.0, volume.series.single().values.last()!!, .02)
        } finally {
            db.close()
        }
    }
}
