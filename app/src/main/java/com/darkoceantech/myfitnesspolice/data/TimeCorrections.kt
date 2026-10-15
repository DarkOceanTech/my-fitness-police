package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import androidx.room.withTransaction
import kotlin.math.abs

@Entity(tableName = "time_corrections", foreignKeys = [ForeignKey(entity = Workout::class,
    parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workoutId")])
data class TimeCorrection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: String, val workSetId: String, val restSetId: String,
    val recordedWork: Long, val recordedRest: Long, val beforeWork: Long, val beforeRest: Long,
    val afterWork: Long, val afterRest: Long, val source: String, val provenance: String,
    val reason: String, val workLabel: String, val restLabel: String,
    val createdAt: Long, val applied: Boolean = true,
)

@Entity(tableName = "time_reviews", foreignKeys = [ForeignKey(entity = Workout::class,
    parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workoutId")])
data class TimeReview(@PrimaryKey val setId: String, val workoutId: String, val status: String, val updatedAt: Long)

@Dao interface TimeCorrectionDao {
    @Insert suspend fun insert(correction: TimeCorrection): Long
    @Upsert suspend fun review(review: TimeReview)
    @Query("UPDATE time_corrections SET applied = 0 WHERE id = :id") suspend fun restore(id: Long)
}

fun hasTimeTag(note: String) = Regex("(?i)(?<![\\w])#time(?![\\w])").containsMatchIn(note)
fun WorkoutDetails.correctionsFor(setId: String) = timeCorrections.filter { it.applied && (it.workSetId == setId || it.restSetId == setId) }.sortedBy { it.id }
fun WorkoutDetails.timeAnnotation(setId: String): String? {
    val linked = correctionsFor(setId)
    return listOfNotNull(linked.lastOrNull { it.workSetId == setId }?.let { "Work: ${it.source} (#${it.id})" },
        linked.lastOrNull { it.restSetId == setId }?.let { "Rest: ${it.source} (#${it.id})" })
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")?.plus(" · Linked adjustments")
}
fun WorkoutDetails.timeReviewStatus(set: WorkoutSet): String? {
    val explicit = timeReviews.firstOrNull { it.setId == set.id }?.status
    if (explicit == "needs_review") return explicit
    if (correctionsFor(set.id).isNotEmpty()) return "corrected"
    return explicit ?: if (hasTimeTag(set.notes)) "needs_review" else null
}

/** Read projection only. Timer transitions and all mutations continue to read original Room rows. */
fun WorkoutDetails.withEffectiveTimes(): WorkoutDetails {
    val active = timeCorrections.filter { it.applied }.sortedBy { it.id }
    if (active.isEmpty()) return this
    return copy(exercises = exercises.map { entry -> entry.copy(sets = entry.sets.map { set ->
        set.copy(activeMillis = active.lastOrNull { it.workSetId == set.id }?.afterWork ?: set.activeMillis,
            restMillis = active.lastOrNull { it.restSetId == set.id }?.afterRest ?: set.restMillis)
    }) })
}

fun WorkoutDetails.setTimeLabel(id: String): String {
    val entry = exercises.first { e -> e.sets.any { it.id == id } }
    val set = entry.sets.first { it.id == id }
    return "${entry.exercise.name} · Set ${set.position + 1} · ${if (set.isWarmup) "Warm-up" else "Working set"}"
}

fun WorkoutDetails.confirmed(set: WorkoutSet) = set.completedAt != null &&
    !(sessionState?.awaitingActual == true && sessionState.currentSetId == set.id)

fun WorkoutDetails.closedRest(set: WorkoutSet): Boolean = confirmed(set) &&
    (workout.finishedAt != null || (sessionState != null &&
        !(sessionState.currentSetId == set.id && sessionState.phase in listOf("rest", "cooldown"))))

/** Only adjacent Rest intervals are offered; pauses and unrecorded gaps are never a source of time. */
fun WorkoutDetails.neighboringRests(setId: String): List<WorkoutSet> {
    val ordered = orderedSets().filter { it.completedAt != null }.sortedWith(compareBy<WorkoutSet> { it.completedAt }.thenBy { it.position })
    val index = ordered.indexOfFirst { it.id == setId }
    if (index < 0) return emptyList()
    return listOfNotNull(ordered.getOrNull(index - 1), ordered[index])
}

data class TimeEstimate(val millis: Long, val count: Int, val low: Long, val high: Long, val provenance: String)
fun estimateWorkTime(session: WorkoutDetails, setId: String, restId: String?, history: List<WorkoutDetails>): TimeEstimate? {
    val targetEntry = session.exercises.first { e -> e.sets.any { it.id == setId } }
    val target = targetEntry.sets.first { it.id == setId }
    fun candidates(w: WorkoutDetails) = w.exercises.filter { it.exercise.id == targetEntry.exercise.id }.flatMap { it.sets }
        .filter { s -> s.id != setId && s.id != restId && w.confirmed(s) && s.activeMillis > 0 &&
            s.isWarmup == target.isWarmup && abs((s.actualReps ?: s.reps) - (target.actualReps ?: target.reps)) <= 2 &&
            abs(s.weightGrams - target.weightGrams) <= maxOf(2268L, (target.weightGrams * .1).toLong()) &&
            w.timeReviewStatus(s) != "needs_review" && w.correctionsFor(s.id).none { it.source == "Estimated" } }
    var pool = candidates(session)
    var source = "current session"
    if (pool.size < 2) {
        pool = history.filter { it.workout.id != session.workout.id }.flatMap { w ->
            candidates(w).filter { (it.completedAt ?: Long.MAX_VALUE) < (target.completedAt ?: Long.MIN_VALUE) }
        }
        source = "earlier sessions"
    }
    if (pool.size < 2) return null
    val values = pool.map { it.activeMillis }.sorted()
    val middle = values.size / 2
    val median = if (values.size % 2 == 0) values[middle - 1] + (values[middle] - values[middle - 1]) / 2 else values[middle]
    return TimeEstimate(median, values.size, values.first(), values.last(),
        "Median of ${values.size} comparable sets from $source; range ${values.first()}–${values.last()} ms. Reference set IDs: ${pool.joinToString { it.id }}")
}

data class TimeTransfer(val work: Long, val rest: Long, val total: Long)
fun transferTime(work: Long, rest: Long, proposedWork: Long): TimeTransfer {
    require(work >= 0 && rest >= 0 && proposedWork >= 0) { "Durations cannot be negative." }
    val total = Math.addExact(work, rest)
    require(proposedWork <= total) { "This Rest interval does not contain enough time. Choose another recorded neighbor or a smaller Work duration." }
    require(proposedWork != work) { "Enter a different Work duration." }
    return TimeTransfer(proposedWork, total - proposedWork, total)
}

class TimeCorrectionRepository(private val db: FitnessDatabase) {
    suspend fun save(workoutId: String, setId: String, restId: String, proposed: Long,
        expectedWork: Long, expectedRest: Long, source: String, provenance: String, reason: String) = db.withTransaction {
        val raw = requireNotNull(db.workoutDao().getDetails(workoutId))
        require(raw.workout.kind == "session") { "Select a recorded session." }
        val effective = raw.withEffectiveTimes()
        val work = effective.orderedSets().first { it.id == setId }
        val rest = requireNotNull(effective.neighboringRests(setId).firstOrNull { it.id == restId }) { "Select an adjacent recorded Rest interval." }
        require(effective.confirmed(work) && effective.closedRest(rest)) { "Wait until both intervals finish and their results are confirmed. Paused intervals must finish too." }
        require(work.activeMillis == expectedWork && rest.restMillis == expectedRest) { "Timing changed while reviewing. Reopen the correction and review the latest values." }
        require(source in listOf("Manual", "Estimated"))
        if (source == "Estimated") require(provenance.isNotBlank()) { "The estimate must include its source." }
        val transfer = transferTime(work.activeMillis, rest.restMillis, proposed)
        db.timeCorrectionDao().insert(TimeCorrection(workoutId = workoutId, workSetId = setId, restSetId = restId,
            recordedWork = raw.orderedSets().first { it.id == setId }.activeMillis,
            recordedRest = raw.orderedSets().first { it.id == restId }.restMillis,
            beforeWork = work.activeMillis, beforeRest = rest.restMillis, afterWork = transfer.work, afterRest = transfer.rest,
            source = source, provenance = provenance, reason = reason.trim(), workLabel = raw.setTimeLabel(setId),
            restLabel = raw.setTimeLabel(restId), createdAt = System.currentTimeMillis()))
        listOf(setId, restId).distinct().forEach { db.timeCorrectionDao().review(TimeReview(it, workoutId, "corrected", System.currentTimeMillis())) }
    }
    suspend fun restore(workoutId: String, id: Long) = db.withTransaction {
        val workout = requireNotNull(db.workoutDao().getDetails(workoutId))
        val correction = requireNotNull(workout.timeCorrections.firstOrNull { it.id == id && it.applied }) { "This correction was already restored." }
        val dependent = workout.timeCorrections.filter { it.applied && it.id > id &&
            (it.workSetId == correction.workSetId || it.restSetId == correction.restSetId) }
        require(dependent.isEmpty()) { "Later linked corrections depend on this transfer (${dependent.joinToString { "#${it.id}" }}). Review and restore them newest first." }
        val sets = workout.orderedSets()
        require(workout.confirmed(sets.first { it.id == correction.workSetId }) && workout.closedRest(sets.first { it.id == correction.restSetId })) {
            "Wait until the affected intervals finish before restoring." }
        db.timeCorrectionDao().restore(id)
        listOf(correction.workSetId, correction.restSetId).distinct().forEach {
            db.timeCorrectionDao().review(TimeReview(it, workoutId, "needs_review", System.currentTimeMillis()))
        }
    }
    suspend fun review(workoutId: String, setId: String, status: String) = db.withTransaction {
        require(status in listOf("needs_review", "dismissed"))
        val workout = requireNotNull(db.workoutDao().getDetails(workoutId))
        require(workout.confirmed(workout.orderedSets().first { it.id == setId })) { "Confirm this set’s results first." }
        db.timeCorrectionDao().review(TimeReview(setId, workoutId, status, System.currentTimeMillis()))
    }
}
