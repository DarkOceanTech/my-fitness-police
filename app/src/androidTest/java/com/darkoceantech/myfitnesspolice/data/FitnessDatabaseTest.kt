package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FitnessDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun seedAndWorkoutSurviveReopenAndDeleteCascades() = runBlocking {
        val name = "test-" + UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name)
            .addCallback(SeedExercises).build()
        var db = open()
        try {
            val catalog = db.exerciseDao().getAll()
            assertEquals(48, catalog.size)
            val exercise = catalog.first()
            db.exerciseDao().archive(exercise.id)
            val workout = Workout(startedAt = 1000L, finishedAt = 2000L)
            db.workoutDao().insert(workout)
            val entry = WorkoutExercise(workoutId = workout.id, exerciseId = exercise.id, position = 0)
            db.workoutExerciseDao().insert(entry)
            val set = WorkoutSet(workoutExerciseId = entry.id, position = 0,
                reps = 5, weightGrams = 60000L, completedAt = 1500L)
            db.workoutSetDao().insert(set)
            db.close()
            db = open()
            assertEquals(48, db.exerciseDao().getAll().size)
            assertTrue(db.exerciseDao().getAll().first { it.id == exercise.id }.isArchived)
            val loaded = db.workoutDao().getDetails(workout.id)!!
            assertEquals(workout, loaded.workout)
            assertEquals(set, loaded.exercises.single().sets.single())
            db.workoutDao().delete(workout.id)
            assertNull(db.workoutDao().getDetails(workout.id))
            assertTrue(db.workoutSetDao().getForExercise(entry.id).isEmpty())
            assertEquals(48, db.exerciseDao().getAll().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun rejectsOrphanSetsAndDuplicatePositions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java)
            .addCallback(SeedExercises).build()
        try {
            var rejected = false
            try {
                db.workoutSetDao().insert(WorkoutSet(workoutExerciseId = "missing",
                    position = 0, reps = 5, weightGrams = 0))
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                rejected = true
            }
            assertTrue("An orphan set must be rejected", rejected)
            val workout = Workout()
            db.workoutDao().insert(workout)
            val entry = WorkoutExercise(workoutId = workout.id,
                exerciseId = db.exerciseDao().getAll().first().id, position = 0)
            db.workoutExerciseDao().insert(entry)
            db.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entry.id,
                position = 0, reps = 5, weightGrams = 0))
            rejected = false
            try {
                db.workoutSetDao().insert(WorkoutSet(workoutExerciseId = entry.id,
                    position = 0, reps = 8, weightGrams = 0))
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                rejected = true
            }
            assertTrue("Set positions must be unique within an exercise", rejected)
        } finally {
            db.close()
        }
    }
}

