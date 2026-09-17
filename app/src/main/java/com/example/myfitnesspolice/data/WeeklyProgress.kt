package com.example.myfitnesspolice.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

data class WeeklyProgress(
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val sets: Long,
    val reps: Long,
    val weightPounds: BigDecimal,
    val activityMillis: Long,
    /** Oldest first, including the current, partial week. Missing weeks contain zero totals. */
    val weeks: List<WeeklyProgressPoint>,
)

data class WeeklyProgressPoint(
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val sets: Long,
    val reps: Long,
    val weightPounds: BigDecimal,
    val activityMillis: Long,
)

/** Attribute a session to its local start date, so all four totals use the same Monday–Sunday week. */
internal fun calculateWeeklyProgress(workouts: List<WorkoutDetails>, now: Long,
    timeZone: TimeZone = TimeZone.getDefault()): WeeklyProgress {
    val monday = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }
    // Calendar days, rather than fixed durations, keep all eight boundaries correct across DST.
    monday.add(Calendar.DAY_OF_MONTH, -7 * 7)
    val weeks = List(8) {
        val start = monday.timeInMillis
        monday.add(Calendar.DAY_OF_MONTH, 7)
        calculateWeek(workouts, start, monday.timeInMillis, now)
    }
    val current = weeks.last()
    return WeeklyProgress(current.weekStartMillis, current.weekEndMillis, current.sets, current.reps,
        current.weightPounds, current.activityMillis, weeks)
}

private fun calculateWeek(workouts: List<WorkoutDetails>, start: Long, end: Long, now: Long): WeeklyProgressPoint {
    var sets = 0L
    var reps = 0L
    var weightGrams = BigDecimal.ZERO
    var activity = 0L
    workouts.filter { it.workout.kind == "session" && it.workout.startedAt in start until end && it.workout.startedAt <= now }
        .forEach { session ->
            val state = session.sessionState
            session.orderedSets().filter { set ->
                set.completedAt != null && set.completedAt <= now &&
                    !(state?.awaitingActual == true && state.currentSetId == set.id)
            }.forEach { set ->
                // Older logs stored performed reps in reps; current sessions keep actualReps separately.
                val actual = set.actualReps ?: set.reps
                sets++
                reps += actual.toLong()
                weightGrams += BigDecimal.valueOf(set.weightGrams).multiply(BigDecimal.valueOf(actual.toLong()))
            }
            val through = minOf(now, session.workout.finishedAt ?: now)
            val wallTime = (through - session.workout.startedAt).coerceAtLeast(0)
            activity += (state?.dutyMillis(through) ?: wallTime).coerceIn(0, wallTime)
        }
    return WeeklyProgressPoint(start, end, sets, reps,
        weightGrams.divide(BigDecimal("453.59237"), 6, RoundingMode.HALF_UP), activity)
}
