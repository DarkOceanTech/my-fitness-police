package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExerciseEditingTest {
    @Test fun editsSurviveReopenWithoutDuplicatingExerciseOrChangingLinkedSets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "exercise-edit-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            db.exerciseDao().insert(Exercise(id = "row", name = "Seated row", equipment = "Machine",
                description = "Original description", primaryMuscles = "Latissimus dorsi", secondaryMuscles = "Biceps brachii"))
            db.workoutDao().insert(Workout(id = "plan", kind = "plan", name = "Back", startedAt = 1000))
            db.workoutDao().insert(Workout(id = "history", kind = "session", name = "Back session",
                startedAt = 1000, finishedAt = 9000, sourcePlanId = "plan"))
            listOf("plan", "history").forEach { id ->
                db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id,
                    exerciseId = "row", position = 0, notes = "Keep this setup note",
                    equipmentPositions = listOf(EquipmentPosition("Seat", "4"))))
                db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry",
                    position = 0, reps = 10, weightGrams = 20000,
                    completedAt = if (id == "history") 7000 else null,
                    actualReps = if (id == "history") 8 else null,
                    activeMillis = if (id == "history") 6000 else 0,
                    restMillis = if (id == "history") 2000 else 0,
                    notes = "Keep this set note", rpe = if (id == "history") 8 else null))
            }
            val originals = listOf("plan", "history").associateWith { db.workoutDao().getDetails(it)!! }
            FitnessRepository(db).updateExercise("row", "  Seated cable row  ", "  Cable machine  ",
                "  Pull the handles toward the lower ribs.  ", "  Latissimus dorsi; Rhomboids  ",
                "  Biceps brachii; Posterior deltoid  ")
            db.close(); db = open()
            val catalog = db.exerciseDao().getAll()
            assertEquals(1, catalog.size)
            val edited = catalog.single()
            assertEquals("row", edited.id)
            assertEquals("Seated cable row", edited.name)
            assertEquals("Cable machine", edited.equipment)
            assertEquals("Pull the handles toward the lower ribs.", edited.description)
            assertEquals("Latissimus dorsi; Rhomboids", edited.primaryMuscles)
            assertEquals("Biceps brachii; Posterior deltoid", edited.secondaryMuscles)
            assertFalse(edited.isArchived)
            assertEquals(listOf(edited), FitnessRepository(db).observeExercises().first())
            originals.forEach { (id, original) ->
                val reloaded = db.workoutDao().getDetails(id)!!
                assertEquals(original.workout, reloaded.workout)
                assertEquals(original.exercises.single().workoutExercise, reloaded.exercises.single().workoutExercise)
                assertEquals(original.orderedSets(), reloaded.orderedSets())
                assertEquals(edited, reloaded.exercises.single().exercise)
            }
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun invalidAndMissingEditsDoNotInsertRowsOrUnarchiveAnExercise() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            FitnessDatabase::class.java).build()
        try {
            val original = Exercise(id = "archived", name = "Original", equipment = "Bodyweight", isArchived = true)
            db.exerciseDao().insert(original)
            val repo = FitnessRepository(db)
            assertTrue(runCatching { repo.updateExercise("archived", "  ", "Cable", "Description", "Back", "") }.isFailure)
            val missing = runCatching { repo.updateExercise("missing", "New", "Cable", "Description", "Back", "") }.exceptionOrNull()
            assertTrue(missing is IllegalArgumentException)
            assertTrue(missing!!.message!!.contains("no longer available"))
            assertEquals(listOf(original), db.exerciseDao().getAll())
            repo.updateExercise("archived", "Renamed", "Bodyweight", "Updated description", "Rectus abdominis", "")
            val edited = db.exerciseDao().getAll().single()
            assertEquals("archived", edited.id)
            assertEquals("Renamed", edited.name)
            assertTrue(edited.isArchived)
            assertTrue(repo.observeExercises().first().isEmpty())
        } finally { db.close() }
    }
}
