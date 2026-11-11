package com.roamatlas.presentation.screens.city

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.repository.CityDayRecord
import com.roamatlas.domain.repository.CityRecord
import com.roamatlas.domain.repository.CityYearRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CityDetailUiState(
    val detail: CityRecord? = null,
    val years: List<CityYearRecord> = emptyList(),
    val days: List<CityDayRecord> = emptyList(),
    val selectedYear: Int? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val visibleDays: List<CityDayRecord>
        get() = selectedYear?.let { year -> days.filter { it.date.year == year } } ?: days
}

@HiltViewModel
class CityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val countryCode = savedStateHandle.get<String>("country").orEmpty().uppercase()
    private val city = savedStateHandle.get<String>("city").orEmpty()
    private val _uiState = MutableStateFlow(CityDetailUiState())
    val uiState: StateFlow<CityDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (city.isBlank() || countryCode.length != 2) {
                _uiState.value = CityDetailUiState(
                    isLoading = false,
                    errorMessage = "This city could not be opened."
                )
                return@launch
            }
            try {
                _uiState.value = CityDetailUiState(
                    detail = locationRepository.getCityDetail(city, countryCode),
                    years = locationRepository.getCityYearRecords(city, countryCode),
                    days = locationRepository.getCityDayRecords(city, countryCode),
                    isLoading = false
                )
            } catch (error: Exception) {
                _uiState.value = CityDetailUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load city history."
                )
            }
        }
    }

    fun selectYear(year: Int?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
    }
}
