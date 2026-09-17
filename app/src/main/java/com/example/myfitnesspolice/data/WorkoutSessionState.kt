package com.example.myfitnesspolice.data

import androidx.room.*

/** Epoch anchors survive process death. Elapsed totals exclude explicitly paused time. */
@Entity(tableName = "workout_session_states", foreignKeys = [
    ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
])
data class WorkoutSessionState(
    @PrimaryKey val workoutId: String,
    val phase: String = "ready",
    val currentSetId: String? = null,
    val phaseStartedAt: Long? = null,
    val phaseElapsedMillis: Long = 0,
    val dutyStartedAt: Long? = null,
    val dutyElapsedMillis: Long = 0,
    val isPaused: Boolean = false,
    val awaitingActual: Boolean = false,
    val pauseReason: String = "",
) {
    fun phaseMillis(now: Long) = phaseElapsedMillis + elapsedSince(phaseStartedAt, now)
    fun dutyMillis(now: Long) = dutyElapsedMillis + elapsedSince(dutyStartedAt, now)
    private fun elapsedSince(start: Long?, now: Long) = start?.let { (now - it).coerceAtLeast(0) } ?: 0
}

@Dao
interface WorkoutSessionStateDao {
    @Query("SELECT * FROM workout_session_states WHERE workoutId = :workoutId")
    suspend fun get(workoutId: String): WorkoutSessionState?
    @Upsert suspend fun save(state: WorkoutSessionState)
}
