package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.*
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal const val UNKNOWN_WORKOUT_SOURCE = "__unknown_workout_source__"
internal const val NO_TRAINING_PLAN = "__no_training_plan__"
internal const val HISTORY_DAY_MILLIS = 86_400_000L

/** Calendar dates are encoded at UTC midnight, without changing their local year/month/day. */
internal fun historyLocalDay(timestamp: Long, timeZone: TimeZone): Long {
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis / HISTORY_DAY_MILLIS
}

internal enum class HistoryDateWindow(val label: String) {
    AllTime("All time"), Last7Days("Last 7 days"), Last30Days("Last 30 days"), ThisYear("This year"), Custom("Custom range")
}

internal data class HistoryFilters(
    val dateWindow: HistoryDateWindow = HistoryDateWindow.AllTime,
    val startDay: Long? = null,
    val endDay: Long? = null,
    val exerciseId: String? = null,
    val workoutId: String? = null,
    val trainingPlanId: String? = null,
) {
    val activeCount: Int get() = listOf(dateWindow != HistoryDateWindow.AllTime,
        exerciseId != null, workoutId != null, trainingPlanId != null).count { it }
}

internal data class HistoryFilterOption(val id: String, val label: String)

/** Only recorded snapshots/IDs are used. A plan's current members cannot describe past sessions. */
internal fun historyWorkoutSourceIds(session: WorkoutDetails): Set<String> =
    (session.exercises.mapNotNull { it.workoutExercise.sourceWorkoutId } + listOfNotNull(session.workout.sourcePlanId)).toSet()

internal fun historyWorkoutOptions(history: List<WorkoutDetails>): List<HistoryFilterOption> {
    val options = linkedMapOf<String, String>()
    history.sortedByDescending { it.workout.startedAt }.forEach { session ->
        session.orderedExercises().forEach { entry ->
            entry.workoutExercise.sourceWorkoutId?.let { id ->
                options.putIfAbsent(id, entry.workoutExercise.sourceWorkoutName.ifBlank { "Recorded workout (${id.take(8)})" })
            }
        }
        session.workout.sourcePlanId?.let { options.putIfAbsent(it, session.workout.displayName()) }
    }
    return options.map { HistoryFilterOption(it.key, it.value) }.sortedBy { it.label.lowercase(Locale.ROOT) } +
        if (history.any { historyWorkoutSourceIds(it).isEmpty() }) listOf(HistoryFilterOption(UNKNOWN_WORKOUT_SOURCE, "Workout source unavailable")) else emptyList()
}

internal fun historyTrainingPlanOptions(history: List<WorkoutDetails>): List<HistoryFilterOption> =
    history.sortedByDescending { it.workout.startedAt }.mapNotNull { session ->
        session.workout.sourceTrainingPlanId?.let { id -> HistoryFilterOption(id,
            session.workout.trainingPlan.ifBlank { session.workout.name }.ifBlank { "Recorded plan (${id.take(8)})" }) }
    }.distinctBy { it.id }.sortedBy { it.label.lowercase(Locale.ROOT) } +
        if (history.any { it.workout.sourceTrainingPlanId == null }) listOf(HistoryFilterOption(NO_TRAINING_PLAN, "No recorded plan")) else emptyList()

internal fun filterWorkoutHistory(history: List<WorkoutDetails>, filters: HistoryFilters,
    todayMillis: Long = System.currentTimeMillis(), timeZone: TimeZone = TimeZone.getDefault()): List<WorkoutDetails> {
    val today = historyLocalDay(todayMillis, timeZone)
    val start = when (filters.dateWindow) {
        HistoryDateWindow.AllTime -> null
        HistoryDateWindow.Last7Days -> today - 6
        HistoryDateWindow.Last30Days -> today - 29
        HistoryDateWindow.ThisYear -> Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = today * HISTORY_DAY_MILLIS; set(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis / HISTORY_DAY_MILLIS
        HistoryDateWindow.Custom -> filters.startDay
    }
    val end = when (filters.dateWindow) {
        HistoryDateWindow.AllTime -> null
        HistoryDateWindow.Custom -> filters.endDay
        else -> today
    }
    return history.filter { session ->
        val day = historyLocalDay(session.workout.startedAt, timeZone)
        val sources = historyWorkoutSourceIds(session)
        (start == null || day >= start) && (end == null || day <= end) &&
            (filters.exerciseId == null || session.exercises.any { it.exercise.id == filters.exerciseId && it.sets.any { set -> set.completedAt != null } }) &&
            (filters.workoutId == null || if (filters.workoutId == UNKNOWN_WORKOUT_SOURCE) sources.isEmpty() else filters.workoutId in sources) &&
            (filters.trainingPlanId == null || if (filters.trainingPlanId == NO_TRAINING_PLAN) session.workout.sourceTrainingPlanId == null
                else session.workout.sourceTrainingPlanId == filters.trainingPlanId)
    }.sortedByDescending { it.workout.startedAt }
}
