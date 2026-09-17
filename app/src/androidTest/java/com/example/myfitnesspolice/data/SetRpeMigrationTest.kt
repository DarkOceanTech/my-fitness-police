package com.example.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SetRpeMigrationTest {
    @Test fun versionNineUpgradePreservesWorkoutsAndStoresOptionalRpe() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "set-rpe-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.example.myfitnesspolice.data.FitnessDatabase/9.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises (id, name, equipment, isArchived) VALUES ('exercise', 'Row', 'Cable', 0)")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, name, notes) VALUES ('plan', 1000, 'plan', 'Rows only', 'plan note')")
            old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, name, notes, sourcePlanId) VALUES ('history', 1000, 2000, 'session', 'Back day', 'history note', 'plan')")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, name, notes) VALUES ('active', 3000, 'session', 'Unfinished', 'pause later')")
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes, equipmentPositions) VALUES ('entry', 'history', 'exercise', 0, 'exercise note', ?)",
                arrayOf("""[{"name":"Seat","position":"4"}]"""))
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes) VALUES ('template-entry', 'plan', 'exercise', 0, '')")
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes) VALUES ('set', 'entry', 0, 10, 8, 12345, 1500, 0, 15000, 20000, 'set note')")
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, weightGrams, isWarmup) VALUES ('template-set', 'template-entry', 0, 10, 12345, 0)")
            old.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES ('training', 'Back focus', 1000)")
            old.execSQL("INSERT INTO training_plan_workouts (trainingPlanId, workoutId, position) VALUES ('training', 'plan', 0)")
            old.version = 9
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename)
            .addMigrations(FitnessDatabase.MIGRATION_9_10, FitnessDatabase.MIGRATION_10_11).build()
        var db = open()
        try {
            val history = db.workoutDao().getDetails("history")!!
            val original = WorkoutSet(id = "set", workoutExerciseId = "entry", position = 0,
                reps = 10, actualReps = 8, weightGrams = 12345, completedAt = 1500,
                activeMillis = 15000, restMillis = 20000, notes = "set note")
            assertEquals(original, history.orderedSets().single())
            assertEquals("Back day", history.workout.name)
            assertEquals("history note", history.workout.notes)
            assertEquals("plan", history.workout.sourcePlanId)
            assertEquals(2000L, history.workout.finishedAt)
            assertEquals(listOf(EquipmentPosition("Seat", "4")), history.exercises.single().workoutExercise.equipmentPositions)
            val template = db.workoutDao().getDetails("plan")!!
            assertNull(template.orderedSets().single().rpe)
            assertEquals(listOf("active"), db.workoutDao().observeUnfinished().first().map { it.id })
            assertEquals(listOf("plan"), db.trainingPlanDao().observeAll().first().single().workoutIds())
            val repo = FitnessRepository(db)
            repo.correctHistorySet("history", "set", null, 10, 8, 1)
            assertEquals(original.copy(rpe = 1), db.workoutDao().getDetails("history")!!.orderedSets().single())
            repo.correctHistorySet("history", "set", null, 10, 8, 10)
            repo.saveHistorySetNote("history", "set", "updated note")
            db.close(); db = open()
            assertEquals(original.copy(rpe = 10, notes = "updated note"), db.workoutDao().getDetails("history")!!.orderedSets().single())
            assertEquals(template, db.workoutDao().getDetails("plan"))
            assertEquals(11, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
