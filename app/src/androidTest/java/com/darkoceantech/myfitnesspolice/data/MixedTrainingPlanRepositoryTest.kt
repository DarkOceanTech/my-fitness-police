package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MixedTrainingPlanRepositoryTest {
    private suspend fun module(db: FitnessDatabase, id: String, exerciseId: String, reps: Int = 8): String {
        db.workoutDao().insert(Workout(id = id, kind = "plan", name = "Saved $id", targetMuscles = "Back"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = exerciseId,
            position = 0, notes = "Setup $id", equipmentPositions = listOf(EquipmentPosition("Seat", "3"))))
        db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0,
            reps = reps, weightGrams = 12345, notes = "Prescription $id"))
        return id
    }

    @Test fun mixedPrescriptionReopensAndExpandsIndependentReadySessionInExactOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "mixed-plan-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            listOf("row", "curl", "squat").forEach { db.exerciseDao().insert(Exercise(id = it, name = it, equipment = "Barbell")) }
            val first = module(db, "first", "row", 8)
            val second = module(db, "second", "curl", 12)
            val directSets = listOf(TrainingPlanSet(15, 5001, true, "none"), TrainingPlanSet(7, 10002, false, "drop_set"))
            val items = listOf(TrainingPlanItem.ExerciseItem("squat"), TrainingPlanItem.WorkoutItem(first),
                TrainingPlanItem.ExerciseItem("row", directSets), TrainingPlanItem.WorkoutItem(second))
            val planId = FitnessRepository(db).trainingPlans.saveItems(null, " Mixed patrol ", items, "Tuesday")
            assertEquals(2, FitnessRepository(db).observeSessions().first().size) // No hidden reusable workout for a direct exercise.
            db.close(); db = open()
            val repo = FitnessRepository(db)
            val plan = db.trainingPlanDao().get(planId)!!
            assertEquals("Mixed patrol", plan.plan.name)
            assertEquals(items, plan.orderedItems())
            assertEquals(listOf(0, 2), plan.exerciseMembers.sortedBy { it.member.position }.map { it.member.position })
            assertEquals(listOf(1, 3), plan.members.sortedBy { it.position }.map { it.position })
            assertEquals("row", plan.exerciseFor("row")!!.id)
            val templates = listOf(first, second).map { db.workoutDao().getDetails(it)!! }
            val sessionId = repo.startTraining(planId)
            val session = db.workoutDao().getDetails(sessionId)!!
            assertEquals(planId, session.workout.sourceTrainingPlanId)
            assertEquals("Tuesday", session.workout.dayOfWeek)
            assertEquals("Mixed patrol", session.workout.name)
            assertNull(session.workout.sourcePlanId)
            val entries = session.orderedExercises()
            assertEquals(listOf("squat", "row", "row", "curl"), entries.map { it.exercise.id })
            assertEquals(listOf(0, 1, 2, 3), entries.map { it.workoutExercise.position })
            assertEquals(listOf(null, first, null, second), entries.map { it.workoutExercise.sourceWorkoutId })
            assertEquals(listOf("", "Saved first", "", "Saved second"), entries.map { it.workoutExercise.sourceWorkoutName })
            assertEquals(4, entries.map { it.workoutExercise.id }.distinct().size)
            assertEquals(listOf(10, 8, 15, 7, 12), session.orderedSets().map { it.reps })
            assertEquals(listOf(0L, 12345L, 5001L, 10002L, 12345L), session.orderedSets().map { it.weightGrams })
            assertEquals(directSets.map { it.isWarmup }, entries[2].sets.sortedBy { it.position }.map { it.isWarmup })
            assertEquals(directSets.map { it.modifier }, entries[2].sets.sortedBy { it.position }.map { it.modifier })
            assertTrue(session.orderedSets().all { it.completedAt == null && it.actualReps == null && it.rpe == null && it.activeMillis == 0L && it.restMillis == 0L })
            assertEquals("ready", session.sessionState!!.phase)
            assertNull(session.sessionState!!.phaseStartedAt)
            assertNull(session.sessionState!!.dutyStartedAt)
            assertEquals(0L, session.sessionState!!.dutyMillis(session.workout.startedAt + 90000))
            assertTrue(session.orderedSets().none { set -> templates.any { template -> template.orderedSets().any { it.id == set.id } } })
            var time = session.workout.startedAt
            val progress = WorkoutSessionRepository(db) { time }
            session.orderedSets().forEach { set ->
                progress.startSet(sessionId, set.id)
                time += 4000
                progress.completeSet(sessionId, set.id)
                progress.recordActual(sessionId, set.id, set.reps - 1, 7, "Recorded ${set.id}")
                time += 2000
            }
            progress.finishSession(sessionId)
            val recorded = db.workoutDao().getDetails(sessionId)!!
            assertTrue(recorded.orderedSets().all { it.activeMillis == 4000L && it.restMillis == 2000L && it.rpe == 7 })
            assertEquals(plan, db.trainingPlanDao().get(planId))
            assertEquals(templates, listOf(first, second).map { db.workoutDao().getDetails(it)!! })
            repo.trainingPlans.delete(planId)
            repo.deletePlan(first)
            repo.deletePlan(second)
            assertEquals(0L, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM training_plan_exercises").use { it.moveToFirst(); it.getLong(0) })
            assertEquals(3, db.exerciseDao().getAll().size)
            db.close(); db = open()
            assertEquals(recorded, db.workoutDao().getDetails(sessionId))
            assertEquals(listOf("session"), FitnessRepository(db).observeSessions().first().map { it.workout.kind })
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun legacySavePreservesDirectSlotsAndPrescriptionsAndAssignmentAppendsAfterBothKinds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            db.exerciseDao().insert(Exercise(id = "movement", name = "Row", equipment = "Cable"))
            db.exerciseDao().insert(Exercise(id = "other", name = "Curl", equipment = "Dumbbell"))
            val repo = FitnessRepository(db)
            val first = module(db, "first", "movement")
            val second = module(db, "second", "movement")
            val third = module(db, "third", "movement")
            val directA = TrainingPlanItem.ExerciseItem("movement", listOf(TrainingPlanSet(6, 13579, false, "superset")))
            val directB = TrainingPlanItem.ExerciseItem("other")
            val id = repo.trainingPlans.saveItems(null, "Original", listOf(directA, TrainingPlanItem.WorkoutItem(first), directB,
                TrainingPlanItem.WorkoutItem(second)), "Monday")
            repo.trainingPlans.save(id, "Renamed", listOf(second, first), "Friday")
            assertEquals(listOf(directA, TrainingPlanItem.WorkoutItem(second), directB, TrainingPlanItem.WorkoutItem(first)),
                db.trainingPlanDao().get(id)!!.orderedItems())
            assertEquals("Renamed", db.trainingPlanDao().get(id)!!.plan.name)
            assertEquals("Friday", db.trainingPlanDao().get(id)!!.plan.dayOfWeek)
            repo.trainingPlans.saveItems(id, "Direct ending", listOf(TrainingPlanItem.WorkoutItem(first), directB, directA))
            repo.trainingPlans.assignWorkout(third, listOf(id))
            assertEquals(listOf(TrainingPlanItem.WorkoutItem(first), directB, directA, TrainingPlanItem.WorkoutItem(third)),
                db.trainingPlanDao().get(id)!!.orderedItems())
            assertEquals(3, db.trainingPlanDao().get(id)!!.members.single { it.workoutId == third }.position)
            repo.trainingPlans.saveItems(id, "Reordered", listOf(directA, TrainingPlanItem.WorkoutItem(third)))
            assertEquals(listOf(directA, TrainingPlanItem.WorkoutItem(third)), db.trainingPlanDao().get(id)!!.orderedItems())
            assertEquals(0, db.trainingPlanDao().get(id)!!.exerciseMembers.single().member.position)
            assertEquals(1, db.trainingPlanDao().get(id)!!.members.single().position)
            assertNotNull(db.workoutDao().getDetails(first))
            assertNotNull(db.exerciseDao().get("other"))
            assertEquals("", db.workoutDao().getDetails(first)!!.workout.trainingPlan)
            assertEquals("Reordered", db.workoutDao().getDetails(third)!!.workout.trainingPlan)
        } finally { db.close() }
    }

    @Test fun existingArchivedDirectExerciseCanBeRetainedAndStartedButCannotBeNewlyAdded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            db.exerciseDao().insert(Exercise(id = "archived", name = "Retired row", equipment = "Machine"))
            db.exerciseDao().insert(Exercise(id = "active", name = "Current row", equipment = "Cable"))
            val original = TrainingPlanItem.ExerciseItem("archived")
            val id = repo.trainingPlans.saveItems(null, "Original", listOf(original))
            db.exerciseDao().archive("archived")
            val edited = original.copy(sets = listOf(TrainingPlanSet(12, 54321)))
            repo.trainingPlans.saveItems(id, "Retained", listOf(edited))
            assertTrue(db.trainingPlanDao().get(id)!!.exerciseFor("archived")!!.isArchived)
            assertEquals(listOf("archived"), db.trainingPlanDao().get(id)!!.includedExercises().map { it.id })
            assertTrue(runCatching { repo.trainingPlans.saveItems(null, "Rejected", listOf(original)) }.isFailure)
            assertEquals(1, db.trainingPlanDao().getAll().size)
            val session = db.workoutDao().getDetails(repo.startTraining(id))!!
            assertEquals(12, session.orderedSets().single().reps)
            assertEquals(54321L, session.orderedSets().single().weightGrams)
            assertEquals("ready", session.sessionState!!.phase)
            assertTrue(runCatching { repo.startTraining(id) }.isFailure)
            repo.trainingPlans.saveItems(id, "Removed", listOf(TrainingPlanItem.ExerciseItem("active")))
            assertTrue(runCatching { repo.trainingPlans.saveItems(id, "Readd rejected", listOf(original)) }.isFailure)
            assertEquals("Removed", db.trainingPlanDao().get(id)!!.plan.name)
            repo.trainingPlans.delete(id)
            assertEquals(session, db.workoutDao().getDetails(session.workout.id))
            assertEquals(2, db.exerciseDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun invalidMixedSaveIsAtomicAndEmptyModulesCannotCreatePartialSession() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            db.exerciseDao().insert(Exercise(id = "movement", name = "Row", equipment = "Cable"))
            val saved = module(db, "saved", "movement")
            val validItems = listOf(TrainingPlanItem.WorkoutItem(saved), TrainingPlanItem.ExerciseItem("movement"))
            val id = repo.trainingPlans.saveItems(null, "Keep me", validItems, "Wednesday")
            val baseline = db.trainingPlanDao().get(id)
            val invalidItems = listOf(
                validItems + TrainingPlanItem.ExerciseItem("missing"),
                validItems + TrainingPlanItem.WorkoutItem("missing"),
                validItems + TrainingPlanItem.ExerciseItem("movement"),
                listOf(TrainingPlanItem.ExerciseItem("movement", emptyList())),
                emptyList(),
            )
            invalidItems.forEach { items ->
                assertTrue(runCatching { repo.trainingPlans.saveItems(id, "Must not save", items, "Sunday") }.isFailure)
                assertEquals(baseline, db.trainingPlanDao().get(id))
            }
            assertTrue(runCatching { repo.trainingPlans.saveItems(id, "", validItems) }.isFailure)
            assertTrue(runCatching { repo.trainingPlans.saveItems(id, "Invalid day", validItems, "Funday") }.isFailure)
            assertEquals(baseline, db.trainingPlanDao().get(id))
            db.workoutSetDao().delete("saved-set")
            assertTrue(runCatching { repo.startTraining(id) }.isFailure)
            assertEquals(1, repo.observeSessions().first().size)
            assertTrue(repo.observeUnfinished().first().isEmpty())
            assertEquals(baseline, db.trainingPlanDao().get(id))
        } finally { db.close() }
    }
}
