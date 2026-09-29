package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.*
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class HistoryFiltersTest {
    private val pacific = TimeZone.getTimeZone("America/Los_Angeles")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun dateWindowsUseInclusiveLocalCalendarDatesAcrossDaylightSaving() {
        val history = listOf(
            session("before", at("2026-11-01T06:59:59Z")),
            session("first-hour", at("2026-11-01T08:30:00Z")),
            session("repeated-hour", at("2026-11-01T09:30:00Z")),
            session("last-minute", at("2026-11-02T07:59:59Z")),
            session("after", at("2026-11-02T08:00:00Z")),
        )
        val day = LocalDate.of(2026, 11, 1).toEpochDay()
        val matches = filterWorkoutHistory(history, HistoryFilters(HistoryDateWindow.Custom, day, day),
            todayMillis = at("2026-11-04T20:00:00Z"), timeZone = pacific)
        assertEquals(listOf("last-minute", "repeated-hour", "first-hour"), matches.map { it.workout.id })
        val week = listOf(session("outside", at("2026-10-29T06:59:59Z")),
            session("first", at("2026-10-29T07:00:00Z")), session("today", at("2026-11-05T07:59:59Z")),
            session("tomorrow", at("2026-11-05T08:00:00Z")))
        assertEquals(listOf("today", "first"), filterWorkoutHistory(week, HistoryFilters(HistoryDateWindow.Last7Days),
            at("2026-11-04T20:00:00Z"), pacific).map { it.workout.id })
    }

    @Test fun exerciseWorkoutPlanAndDateFiltersCombineWithoutGuessingCurrentMembership() {
        val history = listOf(
            session("match", 1000, exercise = "row", source = "back", plan = "day-a"),
            session("other-plan", 2000, exercise = "row", source = "back", plan = "day-b"),
            session("other-workout", 3000, exercise = "row", source = "arms", plan = "day-a"),
            session("other-exercise", 4000, exercise = "curl", source = "back", plan = "day-a"),
            session("old-group", 5000, exercise = "row", source = null, plan = "day-a"),
        )
        val filter = HistoryFilters(exerciseId = "row", workoutId = "back", trainingPlanId = "day-a")
        assertEquals(listOf("match"), filterWorkoutHistory(history, filter).map { it.workout.id })
        assertEquals(listOf("old-group"), filterWorkoutHistory(history, HistoryFilters(workoutId = UNKNOWN_WORKOUT_SOURCE))
            .map { it.workout.id })
        assertEquals(5, filterWorkoutHistory(history, HistoryFilters()).size)
    }

    @Test fun sourceChoicesRetainSavedNamesAndSupportLegacySingleWorkouts() {
        val snapshotted = session("snapshot", 5000, source = "deleted-workout", plan = "deleted-plan")
        val legacy = session("legacy", 1000, source = null).let {
            it.copy(workout = it.workout.copy(sourcePlanId = "legacy-workout", name = "Legacy workout name"))
        }
        val history = listOf(snapshotted, legacy, session("old-group", 2000, source = null, plan = "deleted-plan"))
        assertTrue(historyWorkoutOptions(history).contains(HistoryFilterOption("deleted-workout", "Saved workout name")))
        assertTrue(historyWorkoutOptions(history).contains(HistoryFilterOption("legacy-workout", "Legacy workout name")))
        assertTrue(historyWorkoutOptions(history).any { it.id == UNKNOWN_WORKOUT_SOURCE })
        assertTrue(historyTrainingPlanOptions(history).contains(HistoryFilterOption("deleted-plan", "Saved training plan")))
        assertEquals(listOf("legacy"), filterWorkoutHistory(history, HistoryFilters(workoutId = "legacy-workout")).map { it.workout.id })
        assertEquals(listOf("legacy"), filterWorkoutHistory(history, HistoryFilters(trainingPlanId = NO_TRAINING_PLAN)).map { it.workout.id })
    }

    @Test fun exerciseFilterOnlyMatchesRecordedSetsAndStaleChoicesRemainEmpty() {
        val pending = session("pending", 1000).let { session -> session.copy(exercises = session.exercises.map { entry ->
            entry.copy(sets = entry.sets.map { it.copy(completedAt = null) })
        }) }
        assertTrue(filterWorkoutHistory(listOf(pending), HistoryFilters(exerciseId = "row")).isEmpty())
        assertTrue(filterWorkoutHistory(listOf(session("recorded", 2000)), HistoryFilters(exerciseId = "removed-exercise")).isEmpty())
    }

    private fun session(id: String, started: Long, exercise: String = "row", source: String? = "back", plan: String? = null): WorkoutDetails {
        val movement = Exercise(id = exercise, name = exercise, equipment = "Cable")
        val entry = WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = exercise, position = 0,
            sourceWorkoutId = source, sourceWorkoutName = if (source == null) "" else "Saved workout name")
        val set = WorkoutSet(id = "$id-set", workoutExerciseId = entry.id, position = 0, reps = 10, weightGrams = 10000,
            completedAt = started + 1000, actualReps = 8)
        return WorkoutDetails(Workout(id = id, startedAt = started, finishedAt = started + 60000,
            sourceTrainingPlanId = plan, trainingPlan = if (plan == null) "" else "Saved training plan"),
            listOf(ExerciseWithSets(entry, movement, listOf(set))))
    }
}
