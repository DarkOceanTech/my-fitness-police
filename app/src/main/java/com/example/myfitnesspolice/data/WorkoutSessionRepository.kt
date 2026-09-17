package com.example.myfitnesspolice.data

import androidx.room.withTransaction

/** Every transition writes timing, set results, and session position in one transaction. */
class WorkoutSessionRepository(
    private val database: FitnessDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private suspend fun details(id: String): WorkoutDetails =
        requireNotNull(database.workoutDao().getDetails(id)).also {
            require(it.workout.kind == "session" && it.workout.finishedAt == null) { "This session is already finished." }
        }

    suspend fun prepareSession(id: String) = database.withTransaction {
        if (database.sessionStateDao().get(id) != null) return@withTransaction
        val first = details(id).orderedSets().firstOrNull { it.completedAt == null }
        database.sessionStateDao().save(WorkoutSessionState(id, currentSetId = first?.id))
    }

    suspend fun ensureSession(id: String) = database.withTransaction {
        if (database.sessionStateDao().get(id) != null) return@withTransaction
        val workout = details(id)
        val first = workout.orderedSets().firstOrNull { it.completedAt == null }
        val time = now()
        database.sessionStateDao().save(WorkoutSessionState(id,
            phase = if (first == null) "ready" else "active", currentSetId = first?.id,
            phaseStartedAt = if (first == null) null else time, dutyStartedAt = time,
            dutyElapsedMillis = (time - workout.workout.startedAt).coerceAtLeast(0)))
    }

    suspend fun completeSet(id: String, setId: String) = database.withTransaction {
        val workout = details(id)
        val state = requireNotNull(workout.sessionState)
        val set = workout.orderedSets().single { it.id == setId }
        if (set.completedAt != null) return@withTransaction // Ignore a delayed duplicate tap.
        require(!state.isPaused && state.phase == "active" && state.currentSetId == setId) { "This set is not active." }
        val time = now()
        database.workoutSetDao().update(set.copy(completedAt = time, activeMillis = set.activeMillis + state.phaseMillis(time)))
        database.sessionStateDao().save(state.copy(phase = "rest", phaseStartedAt = time,
            phaseElapsedMillis = 0, awaitingActual = true))
    }

    suspend fun recordActual(id: String, setId: String, reps: Int) = database.withTransaction {
        require(reps >= 0) { "Actual reps cannot be negative." }
        val workout = details(id)
        val state = requireNotNull(workout.sessionState)
        val set = workout.orderedSets().single { it.id == setId }
        if (!state.awaitingActual && set.actualReps == reps) return@withTransaction
        require(state.awaitingActual && state.currentSetId == setId && set.completedAt != null) { "This set is not awaiting a result." }
        database.workoutSetDao().update(set.copy(actualReps = reps))
        database.sessionStateDao().save(state.copy(awaitingActual = false))
    }

    private suspend fun saveRest(workout: WorkoutDetails, state: WorkoutSessionState, time: Long) {
        if (state.phase == "rest") {
            val set = workout.orderedSets().single { it.id == state.currentSetId }
            database.workoutSetDao().update(set.copy(restMillis = set.restMillis + state.phaseMillis(time)))
        }
    }

    suspend fun startSet(id: String, setId: String) = database.withTransaction {
        val workout = details(id)
        val state = requireNotNull(workout.sessionState)
        if (state.phase == "active" && state.currentSetId == setId) return@withTransaction
        require(!state.isPaused && !state.awaitingActual && state.phase in listOf("rest", "ready")) {
            "Complete the active set and enter its actual reps before starting another."
        }
        require(workout.orderedSets().any { it.id == setId && it.completedAt == null }) { "This set is already complete." }
        val time = now()
        saveRest(workout, state, time)
        database.sessionStateDao().save(state.copy(phase = "active", currentSetId = setId,
            phaseElapsedMillis = 0, phaseStartedAt = time, dutyStartedAt = state.dutyStartedAt ?: time))
        if (state.dutyStartedAt == null && state.dutyElapsedMillis == 0L && state.phase == "ready") {
            database.workoutDao().update(workout.workout.copy(startedAt = time))
        }
    }

    suspend fun endRest(id: String) = database.withTransaction {
        val workout = details(id)
        val state = requireNotNull(workout.sessionState)
        require(!state.isPaused && !state.awaitingActual && state.phase == "rest")
        require(workout.orderedSets().all { it.completedAt != null }) { "There are still sets to perform." }
        saveRest(workout, state, now())
        database.sessionStateDao().save(state.copy(phase = "ready", currentSetId = null,
            phaseElapsedMillis = 0, phaseStartedAt = null))
    }

    suspend fun pause(id: String) = database.withTransaction {
        val state = requireNotNull(details(id).sessionState)
        if (state.isPaused) return@withTransaction
        val time = now()
        database.sessionStateDao().save(state.copy(isPaused = true, pauseReason = "",
            phaseElapsedMillis = state.phaseMillis(time), phaseStartedAt = null,
            dutyElapsedMillis = state.dutyMillis(time), dutyStartedAt = null))
    }

    suspend fun savePauseReason(id: String, reason: String) = database.withTransaction {
        val state = requireNotNull(details(id).sessionState)
        require(state.isPaused)
        database.sessionStateDao().save(state.copy(pauseReason = reason.trim()))
    }

    suspend fun resume(id: String) = database.withTransaction {
        val state = requireNotNull(details(id).sessionState)
        if (!state.isPaused) return@withTransaction
        val time = now()
        val waitingToStart = state.phase == "ready" && state.dutyElapsedMillis == 0L
        database.sessionStateDao().save(state.copy(isPaused = false, dutyStartedAt = if (waitingToStart) null else time,
            phaseStartedAt = if (state.phase in listOf("active", "rest")) time else null))
    }

    suspend fun finishSession(id: String) = database.withTransaction {
        val workout = requireNotNull(database.workoutDao().getDetails(id))
        if (workout.workout.finishedAt != null) return@withTransaction
        require(workout.workout.kind == "session")
        val state = requireNotNull(workout.sessionState)
        require(!state.awaitingActual) { "Enter the actual reps for your last set before finishing." }
        val time = now()
        saveRest(workout, state, time)
        if (state.phase == "active") {
            val set = workout.orderedSets().single { it.id == state.currentSetId }
            // Preserve time spent on an interrupted set without marking it as performed.
            database.workoutSetDao().update(set.copy(activeMillis = set.activeMillis + state.phaseMillis(time)))
        }
        database.sessionStateDao().save(state.copy(phase = "finished", phaseStartedAt = null,
            phaseElapsedMillis = 0, dutyElapsedMillis = state.dutyMillis(time), dutyStartedAt = null,
            isPaused = false))
        database.workoutDao().update(workout.workout.copy(finishedAt = maxOf(time, workout.workout.startedAt)))
    }
}
