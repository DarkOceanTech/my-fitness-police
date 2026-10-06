package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActiveExerciseSwapTest {
    private lateinit var db: FitnessDatabase
    private lateinit var repo: FitnessRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        repo = FitnessRepository(db)
        for (id in listOf("row", "replacement", "other")) db.exerciseDao().insert(Exercise(id, "$id exercise", "Cable"))
        for (id in listOf("saved", "live", "history")) {
            db.workoutDao().insert(Workout(id = id, name = "Back patrol", kind = if (id == "saved") "plan" else "session", startedAt = 1,
                finishedAt = if (id == "history") 100 else null, sourceTrainingPlanId = "training-plan"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-e", workoutId = id, exerciseId = if (id == "history") "replacement" else "row",
                position = 0, notes = "$id note", equipmentPositions = listOf(EquipmentPosition("Seat", "3")), sourceWorkoutId = "saved", sourceWorkoutName = "Original back"))
            for (index in 0..1) db.workoutSetDao().insert(WorkoutSet(id = "$id-$index", workoutExerciseId = "$id-e", position = index,
                reps = 8 + index, weightGrams = 560170L + index, isWarmup = index == 0, modifier = if (index == 1) "drop_set" else "none",
                completedAt = if (id == "history") 50 else null, actualReps = if (id == "history") 6 else null,
                rpe = if (id == "history") 8 else null, notes = if (id == "history") "Historical set note" else "",
                activeMillis = if (id == "history") 500 else 0, restMillis = if (id == "history") 1500 else 0))
        }
        db.workoutExerciseDao().insert(WorkoutExercise("live-other", "live", "other", 1))
        db.workoutSetDao().insert(WorkoutSet("other-set", "live-other", 0, 10, 0))
        repo.sessionProgress.prepareSession("live")
    }
    @After fun cleanup() { db.close() }
    private suspend fun details(id: String) = db.workoutDao().getDetails(id)!!
    private suspend fun entry() = details("live").exercises.single { it.workoutExercise.id == "live-e" }
    private suspend fun reject(block: suspend () -> Unit) {
        val before = details("live")
        try { block(); fail("Swap should be rejected") } catch (_: IllegalArgumentException) { }
        assertEquals("Rejected swaps must not change any data", before, details("live"))
    }
    @Test fun copiesCurrentPrescriptionsPreciselyAndPreservesSessionAndSource() = runBlocking {
        val before = details("live"); val saved = details("saved"); val history = details("history")
        repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.CURRENT)
        assertEquals(before.orderedSets(), details("live").orderedSets())
        assertEquals(before.sessionState, details("live").sessionState)
        val changed = entry().workoutExercise
        assertEquals(before.exercises.first().workoutExercise.copy(exerciseId = "replacement", notes = "", equipmentPositions = emptyList()), changed)
        assertEquals(saved, details("saved")); assertEquals(history, details("history"))
    }
    @Test fun lastSessionImportsOnlyPrescriptionsAndRepairsReadyPointer() = runBlocking {
        val history = details("history")
        val snapshot = repo.activeExerciseSwap.preview("replacement")!!
        assertEquals(history.exercises.single(), snapshot.entry)
        repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.LAST_SESSION, snapshot.entry.workoutExercise.id)
        val sets = entry().sets.sortedBy { it.position }
        assertEquals(listOf(8, 9), sets.map { it.reps })
        assertEquals(listOf(560170L, 560171L), sets.map { it.weightGrams })
        assertEquals(listOf(true, false), sets.map { it.isWarmup })
        assertEquals(listOf("none", "drop_set"), sets.map { it.modifier })
        assertTrue(sets.all { it.actualReps == null && it.rpe == null && it.notes.isEmpty() && it.completedAt == null && it.activeMillis == 0L && it.restMillis == 0L })
        assertTrue(sets.none { it.id in listOf("live-0", "live-1", "history-0", "history-1") })
        assertEquals(sets.first().id, details("live").sessionState!!.currentSetId)
        assertEquals("ready", details("live").sessionState!!.phase)
        assertEquals(history, details("history"))
    }
    @Test fun freshSetsOnFutureExerciseDoNotInterruptAnotherExercisesTimer() = runBlocking {
        db.sessionStateDao().save(WorkoutSessionState("live", phase = "active", currentSetId = "other-set", phaseStartedAt = 1000, dutyStartedAt = 1000))
        val timer = details("live").sessionState
        val other = details("live").exercises.single { it.workoutExercise.id == "live-other" }
        repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.NEW)
        val fresh = entry().sets.single()
        assertEquals(10, fresh.reps); assertEquals(0L, fresh.weightGrams); assertFalse(fresh.isWarmup)
        assertEquals(0, fresh.position); assertEquals("none", fresh.modifier)
        assertEquals(timer, details("live").sessionState)
        assertEquals(other, details("live").exercises.single { it.workoutExercise.id == "live-other" })
    }
    @Test fun startedAndPausedAndRecordedExercisesCannotSwap() = runBlocking {
        for (paused in listOf(false, true)) {
            db.sessionStateDao().save(WorkoutSessionState("live", phase = "active", currentSetId = "live-0", isPaused = paused))
            reject { repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.NEW) }
        }
        db.sessionStateDao().save(WorkoutSessionState("live", phase = "rest", currentSetId = "live-0"))
        db.workoutSetDao().update(entry().sets.first().copy(completedAt = 50, actualReps = 8))
        reject { repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.CURRENT) }
        db.workoutSetDao().update(entry().sets.first().copy(completedAt = null, actualReps = null, activeMillis = 1000))
        reject { repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.CURRENT) }
    }
    @Test fun staleHistoryAndWrongExerciseAndInvalidReplacementFailBeforeWrites() = runBlocking {
        reject { repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.LAST_SESSION, "missing") }
        reject { repo.activeExerciseSwap.swap("live", "live-e", "replacement", SwapSetSource.LAST_SESSION, "saved-e") }
        reject { repo.activeExerciseSwap.swap("live", "live-e", "other", SwapSetSource.LAST_SESSION, "history-e") }
        reject { repo.activeExerciseSwap.swap("live", "live-e", "row", SwapSetSource.NEW) }
        reject { repo.activeExerciseSwap.swap("live", "live-e", "missing", SwapSetSource.NEW) }
        assertNull(repo.activeExerciseSwap.preview("row"))
    }
}
