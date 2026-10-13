package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf

val LocalEquipmentLibraryModel = staticCompositionLocalOf<EquipmentLibraryViewModel?> { null }
class EquipmentLibraryViewModel(private val fitness: FitnessRepository) : ViewModel() {
    private val repository = fitness.equipmentLibrary
    val library = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EquipmentLibraryState())
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
    fun gym(id: String?, name: String) = perform("library") { repository.saveGym(id, name) }
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
