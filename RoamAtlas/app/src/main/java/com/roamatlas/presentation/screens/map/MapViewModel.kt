package com.roamatlas.presentation.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roamatlas.data.local.entity.MapPlaceEntity
import com.roamatlas.data.repository.MapPlaceRepository
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    /** Connecting lines between GPS/timeline points. Off by default to reduce map clutter. */
    val showTrackLines: Boolean = false,
    val showSavedPlaces: Boolean = true,
    val savedPlaces: List<MapPlaceEntity> = emptyList(),
    val selectedSavedPlace: MapPlaceEntity? = null,
    val placeSaveMessage: String? = null,
    val showFilters: Boolean = false,
    val isLoading: Boolean = true,
    val mapCenterLat: Double = 0.0,
    val mapCenterLng: Double = 0.0,
    val mapZoom: Float = 2f,
    /** Bumped when the camera must re-center even if lat/lng/zoom values are unchanged. */
    val cameraFocusToken: Int = 0,
    val hasLocationPermission: Boolean = false,
    val countryCodes: List<String> = emptyList(),
    val totalLocations: Int = 0
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val mapPlaceRepository: MapPlaceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapUiState(hasLocationPermission = hasLocationPermission())
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var locationsJob: Job? = null
    private var hasAppliedInitialCamera = false

    init {
        loadLocations(recenterMode = RecenterMode.INITIAL_GPS)
        loadCountryCodes()
        viewModelScope.launch {
            mapPlaceRepository.getAll().collect { places ->
                _uiState.value = _uiState.value.copy(savedPlaces = places)
            }
        }
    }

    private fun loadLocations(recenterMode: RecenterMode = RecenterMode.NONE) {
        locationsJob?.cancel()
        locationsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                hasLocationPermission = hasLocationPermission()
            )

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

            var firstEmission = true
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
                val mode = when {
                    firstEmission && recenterMode != RecenterMode.NONE -> recenterMode
                    firstEmission && !hasAppliedInitialCamera -> RecenterMode.INITIAL_GPS
                    else -> RecenterMode.NONE
                }
                firstEmission = false
                applyLocations(locations, total, mode)
            }
        }
    }

    private suspend fun applyLocations(
        locations: List<LocationRecord>,
        total: Int,
        recenterMode: RecenterMode
    ) {
        val base = _uiState.value.copy(
            locations = locations,
            totalLocations = total,
            isLoading = false,
            hasLocationPermission = hasLocationPermission()
        )
        if (recenterMode == RecenterMode.NONE) {
            _uiState.value = base
            return
        }

        val camera = when (recenterMode) {
            RecenterMode.INITIAL_GPS -> resolveInitialCamera(locations)
            RecenterMode.FIT_LOCATIONS -> {
                val center = calculateCenter(locations)
                Triple(
                    center.first,
                    center.second,
                    if (locations.isEmpty()) 2f else calculateZoom(locations)
                )
            }
            RecenterMode.NONE -> Triple(base.mapCenterLat, base.mapCenterLng, base.mapZoom)
        }
        hasAppliedInitialCamera = true
        _uiState.value = base.copy(
            mapCenterLat = camera.first,
            mapCenterLng = camera.second,
            mapZoom = camera.third,
            cameraFocusToken = base.cameraFocusToken + 1
        )
    }

    private suspend fun resolveInitialCamera(
        locations: List<LocationRecord>
    ): Triple<Double, Double, Float> {
        readDeviceLocation()?.let { device ->
            return Triple(device.latitude, device.longitude, CURRENT_GPS_ZOOM)
        }
        locations.maxByOrNull { it.timestamp }?.let { latest ->
            return Triple(latest.latitude, latest.longitude, CURRENT_GPS_ZOOM)
        }
        if (locations.isEmpty()) return Triple(0.0, 0.0, 2f)
        val center = calculateCenter(locations)
        return Triple(center.first, center.second, calculateZoom(locations))
    }

    @SuppressLint("MissingPermission")
    private suspend fun readDeviceLocation(): Location? {
        if (!hasLocationPermission()) return null
        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellation = CancellationTokenSource()
            val current = try {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellation.token
                ).await()
            } catch (_: Exception) {
                null
            }
            current ?: client.lastLocation.await()
        }.getOrNull()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
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
        if (location == null) {
            _uiState.value = _uiState.value.copy(selectedLocation = null)
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = location,
            selectedSavedPlace = null,
            mapCenterLat = location.latitude,
            mapCenterLng = location.longitude,
            mapZoom = max(_uiState.value.mapZoom, PIN_FOCUS_ZOOM),
            cameraFocusToken = _uiState.value.cameraFocusToken + 1
        )
    }

    fun selectSavedPlace(place: MapPlaceEntity?) {
        if (place == null) {
            _uiState.value = _uiState.value.copy(selectedSavedPlace = null)
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedSavedPlace = place,
            selectedLocation = null,
            mapCenterLat = place.latitude,
            mapCenterLng = place.longitude,
            mapZoom = max(_uiState.value.mapZoom, PIN_FOCUS_ZOOM),
            cameraFocusToken = _uiState.value.cameraFocusToken + 1
        )
    }

    fun saveSelectedAsPlace(name: String) {
        val location = _uiState.value.selectedLocation ?: return
        viewModelScope.launch {
            mapPlaceRepository.savePlace(
                name = name,
                latitude = location.latitude,
                longitude = location.longitude
            )
            _uiState.value = _uiState.value.copy(
                placeSaveMessage = "Saved “${name.trim().ifBlank { "Saved place" }}” on the map."
            )
        }
    }

    fun deleteSelectedSavedPlace() {
        val place = _uiState.value.selectedSavedPlace ?: return
        viewModelScope.launch {
            mapPlaceRepository.deletePlace(place.id)
            _uiState.value = _uiState.value.copy(
                selectedSavedPlace = null,
                placeSaveMessage = "Removed saved place."
            )
        }
    }

    fun clearPlaceMessage() {
        _uiState.value = _uiState.value.copy(placeSaveMessage = null)
    }

    fun setShowSavedPlaces(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSavedPlaces = show)
    }

    fun setDateFilter(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.value = _uiState.value.copy(
            filterStartDate = startDate,
            filterEndDate = endDate
        )
        loadLocations(recenterMode = RecenterMode.FIT_LOCATIONS)
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
                applyLocations(filtered, filtered.size, RecenterMode.FIT_LOCATIONS)
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
            hasAppliedInitialCamera = true
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
        loadLocations(recenterMode = RecenterMode.FIT_LOCATIONS)
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
        loadLocations(recenterMode = RecenterMode.FIT_LOCATIONS)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filterStartDate = null,
            filterEndDate = null,
            filterCountryCode = null,
            showGps = true,
            showTimeline = true,
            showPhoto = true,
            showTrackLines = false,
            showSavedPlaces = true
        )
        loadLocations(recenterMode = RecenterMode.INITIAL_GPS)
    }

    fun setShowTrackLines(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTrackLines = show)
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

    fun centerOnCurrentGps() {
        viewModelScope.launch {
            val device = readDeviceLocation()
            if (device != null) {
                centerOnLocation(device.latitude, device.longitude, CURRENT_GPS_ZOOM)
                return@launch
            }
            val latest = _uiState.value.locations.maxByOrNull { it.timestamp }
            if (latest != null) {
                centerOnLocation(latest.latitude, latest.longitude, CURRENT_GPS_ZOOM)
            }
        }
    }

    fun fitAllLocations() {
        val locations = _uiState.value.locations
        if (locations.isEmpty()) return
        val center = calculateCenter(locations)
        centerOnLocation(center.first, center.second, calculateZoom(locations))
    }

    fun refreshLocationPermission() {
        _uiState.value = _uiState.value.copy(
            hasLocationPermission = hasLocationPermission()
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

    private enum class RecenterMode {
        NONE,
        INITIAL_GPS,
        FIT_LOCATIONS
    }

    companion object {
        private const val MAX_MAP_POINTS = 5_000
        private const val PHOTO_FOCUS_ZOOM = 16f
        private const val PIN_FOCUS_ZOOM = 15f
        private const val CURRENT_GPS_ZOOM = 14f
    }
}
