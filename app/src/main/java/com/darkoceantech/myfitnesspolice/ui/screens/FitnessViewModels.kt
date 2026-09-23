package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darkoceantech.myfitnesspolice.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ready<T>(val items: List<T>) : ListState<T>
    data object Failed : ListState<Nothing>
}

open class ListViewModel<T>(private val source: () -> Flow<List<T>>) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest {
        source().map<List<T>, ListState<T>> { ListState.Ready(it) }
            .onStart { emit(ListState.Loading) }
            .catch { emit(ListState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState.Loading)

    fun retry() { refresh.value += 1 }
}

data class ExerciseFormState(
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class ExercisesViewModel(private val repository: FitnessRepository) :
    ListViewModel<Exercise>(repository::observeExercises) {
    private val _form = MutableStateFlow(ExerciseFormState())
    val form = _form.asStateFlow()

    fun resetForm() { if (!_form.value.saving) _form.value = ExerciseFormState() }

    fun addExercise(name: String, equipment: String, description: String, primaryMuscles: String, secondaryMuscles: String) {
        if (_form.value.saving) return
        if (name.isBlank()) {
            _form.value = ExerciseFormState(error = "Enter an exercise name.")
            return
        }
        if (equipment.isBlank() || description.isBlank() || primaryMuscles.isBlank()) {
            _form.value = ExerciseFormState(error = "Add equipment, a description, and primary muscles. Use Bodyweight when no load is needed.")
            return
        }
        _form.value = ExerciseFormState(saving = true)
        viewModelScope.launch {
            try {
                repository.addExercise(name, equipment, description, primaryMuscles, secondaryMuscles)
                _form.value = ExerciseFormState(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _form.value = ExerciseFormState(error = "Could not save the exercise. Please try again.")
            }
        }
    }
}

class HistoryViewModel(repository: FitnessRepository) :
    ListViewModel<Workout>(repository::observeHistory)

class WorkoutViewModel(repository: FitnessRepository) :
    ListViewModel<Workout>(repository::observeUnfinished)

class FitnessViewModelFactory(private val repository: FitnessRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        SessionViewModel::class.java -> SessionViewModel(repository)
        ExercisesViewModel::class.java -> ExercisesViewModel(repository)
        HistoryViewModel::class.java -> HistoryViewModel(repository)
        WorkoutViewModel::class.java -> WorkoutViewModel(repository)
        else -> error("Unknown ViewModel: " + modelClass.name)
    } as T
}
