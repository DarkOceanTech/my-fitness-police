package com.darkoceantech.myfitnesspolice.data

import androidx.room.withTransaction
import java.util.UUID

/** Saved editor copies keep a workout's public prescription unchanged until the user saves. */
class WorkoutEditorRepository(private val database: FitnessDatabase) {
    companion object {
        fun editId(planId: String) = "workout-edit:$planId"
    }

    suspend fun begin(planId: String): String = database.withTransaction {
        val original = requireNotNull(database.workoutDao().getDetails(planId)) {
            "This workout is no longer available."
        }
        require(original.workout.kind == "plan") { "Choose a saved workout to edit." }
        val id = editId(planId)
        val existing = database.workoutDao().getDetails(id)
        if (existing != null) {
            require(existing.workout.kind == "edit" && existing.workout.sourcePlanId == planId)
        } else {
            database.workoutDao().insert(original.workout.copy(id = id, kind = "edit", sourcePlanId = planId))
            copyExercises(original, id)
        }
        id
    }

    suspend fun commit(editId: String, name: String? = null) = database.withTransaction {
        val edited = requireNotNull(database.workoutDao().getDetails(editId)) {
            "Your workout edit is no longer available."
        }
        require(edited.workout.kind == "edit") { "Choose an editable workout." }
        require(edited.plannedSets() > 0) { "Add at least one set before saving." }
        val originalId = requireNotNull(edited.workout.sourcePlanId)
        val original = requireNotNull(database.workoutDao().getDetails(originalId)) {
            "The original workout was removed. Your changes could not be saved."
        }
        require(original.workout.kind == "plan")
        // Preserve the original identity and training memberships. Sessions contain separate copies.
        val updated = original.workout.copy(name = name?.trim() ?: edited.workout.name,
            targetMuscles = edited.workout.targetMuscles, notes = edited.workout.notes,
            dayOfWeek = edited.workout.dayOfWeek)
        database.workoutDao().update(updated)
        original.exercises.forEach { database.workoutExerciseDao().delete(it.workoutExercise.id) }
        copyExercises(edited, originalId)
        // Leave the working copy open so subsequent edits still need an explicit Save.
        database.workoutDao().update(edited.workout.copy(name = updated.name,
            trainingPlan = updated.trainingPlan))
    }

    suspend fun discard(planId: String) = database.withTransaction {
        val copy = database.workoutDao().getDetails(editId(planId)) ?: return@withTransaction
        require(copy.workout.kind == "edit" && copy.workout.sourcePlanId == planId)
        database.workoutDao().delete(copy.workout.id)
    }

    private suspend fun copyExercises(source: WorkoutDetails, targetId: String) {
        source.orderedExercises().forEachIndexed { position, entry ->
            val exercise = entry.workoutExercise.copy(id = UUID.randomUUID().toString(),
                workoutId = targetId, position = position)
            database.workoutExerciseDao().insert(exercise)
            entry.sets.sortedBy { it.position }.forEachIndexed { setPosition, set ->
                database.workoutSetDao().insert(set.copy(id = UUID.randomUUID().toString(),
                    workoutExerciseId = exercise.id, position = setPosition))
            }
        }
    }
}
