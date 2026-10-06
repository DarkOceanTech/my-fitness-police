package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImportLastSessionTest {
    private lateinit var db: FitnessDatabase
    private lateinit var repository: FitnessRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            FitnessDatabase::class.java).build()
        repository = FitnessRepository(db)
        db.exerciseDao().insert(Exercise(id = "row", name = "Barbell row", equipment = "Barbell"))
        db.exerciseDao().insert(Exercise(id = "curl", name = "Biceps curl", equipment = "Dumbbell"))
    }

    @After fun tearDown() { db.close() }

    private suspend fun workout(id: String, kind: String = "plan", start: Long = 1L,
        finish: Long? = null, exerciseId: String = "row", reps: Int = 10): WorkoutDetails {
        db.workoutDao().insert(Workout(id = id, kind = kind, name = id, startedAt = start, finishedAt = finish))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "$id-entry", workoutId = id,
            exerciseId = exerciseId, position = 0, notes = "Note $id",
            equipmentPositions = listOf(EquipmentPosition("Seat", "3"))))
        db.workoutSetDao().insert(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry",
            position = 0, reps = reps, weightGrams = 0))
        return db.workoutDao().getDetails(id)!!
    }

    private suspend fun expectInvalid(fragment: String, action: suspend () -> Unit) {
        val error = runCatching { action() }.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertTrue("Expected '$fragment' in '${error?.message}'", error?.message?.contains(fragment) == true)
    }

    @Test fun latestCompletedSessionMatchesExerciseAcrossPlansAndUsesLastOccurrence() = runBlocking {
        workout("target")
        workout("older", "session", start = 1, finish = 80, reps = 2)
        workout("later-start-but-earlier-finish", "session", start = 90, finish = 100, reps = 3)
        workout("same-finish-earlier-start", "session", start = 10, finish = 200, reps = 4)
        val source = workout("latest", "session", start = 20, finish = 200, reps = 5)
        db.workoutDao().update(source.workout.copy(sourcePlanId = "another-workout",
            sourceTrainingPlanId = "another-plan", trainingPlan = "Old plan"))
        db.workoutExerciseDao().insert(WorkoutExercise(id = "latest-repeat", workoutId = "latest",
            exerciseId = "row", position = 2, notes = "Most recent occurrence"))
        db.workoutSetDao().insert(WorkoutSet(id = "latest-repeat-set", workoutExerciseId = "latest-repeat",
            position = 0, reps = 16, weightGrams = 12347))
        workout("active", "session", start = 500, reps = 20)
        workout("draft", "draft", start = 500, reps = 21)
        workout("edit", "edit", start = 500, reps = 22)
        workout("saved", "plan", start = 500, reps = 23)
        workout("unrelated", "session", start = 500, finish = 600, exerciseId = "curl", reps = 24)
        val sourceBefore = db.workoutDao().getDetails("latest")
        repository.importLastSession("target", "target-entry")
        val imported = db.workoutDao().getDetails("target")!!.exercises.single()
        assertEquals(16, imported.sets.single().reps)
        assertEquals(12347L, imported.sets.single().weightGrams)
        assertEquals("Note target", imported.workoutExercise.notes)
        assertEquals(sourceBefore, db.workoutDao().getDetails("latest"))
    }

    @Test fun replacesAllPrescriptionRowsWithoutCopyingNotesResultsOrChangingSetup() = runBlocking {
        val target = workout("target", "draft")
        val targetEntry = target.exercises.single().workoutExercise
        db.workoutExerciseDao().insert(WorkoutExercise(id = "untouched", workoutId = "target",
            exerciseId = "curl", position = 1, notes = "Keep this note"))
        db.workoutSetDao().insert(WorkoutSet(id = "untouched-set", workoutExerciseId = "untouched",
            position = 0, reps = 11, weightGrams = 6789))
        workout("source", "session", finish = 100)
        val sourceEntry = db.workoutDao().getDetails("source")!!.exercises.single().workoutExercise
        val note = "  Brace before lifting.\nKeep the back neutral.  "
        db.workoutExerciseDao().update(sourceEntry.copy(notes = note,
            equipmentPositions = listOf(EquipmentPosition("Bench angle", "30 degrees"))))
        db.workoutSetDao().delete("source-set")
        val sourceSets = listOf(
            WorkoutSet(id = "warmup", workoutExerciseId = "source-entry", position = 2, reps = 15,
                weightGrams = 12347, isWarmup = true, modifier = "none", completedAt = 50,
                actualReps = 13, rpe = 6, notes = "Do not import this note", activeMillis = 4000, restMillis = 12000),
            WorkoutSet(id = "super", workoutExerciseId = "source-entry", position = 4, reps = 8,
                weightGrams = 24691, modifier = "superset", completedAt = 80, actualReps = 6,
                rpe = 9, notes = "Another recorded note", activeMillis = 5500, restMillis = 22000),
            // Even an unperformed prescription in the completed session is imported in this first phase.
            WorkoutSet(id = "drop", workoutExerciseId = "source-entry", position = 7, reps = 12,
                weightGrams = 13579, modifier = "drop_set"),
        )
        sourceSets.reversed().forEach { db.workoutSetDao().insert(it) }
        val sourceBefore = db.workoutDao().getDetails("source")
        val targetBefore = db.workoutDao().getDetails("target")!!
        repository.importLastSession("target", "target-entry")
        val imported = db.workoutDao().getDetails("target")!!
        val importedEntry = imported.exercises.single { it.workoutExercise.id == "target-entry" }
        val sets = importedEntry.sets.sortedBy { it.position }
        assertEquals(targetBefore.workout, imported.workout)
        assertEquals(targetEntry, importedEntry.workoutExercise)
        assertEquals(targetBefore.exercises.single { it.workoutExercise.id == "untouched" },
            imported.exercises.single { it.workoutExercise.id == "untouched" })
        assertEquals(listOf(0, 1, 2), sets.map { it.position })
        assertEquals(sourceSets.map { it.reps }, sets.map { it.reps })
        assertEquals(sourceSets.map { it.weightGrams }, sets.map { it.weightGrams })
        assertEquals(sourceSets.map { it.isWarmup }, sets.map { it.isWarmup })
        assertEquals(sourceSets.map { it.modifier }, sets.map { it.modifier })
        val oldIds = sourceSets.map { it.id }.toSet() + "target-set"
        assertTrue(sets.none { it.id in oldIds })
        assertEquals(3, sets.map { it.id }.distinct().size)
        assertTrue(sets.all { it.completedAt == null && it.actualReps == null && it.rpe == null &&
            it.notes.isEmpty() && it.activeMillis == 0L && it.restMillis == 0L })
        assertEquals(sourceBefore, db.workoutDao().getDetails("source"))
    }

    @Test fun invalidTargetsAndMissingHistoryLeaveDataUnchangedAndFailedInsertRollsBack() = runBlocking {
        workout("target", "draft")
        workout("foreign")
        workout("active", "session")
        workout("unrelated-history", "session", finish = 10, exerciseId = "curl")
        val before = repository.observeSessions().first()
        expectInvalid("No completed session") { repository.importLastSession("target", "target-entry") }
        expectInvalid("no longer in the workout") { repository.importLastSession("target", "foreign-entry") }
        expectInvalid("no longer available") { repository.importLastSession("missing", "target-entry") }
        expectInvalid("no longer in the workout") { repository.importLastSession("target", "missing") }
        expectInvalid("Active and recorded sessions") { repository.importLastSession("active", "active-entry") }
        expectInvalid("Active and recorded sessions") {
            repository.importLastSession("unrelated-history", "unrelated-history-entry")
        }
        assertEquals(before, repository.observeSessions().first())

        workout("older", "session", finish = 20, reps = 9)
        workout("empty-latest", "session", finish = 30)
        db.workoutSetDao().delete("empty-latest-set")
        val beforeEmpty = db.workoutDao().getDetails("target")
        expectInvalid("has no sets to import") { repository.importLastSession("target", "target-entry") }
        assertEquals(beforeEmpty, db.workoutDao().getDetails("target"))

        db.workoutSetDao().insert(WorkoutSet(id = "latest-first", workoutExerciseId = "empty-latest-entry",
            position = 0, reps = 13, weightGrams = 123))
        db.workoutSetDao().insert(WorkoutSet(id = "latest-second", workoutExerciseId = "empty-latest-entry",
            position = 1, reps = 14, weightGrams = 456))
        val beforeFailure = repository.observeSessions().first()
        // Force failure after deleting the default and inserting the first replacement row.
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_imported_second_set BEFORE INSERT ON workout_sets
            WHEN NEW.workoutExerciseId = 'target-entry' AND NEW.position = 1
            BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END
        """.trimIndent())
        try {
            assertTrue(runCatching { repository.importLastSession("target", "target-entry") }.isFailure)
            assertEquals(beforeFailure, repository.observeSessions().first())
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_imported_second_set") }
    }

    @Test fun editorImportCanBeDiscardedOrSavedWithoutChangingSourceOrPlanMemberships() = runBlocking {
        workout("new-workout")
        workout("other-workout", exerciseId = "curl")
        val planId = repository.trainingPlans.save(null, "Weekly back and biceps",
            listOf("new-workout", "other-workout"), "Monday")
        workout("history", "session", finish = 50, reps = 14)
        val historyBefore = db.workoutDao().getDetails("history")
        val original = db.workoutDao().getDetails("new-workout")!!
        val otherBefore = db.workoutDao().getDetails("other-workout")
        val membershipsBefore = db.trainingPlanDao().get(planId)
        val editId = repository.workoutEditor.begin("new-workout")
        suspend fun editEntryId() = db.workoutDao().getDetails(editId)!!.exercises.single().workoutExercise.id
        repository.importLastSession(editId, editEntryId())
        assertEquals(14, db.workoutDao().getDetails(editId)!!.orderedSets().single().reps)
        assertEquals(original, db.workoutDao().getDetails("new-workout"))
        repository.workoutEditor.discard("new-workout")
        assertNull(db.workoutDao().getDetails(editId))
        assertEquals(original, db.workoutDao().getDetails("new-workout"))
        repository.workoutEditor.begin("new-workout")
        repository.importLastSession(editId, editEntryId())
        repository.savePlan(editId)
        val saved = db.workoutDao().getDetails("new-workout")!!
        assertEquals(original.workout, saved.workout)
        assertEquals(14, saved.orderedSets().single().reps)
        assertEquals("Note new-workout", saved.exercises.single().workoutExercise.notes)
        assertEquals(original.exercises.single().workoutExercise.equipmentPositions,
            saved.exercises.single().workoutExercise.equipmentPositions)
        assertEquals(otherBefore, db.workoutDao().getDetails("other-workout"))
        assertEquals(historyBefore, db.workoutDao().getDetails("history"))
        assertEquals(membershipsBefore, db.trainingPlanDao().get(planId))
    }

    @Test fun viewingDoesNotMutateAndImportUsesTheViewedExerciseEvenWhenANewerSessionArrives() = runBlocking {
        val target = workout("target", "draft")
        val viewed = workout("viewed", "session", finish = 100, reps = 7)
        db.workoutExerciseDao().insert(WorkoutExercise(id = "other-entry", workoutId = "viewed",
            exerciseId = "curl", position = 1))
        db.workoutSetDao().insert(WorkoutSet(id = "other-set", workoutExerciseId = "other-entry",
            position = 0, reps = 22, weightGrams = 456))
        val before = repository.observeSessions().first()
        val preview = repository.getLastSession("target", "target-entry")
        assertEquals("viewed-entry", preview.entry.workoutExercise.id)
        assertEquals(viewed.exercises.single(), preview.entry)
        assertEquals(before, repository.observeSessions().first())
        assertEquals(target, db.workoutDao().getDetails("target"))

        workout("newer", "session", finish = 200, reps = 19)
        repository.importLastSession("target", "target-entry", preview.entry.workoutExercise.id)
        assertEquals(7, db.workoutDao().getDetails("target")!!.orderedSets().single().reps)
        assertEquals("Note target", db.workoutDao().getDetails("target")!!.exercises.single().workoutExercise.notes)
        assertEquals("newer-entry", repository.getLastSession("target", "target-entry").entry.workoutExercise.id)
    }

    @Test fun missingOrChangedViewedSourcesCannotReplaceTargetSets() = runBlocking {
        val target = workout("target", "draft")
        workout("recorded", "session", finish = 100)
        workout("active", "session")
        workout("other-exercise", "session", finish = 200, exerciseId = "curl")
        expectInvalid("no longer available") { repository.importLastSession("target", "target-entry", "missing") }
        assertTrue(runCatching { repository.importLastSession("target", "target-entry", "active-entry") }.isFailure)
        expectInvalid("recorded exercise has changed") {
            repository.importLastSession("target", "target-entry", "other-exercise-entry")
        }
        val preview = repository.getLastSession("target", "target-entry")
        db.workoutExerciseDao().update(preview.entry.workoutExercise.copy(exerciseId = "curl"))
        expectInvalid("recorded exercise has changed") {
            repository.importLastSession("target", "target-entry", preview.entry.workoutExercise.id)
        }
        assertEquals(target, db.workoutDao().getDetails("target"))
    }
}
