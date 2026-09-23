package com.darkoceantech.myfitnesspolice.data

import androidx.room.*

const val COOL_DOWN_MILLIS = 60_000L

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
    @get:Ignore
    val hasStarted: Boolean get() = dutyStartedAt != null || dutyElapsedMillis > 0 || phase != "ready"
    private fun rawPhaseMillis(now: Long) = phaseElapsedMillis + elapsedSince(phaseStartedAt, now)
    fun phaseMillis(now: Long) = rawPhaseMillis(now).let {
        if (phase == "cooldown") it.coerceAtMost(COOL_DOWN_MILLIS) else it
    }
    fun dutyMillis(now: Long): Long {
        val elapsed = dutyElapsedMillis + elapsedSince(dutyStartedAt, now)
        // The final break stops at one minute even if the app is closed or results are still being entered.
        val overtime = if (phase == "cooldown") (rawPhaseMillis(now) - COOL_DOWN_MILLIS).coerceAtLeast(0) else 0
        return (elapsed - overtime).coerceAtLeast(0)
    }
    fun cooldownRemainingMillis(now: Long) = (COOL_DOWN_MILLIS - phaseMillis(now)).coerceAtLeast(0)
    fun cooldownDeadlineMillis(): Long? = if (phase == "cooldown") phaseStartedAt?.let {
        it + (COOL_DOWN_MILLIS - phaseElapsedMillis).coerceAtLeast(0)
    } else null
    private fun elapsedSince(start: Long?, now: Long) = start?.let { (now - it).coerceAtLeast(0) } ?: 0
}

@Dao
interface WorkoutSessionStateDao {
    @Query("SELECT * FROM workout_session_states WHERE workoutId = :workoutId")
    suspend fun get(workoutId: String): WorkoutSessionState?
    @Upsert suspend fun save(state: WorkoutSessionState)
}
