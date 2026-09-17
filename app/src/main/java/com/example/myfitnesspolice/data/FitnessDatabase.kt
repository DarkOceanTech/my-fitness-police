package com.example.myfitnesspolice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, WorkoutSet::class, WorkoutSessionState::class,
        TrainingPlan::class, TrainingPlanWorkout::class],
    version = 11,
    exportSchema = true,
)
@androidx.room.TypeConverters(EquipmentPositionConverters::class)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun sessionStateDao(): WorkoutSessionStateDao
    abstract fun trainingPlanDao(): TrainingPlanDao

    companion object {
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE training_plans ADD COLUMN dayOfWeek TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workouts ADD COLUMN sourceTrainingPlanId TEXT DEFAULT NULL")
                // Carry over a shared schedule; conflicting days remain unscheduled on the group.
                db.execSQL("""UPDATE training_plans SET dayOfWeek = COALESCE((
                    SELECT CASE WHEN COUNT(DISTINCT NULLIF(w.dayOfWeek, '')) = 1 THEN MAX(w.dayOfWeek) ELSE '' END
                    FROM training_plan_workouts m JOIN workouts w ON w.id = m.workoutId
                    WHERE m.trainingPlanId = training_plans.id), '')""")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing sets have no recorded exertion; never invent historical ratings.
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN rpe INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS training_plans (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS training_plan_workouts (
                    trainingPlanId TEXT NOT NULL, workoutId TEXT NOT NULL, position INTEGER NOT NULL,
                    PRIMARY KEY(trainingPlanId, workoutId),
                    FOREIGN KEY(trainingPlanId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(workoutId) REFERENCES workouts(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_training_plan_workouts_workoutId ON training_plan_workouts(workoutId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_training_plan_workouts_trainingPlanId_position ON training_plan_workouts(trainingPlanId, position)")
                db.execSQL("""INSERT INTO training_plans (id, name, createdAt)
                    SELECT 'training-' || lower(hex(randomblob(16))), trim(trainingPlan), MIN(startedAt)
                    FROM workouts WHERE kind = 'plan' AND trim(trainingPlan) != ''
                    GROUP BY trim(trainingPlan) COLLATE NOCASE""")
                db.execSQL("""INSERT INTO training_plan_workouts (trainingPlanId, workoutId, position)
                    SELECT p.id, w.id, (SELECT COUNT(*) FROM workouts earlier
                        WHERE earlier.kind = 'plan' AND trim(earlier.trainingPlan) = p.name COLLATE NOCASE
                        AND (earlier.startedAt < w.startedAt OR (earlier.startedAt = w.startedAt AND earlier.id < w.id)))
                    FROM workouts w JOIN training_plans p ON trim(w.trainingPlan) = p.name COLLATE NOCASE
                    WHERE w.kind = 'plan'""")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_exercises ADD COLUMN equipmentPositions TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("description", "primaryMuscles", "secondaryMuscles").forEach {
                    db.execSQL("ALTER TABLE exercises ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
                }
                ExerciseCatalog.seed(db)
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN actualReps INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN activeMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN restMillis INTEGER NOT NULL DEFAULT 0")
                // Existing recorded sets retain their actual counts; planned sets remain unperformed.
                db.execSQL("""
                    UPDATE workout_sets SET actualReps = reps WHERE completedAt IS NOT NULL
                    AND workoutExerciseId IN (
                        SELECT e.id FROM workout_exercises e JOIN workouts w ON w.id = e.workoutId WHERE w.kind = 'session'
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workout_session_states (
                        workoutId TEXT NOT NULL PRIMARY KEY,
                        phase TEXT NOT NULL,
                        currentSetId TEXT,
                        phaseStartedAt INTEGER,
                        phaseElapsedMillis INTEGER NOT NULL,
                        dutyStartedAt INTEGER,
                        dutyElapsedMillis INTEGER NOT NULL,
                        isPaused INTEGER NOT NULL,
                        awaitingActual INTEGER NOT NULL,
                        pauseReason TEXT NOT NULL,
                        FOREIGN KEY(workoutId) REFERENCES workouts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN kind TEXT NOT NULL DEFAULT 'session'")
                db.execSQL("ALTER TABLE workouts ADD COLUMN sourcePlanId TEXT DEFAULT NULL")
                // Earlier versions used completed sessions as saved plans. Preserve history
                // and create independent plan copies so existing home cards remain available.
                db.execSQL("""
                    INSERT INTO workouts (id, startedAt, finishedAt, targetMuscles, dayOfWeek, trainingPlan, notes, kind, sourcePlanId)
                    SELECT 'plan-' || id, startedAt, NULL, targetMuscles, dayOfWeek, trainingPlan, notes, 'plan', NULL
                    FROM workouts WHERE finishedAt IS NOT NULL AND kind = 'session'
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO workout_exercises (id, workoutId, exerciseId, position, notes)
                    SELECT 'plan-' || e.id, 'plan-' || e.workoutId, e.exerciseId, e.position, e.notes
                    FROM workout_exercises e JOIN workouts w ON w.id = e.workoutId
                    WHERE w.finishedAt IS NOT NULL AND w.kind = 'session'
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO workout_sets (id, workoutExerciseId, position, reps, weightGrams, completedAt, isWarmup, modifier)
                    SELECT 'plan-' || s.id, 'plan-' || s.workoutExerciseId, s.position, s.reps, s.weightGrams, NULL, s.isWarmup, s.modifier
                    FROM workout_sets s JOIN workout_exercises e ON e.id = s.workoutExerciseId
                    JOIN workouts w ON w.id = e.workoutId WHERE w.finishedAt IS NOT NULL AND w.kind = 'session'
                """.trimIndent())
                db.execSQL("UPDATE workouts SET kind = 'draft' WHERE kind = 'session' AND finishedAt IS NULL")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("targetMuscles", "dayOfWeek", "trainingPlan").forEach {
                    db.execSQL("ALTER TABLE workouts ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN modifier TEXT NOT NULL DEFAULT 'none'")
            }
        }
        @Volatile private var instance: FitnessDatabase? = null

        fun getInstance(context: Context): FitnessDatabase =
            instance ?: synchronized(this) {
                instance ?: builder(context).build().also { instance = it }
            }

        fun builder(context: Context): Builder<FitnessDatabase> =
            Room.databaseBuilder(context.applicationContext, FitnessDatabase::class.java, "fitness.db")
                .addCallback(SeedExercises)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                // Register explicit migrations here when version increases.
                // Deliberately no destructive migration fallback.
    }
}

internal object SeedExercises : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Creation and migration seed before the first DAO query; never replace referenced rows.
        ExerciseCatalog.seed(db)
    }
}
