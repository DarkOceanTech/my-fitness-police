package com.example.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutSessionTimingTest {
    @Test fun workRestAndPauseDurationsSurviveReopenAndExcludePausedTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "timing-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        var time = 1000L
        var progress = WorkoutSessionRepository(db) { time }
        val id = "session"
        try {
            db.workoutDao().insert(Workout(id = id, startedAt = time))
            db.exerciseDao().insert(Exercise(id = "exercise", name = "Row", equipment = "Cable"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "entry", workoutId = id, exerciseId = "exercise", position = 0))
            (1..3).forEach { db.workoutSetDao().insert(WorkoutSet(id = "s$it", workoutExerciseId = "entry", position = it - 1, reps = 10, weightGrams = 10000)) }
            progress.ensureSession(id)
            assertEquals("s1", db.sessionStateDao().get(id)!!.currentSetId)
            assertTrue(runCatching { progress.startSet(id, "s2") }.isFailure)
            time = 5000
            progress.completeSet(id, "s1")
            progress.completeSet(id, "s1") // A double tap must not restart the break.
            assertEquals(4000L, db.workoutSetDao().getForExercise("entry").first().activeMillis)
            assertEquals(5000L, db.sessionStateDao().get(id)!!.phaseStartedAt)
            assertTrue(runCatching { progress.startSet(id, "s2") }.isFailure)
            assertTrue(runCatching { progress.finishSession(id) }.isFailure)
            db.close(); db = open(); progress = WorkoutSessionRepository(db) { time }
            assertTrue(db.sessionStateDao().get(id)!!.awaitingActual)
            time = 6500
            progress.recordActual(id, "s1", 8)
            val first = db.workoutSetDao().getForExercise("entry").first()
            assertEquals(10, first.reps)
            assertEquals(8, first.actualReps)
            time = 9000
            progress.pause(id)
            progress.savePauseReason(id, "Phone call")
            time = 69000
            db.close(); db = open(); progress = WorkoutSessionRepository(db) { time }
            val paused = db.sessionStateDao().get(id)!!
            assertTrue(paused.isPaused)
            assertEquals("Phone call", paused.pauseReason)
            assertEquals(8000L, paused.dutyMillis(time))
            assertEquals(4000L, paused.phaseMillis(time))
            assertTrue(runCatching { progress.startSet(id, "s2") }.isFailure)
            progress.resume(id)
            time = 71000
            progress.startSet(id, "s2")
            assertEquals(6000L, db.workoutSetDao().getForExercise("entry").first().restMillis)
            time = 74000
            progress.pause(id)
            progress.savePauseReason(id, "Doorbell")
            time = 84000
            progress.resume(id)
            time = 86000
            progress.completeSet(id, "s2")
            progress.recordActual(id, "s2", 0)
            time = 87000
            progress.finishSession(id)
            val result = db.workoutDao().getDetails(id)!!
            assertEquals(16000L, result.sessionState!!.dutyMillis(time + 100000))
            assertEquals("finished", result.sessionState.phase)
            assertEquals(2, result.performedSets().size)
            val second = result.orderedSets()[1]
            assertEquals(0, second.actualReps)
            assertEquals(5000L, second.activeMillis)
            assertEquals(1000L, second.restMillis)
            assertNull(result.orderedSets()[2].completedAt)
            assertNull(result.orderedSets()[2].actualReps)
            assertTrue(runCatching { progress.startSet(id, "s3") }.isFailure)
            db.workoutDao().delete(id)
            assertNull(db.sessionStateDao().get(id))
            assertTrue(db.workoutSetDao().getForExercise("entry").isEmpty())
            assertEquals(1, db.exerciseDao().getAll().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun finalBreakCanBeStoppedWithoutFinishingAndTimersResumeAfterProcessDeath() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        var time = 1000L
        val progress = WorkoutSessionRepository(db) { time }
        try {
            db.workoutDao().insert(Workout(id = "w", startedAt = time))
            db.exerciseDao().insert(Exercise(id = "e", name = "Press", equipment = "Barbell"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "we", workoutId = "w", exerciseId = "e", position = 0))
            db.workoutSetDao().insert(WorkoutSet(id = "s", workoutExerciseId = "we", position = 0, reps = 8, weightGrams = 20000))
            progress.ensureSession("w")
            time = 5000
            // A new repository resumes persisted anchors; ensure must not restart the active set.
            WorkoutSessionRepository(db) { time }.ensureSession("w")
            assertEquals(4000L, db.sessionStateDao().get("w")!!.phaseMillis(time))
            progress.completeSet("w", "s")
            progress.recordActual("w", "s", 8)
            time = 7000
            progress.endRest("w")
            assertEquals("ready", db.sessionStateDao().get("w")!!.phase)
            assertEquals(2000L, db.workoutSetDao().getForExercise("we").single().restMillis)
            assertNull(db.workoutDao().getDetails("w")!!.workout.finishedAt)
            time = 8000
            progress.finishSession("w")
            assertEquals(7000L, db.sessionStateDao().get("w")!!.dutyElapsedMillis)
        } finally { db.close() }
    }
}
