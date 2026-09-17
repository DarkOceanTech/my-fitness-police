package com.example.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EquipmentPositionMigrationTest {
    @Test fun versionSevenUpgradePreservesRowsAndSupportsNamedPositionsAfterReopen() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "equipment-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.example.myfitnesspolice.data.FitnessDatabase/7.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
            old.execSQL("INSERT INTO exercises VALUES ('e', 'Cable row', 'Cable', 0, 'Description', 'Latissimus dorsi', 'Biceps brachii')")
            old.execSQL("INSERT INTO workouts (id, startedAt, kind, notes) VALUES ('plan', 1000, 'plan', 'keep')")
            old.execSQL("INSERT INTO workout_exercises VALUES ('entry', 'plan', 'e', 0, 'exercise note')")
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, weightGrams, isWarmup, notes) VALUES ('set', 'entry', 0, 10, 10000, 0, 'set note')")
            old.version = 7
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_7_8, FitnessDatabase.MIGRATION_8_9, FitnessDatabase.MIGRATION_9_10, FitnessDatabase.MIGRATION_10_11).build()
        var db = open()
        try {
            val before = db.workoutDao().getDetails("plan")!!
            assertTrue(before.exercises.single().workoutExercise.equipmentPositions.isEmpty())
            assertEquals("exercise note", before.exercises.single().workoutExercise.notes)
            assertEquals("set note", before.orderedSets().single().notes)
            assertEquals("Latissimus dorsi", before.exercises.single().exercise.primaryMuscles)
            val setup = listOf(EquipmentPosition("Cable pulley", "12"), EquipmentPosition("Back \"support\"", "30° / 2"))
            val repo = FitnessRepository(db)
            repo.saveEquipmentPositions("plan", "entry", setup)
            var rejected = false
            try { repo.saveEquipmentPositions("plan", "entry", listOf(EquipmentPosition("Seat", " "))) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            db.close()
            db = open()
            val after = db.workoutDao().getDetails("plan")!!
            assertEquals(setup, after.exercises.single().workoutExercise.equipmentPositions)
            assertEquals(before.orderedSets(), after.orderedSets())
            assertEquals(before.workout, after.workout)
            assertEquals(1, db.exerciseDao().getAll().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
