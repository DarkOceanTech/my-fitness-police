package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "training_plans")
data class TrainingPlan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val dayOfWeek: String = "",
)

@Entity(tableName = "training_plan_workouts", primaryKeys = ["trainingPlanId", "workoutId"],
    foreignKeys = [
        ForeignKey(entity = TrainingPlan::class, parentColumns = ["id"], childColumns = ["trainingPlanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
    ], indices = [Index("workoutId"), Index(value = ["trainingPlanId", "position"], unique = true)])
data class TrainingPlanWorkout(val trainingPlanId: String, val workoutId: String, val position: Int)

/** A plan owns prescriptions for direct exercises; recorded results only belong to sessions. */
data class TrainingPlanSet(val reps: Int = 10, val weightGrams: Long = 0,
    val isWarmup: Boolean = false, val modifier: String = "none") {
    init {
        require(reps > 0) { "Planned reps must be positive." }
        require(weightGrams >= 0) { "Weight cannot be negative." }
        require(modifier in listOf("none", "superset", "drop_set")) { "Choose a valid set modifier." }
    }
}

sealed interface TrainingPlanItem {
    val key: String
    data class WorkoutItem(val workoutId: String) : TrainingPlanItem {
        override val key: String get() = "workout:$workoutId"
    }
    data class ExerciseItem(val exerciseId: String, val sets: List<TrainingPlanSet> = listOf(TrainingPlanSet())) : TrainingPlanItem {
        override val key: String get() = "exercise:$exerciseId"
    }
}

@Entity(tableName = "training_plan_exercises", primaryKeys = ["trainingPlanId", "exerciseId"],
    foreignKeys = [
        ForeignKey(entity = TrainingPlan::class, parentColumns = ["id"], childColumns = ["trainingPlanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ], indices = [Index("exerciseId"), Index(value = ["trainingPlanId", "position"], unique = true)])
data class TrainingPlanExercise(val trainingPlanId: String, val exerciseId: String, val position: Int,
    @ColumnInfo(defaultValue = "'[]'") val sets: List<TrainingPlanSet> = listOf(TrainingPlanSet()))

data class TrainingPlanExerciseDetails(
    @Embedded val member: TrainingPlanExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
)

class TrainingPlanSetConverters {
    @TypeConverter fun encode(sets: List<TrainingPlanSet>): String = JSONArray().apply {
        sets.forEach { set -> put(JSONObject().put("reps", set.reps).put("weightGrams", set.weightGrams)
            .put("isWarmup", set.isWarmup).put("modifier", set.modifier)) }
    }.toString()

    @TypeConverter fun decode(value: String): List<TrainingPlanSet> {
        val array = JSONArray(value)
        return List(array.length()) { index -> array.getJSONObject(index).let {
            TrainingPlanSet(it.getInt("reps"), it.getLong("weightGrams"), it.getBoolean("isWarmup"), it.getString("modifier"))
        } }
    }
}

data class TrainingPlanDetails(
    @Embedded val plan: TrainingPlan,
    @Relation(parentColumn = "id", entityColumn = "trainingPlanId") val members: List<TrainingPlanWorkout>,
    @Relation(entity = TrainingPlanExercise::class, parentColumn = "id", entityColumn = "trainingPlanId")
    val exerciseMembers: List<TrainingPlanExerciseDetails> = emptyList(),
) {
    fun orderedItems(): List<TrainingPlanItem> =
        (members.map { it.position to TrainingPlanItem.WorkoutItem(it.workoutId) } +
            exerciseMembers.map { it.member.position to TrainingPlanItem.ExerciseItem(it.member.exerciseId, it.member.sets) })
            .sortedBy { it.first }.map { it.second }
    fun exerciseFor(id: String): Exercise? = exerciseMembers.firstOrNull { it.member.exerciseId == id }?.exercise
    fun includedExercises(): List<Exercise> = exerciseMembers.sortedBy { it.member.position }.map { it.exercise }
    fun workoutIds() = members.sortedBy { it.position }.map { it.workoutId }
    fun workouts(library: List<WorkoutDetails>): List<WorkoutDetails> {
        val saved = library.filter { it.workout.kind == "plan" }.associateBy { it.workout.id }
        return workoutIds().mapNotNull { saved[it] }
    }
}

@Dao
interface TrainingPlanDao {
    @Transaction @Query("SELECT * FROM training_plans ORDER BY name COLLATE NOCASE, createdAt")
    fun observeAll(): Flow<List<TrainingPlanDetails>>
    @Query("SELECT * FROM training_plans ORDER BY name COLLATE NOCASE, createdAt")
    suspend fun getAll(): List<TrainingPlan>
    @Transaction @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun get(id: String): TrainingPlanDetails?
    @Insert suspend fun insert(plan: TrainingPlan)
    @Update suspend fun update(plan: TrainingPlan)
    @Query("DELETE FROM training_plans WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun insertMembers(members: List<TrainingPlanWorkout>)
    @Insert suspend fun insertExerciseMembers(members: List<TrainingPlanExercise>)
    @Query("DELETE FROM training_plan_workouts WHERE trainingPlanId = :id") suspend fun clearMembers(id: String)
    @Query("DELETE FROM training_plan_exercises WHERE trainingPlanId = :id") suspend fun clearExerciseMembers(id: String)
    @Query("DELETE FROM training_plan_workouts WHERE workoutId = :workoutId AND trainingPlanId = :planId")
    suspend fun removeMember(planId: String, workoutId: String)
    @Query("SELECT * FROM training_plan_workouts WHERE workoutId = :id")
    suspend fun memberships(id: String): List<TrainingPlanWorkout>
    @Query("""SELECT COALESCE(MAX(position), -1) + 1 FROM (
        SELECT position FROM training_plan_workouts WHERE trainingPlanId = :id
        UNION ALL SELECT position FROM training_plan_exercises WHERE trainingPlanId = :id)""")
    suspend fun nextPosition(id: String): Int
}

class TrainingPlanRepository(private val database: FitnessDatabase) {
    private val dao get() = database.trainingPlanDao()
    fun observeAll() = dao.observeAll()

    suspend fun save(id: String?, name: String, orderedWorkoutIds: List<String>, dayOfWeek: String? = null): String = database.withTransaction {
        // Older callers own only the workout selection; keep direct exercises and their prescriptions.
        val previous = id?.let { requireNotNull(dao.get(it)) { "This training plan no longer exists." } }
        val remaining = orderedWorkoutIds.map { TrainingPlanItem.WorkoutItem(it) }.iterator()
        val items = buildList<TrainingPlanItem> {
            previous?.orderedItems()?.forEach { item ->
                if (item is TrainingPlanItem.ExerciseItem) add(item)
                else if (remaining.hasNext()) add(remaining.next())
            }
            while (remaining.hasNext()) add(remaining.next())
        }
        saveItems(id, name, items, dayOfWeek)
    }

    suspend fun saveItems(id: String?, name: String, orderedItems: List<TrainingPlanItem>, dayOfWeek: String? = null): String = database.withTransaction {
        require(name.isNotBlank()) { "Enter a training plan name." }
        require(dayOfWeek == null || dayOfWeek.isEmpty() || dayOfWeek in trainingWeekdays) { "Choose a day of the week." }
        require(orderedItems.isNotEmpty()) { "Include at least one workout or exercise." }
        require(orderedItems.map { it.key }.distinct().size == orderedItems.size) { "Each workout or direct exercise can only appear once in a training plan." }
        val previous = id?.let { requireNotNull(dao.get(it)) { "This training plan no longer exists." } }
        orderedItems.forEach { item -> when (item) {
            is TrainingPlanItem.WorkoutItem -> require(database.workoutDao().getDetails(item.workoutId)?.workout?.kind == "plan") {
                "A selected workout is no longer available. Refresh your selection."
            }
            is TrainingPlanItem.ExerciseItem -> {
                val exercise = requireNotNull(database.exerciseDao().get(item.exerciseId)) { "A selected exercise is no longer available." }
                require(!exercise.isArchived || previous?.exerciseFor(exercise.id) != null) { "An archived exercise cannot be newly added to a training plan." }
                require(item.sets.isNotEmpty()) { "Add at least one set to each direct exercise." }
            }
        } }
        val plan = previous?.plan?.copy(name = name.trim(), dayOfWeek = dayOfWeek ?: previous.plan.dayOfWeek)
            ?: TrainingPlan(name = name.trim(), dayOfWeek = dayOfWeek.orEmpty())
        if (previous == null) dao.insert(plan) else dao.update(plan)
        dao.clearMembers(plan.id)
        dao.clearExerciseMembers(plan.id)
        orderedItems.forEachIndexed { position, item -> when (item) {
            is TrainingPlanItem.WorkoutItem -> dao.insertMembers(listOf(TrainingPlanWorkout(plan.id, item.workoutId, position)))
            is TrainingPlanItem.ExerciseItem -> dao.insertExerciseMembers(listOf(TrainingPlanExercise(plan.id, item.exerciseId, position, item.sets)))
        } }
        (previous?.workoutIds().orEmpty() + orderedItems.filterIsInstance<TrainingPlanItem.WorkoutItem>().map { it.workoutId })
            .distinct().forEach { syncLabels(it) }
        plan.id
    }

    suspend fun delete(id: String) = database.withTransaction {
        val members = dao.get(id)?.workoutIds().orEmpty()
        dao.delete(id)
        members.forEach { syncLabels(it) }
    }

    // Also used for drafts: clearing a draft cascades its links, and Training only displays saved workouts.
    suspend fun assignWorkout(workoutId: String, selectedPlanIds: List<String>) = database.withTransaction {
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId)).workout
        require(workout.kind in listOf("draft", "plan"))
        val ids = selectedPlanIds.distinct()
        require(ids.all { dao.get(it) != null }) { "A selected training plan no longer exists." }
        val old = dao.memberships(workoutId).map { it.trainingPlanId }
        (old - ids.toSet()).forEach { dao.removeMember(it, workoutId) }
        (ids - old.toSet()).forEach { dao.insertMembers(listOf(TrainingPlanWorkout(it, workoutId, dao.nextPosition(it)))) }
        syncLabels(workoutId)
    }

    // Keep the former text-based repository API usable; UI choices now use stable plan IDs.
    suspend fun assignLegacyName(workoutId: String, name: String) = database.withTransaction {
        if (name.isBlank()) assignWorkout(workoutId, emptyList())
        else {
            val plan = dao.getAll().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                ?: TrainingPlan(name = name.trim()).also { dao.insert(it) }
            assignWorkout(workoutId, listOf(plan.id))
        }
    }

    private suspend fun syncLabels(workoutId: String) {
        val workout = database.workoutDao().getDetails(workoutId)?.workout ?: return
        val ids = dao.memberships(workoutId).map { it.trainingPlanId }.toSet()
        // This text is only a display/session snapshot. Membership is defined by the join table.
        val labels = dao.getAll().filter { it.id in ids }.joinToString(" / ") { it.name }
        database.workoutDao().update(workout.copy(trainingPlan = labels))
    }
}

fun Workout.displayName(): String = name.ifBlank { targetMuscles.replace(",", " / ").ifBlank { "My Workout" } }
fun WorkoutDetails.plannedSets(): Int = exercises.sumOf { it.sets.size }
fun WorkoutDetails.plannedReps(): Long = exercises.sumOf { entry -> entry.sets.sumOf { it.reps.toLong() } }

val trainingWeekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
