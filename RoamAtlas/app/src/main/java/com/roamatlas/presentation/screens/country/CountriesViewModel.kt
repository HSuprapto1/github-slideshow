package com.roamatlas.presentation.screens.country

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.domain.model.CountryVisitStats
import com.roamatlas.domain.model.CountryVisitClassification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountriesUiState(
    val countries: List<CountryVisitStats> = emptyList(),
    val earliestDate: java.time.LocalDate? = null,
    val latestDate: java.time.LocalDate? = null,
    val homeCountryCode: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class CountriesViewModel @Inject constructor(
    private val statisticsRepository: TravelStatisticsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CountriesUiState())
    val uiState: StateFlow<CountriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                statisticsRepository.getTravelStatistics().collect { statistics ->
                    _uiState.value = CountriesUiState(
                        countries = statistics.countriesVisitedList
                            .filter {
                                it.classification != CountryVisitClassification.TRANSIT
                            }
                            .sortedByDescending { it.daysSpent },
                        earliestDate = statistics.earliestRecordDate,
                        latestDate = statistics.latestRecordDate,
                        homeCountryCode = statisticsRepository.getHomeCountryCode(),
                        isLoading = false
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = CountriesUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load countries."
                )
            }
        }
    }
}
