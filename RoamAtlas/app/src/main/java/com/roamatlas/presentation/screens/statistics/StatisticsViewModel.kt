package com.roamatlas.presentation.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.domain.model.TravelStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val statistics: TravelStatistics = TravelStatistics(),
    val error: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: TravelStatisticsRepository
) : ViewModel() {
    val uiState: StateFlow<StatisticsUiState> = repository.getTravelStatistics()
        .map { StatisticsUiState(isLoading = false, statistics = it) }
        .catch { emit(StatisticsUiState(isLoading = false, error = it.message)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatisticsUiState()
        )
}
