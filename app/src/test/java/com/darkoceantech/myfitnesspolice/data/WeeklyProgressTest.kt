package com.darkoceantech.myfitnesspolice.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class WeeklyProgressTest {
    private val now = stamp("2026-09-10T19:00:00Z")
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun recordedActualsExcludePlansDraftsUnfinishedAndUnconfirmedSets() {
        val sets = listOf(
            WorkoutSet(id = "recorded", workoutExerciseId = "entry", position = 0, reps = 12, actualReps = 8,
                weightGrams = 1000, completedAt = now - 1000),
            WorkoutSet(id = "pending", workoutExerciseId = "entry", position = 1, reps = 12, actualReps = 11,
                weightGrams = 1000, completedAt = now - 500),
            WorkoutSet(id = "unfinished", workoutExerciseId = "entry", position = 2, reps = 12, actualReps = 99, weightGrams = 1000),
        )
        val current = details("live", now - 3_600_000, sets = sets,
            state = WorkoutSessionState("live", currentSetId = "pending", awaitingActual = true, isPaused = true, dutyElapsedMillis = 120_000))
        val result = calculateWeeklyProgress(listOf(current,
            details("plan", now - 1000, "plan", sets), details("draft", now - 1000, "draft", sets)), now, utc)
        assertEquals(1L, result.sets)
        assertEquals(8L, result.reps)
        assertEquals(17.636981, result.weightPounds.toDouble(), .000001)
        assertEquals(120_000L, result.activityMillis)
        val cleared = calculateWeeklyProgress(emptyList(), now, utc)
        assertEquals(0L, cleared.sets)
        assertEquals(0L, cleared.reps)
        assertEquals(0.0, cleared.weightPounds.toDouble(), 0.0)
        assertEquals(0L, cleared.activityMillis)
    }

    @Test fun mondayBoundariesUseSessionStartDateAndResetNextWeek() {
        val monday = stamp("2026-09-07T00:00:00Z")
        val nextMonday = stamp("2026-09-14T00:00:00Z")
        val set = WorkoutSet(workoutExerciseId = "entry", position = 0, reps = 10, actualReps = 6, weightGrams = 0, completedAt = now - 1)
        val workouts = listOf(details("previous", monday - 1, sets = listOf(set)),
            details("monday", monday, sets = listOf(set)), details("future", nextMonday, sets = listOf(set)))
        val thisWeek = calculateWeeklyProgress(workouts, now, utc)
        assertEquals(monday, thisWeek.weekStartMillis)
        assertEquals(nextMonday, thisWeek.weekEndMillis)
        assertEquals(1L, thisWeek.sets)
        assertEquals(6L, thisWeek.reps)
        assertEquals(0L, calculateWeeklyProgress(workouts.take(2), nextMonday, utc).sets)
    }

    @Test fun legacyActualsZeroRepsAndTimersUseStoredValuesWithoutPausedTime() {
        val old = WorkoutSet(workoutExerciseId = "entry", position = 0, reps = 5, weightGrams = 1000, completedAt = now - 1000)
        val zero = old.copy(position = 1, actualReps = 0)
        val finished = details("finished", now - 600_000, sets = listOf(old, zero), finishedAt = now - 300_000,
            state = WorkoutSessionState("finished", phase = "finished", dutyElapsedMillis = 60_000))
        val live = details("live", now - 100_000, state = WorkoutSessionState("live", dutyStartedAt = now - 10_000, dutyElapsedMillis = 20_000))
        val paused = details("paused", now - 100_000, state = WorkoutSessionState("paused", isPaused = true, dutyElapsedMillis = 50_000))
        val result = calculateWeeklyProgress(listOf(finished, live, paused), now, utc)
        assertEquals(2L, result.sets)
        assertEquals(5L, result.reps)
        assertEquals(11.023113, result.weightPounds.toDouble(), .000001)
        assertEquals(140_000L, result.activityMillis)
        assertEquals(300_000L, calculateWeeklyProgress(listOf(finished.copy(sessionState = null)), now, utc).activityMillis)
    }

    @Test fun localWeekHandlesDaylightSavingTransition() {
        val result = calculateWeeklyProgress(emptyList(), stamp("2026-03-08T19:00:00Z"), TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals(stamp("2026-03-02T08:00:00Z"), result.weekStartMillis)
        assertEquals(stamp("2026-03-09T07:00:00Z"), result.weekEndMillis)
        assertEquals(167 * 3_600_000L, result.weekEndMillis - result.weekStartMillis)
        assertEquals(8, result.weeks.size)
        assertTrue(result.weeks.zipWithNext().all { (a, b) -> a.weekEndMillis == b.weekStartMillis })
        assertEquals(stamp("2026-01-12T08:00:00Z"), result.weeks.first().weekStartMillis)
        assertTrue(result.weeks.dropLast(1).all { it.weekEndMillis - it.weekStartMillis == 168 * 3_600_000L })
    }

    @Test fun eightWeekSeriesKeepsEmptyWeeksAndUsesTheSameRecordedTotals() {
        val oldest = stamp("2026-07-20T00:00:00Z")
        fun session(id: String, start: Long, actual: Int) = details(id, start,
            sets = listOf(WorkoutSet(workoutExerciseId = "entry", position = 0, reps = 20, actualReps = actual,
                weightGrams = 1000, completedAt = start + 30_000)),
            finishedAt = start + 120_000,
            state = WorkoutSessionState(id, phase = "finished", dutyElapsedMillis = 60_000))
        val workouts = listOf(
            session("too-old", oldest - 1, 99), session("oldest", oldest, 5),
            session("previous", stamp("2026-09-06T23:59:59Z"), 7),
            session("current", stamp("2026-09-07T00:00:00Z"), 9),
            session("future", now + 1, 99))
        val result = calculateWeeklyProgress(workouts, now, utc)
        assertEquals(listOf(5L, 0L, 0L, 0L, 0L, 0L, 7L, 9L), result.weeks.map { it.reps })
        assertEquals(listOf(1L, 0L, 0L, 0L, 0L, 0L, 1L, 1L), result.weeks.map { it.sets })
        assertEquals(180_000L, result.weeks.sumOf { it.activityMillis })
        assertEquals(15.432358, result.weeks[6].weightPounds.toDouble(), .000001)
        assertEquals(result.reps, result.weeks.last().reps)
        assertEquals(result.sets, result.weeks.last().sets)
        assertEquals(result.weightPounds, result.weeks.last().weightPounds)
        assertEquals(result.activityMillis, result.weeks.last().activityMillis)
        // Corrections/deletions must update historical chart points as well as the headline.
        val edited = calculateWeeklyProgress(workouts.filter { it.workout.id != "oldest" }
            .map { if (it.workout.id == "previous") session("previous", it.workout.startedAt, 3) else it }, now, utc)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 3L, 9L), edited.weeks.map { it.reps })
        assertTrue(calculateWeeklyProgress(emptyList(), now, utc).weeks.all {
            it.sets == 0L && it.reps == 0L && it.weightPounds.signum() == 0 && it.activityMillis == 0L
        })
    }

    private fun details(id: String, started: Long, kind: String = "session", sets: List<WorkoutSet> = emptyList(),
        state: WorkoutSessionState? = null, finishedAt: Long? = null) = WorkoutDetails(
        Workout(id = id, startedAt = started, kind = kind, finishedAt = finishedAt),
        listOf(ExerciseWithSets(WorkoutExercise(id = "entry", workoutId = id, exerciseId = "exercise", position = 0),
            Exercise(id = "exercise", name = "Bench press", equipment = "Barbell"), sets)), state)

    private fun stamp(iso: String) = Instant.parse(iso).toEpochMilli()
}
