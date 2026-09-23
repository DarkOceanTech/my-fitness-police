package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExerciseCatalogMigrationTest {
    @Test fun versionSixUpgradeEnrichesCatalogWithoutLosingUserDataOrHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "catalog-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.darkoceantech.myfitnesspolice.data.FitnessDatabase/6.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val originalSeedId = "e12d7b3e-588b-4fc2-8d3f-000000000002"
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
            old.execSQL("INSERT INTO exercises VALUES (?, 'My bench press', 'My custom equipment', 1)", arrayOf(originalSeedId))
            old.execSQL("INSERT INTO exercises VALUES ('custom', 'Custom row', 'Machine', 0)")
            old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, notes) VALUES ('history', 1000, 2000, 'session', 'keep me')")
            old.execSQL("INSERT INTO workout_exercises VALUES ('entry', 'history', ?, 0, 'exercise note')", arrayOf(originalSeedId))
            old.execSQL("""INSERT INTO workout_sets (id, workoutExerciseId, position, reps, weightGrams, completedAt, isWarmup, actualReps, activeMillis, restMillis, notes)
                VALUES ('set', 'entry', 0, 10, 30000, 1900, 0, 8, 400, 200, 'set note')""")
            old.version = 6
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name)
            .addMigrations(FitnessDatabase.MIGRATION_6_7, FitnessDatabase.MIGRATION_7_8, FitnessDatabase.MIGRATION_8_9, FitnessDatabase.MIGRATION_9_10, FitnessDatabase.MIGRATION_10_11).addCallback(SeedExercises).build()
        var db = open()
        try {
            val catalog = db.exerciseDao().getAll()
            assertEquals(49, catalog.size)
            val enriched = catalog.single { it.id == originalSeedId }
            assertEquals("My bench press", enriched.name)
            assertEquals("My custom equipment", enriched.equipment)
            assertTrue(enriched.isArchived)
            assertTrue(enriched.description.isNotBlank())
            assertTrue(enriched.primaryMuscles.contains("Pectoralis major"))
            assertTrue(enriched.secondaryMuscles.contains("Deltoid"))
            val custom = catalog.single { it.id == "custom" }
            assertEquals("Machine", custom.equipment)
            assertEquals("", custom.description)
            assertEquals("", custom.primaryMuscles)
            val history = db.workoutDao().getDetails("history")!!
            assertEquals("keep me", history.workout.notes)
            assertEquals(originalSeedId, history.exercises.single().workoutExercise.exerciseId)
            val set = history.performedSets().single()
            assertEquals(8, set.actualReps)
            assertEquals("set note", set.notes)
            assertEquals(400L, set.activeMillis)
            assertEquals(200L, set.restMillis)
            db.close()
            db = open()
            assertEquals(49, db.exerciseDao().getAll().size)
            assertEquals(enriched, db.exerciseDao().getAll().single { it.id == originalSeedId })
            assertEquals(set, db.workoutDao().getDetails("history")!!.performedSets().single())
            assertEquals(3, db.exerciseDao().getAll().count { it.id in listOf(
                "e12d7b3e-588b-4fc2-8d3f-000000000007",
                "e12d7b3e-588b-4fc2-8d3f-000000000008",
                "e12d7b3e-588b-4fc2-8d3f-000000000009") })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
