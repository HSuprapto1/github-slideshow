package com.roamatlas.presentation.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.max

data class MapUiState(
    val locations: List<LocationRecord> = emptyList(),
    val selectedLocation: LocationRecord? = null,
    val filterStartDate: LocalDate? = null,
    val filterEndDate: LocalDate? = null,
    val filterCountryCode: String? = null,
    val showGps: Boolean = true,
    val showTimeline: Boolean = true,
    val showPhoto: Boolean = true,
    val showFilters: Boolean = false,
    val isLoading: Boolean = true,
    val mapCenterLat: Double = 0.0,
    val mapCenterLng: Double = 0.0,
    val mapZoom: Float = 2f,
    /** Bumped when the camera must re-center even if lat/lng/zoom values are unchanged. */
    val cameraFocusToken: Int = 0,
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
            val showGps = _uiState.value.showGps
            val showTimeline = _uiState.value.showTimeline
            val showPhoto = _uiState.value.showPhoto

            val startMillis = startDate?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli()
            val endMillis = endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli()

            val sampleFlow = locationRepository.getFilteredLocationCount(
                startMillis,
                endMillis,
                countryCode
            ).flatMapLatest { total ->
                val sampleEvery = ((total + MAX_MAP_POINTS - 1) / MAX_MAP_POINTS).coerceAtLeast(1)
                locationRepository.getMapSample(
                    startMillis,
                    endMillis,
                    countryCode,
                    sampleEvery,
                    MAX_MAP_POINTS
                ).map { total to it }
            }

            val photoFlow = if (showPhoto) {
                locationRepository.getLocationsWithPhotos(
                    startMillis,
                    endMillis,
                    countryCode,
                    limit = 500
                )
            } else {
                flowOf(emptyList())
            }

            combine(sampleFlow, photoFlow) { samplePair, photoLocations ->
                val (total, sampleLocations) = samplePair
                val filteredSample = sampleLocations.filter { location ->
                    matchesSourceFilter(location, showGps, showTimeline, showPhoto)
                }
                val merged = LinkedHashMap<Long, LocationRecord>()
                filteredSample.forEach { merged[it.id] = it }
                if (showPhoto) {
                    photoLocations.forEach { merged[it.id] = it }
                }
                total to merged.values.sortedBy { it.timestamp }
            }.collect { (total, locations) ->
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

    private fun matchesSourceFilter(
        location: LocationRecord,
        showGps: Boolean,
        showTimeline: Boolean,
        showPhoto: Boolean
    ): Boolean {
        val isPhoto = !location.photoUri.isNullOrBlank()
        if (isPhoto) return showPhoto
        return when (location.source) {
            LocationSource.GPS_TRACKING -> showGps
            LocationSource.GOOGLE_TIMELINE -> showTimeline
            LocationSource.MANUAL_ENTRY -> showTimeline
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

    fun showLocalDate(
        date: LocalDate,
        countryCode: String? = null,
        city: String? = null
    ) {
        locationsJob?.cancel()
        _uiState.value = _uiState.value.copy(
            filterStartDate = date,
            filterEndDate = date,
            filterCountryCode = countryCode,
            isLoading = true
        )
        locationsJob = viewModelScope.launch {
            val showGps = _uiState.value.showGps
            val showTimeline = _uiState.value.showTimeline
            val showPhoto = _uiState.value.showPhoto
            locationRepository.getLocationsForLocalDate(
                localDate = date,
                countryCode = countryCode,
                city = city,
                limit = MAX_MAP_POINTS
            ).collect { locations ->
                val filtered = locations.filter {
                    matchesSourceFilter(it, showGps, showTimeline, showPhoto)
                }
                val center = calculateCenter(filtered)
                _uiState.value = _uiState.value.copy(
                    locations = filtered,
                    totalLocations = filtered.size,
                    mapCenterLat = center.first,
                    mapCenterLng = center.second,
                    mapZoom = if (filtered.isEmpty()) 2f else calculateZoom(filtered),
                    isLoading = false
                )
            }
        }
    }

    /**
     * Open the map on exactly one photo point: only that thumbnail pin, centered/zoomed.
     */
    fun focusLocation(locationId: Long) {
        locationsJob?.cancel()
        locationsJob = viewModelScope.launch {
            val focused = locationRepository.getLocationById(locationId)
            if (focused == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val date = focused.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
            // Single pin only — do not load other photos or the day's GPS track.
            _uiState.value = _uiState.value.copy(
                filterStartDate = date,
                filterEndDate = date,
                filterCountryCode = null,
                showGps = false,
                showTimeline = false,
                showPhoto = true,
                locations = listOf(focused),
                selectedLocation = focused,
                totalLocations = 1,
                mapCenterLat = focused.latitude,
                mapCenterLng = focused.longitude,
                mapZoom = PHOTO_FOCUS_ZOOM,
                cameraFocusToken = _uiState.value.cameraFocusToken + 1,
                isLoading = false
            )
        }
    }

    fun setCountryFilter(countryCode: String?) {
        _uiState.value = _uiState.value.copy(filterCountryCode = countryCode)
        loadLocations()
    }

    fun setSourceFilters(
        showGps: Boolean = _uiState.value.showGps,
        showTimeline: Boolean = _uiState.value.showTimeline,
        showPhoto: Boolean = _uiState.value.showPhoto
    ) {
        // Keep at least one source visible.
        val nextGps = showGps
        val nextTimeline = showTimeline
        val nextPhoto = showPhoto
        if (!nextGps && !nextTimeline && !nextPhoto) return
        _uiState.value = _uiState.value.copy(
            showGps = nextGps,
            showTimeline = nextTimeline,
            showPhoto = nextPhoto
        )
        loadLocations()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filterStartDate = null,
            filterEndDate = null,
            filterCountryCode = null,
            showGps = true,
            showTimeline = true,
            showPhoto = true
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
            mapZoom = zoom,
            cameraFocusToken = _uiState.value.cameraFocusToken + 1
        )
    }

    private fun calculateCenter(locations: List<LocationRecord>): Pair<Double, Double> {
        if (locations.isEmpty()) return Pair(0.0, 0.0)
        return Pair(
            locations.map { it.latitude }.average(),
            locations.map { it.longitude }.average()
        )
    }

    private fun calculateZoom(locations: List<LocationRecord>): Float {
        if (locations.isEmpty()) return 2f
        if (locations.size == 1) return 15f
        val latRange = locations.maxOf { it.latitude } - locations.minOf { it.latitude }
        val lngRange = locations.maxOf { it.longitude } - locations.minOf { it.longitude }
        val range = max(latRange, lngRange)
        return when {
            range > 50 -> 3f
            range > 20 -> 5f
            range > 5 -> 7f
            range > 1 -> 9f
            range > 0.1 -> 11f
            else -> 13f
        }
    }

    companion object {
        private const val MAX_MAP_POINTS = 5_000
        private const val PHOTO_FOCUS_ZOOM = 16f
    }
}
