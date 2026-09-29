package com.darkoceantech.myfitnesspolice.ui.screens

import com.darkoceantech.myfitnesspolice.data.*
import org.junit.Assert.*
import org.junit.Test

class HistoryGroupsTest {
    @Test fun repeatedPlanUsesSeparateCardsForEachSelectionBatch() {
        val early = session("back", 1000, "week-one")
        val later = session("arms", 2000, "week-one")
        val nextWeek = session("back-next-week", 604801000, "week-two")
        val groups = groupWorkoutHistory(listOf(later, nextWeek, early))
        assertEquals(2, groups.size)
        assertEquals(listOf("group:week-two", "group:week-one"), groups.map { it.key })
        assertEquals(listOf("Arm Focus", "Arm Focus"), groups.map { it.title })
        assertEquals(listOf("back", "arms"), groups.last().workouts.map { it.workout.id })
        assertEquals(1000L, groups.last().startedAt)
        assertEquals(62000L, groups.last().finishedAt)
        assertEquals(4, groups.last().performedSets.size)
    }

    @Test fun recordsWithoutBatchStaySeparateEvenForTheSamePlanAndDate() {
        val records = listOf(session("first", 1000), session("second", 2000))
        val groups = groupWorkoutHistory(records)
        assertEquals(2, groups.size)
        assertTrue(groups.none { it.isGrouped })
        assertEquals(listOf("Workout second", "Workout first"), groups.map { it.title })
        val one = groupWorkoutHistory(listOf(session("only", 3000, "one-member"))).single()
        assertTrue(one.isGrouped)
        assertEquals("Arm Focus", one.title)
    }

    @Test fun exercisesFollowRecordedWorkoutAndExerciseOrderAndKeepTheirOwner() {
        val early = session("early", 1000, "batch")
        val later = session("later", 2000, "batch")
        val group = groupWorkoutHistory(listOf(later, early)).single()
        assertEquals(listOf("early-row", "early-curl", "later-row", "later-curl"),
            group.exercises.map { it.entry.workoutExercise.id })
        // The same movement in separate workouts must retain separate sets and correction targets.
        assertEquals(listOf("row", "curl", "row", "curl"), group.exercises.map { it.entry.exercise.id })
        assertSame(early, group.exercises.first().workout)
        assertSame(later, group.exercises.last().workout)
        assertEquals(listOf("early-row-set", "early-curl-set", "later-row-set", "later-curl-set"),
            group.performedSets.map { it.id })
        assertEquals(listOf("early", "early", "later", "later"), group.performedSets.map { it.notes })
    }

    @Test fun filteringAMemberKeepsCompleteGroupAndNeverIncludesUnmatchedBatches() {
        val early = session("early", 1000, "batch")
        val later = session("later", 2000, "batch")
        val anotherWeek = session("next-week", 604801000, "other-batch")
        val groups = groupWorkoutHistory(listOf(early, later, anotherWeek))
        val matches = filterHistoryGroups(groups, listOf(later))
        assertEquals(1, matches.size)
        assertEquals(listOf(early, later), matches.single().workouts)
        assertEquals(4, matches.single().performedSets.size)
        assertTrue(filterHistoryGroups(groups, emptyList()).isEmpty())
    }

    @Test fun unfinishedSessionsAndTemplatesDoNotEnterHistoryGroups() {
        val complete = session("complete", 1000, "batch")
        val active = session("active", 2000, "batch").let { it.copy(workout = it.workout.copy(finishedAt = null)) }
        val template = session("template", 3000).let { it.copy(workout = it.workout.copy(kind = "plan")) }
        assertEquals(listOf(complete), groupWorkoutHistory(listOf(active, template, complete)).single().workouts)
    }

    private fun session(id: String, startedAt: Long, groupId: String? = null): WorkoutDetails {
        val entries = listOf("row", "curl").mapIndexed { index, exerciseId ->
            val entry = WorkoutExercise(id = "$id-$exerciseId", workoutId = id, exerciseId = exerciseId,
                position = index, notes = "Keep this note")
            ExerciseWithSets(entry, Exercise(id = exerciseId, name = exerciseId, equipment = "Cable"),
                listOf(WorkoutSet(id = "${entry.id}-set", workoutExerciseId = entry.id,
                    position = 0, reps = 10, actualReps = 9, weightGrams = 12000,
                    completedAt = startedAt + 20000, activeMillis = 14000, restMillis = 6000,
                    notes = id, rpe = 7)))
        }
        return WorkoutDetails(Workout(id = id, name = "Workout $id", startedAt = startedAt,
            finishedAt = startedAt + 60000, sourceTrainingPlanId = "same-plan", trainingPlan = "Arm Focus",
            historyGroupId = groupId), entries.reversed())
    }
}
