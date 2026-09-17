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

class TrainingPlanMigrationTest {
    @Test fun versionEightUpgradeGroupsLegacyLabelsWithoutChangingWorkoutHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "training-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.example.myfitnesspolice.data.FitnessDatabase/8.json")
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
            old.execSQL("INSERT INTO exercises (id, name, equipment, isArchived) VALUES ('e', 'Curl', 'Dumbbell', 0)")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, targetMuscles, trainingPlan, notes) VALUES ('p1', 1000, 'plan', 'Biceps', 'Arm Focus', 'keep plan')")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, targetMuscles, trainingPlan, notes) VALUES ('p2', 1000, 'plan', 'Back', ' arm focus ', '')")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, targetMuscles, notes) VALUES ('p3', 1000, 'plan', 'Abdominal', '')")
            old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, targetMuscles, trainingPlan, notes) VALUES ('history', 1000, 2000, 'session', 'Biceps', 'History only', 'keep history')")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, notes) VALUES ('active', 3000, 'session', 'keep active')")
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes, equipmentPositions) VALUES ('entry', 'history', 'e', 0, 'note', ?)",
                arrayOf("""[{"name":"Seat","position":"4"}]"""))
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes) VALUES ('s', 'entry', 0, 10, 7, 10000, 1500, 0, 15000, 20000, 'set note')")
            old.version = 8
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, filename).addMigrations(FitnessDatabase.MIGRATION_8_9, FitnessDatabase.MIGRATION_9_10, FitnessDatabase.MIGRATION_10_11).build()
        var db = open()
        try {
            val groups = db.trainingPlanDao().observeAll().first()
            assertEquals(1, groups.size)
            assertEquals(listOf("p1", "p2"), groups.single().workoutIds())
            val history = db.workoutDao().getDetails("history")!!
            assertEquals("History only", history.workout.trainingPlan)
            assertEquals("keep history", history.workout.notes)
            assertEquals(7, history.orderedSets().single().actualReps)
            assertEquals(15000L, history.orderedSets().single().activeMillis)
            assertEquals(20000L, history.orderedSets().single().restMillis)
            assertEquals(listOf(EquipmentPosition("Seat", "4")), history.exercises.single().workoutExercise.equipmentPositions)
            assertEquals("Biceps", db.workoutDao().getDetails("p1")!!.workout.displayName())
            assertEquals(listOf("active"), db.workoutDao().observeUnfinished().first().map { it.id })
            assertEquals(5, db.workoutDao().observeAllDetails().first().size)
            db.close(); db = open()
            assertEquals(groups, db.trainingPlanDao().observeAll().first())
            assertEquals(history, db.workoutDao().getDetails("history"))
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
