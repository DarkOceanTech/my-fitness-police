package com.example.myfitnesspolice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Exercise>

    @Insert
    suspend fun insert(exercise: Exercise)

    @Update
    suspend fun update(exercise: Exercise)

    @Query("UPDATE exercises SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

data class ExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSet>,
)

data class WorkoutDetails(
    @Embedded val workout: Workout,
    @Relation(entity = WorkoutExercise::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<ExerciseWithSets>,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sessionState: WorkoutSessionState? = null,
) {
    // Room relations do not guarantee order.
    fun orderedExercises() = exercises.sortedBy { it.workoutExercise.position }
    fun orderedSets() = orderedExercises().flatMap { it.sets.sortedBy { set -> set.position } }
    fun performedSets() = orderedSets().filter { it.completedAt != null }
    fun nextSet(afterId: String?): WorkoutSet? {
        val sets = orderedSets()
        val index = sets.indexOfFirst { it.id == afterId }
        return (sets.drop(index + 1) + sets.take(index + 1)).firstOrNull { it.completedAt == null }
    }
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun observeAllDetails(): Flow<List<WorkoutDetails>>
    @Insert suspend fun insert(workout: Workout)
    @Update suspend fun update(workout: Workout)
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM workouts WHERE kind = 'session' AND finishedAt IS NOT NULL")
    suspend fun deleteAllHistory()

    @Query("SELECT * FROM workouts WHERE kind = 'session' AND finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE kind = 'session' AND finishedAt IS NULL ORDER BY startedAt DESC")
    fun observeUnfinished(): Flow<List<Workout>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getDetails(id: String): WorkoutDetails?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeDetails(id: String): Flow<WorkoutDetails?>
}

@Dao
interface WorkoutExerciseDao {
    @Insert suspend fun insert(exercise: WorkoutExercise)
    @Update suspend fun update(exercise: WorkoutExercise)
    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface WorkoutSetDao {
    @Insert suspend fun insert(set: WorkoutSet)
    @Update suspend fun update(set: WorkoutSet)
    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: String)
    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :exerciseId ORDER BY position")
    suspend fun getForExercise(exerciseId: String): List<WorkoutSet>
}
