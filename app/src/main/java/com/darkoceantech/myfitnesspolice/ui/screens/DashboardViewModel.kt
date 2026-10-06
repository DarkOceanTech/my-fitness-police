package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkoceantech.myfitnesspolice.data.DashboardRepository
import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class DashboardUiState(val report: DashboardReport? = null, val loading: Boolean = true, val error: Boolean = false)
private data class DashboardSelection(val day: LocalDate = LocalDate.now(), val dailyLift: String? = null, val monthlyLift: String? = null)

class DashboardViewModel(repository: DashboardRepository) : ViewModel() {
    private val selection = MutableStateFlow(DashboardSelection())
    private val refresh = MutableStateFlow(0)
    private val calculator = DashboardMetricsCalculator()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest {
        combine(repository.input, selection, flow {
            while (true) { emit(System.currentTimeMillis()); delay(60_000) }
        }) { input, selected, _ ->
            // A database update can arrive between minute ticks. Evaluate it at the current
            // time so a just-finished session is not filtered as a future record.
            DashboardUiState(calculator.calculate(input, System.currentTimeMillis(), selected.day, selected.dailyLift, selected.monthlyLift), loading = false)
        }.flowOn(Dispatchers.Default).catch { emit(DashboardUiState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
    fun selectDay(date: LocalDate) { selection.value = selection.value.copy(day = date) }
    fun selectDailyLift(id: String) { selection.value = selection.value.copy(dailyLift = id) }
    fun selectMonthlyLift(id: String) { selection.value = selection.value.copy(monthlyLift = id) }
    fun retry() { refresh.value++ }
}
