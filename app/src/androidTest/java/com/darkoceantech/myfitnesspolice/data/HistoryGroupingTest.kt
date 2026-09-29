package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HistoryGroupingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun seed(db: FitnessDatabase) {
        db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable"))
        db.trainingPlanDao().insert(TrainingPlan(id = "existing", name = "Existing plan", createdAt = 50, dayOfWeek = "Monday"))
        db.workoutDao().insert(Workout(id = "template", kind = "plan", name = "Rows only", startedAt = 100))
        db.trainingPlanDao().insertMembers(listOf(TrainingPlanWorkout("existing", "template", 0)))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "template-entry", workoutId = "template", exerciseId = "row", position = 0))
        db.workoutSetDao().insert(WorkoutSet(id = "template-set", workoutExerciseId = "template-entry", position = 0, reps = 12, weightGrams = 20000))
        listOf("one", "two", "untouched", "four", "active").forEachIndexed { index, id ->
            val start = 1000L + index * 10000
            val finished = id != "active"
            db.workoutDao().insert(Workout(id = id, kind = "session", name = "Recorded $id", startedAt = start,
                finishedAt = if (finished) start + 9000 else null, sourcePlanId = "template",
                sourceTrainingPlanId = "old-plan", trainingPlan = "Old plan snapshot", notes = "Session note $id",
                targetMuscles = "Back", dayOfWeek = "Tuesday"))
            db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = "row", position = 0,
                notes = "Setup $id", equipmentPositions = listOf(EquipmentPosition("Seat", "4")),
                sourceWorkoutId = "template", sourceWorkoutName = "Rows only snapshot"))
            db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0,
                reps = 10, weightGrams = 15000, completedAt = if (finished) start + 5000 else null,
                actualReps = if (finished) 8 else null, activeMillis = if (finished) 5000 else 0,
                restMillis = if (finished) 4000 else 0, isWarmup = true, modifier = "superset", notes = "Set note $id",
                rpe = if (finished) 7 else null))
            db.sessionStateDao().save(WorkoutSessionState(workoutId = id, phase = if (finished) "finished" else "ready",
                currentSetId = "$id-set", dutyElapsedMillis = if (finished) 9000 else 0, pauseReason = "Water break"))
        }
    }

    @Test fun groupingUnderExistingPlanSurvivesReopenWithoutMergingOrChangingPrescriptions() = runBlocking {
        val filename = "history-group-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            seed(db)
            val before = db.workoutDao().observeAllDetails().first().associateBy { it.workout.id }
            val training = db.trainingPlanDao().get("existing")!!
            val exercises = db.exerciseDao().getAll()
            assertEquals("existing", FitnessRepository(db).groupHistoryWorkouts(listOf("one", "two"), trainingPlanId = "existing"))
            db.close(); db = open()
            val after = db.workoutDao().observeAllDetails().first().associateBy { it.workout.id }
            assertEquals(before.keys, after.keys)
            val historyGroupId = after.getValue("one").workout.historyGroupId
            assertFalse(historyGroupId.isNullOrBlank())
            assertEquals(historyGroupId, after.getValue("two").workout.historyGroupId)
            before.forEach { (id, original) ->
                val expected = if (id == "one" || id == "two") original.copy(workout = original.workout.copy(
                    sourceTrainingPlanId = "existing", trainingPlan = "Existing plan", historyGroupId = historyGroupId)) else original
                assertEquals(expected, after[id])
            }
            assertEquals(training, db.trainingPlanDao().get("existing"))
            assertEquals(exercises, db.exerciseDao().getAll())
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun creatingAPlanGroupsHistoryWithoutCreatingReusableWorkoutsOrMembers() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            seed(db)
            val repo = FitnessRepository(db)
            val before = repo.observeSessions().first().associateBy { it.workout.id }
            val existing = db.trainingPlanDao().get("existing")!!
            val id = repo.groupHistoryWorkouts(listOf("one", "two"), newPlanName = "  Saturday recovery  ")
            val created = db.trainingPlanDao().get(id)!!
            assertNotEquals("existing", id)
            assertEquals("Saturday recovery", created.plan.name)
            assertEquals("", created.plan.dayOfWeek)
            assertTrue(created.members.isEmpty())
            assertEquals(2, db.trainingPlanDao().getAll().size)
            assertEquals(existing, db.trainingPlanDao().get("existing"))
            val after = repo.observeSessions().first().associateBy { it.workout.id }
            assertEquals(before.keys, after.keys)
            val historyGroupId = after.getValue("one").workout.historyGroupId
            assertFalse(historyGroupId.isNullOrBlank())
            assertEquals(historyGroupId, after.getValue("two").workout.historyGroupId)
            before.forEach { (workoutId, original) ->
                assertEquals(if (workoutId in listOf("one", "two")) original.copy(workout = original.workout.copy(
                    sourceTrainingPlanId = id, trainingPlan = "Saturday recovery", historyGroupId = historyGroupId)) else original, after[workoutId])
            }
            // Removing the new reusable plan must not remove its recorded sessions.
            repo.trainingPlans.delete(id)
            assertEquals(after, repo.observeSessions().first().associateBy { it.workout.id })
        } finally { db.close() }
    }

    @Test fun separateSelectionsForOnePlanStaySeparateAndRegroupingMovesOnlySelectedRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            seed(db)
            val repo = FitnessRepository(db)
            val original = repo.observeSessions().first().associateBy { it.workout.id }
            repo.groupHistoryWorkouts(listOf("one", "two"), trainingPlanId = "existing")
            val firstBatch = db.workoutDao().getDetails("one")!!.workout.historyGroupId
            repo.groupHistoryWorkouts(listOf("untouched", "four"), trainingPlanId = "existing")
            val secondBatch = db.workoutDao().getDetails("untouched")!!.workout.historyGroupId
            assertFalse(firstBatch.isNullOrBlank())
            assertFalse(secondBatch.isNullOrBlank())
            assertNotEquals(firstBatch, secondBatch)
            assertEquals(firstBatch, db.workoutDao().getDetails("two")!!.workout.historyGroupId)
            assertEquals(secondBatch, db.workoutDao().getDetails("four")!!.workout.historyGroupId)
            val newPlan = repo.groupHistoryWorkouts(listOf("two", "four"), newPlanName = "Recovery")
            val thirdBatch = db.workoutDao().getDetails("two")!!.workout.historyGroupId
            assertFalse(thirdBatch.isNullOrBlank())
            assertNotEquals(firstBatch, thirdBatch)
            assertNotEquals(secondBatch, thirdBatch)
            assertEquals(thirdBatch, db.workoutDao().getDetails("four")!!.workout.historyGroupId)
            val after = repo.observeSessions().first().associateBy { it.workout.id }
            original.forEach { (id, before) ->
                val expected = when (id) {
                    "one" -> before.copy(workout = before.workout.copy(historyGroupId = firstBatch,
                        sourceTrainingPlanId = "existing", trainingPlan = "Existing plan"))
                    "untouched" -> before.copy(workout = before.workout.copy(historyGroupId = secondBatch,
                        sourceTrainingPlanId = "existing", trainingPlan = "Existing plan"))
                    "two", "four" -> before.copy(workout = before.workout.copy(historyGroupId = thirdBatch,
                        sourceTrainingPlanId = newPlan, trainingPlan = "Recovery"))
                    else -> before
                }
                assertEquals(expected, after[id])
            }
            assertTrue(runCatching { repo.groupHistoryWorkouts(listOf("one", "active"), trainingPlanId = "existing") }.isFailure)
            assertEquals(after, repo.observeSessions().first().associateBy { it.workout.id })
            repo.trainingPlans.delete(newPlan)
            assertEquals(after, repo.observeSessions().first().associateBy { it.workout.id })
        } finally { db.close() }
    }

    @Test fun invalidSelectionOrPlanRollsBackEntireGroupAndCannotCreateAnEmptyAccidentalPlan() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            seed(db)
            val repo = FitnessRepository(db)
            val sessions = repo.observeSessions().first()
            val plans = db.trainingPlanDao().observeAll().first()
            val invalid: List<suspend () -> Unit> = listOf(
                { repo.groupHistoryWorkouts(emptyList(), newPlanName = "New") },
                { repo.groupHistoryWorkouts(listOf("one", "one"), newPlanName = "New") },
                { repo.groupHistoryWorkouts(listOf("one", "active"), newPlanName = "New") },
                { repo.groupHistoryWorkouts(listOf("one", "template"), trainingPlanId = "existing") },
                { repo.groupHistoryWorkouts(listOf("one", "missing"), trainingPlanId = "existing") },
                { repo.groupHistoryWorkouts(listOf("one"), trainingPlanId = "missing") },
                { repo.groupHistoryWorkouts(listOf("one"), newPlanName = "  ") },
                { repo.groupHistoryWorkouts(listOf("one")) },
                { repo.groupHistoryWorkouts(listOf("one"), trainingPlanId = "existing", newPlanName = "New") },
            )
            invalid.forEach { operation ->
                assertTrue(runCatching { operation() }.exceptionOrNull() is IllegalArgumentException)
                assertEquals(sessions, repo.observeSessions().first())
                assertEquals(plans, db.trainingPlanDao().observeAll().first())
            }
        } finally { db.close() }
    }
}
