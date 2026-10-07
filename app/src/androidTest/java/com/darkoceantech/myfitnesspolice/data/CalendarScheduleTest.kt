package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CalendarScheduleTest {
    @Test fun deletingOneCalendarDatePreservesOtherDatesPlanAndRecordedSessions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FitnessDatabase::class.java).build()
        try {
            db.trainingPlanDao().insert(TrainingPlan("p", "Back patrol", 100))
            db.workoutDao().insert(Workout("log", 200, finishedAt = 300, sourceTrainingPlanId = "p", trainingPlan = "Back patrol"))
            val schedule = TrainingScheduleRepository(db)
            schedule.saveDates("p", listOf("2026-10-01", "2026-10-08"))
            schedule.deleteDate("p", "2026-10-01")
            assertEquals(listOf(TrainingSchedule("p", "2026-10-08")), schedule.observeAll().first())
            assertNotNull(db.trainingPlanDao().get("p"))
            assertNotNull(db.workoutDao().getDetails("log"))
        } finally { db.close() }
    }
    @Test fun appointmentsPersistReplaceAtomicallyAndCascadeWithoutDeletingHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "schedule-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            db.trainingPlanDao().insert(TrainingPlan("p", "Back patrol", 100, "Monday"))
            db.trainingPlanDao().insert(TrainingPlan("other", "Arms patrol", 100))
            db.workoutDao().insert(Workout("log", 200, finishedAt = 300, sourceTrainingPlanId = "p", trainingPlan = "Back patrol"))
            var schedule = TrainingScheduleRepository(db)
            schedule.saveDates("p", listOf("2026-10-01", "2026-09-30", "2026-10-01"))
            schedule.saveDates("other", listOf("2026-10-02"))
            db.close(); db = open(); schedule = TrainingScheduleRepository(db)
            val expected = listOf(TrainingSchedule("p", "2026-09-30"), TrainingSchedule("p", "2026-10-01"), TrainingSchedule("other", "2026-10-02"))
            assertEquals(expected, schedule.observeAll().first())
            try { schedule.saveDates("p", listOf("2026-10-03", "not-a-date")); fail("Invalid date accepted") }
            catch (_: java.time.format.DateTimeParseException) { }
            try { schedule.saveDates("missing", listOf("2026-10-03")); fail("Missing plan accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(expected, schedule.observeAll().first())
            schedule.saveDates("p", listOf("2026-10-03"))
            assertEquals(listOf(TrainingSchedule("other", "2026-10-02"), TrainingSchedule("p", "2026-10-03")), schedule.observeAll().first())
            schedule.saveDates("other", emptyList())
            FitnessRepository(db).trainingPlans.delete("p")
            assertTrue(schedule.observeAll().first().isEmpty())
            assertEquals("Back patrol", db.workoutDao().getDetails("log")!!.workout.trainingPlan)
            assertNotNull(db.trainingPlanDao().get("other"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionFourteenUpgradePreservesLegacyDataWithoutInventingAppointments() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "calendar-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrumentation.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/14.json")
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
            old.execSQL("INSERT INTO exercises (id, name, equipment, isArchived) VALUES ('e', 'Cable row', 'Cable', 0)")
            old.execSQL("INSERT INTO training_plans (id, name, createdAt, dayOfWeek) VALUES ('p', 'Back patrol', 100, 'Monday')")
            old.execSQL("INSERT INTO training_plan_exercises (trainingPlanId, exerciseId, position, sets) VALUES ('p', 'e', 0, '[{\"reps\":10,\"weightGrams\":0,\"isWarmup\":false,\"modifier\":\"none\"}]')")
            old.execSQL("INSERT INTO workouts (id, startedAt, finishedAt, kind, name, notes, sourceTrainingPlanId, trainingPlan) VALUES ('log', 200, 1200, 'session', 'Back', '', 'p', 'Back patrol')")
            old.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes) VALUES ('entry', 'log', 'e', 0, 'Exercise note')")
            old.execSQL("INSERT INTO workout_sets (id, workoutExerciseId, position, reps, actualReps, weightGrams, completedAt, isWarmup, activeMillis, restMillis, notes, rpe) VALUES ('set', 'entry', 0, 10, 9, 12345, 700, 0, 500, 500, 'Set note', 8)")
            old.execSQL("INSERT INTO workout_session_states (workoutId, phase, currentSetId, phaseElapsedMillis, dutyElapsedMillis, isPaused, awaitingActual, pauseReason) VALUES ('log', 'finished', 'set', 0, 1000, 0, 0, '')")
            old.version = 14
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_14_15, FitnessDatabase.MIGRATION_15_16).build()
        var db = open()
        try {
            assertEquals(16, db.openHelper.writableDatabase.version)
            assertEquals("Monday", db.trainingPlanDao().get("p")!!.plan.dayOfWeek)
            assertEquals(10, db.trainingPlanDao().get("p")!!.exerciseMembers.single().member.sets.single().reps)
            val log = db.workoutDao().getDetails("log")!!
            assertEquals("Exercise note", log.exercises.single().workoutExercise.notes)
            assertEquals(WorkoutSet("set", "entry", 0, 10, 12345, completedAt = 700, actualReps = 9,
                activeMillis = 500, restMillis = 500, notes = "Set note", rpe = 8), log.orderedSets().single())
            assertEquals(1000L, log.sessionState!!.dutyElapsedMillis)
            assertTrue(db.trainingScheduleDao().observeAll().first().isEmpty())
            TrainingScheduleRepository(db).saveDates("p", listOf("2026-10-01"))
            db.close(); db = open()
            assertEquals(listOf(TrainingSchedule("p", "2026-10-01")), db.trainingScheduleDao().observeAll().first())
            assertEquals(log, db.workoutDao().getDetails("log"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
