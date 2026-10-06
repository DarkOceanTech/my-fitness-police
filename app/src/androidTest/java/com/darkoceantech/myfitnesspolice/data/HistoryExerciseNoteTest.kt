package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HistoryExerciseNoteTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun seed(db: FitnessDatabase) {
        db.exerciseDao().insert(Exercise(id = "row", name = "Cable row", equipment = "Cable",
            description = "Pull with control", primaryMuscles = "Latissimus dorsi"))
        db.exerciseDao().insert(Exercise(id = "curl", name = "Biceps curl", equipment = "Dumbbell"))
        val kinds = listOf("saved" to "plan", "history" to "session", "other" to "session",
            "active" to "session", "draft" to "draft", "edit" to "edit", "finished-plan" to "plan")
        kinds.forEachIndexed { index, (id, kind) ->
            val recorded = id == "history" || id == "other"
            db.workoutDao().insert(Workout(id = id, name = "$id workout", kind = kind,
                startedAt = 1000L + index, finishedAt = if (recorded || id == "finished-plan") 90_000L else null,
                sourcePlanId = if (recorded) "saved" else null,
                sourceTrainingPlanId = if (recorded) "training" else null,
                targetMuscles = "Back; Biceps", dayOfWeek = "Monday", trainingPlan = "Back and biceps",
                notes = "Keep workout note $id", historyGroupId = if (recorded) "selected-group" else null))
            listOf("row", "curl").forEachIndexed { position, exercise ->
                val entryId = "$id-$exercise"
                db.workoutExerciseDao().insert(WorkoutExercise(id = entryId, workoutId = id,
                    exerciseId = exercise, position = position, notes = "Original $entryId note",
                    equipmentPositions = listOf(EquipmentPosition("Seat", "4"), EquipmentPosition("Cable", "Low")),
                    sourceWorkoutId = if (recorded) "saved" else null,
                    sourceWorkoutName = if (recorded) "Back source" else ""))
                repeat(3) { setIndex ->
                    val performed = recorded && setIndex < 2
                    db.workoutSetDao().insert(WorkoutSet(id = "$entryId-set-$setIndex", workoutExerciseId = entryId,
                        position = setIndex, reps = 10 + setIndex, weightGrams = 12_347L + setIndex,
                        isWarmup = setIndex == 0, modifier = listOf("none", "superset", "drop_set")[setIndex],
                        completedAt = if (performed) 20_000L + setIndex else null,
                        actualReps = if (performed) 8 + setIndex else null, rpe = if (performed) 7 + setIndex else null,
                        activeMillis = if (performed) 4000L + setIndex else 0,
                        restMillis = if (performed) 12_000L + setIndex else 0,
                        notes = "Keep set note $entryId $setIndex"))
                }
            }
            if (recorded) db.sessionStateDao().save(WorkoutSessionState(workoutId = id, phase = "finished",
                currentSetId = "$id-curl-set-1", dutyElapsedMillis = 64_004,
                phaseElapsedMillis = 12_001, pauseReason = "Answer a call"))
        }
        db.trainingPlanDao().insert(TrainingPlan(id = "training", name = "Back and biceps", createdAt = 100,
            dayOfWeek = "Monday"))
        db.trainingPlanDao().insertMembers(listOf(TrainingPlanWorkout("training", "saved", 0)))
    }

    private suspend fun snapshot(db: FitnessDatabase) =
        FitnessRepository(db).observeSessions().first().associateBy { it.workout.id }

    private fun withNote(original: WorkoutDetails, entryId: String, note: String) = original.copy(
        exercises = original.exercises.map { entry ->
            if (entry.workoutExercise.id == entryId) entry.copy(workoutExercise = entry.workoutExercise.copy(notes = note))
            else entry
        })

    @Test fun saveAndClearSurviveReopenAndPreserveResultsTimingGroupingAndOtherRecords() = runBlocking {
        val filename = "history-exercise-note-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).build()
        var db = open()
        try {
            seed(db)
            val before = snapshot(db)
            val catalogBefore = db.exerciseDao().getAll()
            val planBefore = db.trainingPlanDao().get("training")
            val note = "  Seat 4 felt better.\nRemove the temporary reminder before next training.  "
            FitnessRepository(db).saveHistoryExerciseNote("history", "history-row", note)
            db.close(); db = open()
            val expected = before + ("history" to withNote(before.getValue("history"), "history-row", note.trim()))
            assertEquals(expected, snapshot(db))
            assertEquals(planBefore, db.trainingPlanDao().get("training"))
            assertEquals(catalogBefore, db.exerciseDao().getAll())

            FitnessRepository(db).saveHistoryExerciseNote("history", "history-row", "  \n\t ")
            db.close(); db = open()
            assertEquals(before + ("history" to withNote(before.getValue("history"), "history-row", "")), snapshot(db))
            assertEquals(planBefore, db.trainingPlanDao().get("training"))
            assertEquals(catalogBefore, db.exerciseDao().getAll())
        } finally {
            db.close()
            context.deleteDatabase(filename)
        }
    }

    @Test fun onlyCompletedSessionAndItsOwnExerciseEntryCanBeEdited() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            seed(db)
            val repository = FitnessRepository(db)
            val before = snapshot(db)
            val planBefore = db.trainingPlanDao().get("training")
            val invalidTargets = listOf(
                "saved" to "saved-row",
                "finished-plan" to "finished-plan-row",
                "active" to "active-row",
                "draft" to "draft-row",
                "edit" to "edit-row",
                "missing" to "history-row",
                "history" to "missing",
                "history" to "other-row", // Same exercise and history group, different recorded workout.
                "history" to "saved-row",
            )
            invalidTargets.forEach { (workoutId, entryId) ->
                val error = runCatching {
                    repository.saveHistoryExerciseNote(workoutId, entryId, "Must never be saved")
                }.exceptionOrNull()
                assertTrue("Expected rejection for $workoutId/$entryId, got $error", error is IllegalArgumentException)
                assertEquals(before, snapshot(db))
                assertEquals(planBefore, db.trainingPlanDao().get("training"))
            }
        } finally { db.close() }
    }
}
