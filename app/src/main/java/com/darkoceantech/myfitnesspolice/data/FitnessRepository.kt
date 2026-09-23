package com.darkoceantech.myfitnesspolice.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

class FitnessRepository(private val database: FitnessDatabase) {
    val sessionProgress = WorkoutSessionRepository(database)
    val trainingPlans = TrainingPlanRepository(database)

    private suspend fun recordedWorkout(id: String): WorkoutDetails =
        requireNotNull(database.workoutDao().getDetails(id)) { "This workout is no longer available." }.also {
            require(it.workout.kind == "session" && it.workout.finishedAt != null) { "Select a completed workout." }
        }

    suspend fun renameHistoryWorkout(id: String, name: String) = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Enter a workout name." }
        val workout = recordedWorkout(id)
        database.workoutDao().update(workout.workout.copy(name = trimmed))
    }

    suspend fun correctHistorySet(workoutId: String, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?) = database.withTransaction {
        correctRecordedSet(recordedWorkout(workoutId), setId, weightGrams, plannedReps, actualReps, rpe)
    }

    private suspend fun activeWorkout(id: String): WorkoutDetails =
        requireNotNull(database.workoutDao().getDetails(id)) { "This workout is no longer available." }.also {
            require(it.workout.kind == "session" && it.workout.finishedAt == null) { "Select an active workout." }
        }

    suspend fun correctActiveSet(workoutId: String, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?) = database.withTransaction {
        correctRecordedSet(activeWorkout(workoutId), setId, weightGrams, plannedReps, actualReps, rpe)
    }

    private fun recordedSet(workout: WorkoutDetails, setId: String): WorkoutSet {
        val set = requireNotNull(workout.orderedSets().find { it.id == setId }) { "This set is not in the selected workout." }
        require(set.completedAt != null) { "Only recorded sets can be edited here." }
        require(workout.workout.finishedAt != null || set.actualReps != null) { "Record the actual reps before editing set details." }
        return set
    }

    private suspend fun correctRecordedSet(workout: WorkoutDetails, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?) {
        require(weightGrams == null || weightGrams >= 0) { "Weight cannot be negative." }
        require(plannedReps > 0) { "Planned reps must be positive." }
        require(actualReps >= 0) { "Actual reps cannot be negative." }
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10." }
        val set = recordedSet(workout, setId)
        // Patch only this session's recorded set. Notes, timers, and the source workout remain intact.
        database.workoutSetDao().update(set.copy(weightGrams = weightGrams ?: set.weightGrams,
            reps = plannedReps, actualReps = actualReps, rpe = rpe))
    }

    suspend fun saveHistorySetNote(workoutId: String, setId: String, notes: String) = database.withTransaction {
        val set = recordedSet(recordedWorkout(workoutId), setId)
        database.workoutSetDao().update(set.copy(notes = notes.trim()))
    }

    suspend fun saveActiveSetNote(workoutId: String, setId: String, notes: String) = database.withTransaction {
        val set = recordedSet(activeWorkout(workoutId), setId)
        database.workoutSetDao().update(set.copy(notes = notes.trim()))
    }

    suspend fun deletePlan(id: String) = database.withTransaction {
        val workout = database.workoutDao().getDetails(id)?.workout ?: return@withTransaction
        require(workout.kind == "plan") { "Only a saved workout plan can be deleted here." }
        // Plan children cascade; copied sessions and their sourcePlanId reference remain independent.
        database.workoutDao().delete(id)
    }

    suspend fun deleteAllHistory() = database.withTransaction {
        // Foreign keys remove recorded exercises, sets and timers, preserving plans and active sessions.
        database.workoutDao().deleteAllHistory()
    }

    suspend fun updateActual(workoutId: String, setId: String, actual: Int) = database.withTransaction {
        require(actual >= 0) { "Enter a nonnegative whole number of reps." }
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(workout.workout.kind == "session" && workout.workout.finishedAt == null)
        val set = requireNotNull(workout.orderedSets().find { it.id == setId })
        require(set.completedAt == null) { "Use set info to correct a recorded set." }
        database.workoutSetDao().update(set.copy(actualReps = actual))
    }

    suspend fun saveSetInfo(workoutId: String, setId: String, actual: Int, notes: String) = database.withTransaction {
        require(actual >= 0) { "Enter a nonnegative whole number of reps." }
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(workout.workout.kind == "session")
        val set = requireNotNull(workout.orderedSets().find { it.id == setId })
        require(set.completedAt != null) { "Complete this set before editing its recorded details." }
        // Correct results without changing prescribed reps, timing, or the source plan.
        database.workoutSetDao().update(set.copy(actualReps = actual, notes = notes.trim()))
    }

    private suspend fun editablePlan(id: String?): Workout {
        if (id != null) return requireNotNull(database.workoutDao().getDetails(id)).workout.also {
            require(it.kind == "plan" || it.kind == "draft")
        }
        val draft = observeSessions().first().firstOrNull { it.workout.kind == "draft" }?.workout
        return draft ?: Workout(kind = "draft").also { database.workoutDao().insert(it) }
    }
    suspend fun setWorkoutDetails(field: String, value: String, planId: String? = null) = database.withTransaction {
        val old = editablePlan(planId)
        if (field == "trainingPlans") {
            trainingPlans.assignWorkout(old.id, value.split(',').filter { it.isNotBlank() })
            return@withTransaction
        }
        if (field == "plan") {
            trainingPlans.assignLegacyName(old.id, value)
            return@withTransaction
        }
        val updated = when (field) {
            "muscles" -> old.copy(targetMuscles = value)
            "day" -> old.copy(dayOfWeek = value)
            "name" -> old.copy(name = value.trim())
            else -> error("Unknown workout field")
        }
        database.workoutDao().update(updated)
    }
    suspend fun clearDraft() = database.withTransaction {
        observeSessions().first().filter { it.workout.kind == "draft" }.forEach { database.workoutDao().delete(it.workout.id) }
    }
    suspend fun saveMetadata(planId: String, muscles: String, day: String, trainingPlan: String) = database.withTransaction {
        val old = editablePlan(planId)
        database.workoutDao().update(old.copy(targetMuscles = muscles, dayOfWeek = day, trainingPlan = trainingPlan))
        trainingPlans.assignLegacyName(old.id, trainingPlan)
    }

    suspend fun saveWorkoutDetails(id: String, name: String, muscles: String, day: String, trainingIds: List<String>) = database.withTransaction {
        val old = editablePlan(id)
        database.workoutDao().update(old.copy(name = name.trim(), targetMuscles = muscles, dayOfWeek = day))
        trainingPlans.assignWorkout(id, trainingIds)
    }

    suspend fun reorderExercises(workoutId: String, orderedIds: List<String>) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(details.workout.kind in listOf("plan", "draft"))
        val entries = details.exercises.map { it.workoutExercise }.associateBy { it.id }
        require(orderedIds.size == entries.size && orderedIds.toSet() == entries.keys) {
            "The exercise list changed. Please try again."
        }
        if (entries.isNotEmpty()) {
            // Move into an unused range first to preserve the unique (workoutId, position) index.
            val temporaryStart = Math.addExact(entries.values.maxOf { it.position }, 1)
            orderedIds.forEachIndexed { index, id ->
                database.workoutExerciseDao().update(entries.getValue(id).copy(position = Math.addExact(temporaryStart, index)))
            }
            orderedIds.forEachIndexed { index, id ->
                database.workoutExerciseDao().update(entries.getValue(id).copy(position = index))
            }
        }
    }
    suspend fun removeWorkoutExercise(workoutId: String, entryId: String) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(details.workout.kind in listOf("draft", "plan")) { "Only workout plans can be edited here." }
        require(details.exercises.any { it.workoutExercise.id == entryId }) { "This exercise is no longer in the workout." }
        // The foreign key cascades to this card's sets, never to the catalog or copied sessions.
        database.workoutExerciseDao().delete(entryId)
        reorderExercises(workoutId, details.orderedExercises().map { it.workoutExercise.id }.filter { it != entryId })
    }

    suspend fun chooseExercise(exerciseId: String, planId: String? = null) = database.withTransaction {
        val workout = editablePlan(planId)
        addWorkoutExercise(workout.id, exerciseId)
        val entry = database.workoutDao().getDetails(workout.id)!!.exercises.maxBy { it.workoutExercise.position }
        saveSet(workout.id, entry.workoutExercise.id, null, 10, 0)
    }

    suspend fun savePlan(id: String, name: String? = null) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(id))
        require(details.workout.kind in listOf("draft", "plan"))
        require(details.exercises.any { it.sets.isNotEmpty() }) { "Add at least one set before saving." }
        database.workoutDao().update(details.workout.copy(kind = "plan", finishedAt = null,
            name = name?.trim() ?: details.workout.name))
    }

    suspend fun startTraining(id: String): String = database.withTransaction {
        val plan = requireNotNull(database.trainingPlanDao().get(id)) { "This training plan is no longer available." }
        val active = database.workoutDao().observeUnfinished().first().firstOrNull()
        if (active != null) {
            require(active.sourceTrainingPlanId == id) { "Resume or finish your current session before starting another plan." }
            sessionProgress.prepareSession(active.id)
            return@withTransaction active.id
        }
        val modules = plan.workoutIds().map { workoutId ->
            requireNotNull(database.workoutDao().getDetails(workoutId)) { "A workout in this plan is no longer available." }.also {
                require(it.workout.kind == "plan") { "Save every workout before starting training." }
            }
        }
        require(modules.isNotEmpty() && modules.all { it.plannedSets() > 0 }) { "Add at least one set to every included workout before starting." }
        val session = Workout(kind = "session", sourceTrainingPlanId = id, name = plan.plan.name,
            dayOfWeek = plan.plan.dayOfWeek, trainingPlan = plan.plan.name,
            targetMuscles = modules.flatMap { it.workout.targetMuscles.split(',') }.filter { it.isNotBlank() }.distinct().joinToString(","))
        database.workoutDao().insert(session)
        modules.flatMap { it.orderedExercises() }.filter { it.sets.isNotEmpty() }.forEachIndexed { position, entry ->
            val exercise = entry.workoutExercise.copy(id = java.util.UUID.randomUUID().toString(), workoutId = session.id, position = position)
            database.workoutExerciseDao().insert(exercise)
            entry.sets.sortedBy { it.position }.forEach { set ->
                database.workoutSetDao().insert(set.copy(id = java.util.UUID.randomUUID().toString(),
                    workoutExerciseId = exercise.id, completedAt = null, actualReps = null, rpe = null,
                    activeMillis = 0, restMillis = 0))
            }
        }
        sessionProgress.prepareSession(session.id)
        session.id
    }

    suspend fun startPlan(id: String): String = database.withTransaction {
        val plan = requireNotNull(database.workoutDao().getDetails(id))
        require(plan.workout.kind == "plan") { "Save this plan before starting." }
        require(plan.exercises.any { it.sets.isNotEmpty() }) { "Add at least one set before starting." }
        val active = database.workoutDao().observeUnfinished().first().firstOrNull()
        if (active != null) {
            require(active.sourcePlanId == id) { "Finish your current session before starting another plan." }
            sessionProgress.ensureSession(active.id)
            return@withTransaction active.id
        }
        val session = plan.workout.copy(id = java.util.UUID.randomUUID().toString(),
            kind = "session", sourcePlanId = id, startedAt = System.currentTimeMillis(), finishedAt = null)
        database.workoutDao().insert(session)
        plan.orderedExercises().forEach { entry ->
            val exercise = entry.workoutExercise.copy(id = java.util.UUID.randomUUID().toString(), workoutId = session.id)
            database.workoutExerciseDao().insert(exercise)
            entry.sets.forEach { set ->
                database.workoutSetDao().insert(set.copy(id = java.util.UUID.randomUUID().toString(),
                    workoutExerciseId = exercise.id, completedAt = null, actualReps = null, activeMillis = 0, restMillis = 0))
            }
        }
        sessionProgress.ensureSession(session.id)
        session.id
    }

    suspend fun updateSetFields(workoutId: String, entryId: String, setId: String,
        reps: Int? = null, grams: Long? = null, modifier: String? = null, warmup: Boolean? = null) = database.withTransaction {
        val entry = database.workoutDao().getDetails(workoutId)!!.exercises.single { it.workoutExercise.id == entryId }
        val old = entry.sets.single { it.id == setId }
        database.workoutSetDao().update(old.copy(reps = reps ?: old.reps, weightGrams = grams ?: old.weightGrams,
            modifier = modifier ?: old.modifier, isWarmup = warmup ?: old.isWarmup))
    }

    suspend fun saveEquipmentPositions(workoutId: String, entryId: String, positions: List<EquipmentPosition>) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(details.workout.kind in listOf("plan", "draft") ||
            (details.workout.kind == "session" && details.workout.finishedAt == null)) {
            "Equipment setup can be edited on a saved workout or an active session."
        }
        val entry = requireNotNull(details.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
        val trimmed = positions.map { EquipmentPosition(it.name.trim(), it.position.trim()) }
            .filterNot { it.name.isBlank() && it.position.isBlank() }
        require(trimmed.all { it.name.isNotBlank() && it.position.isNotBlank() }) {
            "Enter both a name and position for each item, or remove the unfinished item."
        }
        database.workoutExerciseDao().update(entry.workoutExercise.copy(equipmentPositions = trimmed))
    }

    suspend fun updateExerciseNote(workoutId: String, entryId: String, note: String) = database.withTransaction {
        val entry = database.workoutDao().getDetails(workoutId)!!.exercises.single { it.workoutExercise.id == entryId }
        database.workoutExerciseDao().update(entry.workoutExercise.copy(notes = note))
    }
    suspend fun deleteWorkout(id: String) = database.withTransaction {
        val workout = database.workoutDao().getDetails(id)?.workout ?: return@withTransaction
        require(workout.finishedAt != null) { "Only completed workouts can be deleted from history." }
        database.workoutDao().delete(id)
    }
    fun observeSessions() = database.workoutDao().observeAllDetails()

    suspend fun startWorkout() = database.withTransaction {
        if (database.workoutDao().observeUnfinished().first().isEmpty()) {
            database.workoutDao().insert(Workout())
        }
    }

    suspend fun addWorkoutExercise(workoutId: String, exerciseId: String) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        check(details.workout.finishedAt == null) { "This workout is already finished." }
        val position = (details.exercises.maxOfOrNull { it.workoutExercise.position } ?: -1) + 1
        database.workoutExerciseDao().insert(WorkoutExercise(workoutId = workoutId,
            exerciseId = exerciseId, position = position))
    }

    suspend fun saveSet(workoutId: String, entryId: String, setId: String?,
        reps: Int, weightGrams: Long) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        val entry = details.exercises.single { it.workoutExercise.id == entryId }
        if (setId == null) {
            require(reps > 0) { "Planned reps must be positive." }
            check(details.workout.finishedAt == null) { "This workout is already finished." }
            database.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entryId,
                position = (entry.sets.maxOfOrNull { it.position } ?: -1) + 1,
                reps = reps, weightGrams = weightGrams,
                completedAt = if (details.workout.kind == "session") System.currentTimeMillis() else null))
        } else {
            val old = entry.sets.single { it.id == setId }
            require(if (old.actualReps == null) reps > 0 else reps >= 0) { "Enter a valid rep count." }
            database.workoutSetDao().update(old.copy(
                reps = if (old.actualReps == null) reps else old.reps,
                actualReps = if (old.actualReps != null) reps else null,
                weightGrams = weightGrams))
        }
    }

    suspend fun copyLastSet(workoutId: String, entryId: String) = database.withTransaction {
        editablePlan(workoutId)
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        val entry = requireNotNull(details.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
        val last = requireNotNull(entry.sets.maxByOrNull { it.position }) { "Add a set before copying it." }
        // Copy the prescription into a fresh row, without recorded results, notes, or timers.
        database.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entryId,
            position = Math.addExact(last.position, 1), reps = last.reps, weightGrams = last.weightGrams,
            modifier = last.modifier, isWarmup = last.isWarmup))
    }

    suspend fun deleteSet(workoutId: String, entryId: String, setId: String) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        val entry = details.exercises.single { it.workoutExercise.id == entryId }
        require(entry.sets.any { it.id == setId })
        database.workoutSetDao().delete(setId)
    }

    suspend fun finishWorkout(id: String) = database.withTransaction {
        if (database.sessionStateDao().get(id) != null) {
            sessionProgress.finishSession(id)
            return@withTransaction
        }
        val details = requireNotNull(database.workoutDao().getDetails(id))
        require(details.workout.kind == "session") { "Only a started session can be finished." }
        require(details.exercises.any { it.sets.isNotEmpty() }) { "Save at least one set before finishing." }
        if (details.workout.finishedAt == null) {
            database.workoutDao().update(details.workout.copy(
                finishedAt = maxOf(System.currentTimeMillis(), details.workout.startedAt)))
        }
    }
    fun observeExercises() = database.exerciseDao().observeActive()
    fun observeHistory() = database.workoutDao().observeHistory()
    fun observeUnfinished() = database.workoutDao().observeUnfinished()
    fun observeWorkout(id: String) = database.workoutDao().observeDetails(id)

    suspend fun addExercise(name: String, equipment: String, description: String = "",
        primaryMuscles: String = "", secondaryMuscles: String = "") {
        require(name.isNotBlank()) { "Enter an exercise name." }
        database.exerciseDao().insert(Exercise(name = name.trim(), equipment = equipment.trim(),
            description = description.trim(), primaryMuscles = primaryMuscles.trim(),
            secondaryMuscles = secondaryMuscles.trim()))
    }
}
