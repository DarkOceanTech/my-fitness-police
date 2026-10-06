package com.darkoceantech.myfitnesspolice.data

/** The recorded exercise shown before importing its prescription into an editable workout. */
data class ExerciseSessionSnapshot(val workout: WorkoutDetails, val entry: ExerciseWithSets)
