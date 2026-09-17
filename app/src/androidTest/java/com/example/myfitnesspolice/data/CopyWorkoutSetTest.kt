package com.example.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CopyWorkoutSetTest {
    @Test fun copyUsesLastPositionAndRejectsEmptyForeignAndSessionEntries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        suspend fun rejected(block: suspend () -> Unit) {
            try { block(); fail("Expected invalid copy to be rejected") } catch (_: IllegalArgumentException) { }
        }
        try {
            val repo = FitnessRepository(db)
            val exercise = Exercise(name = "Row", equipment = "Cable")
            db.exerciseDao().insert(exercise)
            val draft = Workout(kind = "draft")
            val other = Workout(kind = "plan")
            val session = Workout(kind = "session")
            listOf(draft, other, session).forEach { db.workoutDao().insert(it) }
            val entry = WorkoutExercise(workoutId = draft.id, exerciseId = exercise.id, position = 0)
            val foreign = WorkoutExercise(workoutId = other.id, exerciseId = exercise.id, position = 0)
            val active = WorkoutExercise(workoutId = session.id, exerciseId = exercise.id, position = 0)
            listOf(entry, foreign, active).forEach { db.workoutExerciseDao().insert(it) }
            rejected { repo.copyLastSet(draft.id, entry.id) }
            val last = WorkoutSet(workoutExerciseId = entry.id, position = 4, reps = 7, weightGrams = 33333,
                modifier = "superset", isWarmup = true, completedAt = 1000, actualReps = 5,
                notes = "Recorded result", rpe = 8, activeMillis = 700, restMillis = 900)
            db.workoutSetDao().insert(last)
            db.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entry.id, position = 0, reps = 20, weightGrams = 0))
            rejected { repo.copyLastSet(other.id, entry.id) }
            rejected { repo.copyLastSet(draft.id, foreign.id) }
            rejected { repo.copyLastSet(session.id, active.id) }
            repo.copyLastSet(draft.id, entry.id)
            repo.copyLastSet(draft.id, entry.id)
            val rows = db.workoutSetDao().getForExercise(entry.id).sortedBy { it.position }
            assertEquals(listOf(0, 4, 5, 6), rows.map { it.position })
            assertEquals(last, rows[1])
            rows.takeLast(2).forEach { copied ->
                assertEquals(7, copied.reps)
                assertEquals(33333L, copied.weightGrams)
                assertEquals("superset", copied.modifier)
                assertTrue(copied.isWarmup)
                assertNull(copied.completedAt)
                assertNull(copied.actualReps)
                assertNull(copied.rpe)
                assertEquals("", copied.notes)
                assertEquals(0L, copied.activeMillis)
                assertEquals(0L, copied.restMillis)
            }
            assertEquals(4, rows.map { it.id }.distinct().size)
            assertTrue(db.workoutSetDao().getForExercise(foreign.id).isEmpty())
            assertTrue(db.workoutSetDao().getForExercise(active.id).isEmpty())
        } finally { db.close() }
    }
}
