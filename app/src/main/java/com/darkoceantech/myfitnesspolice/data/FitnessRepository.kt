package com.darkoceantech.myfitnesspolice.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

class FitnessRepository(private val database: FitnessDatabase) {
    val sessionProgress = WorkoutSessionRepository(database)
    val trainingPlans = TrainingPlanRepository(database)
    val trainingSchedule = TrainingScheduleRepository(database)
    val workoutEditor = WorkoutEditorRepository(database)
    val activeExerciseSwap = ActiveExerciseSwapRepository(database)

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

    suspend fun groupHistoryWorkouts(ids: List<String>, trainingPlanId: String? = null,
        newPlanName: String? = null): String = database.withTransaction {
        require(ids.isNotEmpty()) { "Select at least one recorded workout." }
        require(ids.distinct().size == ids.size) { "Each recorded workout can only be selected once." }
        val workouts = ids.map { recordedWorkout(it).workout }
        val plan = if (trainingPlanId != null) {
            require(newPlanName.isNullOrBlank()) { "Choose an existing plan or enter a new plan name." }
            requireNotNull(database.trainingPlanDao().get(trainingPlanId)) {
                "The selected training plan is no longer available."
            }.plan
        } else {
            val name = newPlanName?.trim().orEmpty()
            require(name.isNotEmpty()) { "Enter a training plan name." }
            TrainingPlan(name = name).also { database.trainingPlanDao().insert(it) }
        }
        val historyGroupId = java.util.UUID.randomUUID().toString()
        // A fresh batch keeps repeated uses of the same plan separate. Recorded rows remain independent.
        workouts.forEach { workout -> database.workoutDao().update(workout.copy(
            sourceTrainingPlanId = plan.id, trainingPlan = plan.name, historyGroupId = historyGroupId)) }
        plan.id
    }

    suspend fun replaceHistoryExercise(workoutId: String, entryId: String, newExerciseId: String) = database.withTransaction {
        val workout = recordedWorkout(workoutId)
        val entry = requireNotNull(workout.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is not in the selected workout."
        }
        val replacement = requireNotNull(database.exerciseDao().get(newExerciseId)) {
            "The selected exercise is no longer available."
        }
        require(!replacement.isArchived) { "Choose an exercise that has not been archived." }
        // Keep the recorded entry and its children intact; only correct the catalog association.
        database.workoutExerciseDao().update(entry.workoutExercise.copy(exerciseId = replacement.id))
    }

    suspend fun correctHistorySet(workoutId: String, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?, isWarmup: Boolean? = null) = database.withTransaction {
        correctRecordedSet(recordedWorkout(workoutId), setId, weightGrams, plannedReps, actualReps, rpe, isWarmup)
    }

    private suspend fun activeWorkout(id: String): WorkoutDetails =
        requireNotNull(database.workoutDao().getDetails(id)) { "This workout is no longer available." }.also {
            require(it.workout.kind == "session" && it.workout.finishedAt == null) { "Select an active workout." }
        }

    suspend fun correctActiveSet(workoutId: String, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?, isWarmup: Boolean? = null) = database.withTransaction {
        correctRecordedSet(activeWorkout(workoutId), setId, weightGrams, plannedReps, actualReps, rpe, isWarmup)
    }

    private fun recordedSet(workout: WorkoutDetails, setId: String): WorkoutSet {
        val set = requireNotNull(workout.orderedSets().find { it.id == setId }) { "This set is not in the selected workout." }
        require(set.completedAt != null) { "Only recorded sets can be edited here." }
        require(workout.workout.finishedAt != null || set.actualReps != null) { "Record the actual reps before editing set details." }
        return set
    }

