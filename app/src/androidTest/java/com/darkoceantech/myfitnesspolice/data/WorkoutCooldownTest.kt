package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutCooldownTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun database() = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()

    @Test fun onlyNextUnfinishedSetCanStartAcrossExercises() = runBlocking {
        val db = database()
        var time = 1000L
        val progress = WorkoutSessionRepository(db) { time }
        try {
            seed(db, exercises = 2, sets = 2)
            progress.prepareSession("w")
            assertTrue(runCatching { progress.startSet("w", "s0-1") }.isFailure)
            assertTrue(runCatching { progress.startSet("w", "s1-0") }.isFailure)
            for ((index, id) in listOf("s0-0", "s0-1", "s1-0", "s1-1").withIndex()) {
                progress.startSet("w", id)
                if (id != "s1-1") assertTrue(runCatching { progress.startSet("w", "s1-1") }.isFailure)
                time += 10_000
                progress.completeSet("w", id)
                assertTrue(runCatching { progress.startSet("w", "s1-1") }.isFailure)
                progress.recordActual("w", id, 9)
                assertEquals(if (index == 3) "cooldown" else "rest", db.sessionStateDao().get("w")!!.phase)
                if (index == 0) assertTrue(runCatching { progress.startSet("w", "s1-0") }.isFailure)
                time += 2000
            }
            assertTrue(runCatching { progress.startSet("w", "s0-0") }.isFailure)
        } finally { db.close() }
    }

    @Test fun finalFormCanOutlastCooldownWithoutLosingResultsOrOvercountingBreak() = runBlocking {
        val db = database()
        var time = 1000L
        val progress = WorkoutSessionRepository(db) { time }
        try {
            seed(db)
            progress.prepareSession("w")
            progress.startSet("w", "s0-0")
            time = 11_000
            progress.completeSet("w", "s0-0")
            time = 120_000
            val pending = db.sessionStateDao().get("w")!!
            assertEquals(60_000L, pending.phaseMillis(time))
            assertEquals(70_000L, pending.dutyMillis(time))
            assertEquals(71_000L, pending.cooldownDeadlineMillis())
            assertFalse(progress.finishCooldownIfReady("w"))
            assertNull(db.workoutDao().getDetails("w")!!.workout.finishedAt)
            progress.recordActual("w", "s0-0", 8, 7, " Last set felt strong ")
            assertTrue(progress.finishCooldownIfReady("w"))
            val result = db.workoutDao().getDetails("w")!!
            assertEquals(71_000L, result.workout.finishedAt)
            assertEquals(70_000L, result.sessionState!!.dutyMillis(time))
            val set = result.orderedSets().single()
            assertEquals(60_000L, set.restMillis)
            assertEquals(10_000L, set.activeMillis)
            assertEquals(8, set.actualReps)
            assertEquals(7, set.rpe)
            assertEquals("Last set felt strong", set.notes)
            assertFalse(progress.finishCooldownIfReady("w"))
            progress.finishSession("w")
            assertEquals(result, db.workoutDao().getDetails("w"))
        } finally { db.close() }
    }

    @Test fun cooldownPausesAndSurvivesReopenThenFinishesAtPersistedDeadline() = runBlocking {
        val name = "cooldown-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        var time = 1000L
        var progress = WorkoutSessionRepository(db) { time }
        try {
            seed(db)
            progress.prepareSession("w")
            progress.startSet("w", "s0-0")
            time = 11_000
            progress.completeSet("w", "s0-0")
            progress.recordActual("w", "s0-0", 10)
            time = 31_000
            progress.pause("w")
            db.close(); db = open(); progress = WorkoutSessionRepository(db) { time }
            time = 200_000
            assertFalse(progress.finishCooldownIfReady("w"))
            assertEquals(40_000L, db.sessionStateDao().get("w")!!.cooldownRemainingMillis(time))
            progress.resume("w")
            time = 239_999
            assertFalse(progress.finishCooldownIfReady("w"))
            db.close(); db = open(); progress = WorkoutSessionRepository(db) { time }
            time = 400_000 // The app was closed beyond the deadline; no extra break is recorded.
            assertTrue(progress.finishCooldownIfReady("w"))
            val result = db.workoutDao().getDetails("w")!!
            assertEquals(240_000L, result.workout.finishedAt)
            assertEquals(60_000L, result.orderedSets().single().restMillis)
            assertEquals(70_000L, result.sessionState!!.dutyMillis(time))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private suspend fun seed(db: FitnessDatabase, exercises: Int = 1, sets: Int = 1) {
        db.workoutDao().insert(Workout(id = "w", startedAt = 1000))
        repeat(exercises) { exercise ->
            db.exerciseDao().insert(Exercise(id = "e$exercise", name = "Row $exercise", equipment = "Cable"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "we$exercise", workoutId = "w", exerciseId = "e$exercise", position = exercise))
            repeat(sets) { set -> db.workoutSetDao().insert(WorkoutSet(id = "s$exercise-$set", workoutExerciseId = "we$exercise",
                position = set, reps = 10, weightGrams = 10000)) }
        }
    }
}
