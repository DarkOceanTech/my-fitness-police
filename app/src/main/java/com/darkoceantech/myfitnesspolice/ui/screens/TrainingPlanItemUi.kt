package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.runtime.saveable.listSaver
import com.darkoceantech.myfitnesspolice.data.*
import org.json.JSONArray
import org.json.JSONObject

internal data class TrainingItemUi(val item: TrainingPlanItem, val title: String, val sets: Int, val reps: Long) {
    val key get() = item.key
    val isExercise get() = item is TrainingPlanItem.ExerciseItem
    val kind get() = if (isExercise) "Exercise" else "Workout"
    val id get() = when (item) {
        is TrainingPlanItem.WorkoutItem -> item.workoutId
        is TrainingPlanItem.ExerciseItem -> item.exerciseId
    }
    fun tag(prefix: String) = "$prefix-${if (isExercise) "exercise" else "workout"}-$id"
}

internal fun trainingItemUi(items: List<TrainingPlanItem>, library: List<WorkoutDetails>, exercises: List<Exercise>,
    details: TrainingPlanDetails? = null): List<TrainingItemUi> = items.map { item ->
    when (item) {
        is TrainingPlanItem.WorkoutItem -> {
            val workout = library.firstOrNull { it.workout.id == item.workoutId }
            TrainingItemUi(item, workout?.workout?.displayName() ?: "Unavailable workout", workout?.plannedSets() ?: 0,
                workout?.plannedReps() ?: 0L)
        }
        is TrainingPlanItem.ExerciseItem -> {
            val exercise = details?.exerciseFor(item.exerciseId) ?: exercises.firstOrNull { it.id == item.exerciseId }
            TrainingItemUi(item, exercise?.name ?: "Unavailable exercise", item.sets.size, item.sets.sumOf { it.reps.toLong() })
        }
    }
}

internal fun trainingItemCount(items: List<TrainingItemUi>): String {
    val workouts = items.count { !it.isExercise }
    val exercises = items.count { it.isExercise }
    return listOfNotNull(if (workouts > 0 || exercises == 0) "$workouts ${if (workouts == 1) "workout" else "workouts"}" else null,
        if (exercises > 0) "$exercises ${if (exercises == 1) "exercise" else "exercises"}" else null).joinToString(" · ")
}

private fun TrainingPlanSet.json() = JSONObject().put("reps", reps).put("weightGrams", weightGrams)
    .put("warmup", isWarmup).put("modifier", modifier)
private fun trainingSet(json: JSONObject) = TrainingPlanSet(json.getInt("reps"), json.getLong("weightGrams"),
    json.getBoolean("warmup"), json.getString("modifier"))

internal val TrainingItemsSaver = listSaver<List<TrainingPlanItem>, String>(
    save = { items -> items.map { item -> when (item) {
        is TrainingPlanItem.WorkoutItem -> JSONObject().put("workout", item.workoutId)
        is TrainingPlanItem.ExerciseItem -> JSONObject().put("exercise", item.exerciseId)
            .put("sets", JSONArray().apply { item.sets.forEach { put(it.json()) } })
    }.toString() } },
    restore = { saved -> saved.map { value ->
        val json = JSONObject(value)
        if (json.has("workout")) TrainingPlanItem.WorkoutItem(json.getString("workout"))
        else TrainingPlanItem.ExerciseItem(json.getString("exercise"), json.getJSONArray("sets").let { sets ->
            (0 until sets.length()).map { trainingSet(sets.getJSONObject(it)) }
        })
    } }
)
