package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** ISO local dates avoid shifting a scheduled day when the device's time zone changes. */
@Entity(tableName = "training_schedule", primaryKeys = ["trainingPlanId", "scheduledDate"],
    foreignKeys = [ForeignKey(entity = TrainingPlan::class, parentColumns = ["id"],
        childColumns = ["trainingPlanId"], onDelete = ForeignKey.CASCADE)], indices = [Index("scheduledDate")])
data class TrainingSchedule(val trainingPlanId: String, val scheduledDate: String)

@Dao
interface TrainingScheduleDao {
    @Query("SELECT * FROM training_schedule ORDER BY scheduledDate, trainingPlanId")
    fun observeAll(): Flow<List<TrainingSchedule>>
    @Query("DELETE FROM training_schedule WHERE trainingPlanId = :planId")
    suspend fun clear(planId: String)
    @Query("DELETE FROM training_schedule WHERE trainingPlanId = :planId AND scheduledDate = :date")
    suspend fun deleteDate(planId: String, date: String)
    @Insert suspend fun insert(dates: List<TrainingSchedule>)
}

class TrainingScheduleRepository(private val database: FitnessDatabase) {
    fun observeAll() = database.trainingScheduleDao().observeAll()
    suspend fun deleteDate(planId: String, date: String) = database.withTransaction {
        requireNotNull(database.trainingPlanDao().get(planId)) { "This training plan is no longer available." }
        database.trainingScheduleDao().deleteDate(planId, LocalDate.parse(date).toString())
    }
    suspend fun saveDates(planId: String, dates: List<String>) = database.withTransaction {
        requireNotNull(database.trainingPlanDao().get(planId)) { "This training plan is no longer available." }
        val normalized = dates.map { LocalDate.parse(it).toString() }.distinct().sorted()
        database.trainingScheduleDao().clear(planId)
        database.trainingScheduleDao().insert(normalized.map { TrainingSchedule(planId, it) })
    }
}
