package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HistoryExerciseReplacementTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()

    private suspend fun seed(db: FitnessDatabase) {
        db.exerciseDao().insert(Exercise(id = "original", name = "Cable row", equipment = "Cable"))
        db.exerciseDao().insert(Exercise(id = "replacement", name = "Machine row", equipment = "Machine"))
        db.exerciseDao().insert(Exercise(id = "archived", name = "Archived row", equipment = "Machine", isArchived = true))
        listOf("plan", "history", "other", "active").forEach { id ->
            val completed = id == "history" || id == "other"
            db.workoutDao().insert(Workout(id = id, name = id, kind = if (id == "plan") "plan" else "session",
                startedAt = 1000, finishedAt = if (completed) 9000 else null,
                sourcePlanId = if (id != "plan") "plan" else null))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = "original",
                position = 0, notes = "Exercise setup note", equipmentPositions = listOf(EquipmentPosition("Seat", "4")),
                sourceWorkoutId = if (id != "plan") "plan" else null,
                sourceWorkoutName = if (id != "plan") "Original saved workout" else ""))
            db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0,
                reps = 10, weightGrams = 22000, isWarmup = true, modifier = "drop_set",
                completedAt = if (completed) 6000 else null, actualReps = if (completed) 8 else null,
                activeMillis = if (completed) 5000 else 0, restMillis = if (completed) 3000 else 0,
                notes = "Keep my set note", rpe = if (completed) 7 else null))
            if (completed) db.sessionStateDao().save(WorkoutSessionState(workoutId = id, phase = "finished",
                currentSetId = "$id-set", dutyElapsedMillis = 8000))
        }
    }

    @Test fun replacementSurvivesReopenAndPreservesEveryRecordedFieldAndReference() = runBlocking {
        val filename = "history-exercise-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            seed(db)
            val original = db.workoutDao().getDetails("history")!!
            val untouched = listOf("plan", "other", "active").associateWith { db.workoutDao().getDetails(it)!! }
            val catalog = db.exerciseDao().getAll()
            val repo = FitnessRepository(db)
            repo.replaceHistoryExercise("history", "history-entry", "replacement")
            repo.replaceHistoryExercise("history", "history-entry", "replacement") // A duplicate Save cannot duplicate records.
            db.close(); db = open()
            val replaced = db.workoutDao().getDetails("history")!!
            assertEquals(original.workout, replaced.workout)
            assertEquals(original.sessionState, replaced.sessionState)
            assertEquals(original.orderedSets(), replaced.orderedSets())
            assertEquals(original.exercises.single().workoutExercise.copy(exerciseId = "replacement"),
                replaced.exercises.single().workoutExercise)
            assertEquals("Machine row", replaced.exercises.single().exercise.name)
            assertEquals("plan", replaced.exercises.single().workoutExercise.sourceWorkoutId)
            assertEquals("Original saved workout", replaced.exercises.single().workoutExercise.sourceWorkoutName)
            assertEquals(catalog, db.exerciseDao().getAll())
            untouched.forEach { (id, expected) -> assertEquals(expected, db.workoutDao().getDetails(id)) }
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun replacementRejectsWrongSessionEntryMissingAndArchivedTargetsWithoutChanges() = runBlocking {
        val db = memoryDb()
        try {
            seed(db)
            val repo = FitnessRepository(db)
            val original = repo.observeSessions().first()
            val invalid = listOf(
                Triple("plan", "plan-entry", "replacement"),
                Triple("active", "active-entry", "replacement"),
                Triple("missing", "history-entry", "replacement"),
                Triple("history", "other-entry", "replacement"),
                Triple("history", "history-entry", "missing"),
                Triple("history", "history-entry", "archived"),
            )
            invalid.forEach { (workout, entry, exercise) ->
                assertTrue(runCatching { repo.replaceHistoryExercise(workout, entry, exercise) }.exceptionOrNull() is IllegalArgumentException)
                assertEquals(original, repo.observeSessions().first())
            }
        } finally { db.close() }
    }

    @Test fun newSessionProvenanceSurvivesSourceRenameMembershipChangesAndDeletion() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Cable"))
            listOf("a", "b").forEach { id ->
                db.workoutDao().insert(Workout(id = id, kind = "plan", name = if (id == "a") "Back heavy" else "",
                    targetMuscles = if (id == "b") "Biceps" else "Back"))
                db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = "row", position = 0))
                db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0, reps = 10, weightGrams = 10000))
            }
            val trainingId = repo.trainingPlans.save(null, "Mixed day", listOf("a", "b"))
            val groupedId = repo.startTraining(trainingId)
            val grouped = db.workoutDao().getDetails(groupedId)!!
            assertEquals(listOf("a", "b"), grouped.orderedExercises().map { it.workoutExercise.sourceWorkoutId })
            assertEquals(listOf("Back heavy", "Biceps"), grouped.orderedExercises().map { it.workoutExercise.sourceWorkoutName })
            for (set in grouped.orderedSets()) {
                repo.sessionProgress.startSet(groupedId, set.id)
                repo.sessionProgress.completeSet(groupedId, set.id)
                repo.sessionProgress.recordActual(groupedId, set.id, 10)
            }
            repo.finishWorkout(groupedId)
            val legacyId = repo.startPlan("b")
            val legacy = db.workoutDao().getDetails(legacyId)!!
            assertEquals("b", legacy.exercises.single().workoutExercise.sourceWorkoutId)
            assertEquals("Biceps", legacy.exercises.single().workoutExercise.sourceWorkoutName)
            repo.setWorkoutDetails("name", "Renamed source", "a")
            repo.trainingPlans.save(trainingId, "Different day", listOf("b"))
            repo.trainingPlans.delete(trainingId)
            repo.deletePlan("a")
            repo.deletePlan("b")
            assertEquals(grouped.exercises.map { it.workoutExercise }, db.workoutDao().getDetails(groupedId)!!.exercises.map { it.workoutExercise })
            assertEquals(legacy, db.workoutDao().getDetails(legacyId))
            assertEquals("Mixed day", db.workoutDao().getDetails(groupedId)!!.workout.trainingPlan)
        } finally { db.close() }
    }

    @Test fun repeatedPickerAddIsIdempotentAndRemoveThenAddCreatesOneFreshSet() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            db.exerciseDao().insert(Exercise(id = "row", name = "Row", equipment = "Cable"))
            repo.chooseExercise("row")
            val draft = repo.observeSessions().first().single()
            val entry = draft.exercises.single()
            repo.updateSetFields(draft.workout.id, entry.workoutExercise.id, entry.sets.single().id, reps = 15)
            repo.chooseExercise("row", draft.workout.id)
            val unchanged = db.workoutDao().getDetails(draft.workout.id)!!
            assertEquals(1, unchanged.exercises.size)
            assertEquals(1, unchanged.orderedSets().size)
            assertEquals(15, unchanged.orderedSets().single().reps)
            repo.removeWorkoutExercise(draft.workout.id, entry.workoutExercise.id)
            repo.chooseExercise("row", draft.workout.id)
            val readded = db.workoutDao().getDetails(draft.workout.id)!!
            assertEquals(1, readded.exercises.size)
            assertEquals(0, readded.exercises.single().workoutExercise.position)
            assertEquals(10, readded.orderedSets().single().reps)
            assertNotEquals(entry.sets.single().id, readded.orderedSets().single().id)
        } finally { db.close() }
    }
}
