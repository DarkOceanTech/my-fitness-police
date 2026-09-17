package com.example.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrainingPlanRepositoryTest {
    @Test fun reusableVariantsOrderMembershipAndSessionSnapshotsSurviveReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "training-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            var repo = FitnessRepository(db)
            repo.addExercise("Curl", "Dumbbell")
            val exercise = repo.observeExercises().first().single().id
            suspend fun module(name: String, reps: Int): String {
                repo.chooseExercise(exercise)
                repo.setWorkoutDetails("muscles", "Biceps")
                val draft = repo.observeSessions().first().single { it.workout.kind == "draft" }
                val entry = draft.exercises.single()
                repo.updateSetFields(draft.workout.id, entry.workoutExercise.id, entry.sets.single().id, reps = reps)
                repo.savePlan(draft.workout.id, name)
                return draft.workout.id
            }
            val strength = module("Biceps — Strength", 5)
            val rehab = module("Biceps — Rehab", 15)
            val first = repo.trainingPlans.save(null, "Arm Day", listOf(strength, rehab))
            val second = repo.trainingPlans.save(null, "Recovery", listOf(rehab))
            assertEquals(2, repo.observeSessions().first().size)
            assertEquals(listOf(strength, rehab), db.trainingPlanDao().get(first)!!.workoutIds())
            assertEquals(2, db.trainingPlanDao().memberships(rehab).size)
            val sessionId = repo.startPlan(strength)
            repo.finishWorkout(sessionId)
            repo.saveWorkoutDetails(strength, "Biceps — Heavy", "Biceps", "Tuesday", listOf(first, second))
            assertEquals("Biceps — Strength", db.workoutDao().getDetails(sessionId)!!.workout.name)
            assertEquals("Biceps — Heavy", db.workoutDao().getDetails(strength)!!.workout.name)
            assertEquals(listOf(rehab, strength), db.trainingPlanDao().get(second)!!.workoutIds())
            repo.trainingPlans.save(first, "Arms reordered", listOf(rehab, strength))
            assertEquals(listOf(rehab, strength), db.trainingPlanDao().get(first)!!.workoutIds())
            // Reject invalid selections without partially replacing a saved plan.
            suspend fun rejected(ids: List<String>) {
                var failed = false
                try { repo.trainingPlans.save(first, "Invalid edit", ids) } catch (_: IllegalArgumentException) { failed = true }
                assertTrue(failed)
                assertEquals("Arms reordered", db.trainingPlanDao().get(first)!!.plan.name)
                assertEquals(listOf(rehab, strength), db.trainingPlanDao().get(first)!!.workoutIds())
            }
            rejected(emptyList()); rejected(listOf(rehab, rehab)); rejected(listOf(sessionId)); rejected(listOf("missing"))
            db.close(); db = open(); repo = FitnessRepository(db)
            assertEquals(listOf(rehab, strength), db.trainingPlanDao().get(first)!!.workoutIds())
            assertEquals(5, db.workoutDao().getDetails(strength)!!.orderedSets().single().reps)
            assertEquals(15, db.workoutDao().getDetails(rehab)!!.orderedSets().single().reps)
            // Removing membership and deleting groups never deletes reusable workouts or history.
            repo.trainingPlans.save(first, "Arms reordered", listOf(strength))
            assertNotNull(db.workoutDao().getDetails(rehab))
            repo.trainingPlans.delete(second)
            assertEquals(1, repo.observeHistory().first().size)
            assertNotNull(db.workoutDao().getDetails(rehab))
            assertEquals("", db.workoutDao().getDetails(rehab)!!.workout.trainingPlan)
            repo.deletePlan(strength)
            assertTrue(db.trainingPlanDao().get(first)!!.members.isEmpty())
            assertNotNull(db.workoutDao().getDetails(sessionId))
            assertEquals("Biceps — Strength", db.workoutDao().getDetails(sessionId)!!.workout.name)
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
