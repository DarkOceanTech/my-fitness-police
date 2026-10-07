package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MobilityPersistenceTest {
    @Test fun migrationPreservesWorkoutPlanScheduleAndLogsAndSeedsOnlyMovements() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "mobility-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/15.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (n in 0 until entities.length()) {
                val entity = entities.getJSONObject(n)
                old.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises (id,name,equipment,isArchived) VALUES ('e','Row','Cable',0)")
            old.execSQL("INSERT INTO training_plans (id,name,createdAt,dayOfWeek) VALUES ('p','Back patrol',100,'Monday')")
            old.execSQL("INSERT INTO training_schedule (trainingPlanId,scheduledDate) VALUES ('p','2026-10-06')")
            old.execSQL("INSERT INTO workouts (id,startedAt,finishedAt,kind,name,notes,trainingPlan,sourceTrainingPlanId) VALUES ('log',200,300,'session','Back','Keep this note','Back patrol','p')")
            old.execSQL("INSERT INTO workout_exercises (id,workoutId,exerciseId,position,notes) VALUES ('entry','log','e',0,'Original exercise note')")
            old.execSQL("INSERT INTO workout_sets (id,workoutExerciseId,position,reps,actualReps,weightGrams,completedAt,isWarmup,modifier,activeMillis,restMillis,notes) VALUES ('set','entry',0,10,9,10000,250,0,'none',20,30,'Original set note')")
            old.version = 15
        }
        val db = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_15_16).build()
        try {
            assertEquals(16, db.openHelper.writableDatabase.version)
            assertEquals(7, db.stretchDao().catalog().first().size)
            assertTrue(db.stretchDao().sessions().first().isEmpty())
            assertTrue(db.stretchDao().routines().first().isEmpty())
            assertEquals("Keep this note", db.workoutDao().getDetails("log")!!.workout.notes)
            assertEquals(9, db.workoutDao().getDetails("log")!!.performedSets().single().actualReps)
            assertEquals("Back patrol", db.trainingPlanDao().get("p")!!.plan.name)
            assertEquals("2026-10-06", db.trainingScheduleDao().observeAll().first().single().scheduledDate)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun pauseRestorationSkipHistoryAndCrossSessionProtection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "mobility-state-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            var repo = FitnessRepository(db)
            val routine = StretchRoutine(name = "Morning patrol", finalRest = false,
                stretches = listOf(RoutineStretch(movement = StretchMovement(name = "Calf"), sets = listOf(StretchSet(work = 10)))))
            repo.stretches.saveRoutine(routine)
            val id = repo.stretches.start(routine.id, 1000)
            try { repo.stretches.start(routine.id, 2000); fail("Duplicate routine allowed") } catch (_: IllegalArgumentException) {}
            try { repo.startWorkout(); fail("Overlapping strength workout allowed") } catch (_: IllegalArgumentException) {}
            repo.stretches.control(id, "pause", now = 11000)
            val paused = StretchJson.run(db.stretchDao().active()!!.progress)
            assertEquals(5000L, paused.elapsed)
            db.close(); db = open(); repo = FitnessRepository(db)
            repo.stretches.tick(100000)
            assertEquals(paused, StretchJson.run(db.stretchDao().active()!!.progress))
            repo.stretches.control(id, "resume", now = 100000)
            repo.stretches.tick(105000)
            val recorded = db.stretchDao().session(id)!!
            assertEquals("completed", StretchJson.run(recorded.progress).status)
            assertEquals(10000L, StretchTiming.actual(StretchJson.run(recorded.progress), "WORK"))
            assertEquals(105000L, recorded.endedAt)
            repo.stretches.saveRoutine(routine.copy(name = "Edited later", stretches = emptyList<RoutineStretch>() + routine.stretches.map { it.copy(note = "New note") }))
            assertEquals(recorded.snapshot, db.stretchDao().session(id)!!.snapshot)
            assertEquals("Morning patrol", StretchJson.routine(recorded.snapshot).name)
            val second = repo.stretches.start(routine.id, 200000)
            repo.stretches.control(second, "skip", now = 207000)
            val early = db.stretchDao().session(second)!!
            assertEquals(2000L, StretchTiming.actual(StretchJson.run(early.progress), "WORK"))
            assertEquals(0, StretchTiming.completedSets(StretchJson.run(early.progress)))
            val third = repo.stretches.start(routine.id, 300000)
            repo.stretches.control(third, "finish", "Interrupted", 308000)
            assertEquals("finished early", StretchJson.run(db.stretchDao().session(third)!!.progress).status)
            assertEquals("Interrupted", db.stretchDao().session(third)!!.notes)
            val reconciled = repo.stretches.start(routine.id, 500000)
            repo.stretches.control(reconciled, "pause", now = 535000)
            assertEquals(515000L, db.stretchDao().session(reconciled)!!.endedAt)
            repo.startWorkout()
            try { repo.stretches.start(routine.id, 400000); fail("Overlapping mobility workout allowed") } catch (_: IllegalArgumentException) {}
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
