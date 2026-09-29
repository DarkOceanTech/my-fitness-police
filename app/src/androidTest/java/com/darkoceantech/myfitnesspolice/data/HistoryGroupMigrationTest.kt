package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoryGroupMigrationTest {
    @Test fun versionTwelveUpgradeKeepsSamePlanSessionsIndependentAndPreservesRecordedData() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "history-group-migration-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/12.json")
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
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, name, notes) VALUES ('plan', 1000, 'plan', 'Rows only', 'plan note')")
            old.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES ('training', 'Back day', 1000)")
            old.execSQL("INSERT INTO training_plan_workouts (trainingPlanId, workoutId, position) VALUES ('training', 'plan', 0)")
            listOf("week-one", "week-two").forEachIndexed { index, id ->
                val start = 1000L + index * 604800000L
                old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, name, notes, sourcePlanId, sourceTrainingPlanId, trainingPlan) VALUES (?, ?, ?, 'session', ?, 'history note', 'plan', 'training', 'Back day')",
                    arrayOf(id, start, start + 9000, "Recorded $id"))
                old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes, equipmentPositions, sourceWorkoutId, sourceWorkoutName) VALUES (?, ?, 'exercise', 0, 'exercise note', ?, 'plan', 'Rows only snapshot')",
                    arrayOf("$id-entry", id, """[{"name":"Seat","position":"4"}]"""))
                old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes, rpe) VALUES (?, ?, 0, 10, 8, 12345, ?, 1, 5000, 4000, 'set note', 7)",
                    arrayOf("$id-set", "$id-entry", start + 5000))
                old.execSQL("INSERT INTO workout_session_states (workoutId, phase, currentSetId, phaseStartedAt, phaseElapsedMillis, dutyStartedAt, dutyElapsedMillis, isPaused, awaitingActual, pauseReason) VALUES (?, 'finished', ?, NULL, 0, NULL, 9000, 0, 0, 'Water break')",
                    arrayOf(id, "$id-set"))
            }
            old.version = 12
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename)
            .addMigrations(FitnessDatabase.MIGRATION_12_13, FitnessDatabase.MIGRATION_13_14).build()
        var db = open()
        try {
            assertEquals(14, db.openHelper.writableDatabase.version)
            val migrated = listOf("week-one", "week-two").mapIndexed { index, id ->
                val workout = db.workoutDao().getDetails(id)!!
                val start = 1000L + index * 604800000L
                assertNull(workout.workout.historyGroupId)
                assertEquals("training", workout.workout.sourceTrainingPlanId)
                assertEquals("plan", workout.workout.sourcePlanId)
                assertEquals("Back day", workout.workout.trainingPlan)
                assertEquals("Recorded $id", workout.workout.name)
                assertEquals("history note", workout.workout.notes)
                assertEquals(start, workout.workout.startedAt)
                assertEquals(start + 9000, workout.workout.finishedAt)
                assertEquals(WorkoutExercise(id = "$id-entry", workoutId = id, exerciseId = "exercise", position = 0,
                    notes = "exercise note", equipmentPositions = listOf(EquipmentPosition("Seat", "4")),
                    sourceWorkoutId = "plan", sourceWorkoutName = "Rows only snapshot"), workout.exercises.single().workoutExercise)
                assertEquals(WorkoutSet(id = "$id-set", workoutExerciseId = "$id-entry", position = 0, reps = 10,
                    actualReps = 8, weightGrams = 12345, completedAt = start + 5000, isWarmup = true,
                    activeMillis = 5000, restMillis = 4000, notes = "set note", rpe = 7), workout.orderedSets().single())
                assertEquals(9000L, workout.sessionState!!.dutyElapsedMillis)
                assertEquals("Water break", workout.sessionState!!.pauseReason)
                workout
            }
            assertNull(db.workoutDao().getDetails("plan")!!.workout.historyGroupId)
            assertEquals(listOf("plan"), db.trainingPlanDao().get("training")!!.workoutIds())
            db.close(); db = open()
            migrated.forEach { assertEquals(it, db.workoutDao().getDetails(it.workout.id)) }
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
