package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AfterSetResultTest {
    @Test fun formTimeIsBreakTimeAndResultsSurviveReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "after-set-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        var time = 1_000L
        try {
            seed(db)
            var progress = WorkoutSessionRepository(db) { time }
            progress.prepareSession("session")
            time = 10_000
            progress.startSet("session", "s1")
            assertTrue(runCatching { progress.cancelUnstartedSession("session") }.isFailure)
            time = 40_000
            progress.completeSet("session", "s1")
            time = 70_000 // Time spent entering reps, effort, and a note belongs to the break.
            assertEquals(30_000L, db.sessionStateDao().get("session")!!.phaseMillis(time))
            assertTrue(runCatching { progress.recordActual("session", "s1", 8, 11, "Invalid") }.isFailure)
            assertTrue(db.sessionStateDao().get("session")!!.awaitingActual)
            progress.recordActual("session", "s1", 8, 7, " Controlled lowering ")
            progress.recordActual("session", "s1", 8, 7, " Controlled lowering ")
            db.close(); db = open(); progress = WorkoutSessionRepository(db) { time }
            val result = db.workoutSetDao().getForExercise("entry").first()
            assertEquals(8, result.actualReps)
            assertEquals(7, result.rpe)
            assertEquals("Controlled lowering", result.notes)
            assertEquals(30_000L, result.activeMillis)
            assertEquals(40_000L, db.sessionStateDao().get("session")!!.phaseStartedAt)
            time = 85_000
            progress.startSet("session", "s2")
            assertEquals(45_000L, db.workoutSetDao().getForExercise("entry").first().restMillis)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun cancellationDeletesOnlyUnstartedSessionAndProtectsStartedSessions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            seed(db)
            db.workoutDao().insert(Workout(id = "plan", kind = "plan"))
            val progress = WorkoutSessionRepository(db) { 1000L }
            progress.prepareSession("session")
            assertFalse(db.sessionStateDao().get("session")!!.hasStarted)
            progress.cancelUnstartedSession("session")
            assertNull(db.workoutDao().getDetails("session"))
            assertNull(db.sessionStateDao().get("session"))
            assertTrue(db.workoutSetDao().getForExercise("entry").isEmpty())
            assertNotNull(db.workoutDao().getDetails("plan"))
            assertEquals(1, db.exerciseDao().getAll().size)
        } finally { db.close() }
    }

    private suspend fun seed(db: FitnessDatabase) {
        db.exerciseDao().insert(Exercise(id = "exercise", name = "Row", equipment = "Cable"))
        db.workoutDao().insert(Workout(id = "session", startedAt = 1000))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "entry", workoutId = "session", exerciseId = "exercise", position = 0))
        (1..2).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "entry", position = it - 1, reps = 10, weightGrams = 10000)) }
    }
}
