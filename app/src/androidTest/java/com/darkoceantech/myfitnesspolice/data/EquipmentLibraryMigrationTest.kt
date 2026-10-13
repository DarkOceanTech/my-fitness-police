package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EquipmentLibraryMigrationTest {
    @Test fun version16PreservesLegacyDescriptionsPositionsAndHistoryWithoutGuessing() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "equipment-v16-" + UUID.randomUUID() + ".db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/16.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val positions = EquipmentPositionConverters().encode(listOf(EquipmentPosition("Seat", "4 / high"), EquipmentPosition("Bench", "30°")))
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises (id,name,equipment,isArchived,description,primaryMuscles,secondaryMuscles) VALUES ('e','Row','Cable; independent handles',0,'Keep description','Latissimus dorsi','Biceps brachii')")
            old.execSQL("INSERT INTO workouts (id,startedAt,kind,notes) VALUES ('p',1000,'plan','Keep workout')")
            old.execSQL("INSERT INTO workouts (id,startedAt,finishedAt,kind,notes) VALUES ('h',1000,2000,'session','Keep history')")
            for (w in listOf("p", "h")) {
                old.execSQL("INSERT INTO workout_exercises (id,workoutId,exerciseId,position,notes,equipmentPositions) VALUES (?,?,?,0,?,?)", arrayOf(w + "e", w, "e", "Keep exercise note", positions))
                old.execSQL("INSERT INTO workout_sets (id,workoutExerciseId,position,reps,weightGrams,isWarmup,notes,completedAt,actualReps,rpe) VALUES (?,?,0,10,10000,0,'Keep set note',1500,8,7)", arrayOf(w + "s", w + "e"))
            }
            old.version = 16
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_16_17).build()
        var db = open()
        try {
            val plan = db.workoutDao().getDetails("p")!!
            val history = db.workoutDao().getDetails("h")!!
            assertEquals("Cable; independent handles", db.exerciseDao().get("e")!!.equipment)
            assertNull(db.exerciseDao().get("e")!!.equipmentDefault)
            assertEquals(positions, EquipmentPositionConverters().encode(plan.exercises.single().workoutExercise.equipmentOverride!!.positions))
            val snap = history.exercises.single().workoutExercise.equipmentSnapshot!!
            assertTrue(snap.equipment.isEmpty()); assertEquals("", snap.legacyDescription)
            assertEquals(plan.exercises.single().workoutExercise.equipmentPositions, snap.positions)
            assertEquals("Keep set note", history.orderedSets().single().notes)
            assertEquals(8, history.orderedSets().single().actualReps); assertEquals(7, history.orderedSets().single().rpe)
            assertTrue(EquipmentLibraryRepository(db).observe().first().equipment.isEmpty())
            db.close(); db = open()
            assertEquals(snap, db.workoutDao().getDetails("h")!!.exercises.single().workoutExercise.equipmentSnapshot)
            assertEquals("Keep history", db.workoutDao().getDetails("h")!!.workout.notes)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
