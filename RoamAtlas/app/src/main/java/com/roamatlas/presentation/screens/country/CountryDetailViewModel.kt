package com.roamatlas.presentation.screens.country

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.domain.repository.CountryDayRecord
import com.roamatlas.domain.repository.CountryDetail
import com.roamatlas.domain.repository.CountryYearRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountryDetailUiState(
    val countryCode: String = "",
    val detail: CountryDetail? = null,
    val years: List<CountryYearRecord> = emptyList(),
    val days: List<CountryDayRecord> = emptyList(),
    val isHomeCountry: Boolean = false,
    val selectedYear: Int? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val visibleDays: List<CountryDayRecord>
        get() = selectedYear?.let { year -> days.filter { it.date.year == year } } ?: days
}

@HiltViewModel
class CountryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val travelStatisticsRepository: TravelStatisticsRepository
) : ViewModel() {
    private val countryCode = savedStateHandle.get<String>("code").orEmpty().uppercase()
    private val _uiState = MutableStateFlow(CountryDetailUiState(countryCode = countryCode))
    val uiState: StateFlow<CountryDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectYear(year: Int?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
    }

    fun refresh() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (countryCode.length != 2) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "This country could not be opened."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                _uiState.value = _uiState.value.copy(
                    detail = locationRepository.getCountryDetail(countryCode),
                    years = locationRepository.getCountryYearRecords(countryCode),
                    days = locationRepository.getCountryDayRecords(countryCode),
                    isHomeCountry = travelStatisticsRepository.getHomeCountryCode()
                        ?.equals(countryCode, ignoreCase = true) == true,
                    isLoading = false
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load country history."
                )
            }
        }
    }
}
