package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
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

data class TrainingPlanDetails(
    @Embedded val plan: TrainingPlan,
    @Relation(parentColumn = "id", entityColumn = "trainingPlanId") val members: List<TrainingPlanWorkout>,
) {
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
    @Query("DELETE FROM training_plan_workouts WHERE trainingPlanId = :id") suspend fun clearMembers(id: String)
    @Query("DELETE FROM training_plan_workouts WHERE workoutId = :workoutId AND trainingPlanId = :planId")
    suspend fun removeMember(planId: String, workoutId: String)
    @Query("SELECT * FROM training_plan_workouts WHERE workoutId = :id")
    suspend fun memberships(id: String): List<TrainingPlanWorkout>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM training_plan_workouts WHERE trainingPlanId = :id")
    suspend fun nextPosition(id: String): Int
}

class TrainingPlanRepository(private val database: FitnessDatabase) {
    private val dao get() = database.trainingPlanDao()
    fun observeAll() = dao.observeAll()

    suspend fun save(id: String?, name: String, orderedWorkoutIds: List<String>, dayOfWeek: String? = null): String = database.withTransaction {
        require(name.isNotBlank()) { "Enter a training plan name." }
        require(dayOfWeek == null || dayOfWeek.isEmpty() || dayOfWeek in trainingWeekdays) { "Choose a day of the week." }
        require(orderedWorkoutIds.isNotEmpty()) { "Include at least one workout." }
        require(orderedWorkoutIds.distinct().size == orderedWorkoutIds.size) { "A workout can only appear once in a training plan." }
        orderedWorkoutIds.forEach {
            require(database.workoutDao().getDetails(it)?.workout?.kind == "plan") {
                "A selected workout is no longer available. Refresh your selection."
            }
        }
        val previous = id?.let { requireNotNull(dao.get(it)) { "This training plan no longer exists." } }
        val plan = previous?.plan?.copy(name = name.trim(), dayOfWeek = dayOfWeek ?: previous.plan.dayOfWeek)
            ?: TrainingPlan(name = name.trim(), dayOfWeek = dayOfWeek.orEmpty())
        if (previous == null) dao.insert(plan) else dao.update(plan)
        dao.clearMembers(plan.id)
        dao.insertMembers(orderedWorkoutIds.mapIndexed { index, workout -> TrainingPlanWorkout(plan.id, workout, index) })
        (previous?.workoutIds().orEmpty() + orderedWorkoutIds).distinct().forEach { syncLabels(it) }
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
