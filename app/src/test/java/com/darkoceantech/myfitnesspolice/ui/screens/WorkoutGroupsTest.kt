package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.Workout
import com.darkoceantech.myfitnesspolice.data.WorkoutDetails
import org.junit.Assert.*
import org.junit.Test

class WorkoutGroupsTest {
    private fun workout(id: String, name: String, muscles: String, day: String = "") =
        WorkoutDetails(Workout(id = id, name = name, targetMuscles = muscles, dayOfWeek = day, kind = "plan"), emptyList())

    @Test fun groupsAndWorkoutNamesSortAlphabeticallyInsteadOfBySchedule() {
        val groups = groupWorkoutsByMuscle(listOf(
            workout("biceps", "Arm light", "Biceps", "Monday"),
            workout("z", "Z rows", "Back", "Monday"),
            workout("a", "alpha rows", "Back", "Friday"),
            workout("abs", "Core light", "Abdominals", "Sunday")))
        assertEquals(listOf("Abdominals", "Biceps", "Upper Back"), groups.map { it.title })
        assertEquals(listOf("a", "z"), groups[2].workouts.map { it.workout.id })
    }

    @Test fun multiMuscleWorkoutsAppearOnceRegardlessOfSelectionOrderOrCase() {
        val groups = groupWorkoutsByMuscle(listOf(
            workout("one", "Strength", " Biceps,Back, biceps ,"),
            workout("two", "Light", "back,BICEPS"),
            workout("three", "Rows", "Back")))
        assertEquals(listOf("Biceps, Upper Back", "Upper Back"), groups.map { it.title })
        assertEquals(listOf("two", "one"), groups.first().workouts.map { it.workout.id })
        assertEquals(3, groups.sumOf { it.workouts.size })
    }

    @Test fun emptyMusclesAndUnnamedWorkoutsRemainReachableWithStableTies() {
        assertTrue(groupWorkoutsByMuscle(emptyList()).isEmpty())
        val groups = groupWorkoutsByMuscle(listOf(
            workout("z", "", " "), workout("a", "", ""), workout("back", "", "Back")))
        assertEquals(listOf("No target muscles", "Upper Back"), groups.map { it.title })
        assertEquals(listOf("a", "z"), groups.first().workouts.map { it.workout.id })
    }
}
