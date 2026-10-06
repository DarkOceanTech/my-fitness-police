package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MixedTrainingPlanMigrationTest {
    @Test fun versionThirteenUpgradePreservesPlansAndGroupedHistoryThenPersistsDirectPrescriptions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "mixed-plan-migration-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/13.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql")
                    .replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises (id, name, equipment, isArchived) VALUES ('exercise', 'Row', 'Cable', 0)")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, name, notes, trainingPlan) VALUES ('saved', 1000, 'plan', 'Rows only', 'plan note', 'Back day')")
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes) VALUES ('saved-entry', 'saved', 'exercise', 0, 'saved setup')")
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, weightGrams, isWarmup) VALUES ('saved-set', 'saved-entry', 0, 12, 25000, 0)")
            old.execSQL("INSERT INTO training_plans (id, name, createdAt, dayOfWeek) VALUES ('training', 'Back day', 1000, 'Thursday')")
            old.execSQL("INSERT INTO training_plan_workouts (trainingPlanId, workoutId, position) VALUES ('training', 'saved', 0)")
            listOf("first", "second").forEachIndexed { index, id ->
                val start = 1000L + index * 10000L
                old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, name, notes, sourcePlanId, sourceTrainingPlanId, trainingPlan, historyGroupId) VALUES (?, ?, ?, 'session', ?, 'history note', 'saved', 'training', 'Back day snapshot', 'same-batch')",
                    arrayOf(id, start, start + 9000, "Recorded $id"))
                old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes, equipmentPositions, sourceWorkoutId, sourceWorkoutName) VALUES (?, ?, 'exercise', 0, 'exercise note', ?, 'saved', 'Rows only snapshot')",
                    arrayOf("$id-entry", id, """[{"name":"Seat","position":"4"}]"""))
                old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes, rpe, modifier) VALUES (?, ?, 0, 10, 8, 12345, ?, 1, 5000, 4000, 'set note', 7, 'drop_set')",
                    arrayOf("$id-set", "$id-entry", start + 5000))
                old.execSQL("INSERT INTO workout_session_states (workoutId, phase, currentSetId, phaseStartedAt, phaseElapsedMillis, dutyStartedAt, dutyElapsedMillis, isPaused, awaitingActual, pauseReason) VALUES (?, 'finished', ?, NULL, 0, NULL, 9000, 0, 0, 'Water break')",
                    arrayOf(id, "$id-set"))
            }
            old.version = 13
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename)
            .addMigrations(FitnessDatabase.MIGRATION_13_14, FitnessDatabase.MIGRATION_14_15).build()
        var db = open()
        try {
            assertEquals(15, db.openHelper.writableDatabase.version)
            val plan = db.trainingPlanDao().get("training")!!
            assertEquals(TrainingPlan(id = "training", name = "Back day", createdAt = 1000, dayOfWeek = "Thursday"), plan.plan)
            assertEquals(listOf(TrainingPlanItem.WorkoutItem("saved")), plan.orderedItems())
            assertTrue(plan.exerciseMembers.isEmpty())
            val saved = db.workoutDao().getDetails("saved")!!
            assertEquals("Rows only", saved.workout.name)
            assertEquals("plan note", saved.workout.notes)
            assertEquals(WorkoutSet(id = "saved-set", workoutExerciseId = "saved-entry", position = 0, reps = 12, weightGrams = 25000), saved.orderedSets().single())
            val history = listOf("first", "second").mapIndexed { index, id ->
                val workout = db.workoutDao().getDetails(id)!!
                val start = 1000L + index * 10000L
                assertEquals("same-batch", workout.workout.historyGroupId)
                assertEquals("training", workout.workout.sourceTrainingPlanId)
                assertEquals("saved", workout.workout.sourcePlanId)
                assertEquals("Back day snapshot", workout.workout.trainingPlan)
                assertEquals("Recorded $id", workout.workout.name)
                assertEquals("history note", workout.workout.notes)
                assertEquals(start, workout.workout.startedAt)
                assertEquals(start + 9000, workout.workout.finishedAt)
                assertEquals(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = "exercise", position = 0,
                    notes = "exercise note", equipmentPositions = listOf(EquipmentPosition("Seat", "4")),
                    sourceWorkoutId = "saved", sourceWorkoutName = "Rows only snapshot"), workout.exercises.single().workoutExercise)
                assertEquals(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0, reps = 10,
                    actualReps = 8, weightGrams = 12345, completedAt = start + 5000, isWarmup = true,
                    activeMillis = 5000, restMillis = 4000, notes = "set note", rpe = 7, modifier = "drop_set"), workout.orderedSets().single())
                assertEquals(9000L, workout.sessionState!!.dutyElapsedMillis)
                assertEquals("Water break", workout.sessionState!!.pauseReason)
                workout
            }
            val items = listOf(TrainingPlanItem.ExerciseItem("exercise", listOf(TrainingPlanSet(5, 32109, true, "superset"))),
                TrainingPlanItem.WorkoutItem("saved"))
            FitnessRepository(db).trainingPlans.saveItems("training", "Back day", items)
            db.close(); db = open()
            assertEquals(items, db.trainingPlanDao().get("training")!!.orderedItems())
            assertEquals("Thursday", db.trainingPlanDao().get("training")!!.plan.dayOfWeek)
            assertEquals(saved, db.workoutDao().getDetails("saved"))
            history.forEach { assertEquals(it, db.workoutDao().getDetails(it.workout.id)) }
            assertEquals(3, FitnessRepository(db).observeSessions().first().size)
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