    private suspend fun correctRecordedSet(workout: WorkoutDetails, setId: String, weightGrams: Long?, plannedReps: Int, actualReps: Int, rpe: Int?, isWarmup: Boolean? = null) {
        require(weightGrams == null || weightGrams >= 0) { "Weight cannot be negative." }
        require(plannedReps > 0) { "Planned reps must be positive." }
        require(actualReps >= 0) { "Actual reps cannot be negative." }
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10." }
        val set = recordedSet(workout, setId)
        // Patch only this session's recorded set. Notes, timers, and the source workout remain intact.
        database.workoutSetDao().update(set.copy(weightGrams = weightGrams ?: set.weightGrams,
            reps = plannedReps, actualReps = actualReps, rpe = rpe, isWarmup = isWarmup ?: set.isWarmup))
    }

    suspend fun saveHistorySetNote(workoutId: String, setId: String, notes: String) = database.withTransaction {
        val set = recordedSet(recordedWorkout(workoutId), setId)
        database.workoutSetDao().update(set.copy(notes = notes.trim()))
    }

    suspend fun saveHistoryExerciseNote(workoutId: String, entryId: String, notes: String) = database.withTransaction {
        val workout = recordedWorkout(workoutId)
        val entry = requireNotNull(workout.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is not in the selected workout."
        }
        database.workoutExerciseDao().update(entry.workoutExercise.copy(notes = notes.trim()))
    }

    suspend fun saveActiveSetNote(workoutId: String, setId: String, notes: String) = database.withTransaction {
        val set = recordedSet(activeWorkout(workoutId), setId)
        database.workoutSetDao().update(set.copy(notes = notes.trim()))
    }

