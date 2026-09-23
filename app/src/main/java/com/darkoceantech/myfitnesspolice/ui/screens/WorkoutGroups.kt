package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import com.darkoceantech.myfitnesspolice.data.displayName
import java.util.Locale

internal data class WorkoutMuscleGroup(val title: String, val workouts: List<WorkoutDetails>)

// A multi-muscle workout appears once; selection order and casing do not create extra groups.
internal fun groupWorkoutsByMuscle(workouts: List<WorkoutDetails>): List<WorkoutMuscleGroup> =
    workouts.groupBy { details ->
        details.workout.targetMuscles.split(',').map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }.distinct().sorted()
            .joinToString(", ") { muscle -> muscle.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .ifBlank { "No target muscles" }
    }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (title, members) ->
        WorkoutMuscleGroup(title, members.sortedWith(
            compareBy<WorkoutDetails> { it.workout.displayName().trim().lowercase(Locale.ROOT) }
                .thenBy { it.workout.id }))
    }
