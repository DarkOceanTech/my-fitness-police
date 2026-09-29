package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.ExerciseWithSets
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.data.displayName

/** A display group keeps the original records as the authority for edits and deletion. */
internal data class HistoryGroup(val key: String, val workouts: List<WorkoutDetails>) {
    val isGrouped: Boolean get() = workouts.first().workout.historyGroupId != null
    val title: String get() = if (isGrouped) {
        workouts.firstNotNullOfOrNull { it.workout.trainingPlan.takeIf(String::isNotBlank) }
            ?: "Recorded training plan"
    } else workouts.first().workout.displayName()
    val representativeId: String get() = workouts.first().workout.id
    val startedAt: Long get() = workouts.minOf { it.workout.startedAt }
    val finishedAt: Long get() = workouts.maxOf { it.workout.finishedAt ?: it.workout.startedAt }
    val performedSets: List<WorkoutSet> get() = workouts.flatMap { it.performedSets() }
    val exercises: List<HistoryGroupExercise> get() = workouts.flatMap { workout ->
        workout.orderedExercises().map { HistoryGroupExercise(workout, it) }
    }
}

internal data class HistoryGroupExercise(val workout: WorkoutDetails, val entry: ExerciseWithSets)

/** Group only explicit selection batches; using the same plan again must not merge weeks. */
internal fun groupWorkoutHistory(history: List<WorkoutDetails>): List<HistoryGroup> = history
    .filter { it.workout.kind == "session" && it.workout.finishedAt != null }
    .groupBy { it.workout.historyGroupId?.let { id -> "group:$id" } ?: "workout:${it.workout.id}" }
    .map { (key, members) ->
        HistoryGroup(key, members.sortedWith(compareBy({ it.workout.startedAt }, { it.workout.id })))
    }
    .sortedWith(compareByDescending<HistoryGroup> { it.startedAt }.thenBy { it.key })

/** Matching a member reveals the complete card, so filters never conceal part of its totals. */
internal fun filterHistoryGroups(groups: List<HistoryGroup>, matchingWorkouts: List<WorkoutDetails>): List<HistoryGroup> {
    val ids = matchingWorkouts.mapTo(hashSetOf()) { it.workout.id }
    return groups.filter { group -> group.workouts.any { it.workout.id in ids } }
}
