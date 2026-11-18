package com.roamatlas.presentation.screens.city

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.SavedPlaceRepository
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
    val customLabel: String? = null,
    val photoUris: List<String> = emptyList(),
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
    private val locationRepository: LocationRepository,
    private val savedPlaceRepository: SavedPlaceRepository
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
                    customLabel = savedPlaceRepository.getLabel(countryCode, city),
                    photoUris = savedPlaceRepository.getPhotoUris(countryCode, city),
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

    fun saveCustomLabel(label: String) {
        viewModelScope.launch {
            savedPlaceRepository.saveLabel(countryCode, city, label)
            _uiState.value = _uiState.value.copy(
                customLabel = label.trim().takeIf { it.isNotBlank() }
            )
        }
    }

    fun addPhotos(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val updated = (_uiState.value.photoUris + uris).distinct()
            savedPlaceRepository.savePhotoUris(countryCode, city, updated)
            _uiState.value = _uiState.value.copy(photoUris = updated)
        }
    }

    fun removePhoto(uri: String) {
        viewModelScope.launch {
            val updated = _uiState.value.photoUris - uri
            savedPlaceRepository.savePhotoUris(countryCode, city, updated)
            _uiState.value = _uiState.value.copy(photoUris = updated)
        }
    }
}
