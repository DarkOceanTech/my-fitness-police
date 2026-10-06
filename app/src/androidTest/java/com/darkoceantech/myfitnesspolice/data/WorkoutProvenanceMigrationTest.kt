package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutProvenanceMigrationTest {
    @Test fun versionElevenUpgradePreservesHistoryWithoutInventingOldWorkoutMembership() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "workout-provenance-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/11.json")
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
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, name, notes) VALUES ('plan', 1000, 'plan', 'Current module', 'plan note')")
            old.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES ('training', 'Current training name', 1000)")
            old.execSQL("INSERT INTO training_plan_workouts (trainingPlanId, workoutId, position) VALUES ('training', 'plan', 0)")
            old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, name, notes, sourceTrainingPlanId, trainingPlan) VALUES ('history', 1000, 9000, 'session', 'Recorded session', 'history note', 'training', 'Recorded training name')")
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes, equipmentPositions) VALUES ('entry', 'history', 'exercise', 0, 'exercise note', ?)",
                arrayOf("""[{"name":"Seat","position":"4"}]"""))
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes, rpe) VALUES ('set', 'entry', 0, 10, 8, 12345, 6000, 1, 5000, 3000, 'set note', 7)")
            old.execSQL("INSERT INTO workout_session_states (workoutId, phase, currentSetId, phaseStartedAt, phaseElapsedMillis, dutyStartedAt, dutyElapsedMillis, isPaused, awaitingActual, pauseReason) VALUES ('history', 'finished', 'set', NULL, 0, NULL, 8000, 0, 0, 'Kept pause note')")
            old.version = 11
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename)
            .addMigrations(FitnessDatabase.MIGRATION_11_12, FitnessDatabase.MIGRATION_12_13, FitnessDatabase.MIGRATION_13_14, FitnessDatabase.MIGRATION_14_15).build()
        var db = open()
        try {
            val migrated = db.workoutDao().getDetails("history")!!
            assertEquals(15, db.openHelper.writableDatabase.version)
            assertEquals(WorkoutSet(id = "set", workoutExerciseId = "entry", position = 0, reps = 10,
                actualReps = 8, weightGrams = 12345, completedAt = 6000, isWarmup = true,
                activeMillis = 5000, restMillis = 3000, notes = "set note", rpe = 7), migrated.orderedSets().single())
            assertEquals("history note", migrated.workout.notes)
            assertEquals("training", migrated.workout.sourceTrainingPlanId)
            assertEquals("Recorded training name", migrated.workout.trainingPlan)
            assertEquals("exercise note", migrated.exercises.single().workoutExercise.notes)
            assertEquals(listOf(EquipmentPosition("Seat", "4")), migrated.exercises.single().workoutExercise.equipmentPositions)
            assertNull(migrated.exercises.single().workoutExercise.sourceWorkoutId)
            assertEquals("", migrated.exercises.single().workoutExercise.sourceWorkoutName)
            assertEquals(8000L, migrated.sessionState!!.dutyElapsedMillis)
            assertEquals("Kept pause note", migrated.sessionState!!.pauseReason)
            assertEquals(listOf("plan"), db.trainingPlanDao().get("training")!!.workoutIds())
            db.close(); db = open()
            assertEquals(migrated, db.workoutDao().getDetails("history"))
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
