package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TimeCorrectionTest {
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
    private suspend fun seed(db: FitnessDatabase, finished: Boolean = true) {
        db.exerciseDao().insert(Exercise("e", "Cable row", "Cable"))
        db.workoutDao().insert(Workout("w", startedAt = 1000, finishedAt = if (finished) 301000 else null))
        db.workoutExerciseDao().insert(WorkoutExercise("entry", "w", "e", 0))
        db.workoutSetDao().insert(WorkoutSet("a", "entry", 0, 10, 45000, completedAt = 31000, actualReps = 10, activeMillis = 30000, restMillis = 60000))
        db.workoutSetDao().insert(WorkoutSet("b", "entry", 1, 10, 45000, completedAt = 93000, actualReps = 8, activeMillis = 2000, restMillis = 60000, notes = "Oops #TiMe missed Start"))
        db.workoutSetDao().insert(WorkoutSet("c", "entry", 2, 10, 45000, completedAt = 193000, actualReps = 10, activeMillis = 40000, restMillis = 60000))
    }
    @Test fun linkedTransfersPreserveRawDataAndAnalyticsAndRestoreInDependencyOrder() = runBlocking {
        val db = database()
        try {
            seed(db)
            val repo = FitnessRepository(db)
            val raw = db.workoutDao().getDetails("w")!!
            assertEquals("needs_review", raw.timeReviewStatus(raw.orderedSets()[1]))
            repo.timeCorrections.save("w", "b", "a", 42000, 2000, 60000, "Manual", "", "Missed Start")
            var effective = repo.observeWorkout("w").first()!!
            assertEquals(42000L, effective.orderedSets()[1].activeMillis)
            assertEquals(20000L, effective.orderedSets()[0].restMillis)
            assertEquals(raw.orderedSets().sumOf { it.activeMillis + it.restMillis }, effective.orderedSets().sumOf { it.activeMillis + it.restMillis })
            assertEquals(raw.workout, effective.workout)
            assertEquals(raw.orderedSets(), db.workoutDao().getDetails("w")!!.orderedSets())
            assertEquals("corrected", effective.timeReviewStatus(effective.orderedSets()[1]))
            val input = DashboardRepository(repo).input.first()
            assertEquals(42000L, input.sessions.single().sets.first { it.id == "b" }.activeMillis)
            assertEquals(20000L, input.sessions.single().sets.first { it.id == "a" }.breakMillis)
            val first = effective.timeCorrections.single().id
            repo.timeCorrections.save("w", "b", "b", 32000, 42000, 60000, "Manual", "", "Done late")
            effective = repo.observeWorkout("w").first()!!
            val second = effective.timeCorrections.maxOf { it.id }
            try { repo.timeCorrections.restore("w", first); fail("Dependent transfer must be reviewed first") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("Later linked")) }
            assertEquals(32000L, repo.observeWorkout("w").first()!!.orderedSets()[1].activeMillis)
            repo.timeCorrections.restore("w", second)
            assertEquals(42000L, repo.observeWorkout("w").first()!!.orderedSets()[1].activeMillis)
            repo.timeCorrections.restore("w", first)
            assertEquals(raw.orderedSets(), repo.observeWorkout("w").first()!!.orderedSets())
            repo.timeCorrections.review("w", "b", "dismissed")
            assertEquals("dismissed", repo.observeWorkout("w").first()!!.timeReviewStatus(raw.orderedSets()[1]))
            assertEquals("Oops #TiMe missed Start", db.workoutDao().getDetails("w")!!.orderedSets()[1].notes)
        } finally { db.close() }
    }
    @Test fun invalidTransfersAreAtomicAndNeverBorrowRunningPausedOrMissingTime() = runBlocking {
        val db = database()
        try {
            seed(db, false)
            val repo = TimeCorrectionRepository(db)
            db.sessionStateDao().save(WorkoutSessionState("w", phase = "rest", currentSetId = "c", phaseStartedAt = 193000))
            suspend fun rejected(set: String, rest: String, value: Long, beforeWork: Long, beforeRest: Long) {
                try { repo.save("w", set, rest, value, beforeWork, beforeRest, "Manual", "", ""); fail("Must reject") }
                catch (_: IllegalArgumentException) { }
                assertTrue(db.workoutDao().getDetails("w")!!.timeCorrections.isEmpty())
            }
            rejected("c", "c", 30000, 40000, 60000)
            db.sessionStateDao().save(WorkoutSessionState("w", phase = "rest", currentSetId = "c", isPaused = true))
            rejected("c", "c", 30000, 40000, 60000)
            rejected("a", "c", 42000, 30000, 60000)
            rejected("b", "a", 62001, 2000, 60000)
            rejected("b", "a", -1, 2000, 60000)
            rejected("b", "a", 42000, 1999, 60000)
            db.sessionStateDao().save(WorkoutSessionState("w", phase = "rest", currentSetId = "b", awaitingActual = true))
            rejected("b", "a", 42000, 2000, 60000)
            assertEquals(TimeTransfer(0, 62000, 62000), transferTime(2000, 60000, 0))
            assertEquals(TimeTransfer(500, 1500, 2000), transferTime(2000, 0, 500))
        } finally { db.close() }
    }
    @Test fun estimatesAndFlagsRespectEligibility() = runBlocking {
        val db = database()
        try {
            seed(db)
            val w = db.workoutDao().getDetails("w")!!
            assertTrue(hasTimeTag("#TIME forgot")); assertFalse(hasTimeTag("#timer"))
            val estimate = estimateWorkTime(w, "b", null, listOf(w))!!
            assertEquals(35000L, estimate.millis); assertEquals(2, estimate.count)
            assertNull(estimateWorkTime(w, "b", "a", listOf(w)))
            val flagged = w.copy(timeReviews = listOf(TimeReview("a", "w", "needs_review", 0)))
            assertNull(estimateWorkTime(flagged, "b", null, listOf(flagged)))
            val earlier = w.copy(workout = w.workout.copy(id = "old", startedAt = 0), exercises = w.exercises.map { e -> e.copy(sets =
                e.sets.filter { it.id != "b" }.map { it.copy(id = "old-${it.id}", completedAt = 500) }) })
            assertNotNull(estimateWorkTime(w, "b", "a", listOf(earlier)))
            val future = earlier.copy(exercises = earlier.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(completedAt = 999999) }) })
            assertNull(estimateWorkTime(w, "b", "a", listOf(future)))
            val unconfirmed = w.copy(sessionState = WorkoutSessionState("w", currentSetId = "a", awaitingActual = true))
            assertNull(estimateWorkTime(unconfirmed, "b", null, emptyList()))
            val unfinished = w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets.map { if (it.id == "a") it.copy(completedAt = null) else it }) })
            assertNull(estimateWorkTime(unfinished, "b", null, emptyList()))
            val correctionRepo = TimeCorrectionRepository(db)
            correctionRepo.save("w", "a", "a", 35000, 30000, 60000, "Estimated", "Median of test references", "")
            val corrected = db.workoutDao().getDetails("w")!!.withEffectiveTimes()
            assertNull(estimateWorkTime(corrected, "b", null, emptyList()))
        } finally { db.close() }
    }
}
