package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrainingExerciseNoteMigrationTest {
    @Test fun migrate18PreservesDataAndPlanNotesReloadAndSnapshotIndependently() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "plan-note-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/18.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
            old.execSQL("INSERT INTO training_plans (id,name,createdAt) VALUES ('plan','Back patrol',1000)")
            old.execSQL("INSERT INTO training_plan_exercises (trainingPlanId,exerciseId,position,sets) VALUES ('plan','e',0,?)",
                arrayOf(TrainingPlanSetConverters().encode(listOf(TrainingPlanSet()))))
            old.version = 18
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_18_19).build()
        var db = open()
        try {
            val existing = db.trainingPlanDao().get("plan")!!.orderedItems().single() as TrainingPlanItem.ExerciseItem
            assertEquals("", existing.notes)
            assertEquals("#TIME", db.workoutDao().getDetails("w")!!.orderedSets().single().notes)
            val repo = FitnessRepository(db)
            repo.trainingPlans.saveItems("plan", "Back patrol", listOf(existing.copy(notes = "Seat 4; slow lowering")))
            db.close(); db = open()
            val saved = db.trainingPlanDao().get("plan")!!.orderedItems().single() as TrainingPlanItem.ExerciseItem
            assertEquals("Seat 4; slow lowering", saved.notes)
            val session = FitnessRepository(db).startTraining("plan")
            assertEquals(saved.notes, db.workoutDao().getDetails(session)!!.exercises.single().workoutExercise.notes)
            FitnessRepository(db).trainingPlans.saveItems("plan", "Back patrol", listOf(saved.copy(notes = "Changed plan note")))
            db.close(); db = open()
            assertEquals("Changed plan note", db.trainingPlanDao().get("plan")!!.exerciseMembers.single().member.notes)
            assertEquals("Seat 4; slow lowering", db.workoutDao().getDetails(session)!!.exercises.single().workoutExercise.notes)
            assertEquals(2000L, db.workoutDao().getDetails("w")!!.orderedSets().single().activeMillis)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}