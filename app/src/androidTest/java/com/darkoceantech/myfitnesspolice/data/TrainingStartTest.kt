package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TrainingStartTest {
    @Test fun orderedTrainingSnapshotWaitsForManualStartAndSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "training-start-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        var repo = FitnessRepository(db)
        try {
            repo.addExercise("Cable row", "Cable")
            val exercise = repo.observeExercises().first().single().id
            val modules = listOf("Back — Heavy", "Back — Light").mapIndexed { index, name ->
                repo.chooseExercise(exercise)
                val draft = repo.observeSessions().first().single { it.workout.kind == "draft" }
                val entry = draft.exercises.single()
                repo.updateSetFields(draft.workout.id, entry.workoutExercise.id, entry.sets.single().id,
                    reps = 8 + index, grams = 20000L - index * 10000L)
                repo.updateExerciseNote(draft.workout.id, entry.workoutExercise.id, "Setup $index")
                repo.saveEquipmentPositions(draft.workout.id, entry.workoutExercise.id, listOf(EquipmentPosition("Seat", "$index")))
                repo.savePlan(draft.workout.id, name)
                draft.workout.id
            }
            val training = repo.trainingPlans.save(null, "Back day", modules.reversed(), "Thursday")
            val otherTraining = repo.trainingPlans.save(null, "Recovery", listOf(modules[0]))
            val templates = modules.map { db.workoutDao().getDetails(it)!! }
            val id = repo.startTraining(training)
            var session = db.workoutDao().getDetails(id)!!
            assertEquals(training, session.workout.sourceTrainingPlanId)
            assertNull(session.workout.sourcePlanId)
            assertEquals("Back day", session.workout.name)
            assertEquals("Thursday", session.workout.dayOfWeek)
            assertEquals(listOf(9, 8), session.orderedSets().map { it.reps })
            assertEquals(listOf(0, 1), session.orderedExercises().map { it.workoutExercise.position })
            assertEquals(listOf("Setup 1", "Setup 0"), session.orderedExercises().map { it.workoutExercise.notes })
            assertEquals(listOf("1", "0"), session.orderedExercises().map { it.workoutExercise.equipmentPositions.single().position })
            assertTrue(session.orderedSets().all { it.completedAt == null && it.actualReps == null && it.rpe == null })
            assertEquals(2, session.exercises.map { it.workoutExercise.id }.distinct().size)
            assertTrue(session.orderedSets().none { recorded -> templates.any { template -> template.orderedSets().any { it.id == recorded.id } } })
            var time = session.workout.startedAt + 60000
            assertEquals("ready", session.sessionState!!.phase)
            assertEquals(0L, session.sessionState!!.dutyMillis(time))
            assertEquals(0L, session.sessionState!!.phaseMillis(time))
            assertEquals(0L, calculateWeeklyProgress(listOf(session), time).activityMillis)
            assertEquals(id, repo.startTraining(training))
            assertTrue(runCatching { repo.startTraining(otherTraining) }.isFailure)
            assertEquals(1, repo.observeUnfinished().first().size)
            db.close(); db = open(); repo = FitnessRepository(db)
            var progress = WorkoutSessionRepository(db) { time }
            // Loading, leaving, reopening, and pausing a prepared session cannot start its timers.
            progress.ensureSession(id)
            progress.pause(id)
            time += 60000
            progress.resume(id)
            assertEquals(0L, db.sessionStateDao().get(id)!!.dutyMillis(time))
            val first = session.orderedSets()[0].id
            val second = session.orderedSets()[1].id
            assertTrue(runCatching { progress.completeSet(id, first) }.isFailure)
            progress.startSet(id, first)
            assertEquals(time, db.workoutDao().getDetails(id)!!.workout.startedAt)
            time += 5000
            progress.startSet(id, first) // Duplicate tap must not reset timing.
            assertEquals(5000L, db.sessionStateDao().get(id)!!.phaseMillis(time))
            progress.completeSet(id, first)
            progress.recordActual(id, first, 7)
            time += 3000
            progress.startSet(id, second)
            time += 4000
            progress.completeSet(id, second)
            progress.recordActual(id, second, 8)
            time += 2000
            progress.finishSession(id)
            db.close(); db = open(); repo = FitnessRepository(db)
            session = db.workoutDao().getDetails(id)!!
            assertEquals(14000L, session.sessionState!!.dutyElapsedMillis)
            assertEquals(listOf(5000L, 4000L), session.orderedSets().map { it.activeMillis })
            assertEquals(listOf(3000L, 2000L), session.orderedSets().map { it.restMillis })
            assertEquals(listOf(7, 8), session.orderedSets().map { it.actualReps })
            assertEquals(templates, modules.map { db.workoutDao().getDetails(it)!! })
            repo.trainingPlans.delete(training)
            assertEquals(session, db.workoutDao().getDetails(id))
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun emptyOrUnfinishedModulesCannotCreatePartialSessions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        try {
            db.trainingPlanDao().insert(TrainingPlan(id = "empty", name = "Empty"))
            assertTrue(runCatching { repo.startTraining("empty") }.isFailure)
            repo.addExercise("Curl", "Dumbbell")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            val draft = repo.observeSessions().first().single()
            val training = repo.trainingPlans.assignWorkout(draft.workout.id, listOf("empty"))
            assertTrue(runCatching { repo.startTraining("empty") }.isFailure)
            repo.savePlan(draft.workout.id)
            repo.removeWorkoutExercise(draft.workout.id, draft.exercises.single().workoutExercise.id)
            assertTrue(runCatching { repo.startTraining("empty") }.isFailure)
            assertTrue(repo.observeUnfinished().first().isEmpty())
            assertEquals(1, repo.observeSessions().first().size)
        } finally { db.close() }
    }
}
