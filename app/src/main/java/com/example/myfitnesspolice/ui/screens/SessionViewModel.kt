package com.example.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnesspolice.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

data class SessionState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val sessions: List<WorkoutDetails> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val trainingPlans: List<TrainingPlanDetails> = emptyList(),
)

data class SessionAction(val saving: Boolean = false, val error: String? = null, val revision: Int = 0,
    val completedAction: String? = null)

class SessionViewModel(private val repository: FitnessRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest {
        combine(repository.observeSessions(), repository.observeExercises(), repository.trainingPlans.observeAll()) { sessions, exercises, plans ->
            SessionState(loading = false, sessions = sessions, exercises = exercises, trainingPlans = plans)
        }.catch { emit(SessionState(loading = false, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState())
    // Collected by Home only. Room updates refresh totals; ticking keeps live activity and week rollover accurate.
    val weeklyProgress = combine(state, flow {
        while (true) { emit(System.currentTimeMillis()); delay(1000) }
    }) { current, now ->
        if (current.loading || current.failed) null else calculateWeeklyProgress(current.sessions, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _action = MutableStateFlow(SessionAction())
    val action = _action.asStateFlow()
    fun retry() { refresh.value++ }
    fun clearError() { _action.value = _action.value.copy(error = null) }
    private fun perform(operation: String? = null, block: suspend () -> Unit) {
        if (_action.value.saving) return
        _action.value = _action.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                block()
                _action.value = SessionAction(revision = _action.value.revision + 1, completedAction = operation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _action.value = _action.value.copy(saving = false, error = e.message ?: "Check the entered values.")
            } catch (_: Exception) {
                _action.value = _action.value.copy(saving = false, error = "Could not save your changes. Please retry.")
            }
        }
    }
    fun ensureSession(id: String) = perform("ensure-session") { repository.sessionProgress.ensureSession(id) }
    fun completeActiveSet(workout: String, set: String) =
        perform("complete-active-set") { repository.sessionProgress.completeSet(workout, set) }
    fun recordActual(workout: String, set: String, reps: String) = perform("record-actual") {
        val count = reps.trim().toIntOrNull()
        require(count != null && count >= 0) { "Enter a whole number of reps, including 0 for an unsuccessful set." }
        repository.sessionProgress.recordActual(workout, set, count)
    }
    fun startNextSet(workout: String, set: String) =
        perform("start-next-set") { repository.sessionProgress.startSet(workout, set) }
    fun endRest(workout: String) = perform("end-rest") { repository.sessionProgress.endRest(workout) }
    fun pauseSession(workout: String) = perform("pause-session") { repository.sessionProgress.pause(workout) }
    fun savePauseReason(workout: String, note: String) =
        perform("pause-reason") { repository.sessionProgress.savePauseReason(workout, note) }
    fun resumeSession(workout: String) = perform("resume-session") { repository.sessionProgress.resume(workout) }
    fun finishSession(workout: String) = perform("finish-session") { repository.finishWorkout(workout) }
    fun start() = perform { repository.startWorkout() }
    fun setDetails(field: String, value: String, planId: String? = null) = perform { repository.setWorkoutDetails(field, value, planId) }
    fun deletePlan(id: String) = perform("delete-plan") { repository.deletePlan(id) }
    fun savePlan(id: String, name: String? = null) = perform("save-plan") { repository.savePlan(id, name) }
    fun saveTrainingPlan(id: String?, name: String, workouts: List<String>, day: String) =
        perform("save-training-plan") { repository.trainingPlans.save(id, name, workouts, day) }
    fun startTraining(id: String) = perform("start-training") { repository.startTraining(id) }
    fun deleteTrainingPlan(id: String) = perform("delete-training-plan") { repository.trainingPlans.delete(id) }
    fun startPlan(id: String) = perform("start-plan") { repository.startPlan(id) }
    fun clearDraft() = perform { repository.clearDraft() }
    fun saveMetadata(id: String, name: String, muscles: String, day: String, plans: List<String>) =
        perform("save-metadata") { repository.saveWorkoutDetails(id, name, muscles, day, plans) }
    fun reorderExercises(id: String, entries: List<String>) =
        perform("reorder-exercises") { repository.reorderExercises(id, entries) }
    fun removeExercise(workout: String, entry: String) =
        perform("remove-exercise") { repository.removeWorkoutExercise(workout, entry) }
    fun saveEquipmentPositions(workout: String, entry: String, positions: List<EquipmentPosition>) =
        perform("save-equipment-positions") { repository.saveEquipmentPositions(workout, entry, positions) }
    fun chooseExercise(id: String, planId: String? = null) = perform { repository.chooseExercise(id, planId) }
    fun addDefaultSet(workout: String, entry: String) = perform { repository.saveSet(workout, entry, null, 10, 0) }
    fun copyLastSet(workout: String, entry: String) = perform { repository.copyLastSet(workout, entry) }
    fun sessionNote(workout: String, entry: String, value: String) = perform("session-note") { repository.updateExerciseNote(workout, entry, value) }
    fun note(workout: String, entry: String, value: String) = perform { repository.updateExerciseNote(workout, entry, value) }
    fun changeSet(workout: String, entry: String, set: String, field: String, value: String) = perform {
        repository.updateSetFields(workout, entry, set,
            reps = if (field == "reps") value.toInt() else null,
            grams = if (field == "weight") value.toBigDecimal().multiply(BigDecimal("453.59237"))
                .setScale(0, RoundingMode.HALF_UP).longValueExact() else null,
            modifier = if (field == "modifier") value else null,
            warmup = if (field == "type") value == "warmup" else null)
    }
    fun deleteWorkout(id: String) = perform("delete-history-workout") { repository.deleteWorkout(id) }
    fun deleteAllHistory() = perform("delete-all-history") { repository.deleteAllHistory() }
    fun renameHistoryWorkout(id: String, name: String) = perform("rename-history-workout") { repository.renameHistoryWorkout(id, name) }
    fun saveHistorySetNote(workout: String, set: String, notes: String) = perform("save-history-set-note") {
        repository.saveHistorySetNote(workout, set, notes)
    }
    fun saveActiveSetNote(workout: String, set: String, notes: String) = perform("save-active-set-note") {
        repository.saveActiveSetNote(workout, set, notes)
    }
    fun correctHistorySet(workout: String, set: String, pounds: String?, planned: String, actual: String, rpe: Int?) =
        correctRecordedSet("correct-history-set", pounds, planned, actual, rpe) { grams, plannedCount, actualCount, effort ->
            repository.correctHistorySet(workout, set, grams, plannedCount, actualCount, effort)
        }
    fun correctActiveSet(workout: String, set: String, pounds: String?, planned: String, actual: String, rpe: Int?) =
        correctRecordedSet("correct-active-set", pounds, planned, actual, rpe) { grams, plannedCount, actualCount, effort ->
            repository.correctActiveSet(workout, set, grams, plannedCount, actualCount, effort)
        }
    private fun correctRecordedSet(operation: String, pounds: String?, planned: String, actual: String, rpe: Int?,
        save: suspend (Long?, Int, Int, Int?) -> Unit) = perform(operation) {
        val plannedCount = planned.toIntOrNull()
        val actualCount = actual.toIntOrNull()
        require(plannedCount != null && plannedCount > 0) { "Enter a positive whole number of planned reps." }
        require(actualCount != null && actualCount >= 0) { "Enter a nonnegative whole number of actual reps." }
        val grams = pounds?.let {
            val amount = it.toBigDecimalOrNull()
            require(amount != null && amount >= BigDecimal.ZERO) { "Enter a nonnegative weight." }
            try { amount.multiply(BigDecimal("453.59237")).setScale(0, RoundingMode.HALF_UP).longValueExact() }
            catch (_: ArithmeticException) { throw IllegalArgumentException("That weight is too large.") }
        }
        save(grams, plannedCount, actualCount, rpe)
    }
    fun updateActual(workout: String, set: String, reps: String) = perform("update-actual") {
        val count = reps.trim().toIntOrNull()
        require(count != null && count >= 0) { "Enter a nonnegative whole number of reps." }
        repository.updateActual(workout, set, count)
    }
    fun saveSetInfo(workout: String, set: String, reps: String, notes: String) = perform("save-set-info") {
        val count = reps.trim().toIntOrNull()
        require(count != null && count >= 0) { "Enter a nonnegative whole number of reps." }
        repository.saveSetInfo(workout, set, count, notes)
    }
    fun addExercise(workout: String, exercise: String) = perform { repository.addWorkoutExercise(workout, exercise) }
    fun finish(id: String) = perform { repository.finishWorkout(id) }
    fun deleteSet(workout: String, entry: String, set: String) =
        perform { repository.deleteSet(workout, entry, set) }
    fun saveSet(workout: String, entry: String, set: String?, reps: String, weight: String, unit: String) = perform {
        val count = reps.trim().toIntOrNull()
        require(count != null && count >= 0) { "Enter a nonnegative whole number of reps." }
        val amount = weight.trim().replace(',', '.').toBigDecimalOrNull()
        require(amount != null && amount >= BigDecimal.ZERO) { "Enter a nonnegative weight." }
        val grams = try {
            amount.multiply(BigDecimal(if (unit == "lb") "453.59237" else "1000"))
                .setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: ArithmeticException) { throw IllegalArgumentException("That weight is too large.") }
        repository.saveSet(workout, entry, set, count, grams)
    }
}
