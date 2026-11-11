package com.roamatlas.presentation.screens.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.repository.CityRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CitiesUiState(
    val cities: List<CityRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class CitiesViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CitiesUiState())
    val uiState: StateFlow<CitiesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _uiState.value = CitiesUiState(
                    cities = locationRepository.getCities(),
                    isLoading = false
                )
            } catch (error: Exception) {
                _uiState.value = CitiesUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load cities."
                )
            }
        }
    }
}
