package com.darkoceantech.myfitnesspolice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.ui.theme.MyFitnessPoliceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class WorkoutBuilderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun inlineSetsAndNoteSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repo = FitnessRepository(db)
        runBlocking { repo.addExercise("Test press", "Dumbbells") }
        try {
            compose.setContent { MyFitnessPoliceTheme { MyFitnessPoliceApp(repo) } }
            compose.onNode(hasText("Academy") and hasClickAction()).performClick()
            compose.onNodeWithTag("workout-section-0").performClick()
            compose.onNodeWithContentDescription("Add workout").performClick()
            waitFor("Add Exercise")
            compose.onNodeWithText("Create your Workout").assertIsDisplayed()
            compose.onNodeWithText("Start My Workout").assertDoesNotExist()
            compose.onNodeWithText("Add Exercise").performScrollTo().performClick()
            compose.onNodeWithText("Test press").performClick()
            compose.onNodeWithText("Add Set").performScrollTo()
            compose.onNodeWithContentDescription("Modifier set 1").performClick()
            compose.onNodeWithText("Superset", substring = false).performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Type set 1").performClick()
            compose.onNodeWithText("Warmup", substring = false).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Add a note prior to workout").performClick()
            compose.onNodeWithText("Exercise note").performTextInput("Keep elbows tucked")
            compose.onNodeWithText("Save note").performClick()
            waitFor("Keep elbows tucked")
            compose.onNodeWithText("Add Set").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Modifier set 2").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                val entry = repo.observeSessions().first().single().exercises.single()
                assertEquals("Keep elbows tucked", entry.workoutExercise.notes)
                assertEquals(2, entry.sets.size)
                val first = entry.sets.minBy { it.position }
                assertEquals("superset", first.modifier)
                assertTrue(first.isWarmup)
            }
        } finally { db.close() }
    }
    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun versionOneMigrationPreservesSets() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-" + java.util.UUID.randomUUID() + ".db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = org.json.JSONObject(instrumentation.context.assets.open(
            "com.darkoceantech.myfitnesspolice.data.FitnessDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO exercises VALUES ('e', 'Press', 'Barbell', 0)")
            old.execSQL("INSERT INTO workouts VALUES ('w', 1000, NULL, '')")
            old.execSQL("INSERT INTO workout_exercises VALUES ('we', 'w', 'e', 0, 'existing note')")
            old.execSQL("INSERT INTO workout_sets VALUES ('s', 'we', 0, 5, 20000, 1000, 1)")
            old.execSQL("INSERT INTO workouts VALUES ('completed', 1000, 2000, 'old session')")
            old.execSQL("INSERT INTO workout_exercises VALUES ('completed-e', 'completed', 'e', 0, 'old note')")
            old.execSQL("INSERT INTO workout_sets VALUES ('completed-s', 'completed-e', 0, 8, 30000, 1500, 0)")
            old.version = 1
        } finally { old.close() }
        val db = Room.databaseBuilder(context, FitnessDatabase::class.java, name)
            .addMigrations(FitnessDatabase.MIGRATION_1_2, FitnessDatabase.MIGRATION_2_3, FitnessDatabase.MIGRATION_3_4, FitnessDatabase.MIGRATION_4_5, FitnessDatabase.MIGRATION_5_6, FitnessDatabase.MIGRATION_6_7, FitnessDatabase.MIGRATION_7_8, FitnessDatabase.MIGRATION_8_9, FitnessDatabase.MIGRATION_9_10, FitnessDatabase.MIGRATION_10_11).build()
        try {
            val entry = db.workoutDao().getDetails("w")!!.exercises.single()
            assertEquals("existing note", entry.workoutExercise.notes)
            assertEquals("none", entry.sets.single().modifier)
            assertEquals("", entry.sets.single().notes)
            assertTrue(entry.sets.single().isWarmup)
            assertEquals(20000L, entry.sets.single().weightGrams)
            assertEquals("draft", db.workoutDao().getDetails("w")!!.workout.kind)
            assertEquals(1, db.workoutDao().observeHistory().first().size)
            val plan = db.workoutDao().getDetails("plan-completed")!!
            assertEquals("plan", plan.workout.kind)
            assertNull(plan.workout.finishedAt)
            assertEquals("old note", plan.exercises.single().workoutExercise.notes)
            assertEquals(8, plan.exercises.single().sets.single().reps)
            assertNull(plan.exercises.single().sets.single().completedAt)
            assertNull(plan.exercises.single().sets.single().actualReps)
            assertEquals(8, db.workoutDao().getDetails("completed")!!.exercises.single().sets.single().actualReps)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}


