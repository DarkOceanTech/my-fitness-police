package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SetReorderTest {
    private lateinit var db: FitnessDatabase
    private lateinit var repository: FitnessRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            FitnessDatabase::class.java).build()
        repository = FitnessRepository(db)
        db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable"))
        db.exerciseDao().insert(Exercise(id = "curl", name = "Biceps curl", equipment = "Dumbbell"))
        listOf("saved" to "plan", "draft" to "draft", "edit" to "edit",
            "active" to "session", "history" to "session").forEach { (id, kind) ->
            db.workoutDao().insert(Workout(id = id, name = "$id workout", kind = kind, startedAt = 1000,
                finishedAt = if (id == "history") 90_000 else null, notes = "Keep workout note",
                targetMuscles = "Back; Biceps", sourcePlanId = if (id == "history") "saved" else null,
                historyGroupId = if (id == "history") "recorded-group" else null))
            listOf("row", "curl").forEachIndexed { entryPosition, exercise ->
                val entryId = "$id-$exercise"
                db.workoutExerciseDao().insert(WorkoutExercise(id = entryId, workoutId = id,
                    exerciseId = exercise, position = entryPosition, notes = "Keep $entryId note",
                    equipmentPositions = listOf(EquipmentPosition("Seat", "4")),
                    sourceWorkoutId = if (id == "history") "saved" else null,
                    sourceWorkoutName = if (id == "history") "Original workout" else ""))
                val positions = if (exercise == "row") listOf(0, 2, 5) else listOf(0)
                positions.forEachIndexed { index, position ->
                    // Deliberately vary every field: reorder must never rebuild or reset stored sets.
                    db.workoutSetDao().insert(WorkoutSet(id = "$entryId-$index", workoutExerciseId = entryId,
                        position = position, reps = 12 - index, weightGrams = 12_347L + index * 1357,
                        isWarmup = index == 0, modifier = listOf("none", "superset", "drop_set")[index],
                        completedAt = 30_000L + index, actualReps = 10 - index, rpe = 7 + index,
                        activeMillis = 4000L + index, restMillis = 12_000L + index, notes = "Keep set $entryId-$index"))
                }
            }
            if (kind == "session") db.sessionStateDao().save(WorkoutSessionState(workoutId = id,
                phase = if (id == "history") "finished" else "active", currentSetId = "$id-row-0",
                dutyElapsedMillis = 48_000, pauseReason = "Keep reason"))
        }
    }

    @After fun tearDown() { db.close() }

    private suspend fun snapshot() = repository.observeSessions().first().associateBy { it.workout.id }

    private suspend fun assertOnlyPositionsChanged(
        workoutId: String, before: Map<String, WorkoutDetails>, entryId: String, order: List<String>,
    ) {
        val original = before.getValue(workoutId)
        val after = snapshot()
        assertEquals(before - workoutId, after - workoutId)
        val actual = after.getValue(workoutId)
        assertEquals(original.workout, actual.workout)
        assertEquals(original.sessionState, actual.sessionState)
        assertEquals(original.exercises.map { it.workoutExercise }, actual.exercises.map { it.workoutExercise })
        assertEquals(original.exercises.filterNot { it.workoutExercise.id == entryId },
            actual.exercises.filterNot { it.workoutExercise.id == entryId })
        val originalSets = original.exercises.single { it.workoutExercise.id == entryId }.sets.associateBy { it.id }
        assertEquals(order.mapIndexed { index, id -> originalSets.getValue(id).copy(position = index) },
            db.workoutSetDao().getForExercise(entryId))
    }

    @Test fun movingSetsUpAndDownCompactsPositionsAndPreservesEveryStoredField() = runBlocking {
        for (workout in listOf("saved", "draft", "edit")) {
            val entry = "$workout-row"
            val moveUp = listOf("$entry-2", "$entry-0", "$entry-1")
            var before = snapshot()
            repository.reorderSets(workout, entry, moveUp)
            assertOnlyPositionsChanged(workout, before, entry, moveUp)
            before = snapshot()
            val moveDown = listOf("$entry-0", "$entry-1", "$entry-2")
            repository.reorderSets(workout, entry, moveDown)
            assertOnlyPositionsChanged(workout, before, entry, moveDown)
            before = snapshot()
            repository.reorderSets(workout, entry, moveDown)
            repository.reorderSets(workout, "$workout-curl", listOf("$workout-curl-0"))
            assertEquals(before, snapshot())
        }
        db.workoutExerciseDao().insert(WorkoutExercise(id = "empty", workoutId = "draft", exerciseId = "row", position = 2))
        val beforeEmpty = snapshot()
        repository.reorderSets("draft", "empty", emptyList())
        assertEquals(beforeEmpty, snapshot())
    }

    @Test fun invalidOrdersAndSessionTargetsNeverChangeAnyRecords() = runBlocking {
        val before = snapshot()
        val ids = listOf("saved-row-0", "saved-row-1", "saved-row-2")
        val invalid = listOf(
            Triple("missing", "saved-row", ids),
            Triple("saved", "missing", ids),
            Triple("saved", "draft-row", ids),
            Triple("active", "active-row", listOf("active-row-2", "active-row-0", "active-row-1")),
            Triple("history", "history-row", listOf("history-row-2", "history-row-0", "history-row-1")),
            Triple("saved", "saved-row", emptyList()),
            Triple("saved", "saved-row", ids.dropLast(1)),
            Triple("saved", "saved-row", ids + "saved-curl-0"),
            Triple("saved", "saved-row", listOf(ids[0], ids[0], ids[2])),
            Triple("saved", "saved-row", listOf(ids[0], ids[1], "saved-curl-0")),
            Triple("saved", "saved-row", listOf(ids[0], ids[1], "draft-row-2")),
            Triple("saved", "saved-row", listOf(ids[0], ids[1], "missing")),
        )
        invalid.forEach { (workout, entry, order) ->
            val error = runCatching { repository.reorderSets(workout, entry, order) }.exceptionOrNull()
            assertTrue("Expected invalid reorder to fail: $workout/$entry $order, got $error", error is IllegalArgumentException)
            assertEquals(before, snapshot())
        }
    }

    @Test fun failedWriteRollsBackBothTemporaryAndFinalPositionChanges() = runBlocking {
        val before = snapshot()
        val order = listOf("saved-row-2", "saved-row-0", "saved-row-1")
        // First fail midway through temporary moves, then after temporary moves and one final assignment.
        for (position in listOf(7, 1)) {
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_set_reorder BEFORE UPDATE OF position ON workout_sets
                WHEN NEW.id = 'saved-row-0' AND NEW.position = $position
                BEGIN SELECT RAISE(ABORT, 'simulated reorder write failure'); END
            """.trimIndent())
            try {
                assertTrue(runCatching { repository.reorderSets("saved", "saved-row", order) }.isFailure)
                assertEquals(before, snapshot())
            } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_set_reorder") }
        }
    }

    @Test fun editorReorderCanBeDiscardedOrSavedWithoutChangingHistoryOrTrainingMemberships() = runBlocking {
        val planId = repository.trainingPlans.save(null, "Weekly back", listOf("saved"), "Monday")
        val original = db.workoutDao().getDetails("saved")!!
        val history = db.workoutDao().getDetails("history")
        val training = db.trainingPlanDao().get(planId)
        val editId = repository.workoutEditor.begin("saved")
        var edit = db.workoutDao().getDetails(editId)!!.orderedExercises().first()
        repository.reorderSets(editId, edit.workoutExercise.id, edit.sets.sortedByDescending { it.position }.map { it.id })
        assertEquals(original, db.workoutDao().getDetails("saved"))
        repository.workoutEditor.discard("saved")
        assertNull(db.workoutDao().getDetails(editId))
        assertEquals(original, db.workoutDao().getDetails("saved"))

        repository.workoutEditor.begin("saved")
        edit = db.workoutDao().getDetails(editId)!!.orderedExercises().first()
        val order = edit.sets.sortedByDescending { it.position }.map { it.id }
        repository.reorderSets(editId, edit.workoutExercise.id, order)
        assertEquals(original, db.workoutDao().getDetails("saved"))
        val expectedSets = db.workoutSetDao().getForExercise(edit.workoutExercise.id)
        repository.savePlan(editId)
        val saved = db.workoutDao().getDetails("saved")!!
        val actualSets = saved.orderedExercises().first().sets.sortedBy { it.position }
        assertEquals(original.workout, saved.workout)
        assertEquals(expectedSets.map { it.copy(id = "", workoutExerciseId = "") },
            actualSets.map { it.copy(id = "", workoutExerciseId = "") })
        assertEquals(listOf(0, 1, 2), actualSets.map { it.position })
        assertEquals(history, db.workoutDao().getDetails("history"))
        assertEquals(training, db.trainingPlanDao().get(planId))
    }
}
