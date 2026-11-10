package com.roamatlas.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeCountryUiState(
    val selectedCountryCode: String? = null,
    val selectedCountryName: String? = null,
    val countries: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeCountryViewModel @Inject constructor(
    private val travelStatisticsRepository: TravelStatisticsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeCountryUiState())
    val uiState: StateFlow<HomeCountryUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            val countries = TravelStatisticsRepository.getAllCountries()
            val currentCode = travelStatisticsRepository.getHomeCountryCode()
            val currentName = countries.find { it.first == currentCode }?.second
            
            _uiState.value = HomeCountryUiState(
                selectedCountryCode = currentCode,
                selectedCountryName = currentName,
                countries = countries,
                isLoading = false
            )
        }
    }
    
    fun selectCountry(code: String, name: String) {
        viewModelScope.launch {
            travelStatisticsRepository.setHomeCountry(code, name)
            _uiState.value = _uiState.value.copy(
                selectedCountryCode = code,
                selectedCountryName = name
            )
        }
    }
}
