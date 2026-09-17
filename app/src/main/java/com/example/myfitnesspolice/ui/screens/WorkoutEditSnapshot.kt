package com.example.myfitnesspolice.ui.screens

import com.example.myfitnesspolice.data.WorkoutDetails
import org.json.JSONArray

/** Saveable comparison of editor content, excluding schedule, memberships, and session bookkeeping. */
internal fun WorkoutDetails.editSnapshot(): String = JSONArray()
    .put(workout.name.trim())
    .put(JSONArray(workout.targetMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()))
    .put(JSONArray().apply {
        orderedExercises().forEach { entry ->
            put(JSONArray().put(entry.workoutExercise.id).put(entry.workoutExercise.exerciseId)
                .put(entry.workoutExercise.notes)
                .put(JSONArray().apply {
                    entry.workoutExercise.equipmentPositions.forEach { put(JSONArray().put(it.name).put(it.position)) }
                })
                .put(JSONArray().apply {
                    entry.sets.sortedBy { it.position }.forEach { set ->
                        put(JSONArray().put(set.id).put(set.reps).put(set.weightGrams).put(set.isWarmup).put(set.modifier))
                    }
                }))
        }
    }).toString()
