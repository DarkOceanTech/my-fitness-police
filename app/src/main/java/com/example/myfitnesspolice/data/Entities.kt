package com.example.myfitnesspolice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val equipment: String,
    val isArchived: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val description: String = "",
    // Semicolon-separated readable lists, consistent with the prototype's text metadata.
    @androidx.room.ColumnInfo(defaultValue = "''") val primaryMuscles: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val secondaryMuscles: String = "",
) {
    init { require(name.isNotBlank()) { "Exercise name is required" } }
}

// Times are UTC epoch milliseconds. Only kind="session" uses finishedAt for completion.
// Drafts and saved plans always keep finishedAt null.
@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class Workout(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "'session'") val kind: String = "session",
    @androidx.room.ColumnInfo(defaultValue = "NULL") val sourcePlanId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val sourceTrainingPlanId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "''") val targetMuscles: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val dayOfWeek: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val trainingPlan: String = "",
    val notes: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val name: String = "",
) {
    init { require(finishedAt == null || finishedAt >= startedAt) }
}

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"],
            childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"],
            childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["workoutId", "position"], unique = true), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    val notes: String = "",
    @androidx.room.ColumnInfo(defaultValue = "'[]'")
    val equipmentPositions: List<EquipmentPosition> = emptyList(),
) {
    init { require(position >= 0) }
}

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(entity = WorkoutExercise::class, parentColumns = ["id"],
            childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["workoutExerciseId", "position"], unique = true)],
)
data class WorkoutSet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workoutExerciseId: String,
    val position: Int,
    val reps: Int,
    val weightGrams: Long,
    val completedAt: Long? = null,
    val isWarmup: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val actualReps: Int? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val activeMillis: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val restMillis: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "'none'")
    val modifier: String = "none",
    @androidx.room.ColumnInfo(defaultValue = "''") val notes: String = "",
    @androidx.room.ColumnInfo(defaultValue = "NULL") val rpe: Int? = null,
) {
    init {
        require(position >= 0)
        require(modifier in listOf("none", "superset"))
        require(reps > 0)
        require(actualReps == null || actualReps >= 0)
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10" }
        require(activeMillis >= 0 && restMillis >= 0)
        require(weightGrams >= 0) { "External weight may be zero, but not negative" }
    }
}
