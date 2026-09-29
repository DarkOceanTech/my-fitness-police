package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutEditingSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()

    private suspend fun seedPlan(db: FitnessDatabase, id: String = "plan"): String {
        db.exerciseDao().insert(Exercise(id = "$id-exercise", name = "Cable row", equipment = "Cable"))
        db.workoutDao().insert(Workout(id = id, kind = "plan", name = "Back", targetMuscles = "Back"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id,
            exerciseId = "$id-exercise", position = 0, notes = "Original note"))
        repeat(3) { index -> db.workoutSetDao().insert(WorkoutSet(id = "$id-set-$index",
            workoutExerciseId = "$id-entry", position = index, reps = 8 + index, weightGrams = 12000)) }
        return id
    }

    @Test fun savedWorkoutChangesOnlyOnCommitAndDiscardKeepsMembershipsAndSessions() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            val plan = seedPlan(db)
            val training = repo.trainingPlans.save(null, "Back day", listOf(plan))
            val sessionId = repo.startTraining(training)
            val source = db.workoutDao().getDetails(plan)!!
            val session = db.workoutDao().getDetails(sessionId)!!
            val editId = repo.workoutEditor.begin(plan)
            val editing = db.workoutDao().getDetails(editId)!!
            val entry = editing.exercises.single()
            repo.saveWorkoutDetails(editId, "New name", "Back,Biceps", "", emptyList())
            repo.updateExerciseNote(editId, entry.workoutExercise.id, "Changed note")
            repo.updateSetFields(editId, entry.workoutExercise.id, entry.sets.first().id,
                reps = 15, modifier = "drop_set", warmup = true)
            repo.deleteSet(editId, entry.workoutExercise.id, entry.sets[1].id)
            assertEquals(source, db.workoutDao().getDetails(plan))
            assertEquals(listOf(plan), db.trainingPlanDao().get(training)!!.workoutIds())
            assertEquals(editId, repo.workoutEditor.begin(plan))
            repo.savePlan(editId)
            val committed = db.workoutDao().getDetails(plan)!!
            assertEquals("New name", committed.workout.name)
            assertEquals("Back,Biceps", committed.workout.targetMuscles)
            assertEquals("Changed note", committed.exercises.single().workoutExercise.notes)
            assertEquals(listOf(15, 10), committed.orderedSets().map { it.reps })
            assertEquals("drop_set", committed.orderedSets().first().modifier)
            assertTrue(committed.orderedSets().first().isWarmup)
            assertEquals(listOf(plan), db.trainingPlanDao().get(training)!!.workoutIds())
            assertEquals(session, db.workoutDao().getDetails(sessionId))
            repo.setWorkoutDetails("name", "Discard me", editId)
            repo.workoutEditor.discard(plan)
            assertNull(db.workoutDao().getDetails(editId))
            assertEquals(committed, db.workoutDao().getDetails(plan))
        } finally { db.close() }
    }

    @Test fun pendingEditSurvivesReopeningWithoutLeakingIntoSavedPlan() = runBlocking {
        val filename = "workout-edit-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            seedPlan(db)
            var repo = FitnessRepository(db)
            val editId = repo.workoutEditor.begin("plan")
            repo.setWorkoutDetails("name", "Pending edit", editId)
            db.close(); db = open(); repo = FitnessRepository(db)
            assertEquals(editId, repo.workoutEditor.begin("plan"))
            assertEquals("Back", db.workoutDao().getDetails("plan")!!.workout.name)
            assertEquals("Pending edit", db.workoutDao().getDetails(editId)!!.workout.name)
            repo.workoutEditor.discard("plan")
            assertEquals("Back", db.workoutDao().getDetails(repo.workoutEditor.begin("plan"))!!.workout.name)
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun removingPlannedSetsCompactsNumbersAndCopyRetainsDropSetPrescription() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            seedPlan(db)
            repo.updateSetFields("plan", "plan-entry", "plan-set-2", modifier = "drop_set", warmup = true)
            repo.deleteSet("plan", "plan-entry", "plan-set-1")
            assertEquals(listOf("plan-set-0", "plan-set-2"), db.workoutSetDao().getForExercise("plan-entry").map { it.id })
            assertEquals(listOf(0, 1), db.workoutSetDao().getForExercise("plan-entry").map { it.position })
            repo.copyLastSet("plan", "plan-entry")
            val copied = db.workoutSetDao().getForExercise("plan-entry").last()
            assertEquals(2, copied.position)
            assertEquals("drop_set", copied.modifier)
            assertTrue(copied.isWarmup)
            repo.deleteSet("plan", "plan-entry", "plan-set-0")
            repo.deleteSet("plan", "plan-entry", copied.id)
            repo.deleteSet("plan", "plan-entry", "plan-set-2")
            assertTrue(db.workoutSetDao().getForExercise("plan-entry").isEmpty())
            repo.saveSet("plan", "plan-entry", null, 10, 0)
            assertEquals(0, db.workoutSetDao().getForExercise("plan-entry").single().position)
            val sessionId = repo.startPlan("plan")
            val session = db.workoutDao().getDetails(sessionId)!!
            assertTrue(runCatching { repo.deleteSet(sessionId, session.exercises.single().workoutExercise.id,
                session.orderedSets().single().id) }.isFailure)
        } finally { db.close() }
    }

    @Test fun allStartPathsRejectReadyAndPausedSessionsAndConcurrentStartsCannotOverlap() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            seedPlan(db)
            val training = repo.trainingPlans.save(null, "Back day", listOf("plan"))
            val outcomes = (0..5).map { index -> async {
                runCatching { if (index % 2 == 0) repo.startTraining(training) else repo.startPlan("plan") }.isSuccess
            } }.awaitAll()
            assertEquals(1, outcomes.count { it })
            val sessionId = repo.observeUnfinished().first().single().id
            assertTrue(runCatching { repo.startTraining(training) }.isFailure)
            assertTrue(runCatching { repo.startPlan("plan") }.isFailure)
            assertTrue(runCatching { repo.startWorkout() }.isFailure)
            val session = db.workoutDao().getDetails(sessionId)!!
            if (session.sessionState!!.phase == "ready") repo.sessionProgress.startSet(sessionId, session.orderedSets().first().id)
            repo.sessionProgress.pause(sessionId)
            assertTrue(runCatching { repo.startTraining(training) }.isFailure)
            assertTrue(runCatching { repo.startPlan("plan") }.isFailure)
            assertTrue(runCatching { repo.startWorkout() }.isFailure)
            assertEquals(1, repo.observeUnfinished().first().size)
            assertTrue(db.sessionStateDao().get(sessionId)!!.isPaused)
        } finally { db.close() }
    }

    @Test fun activeAndHistoryTypeCorrectionsKeepNotesTimersAndOriginalPrescription() = runBlocking {
        val db = memoryDb()
        try {
            val repo = FitnessRepository(db)
            seedPlan(db)
            val original = db.workoutDao().getDetails("plan")!!
            val sessionId = repo.startPlan("plan")
            val setId = db.workoutDao().getDetails(sessionId)!!.orderedSets().first().id
            repo.sessionProgress.completeSet(sessionId, setId)
            repo.sessionProgress.recordActual(sessionId, setId, 7, 8, "Good control")
            val before = db.workoutDao().getDetails(sessionId)!!
            repo.correctActiveSet(sessionId, setId, null, 8, 7, 8, isWarmup = true)
            val after = db.workoutDao().getDetails(sessionId)!!
            assertEquals(before.sessionState, after.sessionState)
            assertEquals(before.orderedSets().first().copy(isWarmup = true), after.orderedSets().first())
            // Older callers that do not change type must preserve a prior correction.
            repo.correctActiveSet(sessionId, setId, null, 8, 7, 8)
            assertTrue(db.workoutDao().getDetails(sessionId)!!.orderedSets().first().isWarmup)
            repo.finishWorkout(sessionId)
            val finished = db.workoutDao().getDetails(sessionId)!!
            repo.correctHistorySet(sessionId, setId, null, 8, 7, 8, isWarmup = false)
            val history = db.workoutDao().getDetails(sessionId)!!
            assertEquals(finished.sessionState, history.sessionState)
            assertEquals(finished.orderedSets().first().copy(isWarmup = false), history.orderedSets().first())
            assertEquals(original, db.workoutDao().getDetails("plan"))
        } finally { db.close() }
    }
}
