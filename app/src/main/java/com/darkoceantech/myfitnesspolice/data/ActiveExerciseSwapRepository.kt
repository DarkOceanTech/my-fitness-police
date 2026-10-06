package com.darkoceantech.myfitnesspolice.data

import androidx.room.withTransaction

enum class SwapSetSource { CURRENT, LAST_SESSION, NEW }

fun ExerciseWithSets.hasStartedInSession(state: WorkoutSessionState?): Boolean =
    sets.any { it.completedAt != null || it.activeMillis > 0 || it.restMillis > 0 || it.actualReps != null } ||
        (state?.phase == "active" && sets.any { it.id == state.currentSetId })

/** Session substitutions never rewrite a saved workout or an already started exercise. */
class ActiveExerciseSwapRepository(private val database: FitnessDatabase) {
    suspend fun preview(exerciseId: String): ExerciseSessionSnapshot? = database.withTransaction {
        val entry = database.workoutExerciseDao().latestCompletedForExercise(exerciseId) ?: return@withTransaction null
        if (entry.sets.isEmpty()) return@withTransaction null
        ExerciseSessionSnapshot(requireNotNull(database.workoutDao().getDetails(entry.workoutExercise.workoutId)), entry)
    }

    suspend fun swap(workoutId: String, entryId: String, exerciseId: String, source: SwapSetSource,
        sourceEntryId: String? = null) = database.withTransaction {
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId)) { "This session is no longer available." }
        require(workout.workout.kind == "session" && workout.workout.finishedAt == null) { "Select an active session." }
        val progress = requireNotNull(workout.sessionState) { "Load the session before swapping exercises." }
        val entry = requireNotNull(workout.exercises.singleOrNull { it.workoutExercise.id == entryId }) { "This exercise is no longer in the session." }
        require(!entry.hasStartedInSession(progress)) { "You can only swap an exercise before starting its first set." }
        require(exerciseId != entry.exercise.id) { "Choose a different exercise." }
        val replacement = requireNotNull(database.exerciseDao().get(exerciseId)) { "This exercise is no longer available." }
        require(!replacement.isArchived) { "Choose an available exercise." }
        val existingSets = entry.sets.sortedBy { it.position }
        val prescriptions = when (source) {
            SwapSetSource.CURRENT -> existingSets.map { TrainingPlanSet(it.reps, it.weightGrams, it.isWarmup, it.modifier) }
            SwapSetSource.NEW -> listOf(TrainingPlanSet())
            SwapSetSource.LAST_SESSION -> {
                val last = requireNotNull(sourceEntryId?.let { database.workoutExerciseDao().getDetails(it) }) { "View the replacement's last session before importing it." }
                val recorded = requireNotNull(database.workoutDao().getDetails(last.workoutExercise.workoutId)) { "That recorded session is no longer available." }
                require(recorded.workout.kind == "session" && recorded.workout.finishedAt != null && last.exercise.id == exerciseId) {
                    "The recorded exercise has changed. Choose the replacement again."
                }
                last.sets.sortedBy { it.position }.map { TrainingPlanSet(it.reps, it.weightGrams, it.isWarmup, it.modifier) }
            }
        }
        require(prescriptions.isNotEmpty()) { "Add at least one planned set." }
        val updatedSets = prescriptions.mapIndexed { position, set -> WorkoutSet(
            id = if (source == SwapSetSource.CURRENT) existingSets[position].id else java.util.UUID.randomUUID().toString(),
            workoutExerciseId = entryId, position = position, reps = set.reps, weightGrams = set.weightGrams,
            isWarmup = set.isWarmup, modifier = set.modifier)
        }
        // Validate before deleting. Keep the exercise position and source-workout provenance.
        existingSets.forEach { database.workoutSetDao().delete(it.id) }
        database.workoutExerciseDao().update(entry.workoutExercise.copy(exerciseId = exerciseId, notes = "", equipmentPositions = emptyList()))
        updatedSets.forEach { database.workoutSetDao().insert(it) }
        if (existingSets.any { it.id == progress.currentSetId }) {
            database.sessionStateDao().save(progress.copy(currentSetId = updatedSets.first().id))
        }
    }
}
