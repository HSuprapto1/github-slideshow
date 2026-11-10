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
    val totalLocationsRecorded: Int = 0,
    val showModeSelector: Boolean = false
)

@HiltViewModel
class GpsTrackingViewModel @Inject constructor(
    private val locationTracker: LocationTracker,
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _selectedMode = MutableStateFlow(TrackingMode.SMART)
    private val _showModeSelector = MutableStateFlow(false)
    private val _totalLocations = MutableStateFlow(0)
    
    val uiState: StateFlow<GpsTrackingUiState> = combine(
        locationTracker.trackingState,
        _selectedMode,
        _showModeSelector,
        _totalLocations
    ) { trackingState, selectedMode, showModeSelector, totalLocations ->
        GpsTrackingUiState(
            trackingState = trackingState,
            selectedMode = selectedMode,
            hasLocationPermission = checkLocationPermission(),
            hasBackgroundPermission = checkBackgroundPermission(),
            hasNotificationPermission = checkNotificationPermission(),
            totalLocationsRecorded = totalLocations,
            showModeSelector = showModeSelector
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GpsTrackingUiState()
    )
    
    init {
        loadTotalLocations()
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
        viewModelScope.launch {
            _selectedMode.value = _selectedMode.value
        }
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
