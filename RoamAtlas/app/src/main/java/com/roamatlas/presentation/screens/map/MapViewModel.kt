package com.roamatlas.presentation.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MapUiState(
    val locations: List<LocationRecord> = emptyList(),
    val selectedLocation: LocationRecord? = null,
    val filterStartDate: LocalDate? = null,
    val filterEndDate: LocalDate? = null,
    val filterCountryCode: String? = null,
    val showFilters: Boolean = false,
    val isLoading: Boolean = true,
    val mapCenterLat: Double = 0.0,
    val mapCenterLng: Double = 0.0,
    val mapZoom: Float = 2f,
    val countryCodes: List<String> = emptyList(),
    val totalLocations: Int = 0
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var locationsJob: Job? = null
    
    init {
        loadLocations()
        loadCountryCodes()
    }
    
    private fun loadLocations() {
        locationsJob?.cancel()
        locationsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val startDate = _uiState.value.filterStartDate
            val endDate = _uiState.value.filterEndDate
            val countryCode = _uiState.value.filterCountryCode
            
            val startMillis = startDate?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli()
            val endMillis = endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli()

            locationRepository.getFilteredLocationCount(startMillis, endMillis, countryCode)
                .flatMapLatest { total ->
                    val sampleEvery = ((total + MAX_MAP_POINTS - 1) / MAX_MAP_POINTS).coerceAtLeast(1)
                    locationRepository.getMapSample(
                        startMillis,
                        endMillis,
                        countryCode,
                        sampleEvery,
                        MAX_MAP_POINTS
                    ).map { total to it }
                }
                .collect { (total, locations) ->
                val center = calculateCenter(locations)
                
                _uiState.value = _uiState.value.copy(
                    locations = locations,
                    totalLocations = total,
                    mapCenterLat = center.first,
                    mapCenterLng = center.second,
                    mapZoom = if (locations.isEmpty()) 2f else calculateZoom(locations),
                    isLoading = false
                )
            }
        }
    }
    
    private fun loadCountryCodes() {
        viewModelScope.launch {
            locationRepository.getDistinctCountryCodes().collect { codes ->
                _uiState.value = _uiState.value.copy(countryCodes = codes)
            }
        }
    }
    
    fun selectLocation(location: LocationRecord?) {
        _uiState.value = _uiState.value.copy(selectedLocation = location)
    }
    
    fun setDateFilter(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.value = _uiState.value.copy(
            filterStartDate = startDate,
            filterEndDate = endDate
        )
        loadLocations()
    }
    
    fun setCountryFilter(countryCode: String?) {
        _uiState.value = _uiState.value.copy(filterCountryCode = countryCode)
        loadLocations()
    }
    
    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filterStartDate = null,
            filterEndDate = null,
            filterCountryCode = null
        )
        loadLocations()
    }
    
    fun toggleFilters() {
        _uiState.value = _uiState.value.copy(showFilters = !_uiState.value.showFilters)
    }
    
    fun centerOnLocation(lat: Double, lng: Double, zoom: Float = 15f) {
        _uiState.value = _uiState.value.copy(
            mapCenterLat = lat,
            mapCenterLng = lng,
            mapZoom = zoom
        )
    }
    
    private fun calculateCenter(locations: List<LocationRecord>): Pair<Double, Double> {
        if (locations.isEmpty()) return Pair(0.0, 0.0)
        
        val avgLat = locations.map { it.latitude }.average()
        val avgLng = locations.map { it.longitude }.average()
        
        return Pair(avgLat, avgLng)
    }
    
    private fun calculateZoom(locations: List<LocationRecord>): Float {
        if (locations.isEmpty()) return 2f
        if (locations.size == 1) return 15f
        
        val latitudes = locations.map { it.latitude }
        val longitudes = locations.map { it.longitude }
        
        val latSpan = (latitudes.maxOrNull() ?: 0.0) - (latitudes.minOrNull() ?: 0.0)
        val lngSpan = (longitudes.maxOrNull() ?: 0.0) - (longitudes.minOrNull() ?: 0.0)
        
        val maxSpan = maxOf(latSpan, lngSpan)
        
        return when {
            maxSpan > 100 -> 2f
            maxSpan > 50 -> 3f
            maxSpan > 20 -> 4f
            maxSpan > 10 -> 5f
            maxSpan > 5 -> 6f
            maxSpan > 2 -> 8f
            maxSpan > 1 -> 10f
            maxSpan > 0.5 -> 12f
            else -> 14f
        }
    }

    companion object {
        private const val MAX_MAP_POINTS = 5_000
    }
}
