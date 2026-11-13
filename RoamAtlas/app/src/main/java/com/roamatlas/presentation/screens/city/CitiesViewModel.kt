package com.roamatlas.presentation.screens.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.SavedPlaceRepository
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
    val customLabels: Map<String, String> = emptyMap(),
    val photoCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class CitiesViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val savedPlaceRepository: SavedPlaceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CitiesUiState())
    val uiState: StateFlow<CitiesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val cities = locationRepository.getCities()
                _uiState.value = CitiesUiState(
                    cities = cities,
                    customLabels = savedPlaceRepository.getAllLabels(),
                    photoCounts = savedPlaceRepository.getAllPhotoCounts(),
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

    fun refreshLabels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                customLabels = savedPlaceRepository.getAllLabels(),
                photoCounts = savedPlaceRepository.getAllPhotoCounts()
            )
        }
    }
}
