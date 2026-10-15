package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TimeCorrectionMigrationTest {
    @Test fun migrate17PreservesRecordedTimingAndReloadsLinkedCorrections() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "time-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/17.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises (id,name,equipment,isArchived,description,primaryMuscles,secondaryMuscles) VALUES ('e','Row','Cable',0,'Keep','Back','Biceps')")
            old.execSQL("INSERT INTO workouts (id,startedAt,finishedAt,kind,notes) VALUES ('w',1000,101000,'session','Keep')")
            old.execSQL("INSERT INTO workout_exercises (id,workoutId,exerciseId,position,notes,equipmentPositions) VALUES ('entry','w','e',0,'Keep','[]')")
            old.execSQL("INSERT INTO workout_sets (id,workoutExerciseId,position,reps,weightGrams,isWarmup,notes,completedAt,actualReps,rpe,activeMillis,restMillis) VALUES ('s','entry',0,10,10000,0,'#TIME',5000,8,7,2000,60000)")
            old.version = 17
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_17_18).build()
        var db = open()
        try {
            assertEquals(2000L, db.workoutDao().getDetails("w")!!.orderedSets().single().activeMillis)
            TimeCorrectionRepository(db).save("w", "s", "s", 42000, 2000, 60000, "Manual", "", "Keep")
            db.close(); db = open()
            val raw = db.workoutDao().getDetails("w")!!
            assertEquals(1000L, raw.workout.startedAt); assertEquals(101000L, raw.workout.finishedAt)
            assertEquals(2000L, raw.orderedSets().single().activeMillis)
            assertEquals(60000L, raw.orderedSets().single().restMillis)
            assertEquals(42000L, raw.withEffectiveTimes().orderedSets().single().activeMillis)
            assertEquals(20000L, raw.withEffectiveTimes().orderedSets().single().restMillis)
            assertEquals("#TIME", raw.orderedSets().single().notes)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
