package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf

val LocalEquipmentLibraryModel = staticCompositionLocalOf<EquipmentLibraryViewModel?> { null }
class EquipmentLibraryViewModel(private val fitness: FitnessRepository, private val settings: AppSettingsRepository? = null) : ViewModel() {
    private val repository = fitness.equipmentLibrary
    val library = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EquipmentLibraryState())
    private val transientDefaultGym = MutableStateFlow<String?>(null)
    val defaultGymId: StateFlow<String?> = settings?.defaultGymId ?: transientDefaultGym
    init {
        viewModelScope.launch {
            // Validate only actual database emissions, never the UI's initial empty loading state.
            combine(repository.observe(), defaultGymId) { records, id -> records to id }.collect { (records, id) ->
                if (id != null && records.gyms.none { it.id == id && !it.isArchived }) {
                    if (settings != null) settings.setDefaultGym(null) else transientDefaultGym.value = null
                }
            }
        }
    }
    fun setDefaultGym(id: String?) = perform("default-gym") {
        require(id == null || library.value.gyms.any { it.id == id && !it.isArchived }) { "Choose an available gym." }
        if (settings != null) settings.setDefaultGym(id) else transientDefaultGym.value = id
    }
    private val _action = MutableStateFlow(SessionAction())
    val action = _action.asStateFlow()
    fun reset() { if (!_action.value.saving) _action.value = SessionAction(revision = _action.value.revision) }
    private fun perform(operation: String, block: suspend () -> Unit) {
        if (_action.value.saving) return
        _action.value = SessionAction(saving = true, revision = _action.value.revision)
        viewModelScope.launch {
            try { block(); _action.value = SessionAction(revision = _action.value.revision + 1, completedAction = operation) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _action.value = SessionAction(error = e.message ?: "Could not save equipment. Try again.", revision = _action.value.revision) }
        }
    }
    private val _catalogHistory = MutableStateFlow(LastSessionState())
    val catalogHistory = _catalogHistory.asStateFlow()
    private var historyJob: kotlinx.coroutines.Job? = null
    fun loadCatalogHistory(exerciseId: String) {
        historyJob?.cancel()
        _catalogHistory.value = LastSessionState(loading = true)
        historyJob = viewModelScope.launch {
            try {
                val history = fitness.getExerciseSessionHistory(exerciseId)
                _catalogHistory.value = LastSessionState(snapshot = fitness.activeExerciseSwap.preview(exerciseId), history = history)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _catalogHistory.value = LastSessionState(error = "Could not load this exercise's sessions. Please retry.") }
        }
    }
    fun gym(id: String?, name: String, address: List<String>? = null) = perform("library") { repository.saveGym(id, name, address) }
    fun equipment(id: String?, type: String, name: String, model: String, gymId: String?) = perform("library") { repository.saveEquipment(id, type, name, model, gymId) }
    fun setup(id: String?, name: String, type: String, config: EquipmentConfiguration) = perform("library") { repository.saveSetup(id, name, type, config) }
    fun archive(category: Int, id: String) = perform("library") { when (category) {
        0 -> repository.archiveGym(id); 1 -> repository.archiveEquipment(id); else -> repository.archiveSetup(id)
    } }
    fun saveEntry(workout: String, entry: String, config: EquipmentConfiguration?, asDefault: Boolean,
        namedSetup: String = "", compatibleType: String = "") = perform("entry") {
        repository.saveEntryWithSetup(workout, entry, config, asDefault, namedSetup, compatibleType)
    }
    fun saveExercise(exercise: Exercise?, name: String, equipment: String, description: String, primary: String, secondary: String,
        config: EquipmentConfiguration?) = perform("exercise") {
        require(name.isNotBlank()) { "Enter an exercise name." }
        require(equipment.isNotBlank() && description.isNotBlank() && primary.isNotBlank()) {
            "Add equipment, a description, and primary muscles. Use Bodyweight when no load is needed."
        }
        if (exercise == null) fitness.addExercise(name, equipment, description, primary, secondary, config)
        else fitness.updateExercise(exercise.id, name, equipment, description, primary, secondary, config, updateEquipmentDefault = true)
    }
}