    suspend fun deletePlan(id: String) = database.withTransaction {
        val workout = database.workoutDao().getDetails(id)?.workout ?: return@withTransaction
        require(workout.kind == "plan") { "Only a saved workout plan can be deleted here." }
        workoutEditor.discard(id)
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
            require(it.kind in listOf("plan", "draft", "edit"))
        }
        val draft = observeSessions().first().firstOrNull { it.workout.kind == "draft" }?.workout
        return draft ?: Workout(kind = "draft").also { database.workoutDao().insert(it) }
    }
    suspend fun setWorkoutDetails(field: String, value: String, planId: String? = null) = database.withTransaction {
        val old = editablePlan(planId)
        if (field == "trainingPlans") {
            require(old.kind != "edit") { "Edit training plan membership on the training plan page." }
            trainingPlans.assignWorkout(old.id, value.split(',').filter { it.isNotBlank() })
            return@withTransaction
        }
        if (field == "plan") {
            require(old.kind != "edit") { "Edit training plan membership on the training plan page." }
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
        if (old.kind != "edit") trainingPlans.assignLegacyName(old.id, trainingPlan)
    }

    suspend fun saveWorkoutDetails(id: String, name: String, muscles: String, day: String, trainingIds: List<String>) = database.withTransaction {
        val old = editablePlan(id)
        database.workoutDao().update(old.copy(name = name.trim(), targetMuscles = muscles, dayOfWeek = day))
        if (old.kind != "edit") trainingPlans.assignWorkout(id, trainingIds)
    }

    suspend fun reorderExercises(workoutId: String, orderedIds: List<String>) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(details.workout.kind in listOf("plan", "draft", "edit"))
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
        require(details.workout.kind in listOf("draft", "plan", "edit")) { "Only workout plans can be edited here." }
        require(details.exercises.any { it.workoutExercise.id == entryId }) { "This exercise is no longer in the workout." }
        // The foreign key cascades to this card's sets, never to the catalog or copied sessions.
        database.workoutExerciseDao().delete(entryId)
        reorderExercises(workoutId, details.orderedExercises().map { it.workoutExercise.id }.filter { it != entryId })
    }

    suspend fun chooseExercise(exerciseId: String, planId: String? = null) = database.withTransaction {
        val workout = editablePlan(planId)
        val existing = requireNotNull(database.workoutDao().getDetails(workout.id))
        if (existing.exercises.any { it.exercise.id == exerciseId }) return@withTransaction
        addWorkoutExercise(workout.id, exerciseId)
        val entry = database.workoutDao().getDetails(workout.id)!!.exercises.maxBy { it.workoutExercise.position }
        saveSet(workout.id, entry.workoutExercise.id, null, 10, 0)
    }

    suspend fun savePlan(id: String, name: String? = null) = database.withTransaction {
        if (database.workoutDao().getDetails(id)?.workout?.kind == "edit") {
            workoutEditor.commit(id, name)
            return@withTransaction
        }
        val details = requireNotNull(database.workoutDao().getDetails(id))
        require(details.workout.kind in listOf("draft", "plan", "edit"))
        require(details.exercises.any { it.sets.isNotEmpty() }) { "Add at least one set before saving." }
        database.workoutDao().update(details.workout.copy(kind = "plan", finishedAt = null,
            name = name?.trim() ?: details.workout.name))
    }

    suspend fun startTraining(id: String): String = database.withTransaction {
        val plan = requireNotNull(database.trainingPlanDao().get(id)) { "This training plan is no longer available." }
        requireNoUnfinishedSession()
        val items = plan.orderedItems()
        require(items.isNotEmpty()) { "Add at least one workout or exercise before starting." }
        val modules = items.filterIsInstance<TrainingPlanItem.WorkoutItem>().associate { item ->
            item.workoutId to requireNotNull(database.workoutDao().getDetails(item.workoutId)) {
                "A workout in this plan is no longer available."
            }.also {
                require(it.workout.kind == "plan") { "Save every workout before starting training." }
                require(it.plannedSets() > 0) { "Add at least one set to every included workout before starting." }
            }
        }
        val direct = plan.exerciseMembers.associateBy { it.member.exerciseId }
        items.filterIsInstance<TrainingPlanItem.ExerciseItem>().forEach { item ->
            require(direct[item.exerciseId] != null) { "An exercise in this plan is no longer available." }
            require(item.sets.isNotEmpty()) { "Add at least one set to each direct exercise before starting." }
        }
        val targets = items.flatMap { item -> when (item) {
            is TrainingPlanItem.WorkoutItem -> modules.getValue(item.workoutId).workout.targetMuscles.split(',')
            is TrainingPlanItem.ExerciseItem -> listOf(direct.getValue(item.exerciseId).exercise.mainMuscleGroup().label)
        } }.filter { it.isNotBlank() }.distinct().joinToString(",")
        val session = Workout(kind = "session", sourceTrainingPlanId = id, name = plan.plan.name,
            dayOfWeek = plan.plan.dayOfWeek, trainingPlan = plan.plan.name, targetMuscles = targets)
        database.workoutDao().insert(session)
        var position = 0
        items.forEach { item -> when (item) {
            is TrainingPlanItem.WorkoutItem -> {
                val source = modules.getValue(item.workoutId)
                source.orderedExercises().filter { it.sets.isNotEmpty() }.forEach { entry ->
                    val exercise = entry.workoutExercise.copy(id = java.util.UUID.randomUUID().toString(), workoutId = session.id,
                        position = position++, sourceWorkoutId = source.workout.id, sourceWorkoutName = source.workout.displayName())
                    database.workoutExerciseDao().insert(exercise)
                    entry.sets.sortedBy { it.position }.forEach { set ->
                        database.workoutSetDao().insert(set.copy(id = java.util.UUID.randomUUID().toString(),
                            workoutExerciseId = exercise.id, completedAt = null, actualReps = null, rpe = null,
                            activeMillis = 0, restMillis = 0))
                    }
                }
            }
            is TrainingPlanItem.ExerciseItem -> {
                val exercise = WorkoutExercise(workoutId = session.id, exerciseId = item.exerciseId, position = position++)
                database.workoutExerciseDao().insert(exercise)
                item.sets.forEachIndexed { setPosition, set ->
                    database.workoutSetDao().insert(WorkoutSet(workoutExerciseId = exercise.id, position = setPosition,
                        reps = set.reps, weightGrams = set.weightGrams, isWarmup = set.isWarmup, modifier = set.modifier))
                }
            }
        } }
        sessionProgress.prepareSession(session.id)
        session.id
    }

    suspend fun startPlan(id: String): String = database.withTransaction {
        val plan = requireNotNull(database.workoutDao().getDetails(id))
        require(plan.workout.kind == "plan") { "Save this plan before starting." }
        require(plan.exercises.any { it.sets.isNotEmpty() }) { "Add at least one set before starting." }
        requireNoUnfinishedSession()
        val session = plan.workout.copy(id = java.util.UUID.randomUUID().toString(),
            kind = "session", sourcePlanId = id, startedAt = System.currentTimeMillis(), finishedAt = null)
        database.workoutDao().insert(session)
        plan.orderedExercises().forEach { entry ->
            val exercise = entry.workoutExercise.copy(id = java.util.UUID.randomUUID().toString(), workoutId = session.id,
                sourceWorkoutId = plan.workout.id, sourceWorkoutName = plan.workout.displayName())
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
        require(details.workout.kind in listOf("plan", "draft", "edit") ||
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
        require(workout.finishedAt != null) { "Only completed workouts can be deleted from Workout Log." }
        database.workoutDao().delete(id)
    }
    fun observeSessions() = database.workoutDao().observeAllDetails()

    private suspend fun requireNoUnfinishedSession() {
        require(database.workoutDao().observeUnfinished().first().isEmpty()) {
            "A workout is already in progress. Return to your current session before starting another."
        }
    }

    suspend fun startWorkout() = database.withTransaction {
        requireNoUnfinishedSession()
        database.workoutDao().insert(Workout())
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

    private suspend fun importTarget(workoutId: String, entryId: String): ExerciseWithSets {
        val target = requireNotNull(database.workoutDao().getDetails(workoutId)) {
            "This workout is no longer available."
        }
        require(target.workout.kind in listOf("plan", "draft", "edit") && target.workout.finishedAt == null) {
            "Import sets while creating or editing a workout. Active and recorded sessions cannot be replaced."
        }
        return requireNotNull(target.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
    }

    suspend fun getExerciseSessionHistory(exerciseId: String): List<ExerciseSessionSnapshot> = database.withTransaction {
        val workouts = mutableMapOf<String, WorkoutDetails>()
        database.workoutExerciseDao().completedForExercise(exerciseId).map { entry ->
            val workout = workouts.getOrPut(entry.workoutExercise.workoutId) {
                recordedWorkout(entry.workoutExercise.workoutId)
            }
            ExerciseSessionSnapshot(workout, entry)
        }
    }

    suspend fun getLastSession(workoutId: String, entryId: String): ExerciseSessionSnapshot = database.withTransaction {
        val target = requireNotNull(database.workoutDao().getDetails(workoutId)) { "This workout is no longer available." }
        val entry = requireNotNull(target.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
        // Match the catalog exercise, including sessions from other workouts or training plans.
        // For repeated exercises in one session, use its last occurrence in workout order.
        val source = requireNotNull(database.workoutExerciseDao().latestCompletedForExercise(entry.exercise.id)) {
            "No completed session was found for ${entry.exercise.name}. Your current sets have not changed."
        }
        ExerciseSessionSnapshot(recordedWorkout(source.workoutExercise.workoutId), source)
    }

    suspend fun editActivePlannedSets(workoutId: String, entryId: String, changes: List<PlannedSetUpdate>) = database.withTransaction {
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId)) { "This session is no longer available." }
        require(workout.workout.kind == "session" && workout.workout.finishedAt == null) { "Select an active session." }
        val entry = requireNotNull(workout.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is not in the selected session."
        }
        require(changes.map { it.id }.distinct().size == changes.size) { "Each set can only be edited once." }
        // Validate the entire batch before writing. A set completed while the editor was open
        // must be corrected through Set Details instead; its results cannot be overwritten here.
        val updated = changes.map { change ->
            val old = requireNotNull(entry.sets.singleOrNull { it.id == change.id }) { "This set is no longer in the exercise." }
            require(old.completedAt == null) { "This set has been completed. Use Set Details to correct it." }
            old.copy(reps = change.reps, weightGrams = change.weightGrams,
                isWarmup = change.isWarmup, modifier = change.modifier)
        }
        updated.forEach { database.workoutSetDao().update(it) }
    }

    suspend fun importLastSession(workoutId: String, entryId: String, sourceEntryId: String? = null): Unit = database.withTransaction {
        val entry = importTarget(workoutId, entryId)
        // A preview pins its recorded entry, so a newer session cannot silently replace what was shown.
        val source = if (sourceEntryId == null) getLastSession(workoutId, entryId).entry
        else requireNotNull(database.workoutExerciseDao().getDetails(sourceEntryId)) {
            "The session you viewed is no longer available. Close this view and try again."
        }.also {
            recordedWorkout(it.workoutExercise.workoutId)
            require(it.workoutExercise.exerciseId == entry.workoutExercise.exerciseId) {
                "The recorded exercise has changed. Close this view and try again."
            }
        }
        require(source.sets.isNotEmpty()) {
            "The last session for ${entry.exercise.name} has no sets to import. Your current sets have not changed."
        }
        entry.sets.forEach { database.workoutSetDao().delete(it.id) }
        source.sets.sortedBy { it.position }.forEachIndexed { position, set ->
            // A fresh prescription intentionally excludes recorded results, per-set notes and timers.
            database.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entryId, position = position,
                reps = set.reps, weightGrams = set.weightGrams, isWarmup = set.isWarmup, modifier = set.modifier))
        }
        // Exercise notes and equipment setup belong to the target workout and are not imported.
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
        editablePlan(workoutId)
        val details = requireNotNull(database.workoutDao().getDetails(workoutId))
        val entry = requireNotNull(details.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
        require(entry.sets.any { it.id == setId }) { "This set is no longer in the workout." }
        database.workoutSetDao().delete(setId)
        // Ascending compaction only fills earlier, now-empty positions, preserving the unique index.
        entry.sets.filterNot { it.id == setId }.sortedBy { it.position }.forEachIndexed { index, set ->
            if (set.position != index) database.workoutSetDao().update(set.copy(position = index))
        }
    }

    suspend fun reorderSets(workoutId: String, entryId: String, orderedIds: List<String>) = database.withTransaction {
        val details = requireNotNull(database.workoutDao().getDetails(workoutId)) {
            "This workout is no longer available."
        }
        require(details.workout.kind in listOf("plan", "draft", "edit")) {
            "Only planned sets can be reordered."
        }
        val entry = requireNotNull(details.exercises.singleOrNull { it.workoutExercise.id == entryId }) {
            "This exercise is no longer in the workout."
        }
        val sets = entry.sets.associateBy { it.id }
        require(orderedIds.size == sets.size && orderedIds.toSet() == sets.keys) {
            "The set list changed. Please try again."
        }
        if (orderedIds.withIndex().all { (position, id) -> sets.getValue(id).position == position }) return@withTransaction
        // Vacate every existing position before assigning the requested order, preserving the unique index.
        val temporaryStart = Math.addExact(sets.values.maxOf { it.position }, 1)
        Math.addExact(temporaryStart, orderedIds.lastIndex)
        orderedIds.forEachIndexed { index, id ->
            database.workoutSetDao().update(sets.getValue(id).copy(position = temporaryStart + index))
        }
        orderedIds.forEachIndexed { index, id ->
            database.workoutSetDao().update(sets.getValue(id).copy(position = index))
        }
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

    suspend fun updateExercise(id: String, name: String, equipment: String, description: String,
        primaryMuscles: String, secondaryMuscles: String) = database.withTransaction {
        require(name.isNotBlank()) { "Enter an exercise name." }
        val original = requireNotNull(database.exerciseDao().get(id)) {
            "This exercise is no longer available. Close the form and refresh your exercise list."
        }
        // Update the shared catalog row in place: plans and recorded sets retain their references.
        database.exerciseDao().update(original.copy(name = name.trim(), equipment = equipment.trim(),
            description = description.trim(), primaryMuscles = primaryMuscles.trim(),
            secondaryMuscles = secondaryMuscles.trim()))
    }
}
