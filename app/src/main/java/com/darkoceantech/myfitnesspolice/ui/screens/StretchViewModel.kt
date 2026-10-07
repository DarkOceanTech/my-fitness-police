package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkoceantech.myfitnesspolice.data.*
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StretchUiState(val loading: Boolean = true, val catalog: List<StretchMovement> = emptyList(),
    val routines: List<StretchRoutine> = emptyList(), val sessions: List<StretchSessionRow> = emptyList(),
    val preferences: StretchPreferences = StretchPreferences(), val failed: Boolean = false)
class StretchViewModel(private val repository: StretchRepository) : ViewModel() {
    val state = combine(repository.catalog, repository.routines, repository.sessions, repository.preferences) { catalog, routines, sessions, preferences ->
        StretchUiState(false, catalog, routines, sessions, preferences)
    }.catch { emit(StretchUiState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StretchUiState())
    private val _action = MutableStateFlow(SessionAction())
    val action = _action.asStateFlow()
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now = _now.asStateFlow()
    private val _cues = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cues = _cues.asSharedFlow()
    init {
        viewModelScope.launch {
            var previous: Pair<String, Int>? = null
            state.collect { current ->
                val session = current.sessions.firstOrNull { it.endedAt == null }
                val position = session?.let { it.id to StretchJson.run(it.progress).index }
                if (previous != null && position != previous && (position == null || position.first == previous?.first)) _cues.tryEmit(Unit)
                previous = position
            }
        }
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                try { if (state.value.sessions.any { it.endedAt == null }) repository.tick(_now.value) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { _action.value = _action.value.copy(error = "Could not update the timer. Your saved session is preserved.") }
                delay(250)
            }
        }
    }
    private fun perform(name: String, block: suspend () -> Unit) {
        if (_action.value.saving) return
        _action.value = _action.value.copy(saving = true, error = null, completedAction = null)
        viewModelScope.launch {
            try { block(); _action.value = SessionAction(revision = _action.value.revision + 1, completedAction = name) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _action.value = _action.value.copy(saving = false, error = e.message ?: "Please try again.") }
        }
    }
    fun clearError() { _action.value = _action.value.copy(error = null) }
    fun saveMovement(value: StretchMovement) = perform("save-stretch") { repository.saveMovement(value) }
    fun saveRoutine(value: StretchRoutine) = perform("save-routine") { repository.saveRoutine(value) }
    fun preferences(value: StretchPreferences) = perform("stretch-preferences") { repository.savePreferences(value) }
    fun start(id: String) = perform("start-stretch") { repository.start(id) }
    fun control(id: String, command: String, notes: String? = null) = perform(command) { repository.control(id, command, notes) }
    fun note(id: String, notes: String) = perform("stretch-session-note") { repository.saveSessionNote(id, notes) }
}
