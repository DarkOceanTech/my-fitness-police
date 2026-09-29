package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import org.json.JSONArray

/** Compares saved plans with isolated editor copies, without their distinct database IDs. */
internal fun WorkoutDetails.editSnapshot(): String = JSONArray()
    .put(workout.name.trim())
    .put(JSONArray(workout.targetMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()))
    .put(JSONArray().apply {
        orderedExercises().forEach { entry ->
            put(JSONArray().put(entry.workoutExercise.exerciseId)
                .put(entry.workoutExercise.notes)
                .put(JSONArray().apply {
                    entry.workoutExercise.equipmentPositions.forEach { put(JSONArray().put(it.name).put(it.position)) }
                })
                .put(JSONArray().apply {
                    entry.sets.sortedBy { it.position }.forEach { set ->
                        put(JSONArray().put(set.reps).put(set.weightGrams).put(set.isWarmup).put(set.modifier))
                    }
                }))
        }
    }).toString()
