package com.roamatlas.presentation.screens.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.location.LocationTracker
import com.roamatlas.data.location.LocationTrackingService
import com.roamatlas.data.location.TrackingMode
import com.roamatlas.data.location.TrackingPreferences
import com.roamatlas.data.location.TrackingState
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GpsTrackingUiState(
    val trackingState: TrackingState = TrackingState(),
    val selectedMode: TrackingMode = TrackingMode.SMART,
    val hasLocationPermission: Boolean = false,
    val hasBackgroundPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val allPermissionsGranted: Boolean = false,
    val totalLocationsRecorded: Int = 0,
    val showModeSelector: Boolean = false
)

@HiltViewModel
class GpsTrackingViewModel @Inject constructor(
    private val locationTracker: LocationTracker,
    private val locationRepository: LocationRepository,
    private val trackingPreferences: TrackingPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _selectedMode = MutableStateFlow(trackingPreferences.mode)
    private val _showModeSelector = MutableStateFlow(false)
    private val _totalLocations = MutableStateFlow(0)
    /** Bumped so permission checks re-run even when other flows are unchanged. */
    private val _permissionRevision = MutableStateFlow(0)
    
    val uiState: StateFlow<GpsTrackingUiState> = combine(
        locationTracker.trackingState,
        _selectedMode,
        _showModeSelector,
        _totalLocations,
        _permissionRevision
    ) { trackingState, selectedMode, showModeSelector, totalLocations, _ ->
        val hasLocation = checkLocationPermission()
        val hasBackground = checkBackgroundPermission()
        val hasNotification = checkNotificationPermission()
        GpsTrackingUiState(
            trackingState = trackingState,
            selectedMode = selectedMode,
            hasLocationPermission = hasLocation,
            hasBackgroundPermission = hasBackground,
            hasNotificationPermission = hasNotification,
            allPermissionsGranted = hasLocation && hasBackground && hasNotification,
            totalLocationsRecorded = totalLocations,
            showModeSelector = showModeSelector
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GpsTrackingUiState(
            hasLocationPermission = checkLocationPermission(),
            hasBackgroundPermission = checkBackgroundPermission(),
            hasNotificationPermission = checkNotificationPermission(),
            allPermissionsGranted = checkLocationPermission() &&
                checkBackgroundPermission() &&
                checkNotificationPermission(),
            selectedMode = trackingPreferences.mode
        )
    )
    
    init {
        loadTotalLocations()
        refreshPermissions()
    }
    
    private fun loadTotalLocations() {
        viewModelScope.launch {
            locationRepository.getLocationCount().collect { count ->
                _totalLocations.value = count
            }
        }
    }
    
    fun startTracking() {
        LocationTrackingService.startTracking(context, _selectedMode.value)
    }
    
    fun stopTracking() {
        LocationTrackingService.stopTracking(context)
    }
    
    fun pauseTracking() {
        LocationTrackingService.pauseTracking(context)
    }
    
    fun resumeTracking() {
        LocationTrackingService.resumeTracking(context)
    }
    
    fun selectMode(mode: TrackingMode) {
        _selectedMode.value = mode
        trackingPreferences.mode = mode
        _showModeSelector.value = false
        
        if (locationTracker.trackingState.value.isTracking) {
            stopTracking()
            startTracking()
        }
    }
    
    fun showModeSelector() {
        _showModeSelector.value = true
    }
    
    fun hideModeSelector() {
        _showModeSelector.value = false
    }
    
    fun refreshPermissions() {
        _permissionRevision.value = _permissionRevision.value + 1
    }
    
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun checkBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
