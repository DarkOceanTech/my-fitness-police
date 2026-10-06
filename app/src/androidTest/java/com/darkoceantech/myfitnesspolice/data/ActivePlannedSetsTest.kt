package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActivePlannedSetsTest {
    private lateinit var db: FitnessDatabase
    private lateinit var repo: FitnessRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        repo = FitnessRepository(db)
        db.exerciseDao().insert(Exercise(id = "e", name = "Cable row", equipment = "Cable"))
        for (id in listOf("plan", "live", "history")) {
            db.workoutDao().insert(Workout(id = id, kind = if (id == "plan") "plan" else "session",
                startedAt = 1, finishedAt = if (id == "history") 100 else null))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-e", workoutId = id, exerciseId = "e", position = 0,
                notes = "Exercise note", equipmentPositions = listOf(EquipmentPosition("Seat", "3"))))
            for (position in 0..1) db.workoutSetDao().insert(WorkoutSet(id = "$id-$position", workoutExerciseId = "$id-e",
                position = position, reps = 10, weightGrams = 20000,
                completedAt = if (id == "history" || (id == "live" && position == 0)) 50 else null,
                actualReps = if (id == "history" || (id == "live" && position == 0)) 9 else null,
                rpe = 7, notes = "Set note", activeMillis = 1234, restMillis = 5678))
        }
        db.sessionStateDao().save(WorkoutSessionState("live", phase = "active", currentSetId = "live-1",
            phaseStartedAt = 1000, dutyStartedAt = 1000))
    }
    @After fun cleanup() { db.close() }
    private suspend fun details(id: String) = db.workoutDao().getDetails(id)!!
    @Test fun editsOnlySessionPrescriptionAndPreservesResultsTimingAndSource() = runBlocking {
        val plan = details("plan")
        val before = details("live")
        val timer = db.sessionStateDao().get("live")
        repo.editActivePlannedSets("live", "live-e", listOf(PlannedSetUpdate("live-1", 12, 25000, true, "drop_set")))
        val after = details("live")
        assertEquals(before.orderedSets()[0], after.orderedSets()[0])
        assertEquals(before.orderedSets()[1].copy(reps = 12, weightGrams = 25000, isWarmup = true, modifier = "drop_set"), after.orderedSets()[1])
        assertEquals(before.exercises.single().workoutExercise, after.exercises.single().workoutExercise)
        assertEquals(timer, db.sessionStateDao().get("live"))
        assertEquals(plan, details("plan"))
    }
    @Test fun rejectsCompletedForeignDuplicateAndInvalidRowsAtomically() = runBlocking {
        val before = details("live")
        val valid = PlannedSetUpdate("live-1", 12, 10000, false, "none")
        for (changes in listOf(listOf(valid, valid.copy(id = "live-0")), listOf(valid, valid.copy(id = "plan-1")),
            listOf(valid, valid), listOf(valid.copy(reps = 0)))) {
            try { repo.editActivePlannedSets("live", "live-e", changes); fail("Expected rejection") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, details("live"))
        }
        try { repo.editActivePlannedSets("history", "history-e", listOf(valid)); fail("Expected recorded-session rejection") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, details("live"))
    }
    @Test fun activeSessionCanPreviewButCannotImportOrReplaceItsSets() = runBlocking {
        val before = details("live")
        val snapshot = repo.getLastSession("live", "live-e")
        assertEquals("history", snapshot.workout.workout.id)
        assertEquals(details("history").exercises.single(), snapshot.entry)
        assertEquals(before, details("live"))
        try { repo.importLastSession("live", "live-e"); fail("Expected import rejection") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, details("live"))
    }
}
