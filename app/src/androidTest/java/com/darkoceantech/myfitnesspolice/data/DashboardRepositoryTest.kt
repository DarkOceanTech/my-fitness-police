package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DashboardRepositoryTest {
    @Test fun confirmedResultsAndMixedPlanPrescriptionsComeFromRoomAndRefreshAfterCorrections() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            db.exerciseDao().insert(Exercise("e", "Cable row", "Cable", primaryMuscles = "Latissimus dorsi"))
            for (id in listOf("saved", "history", "active")) {
                db.workoutDao().insert(Workout(id = id, kind = if (id == "saved") "plan" else "session",
                    startedAt = 100, finishedAt = if (id == "history") 500 else null))
                db.workoutExerciseDao().insert(WorkoutExercise("$id-e", id, "e", 0))
                db.workoutSetDao().insert(WorkoutSet("$id-0", "$id-e", 0, 10, 45359, completedAt = 200,
                    actualReps = if (id == "history") 0 else null, activeMillis = 1000, restMillis = 2000))
                db.workoutSetDao().insert(WorkoutSet("$id-1", "$id-e", 1, 7, 45359,
                    completedAt = if (id == "history") 300 else null))
            }
            db.sessionStateDao().save(WorkoutSessionState("active", phase = "rest", currentSetId = "active-0", awaitingActual = true))
            db.trainingPlanDao().insert(TrainingPlan("p", "Back patrol", 100, "Wednesday"))
            db.trainingPlanDao().insertMembers(listOf(TrainingPlanWorkout("p", "saved", 0)))
            db.trainingPlanDao().insertExerciseMembers(listOf(TrainingPlanExercise("p", "e", 1, listOf(TrainingPlanSet(6), TrainingPlanSet(8)))))
            val dashboard = DashboardRepository(FitnessRepository(db))
            val input = withTimeout(5000) { dashboard.input.first() }
            assertEquals(setOf("history", "active"), input.sessions.map { it.id }.toSet())
            assertTrue(input.sessions.single { it.id == "active" }.sets.isEmpty())
            val history = input.sessions.single { it.id == "history" }.sets
            assertEquals(listOf(0, 7), history.map { it.reps })
            assertEquals(100.0, history.first().pounds, .001)
            assertEquals(4, input.plans.single().sets)
            assertEquals(31L, input.plans.single().reps)
            assertEquals(3, input.plans.single().weekday)
            val original = db.workoutDao().getDetails("active")!!.orderedSets().first()
            db.workoutSetDao().update(original.copy(actualReps = 8))
            db.sessionStateDao().save(WorkoutSessionState("active", phase = "rest", currentSetId = "active-0", awaitingActual = false))
            val updated = withTimeout(5000) { dashboard.input.first { it.sessions.single { s -> s.id == "active" }.sets.size == 1 } }
            assertEquals(8, updated.sessions.single { it.id == "active" }.sets.single().reps)
            assertEquals(1000L, updated.sessions.single { it.id == "active" }.sets.single().activeMillis)
        } finally { db.close() }
    }
}
