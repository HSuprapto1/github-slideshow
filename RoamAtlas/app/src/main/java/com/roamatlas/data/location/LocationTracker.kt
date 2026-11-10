package com.roamatlas.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.roamatlas.domain.model.DataConfidence
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val lastLocation: Location? = null,
    val lastUpdateTime: Instant? = null,
    val locationCount: Int = 0,
    val configuration: TrackingConfiguration = TrackingConfiguration.SMART,
    val error: String? = null
)

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    private var onLocationReceived: ((LocationRecord) -> Unit)? = null
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun startTracking(
        configuration: TrackingConfiguration = TrackingConfiguration.SMART,
        onLocation: (LocationRecord) -> Unit
    ): Boolean {
        if (!hasLocationPermission()) {
            _trackingState.value = _trackingState.value.copy(
                error = "Location permission not granted"
            )
            return false
        }
        
        if (_trackingState.value.isTracking) {
            stopTracking()
        }
        
        onLocationReceived = onLocation
        
        val locationRequest = LocationRequest.Builder(
            configuration.priority,
            configuration.intervalMillis
        )
            .setMinUpdateIntervalMillis(configuration.fastestIntervalMillis)
            .setMinUpdateDistanceMeters(configuration.minDisplacementMeters)
            .setMaxUpdateDelayMillis(configuration.maxWaitTimeMillis)
            .setWaitForAccurateLocation(configuration.mode == TrackingMode.HIGH_ACCURACY)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    processLocation(location)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            _trackingState.value = TrackingState(
                isTracking = true,
                isPaused = false,
                configuration = configuration,
                error = null
            )
            
            return true
        } catch (e: SecurityException) {
            _trackingState.value = _trackingState.value.copy(
                error = "Security exception: ${e.message}"
            )
            return false
        }
    }
    
    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        onLocationReceived = null
        
        _trackingState.value = TrackingState(
            isTracking = false,
            isPaused = false,
            locationCount = _trackingState.value.locationCount
        )
    }
    
    fun pauseTracking() {
        if (!_trackingState.value.isTracking) return
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        
        _trackingState.value = _trackingState.value.copy(isPaused = true)
    }
    
    fun resumeTracking() {
        if (!_trackingState.value.isTracking || !_trackingState.value.isPaused) return
        
        val config = _trackingState.value.configuration
        val callback = onLocationReceived ?: return
        
        val locationRequest = LocationRequest.Builder(
            config.priority,
            config.intervalMillis
        )
            .setMinUpdateIntervalMillis(config.fastestIntervalMillis)
            .setMinUpdateDistanceMeters(config.minDisplacementMeters)
            .setMaxUpdateDelayMillis(config.maxWaitTimeMillis)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    processLocation(location)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            _trackingState.value = _trackingState.value.copy(isPaused = false)
        } catch (e: SecurityException) {
            _trackingState.value = _trackingState.value.copy(
                error = "Failed to resume: ${e.message}"
            )
        }
    }
    
    fun updateConfiguration(configuration: TrackingConfiguration) {
        if (_trackingState.value.isTracking) {
            val callback = onLocationReceived
            stopTracking()
            if (callback != null) {
                startTracking(configuration, callback)
            }
        } else {
            _trackingState.value = _trackingState.value.copy(configuration = configuration)
        }
    }
    
    private fun processLocation(location: Location) {
        val now = Instant.now()
        
        _trackingState.value = _trackingState.value.copy(
            lastLocation = location,
            lastUpdateTime = now,
            locationCount = _trackingState.value.locationCount + 1
        )
        
        val record = LocationRecord(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = Instant.ofEpochMilli(location.time),
            source = LocationSource.GPS_TRACKING,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            confidence = determineConfidence(location),
            originalSourceId = "gps_${location.time}_${location.latitude}_${location.longitude}",
            notes = buildLocationNotes(location)
        )
        
        onLocationReceived?.invoke(record)
    }
    
    private fun determineConfidence(location: Location): DataConfidence {
        return when {
            !location.hasAccuracy() -> DataConfidence.UNCERTAIN
            location.accuracy <= 10f -> DataConfidence.VERIFIED
            location.accuracy <= 50f -> DataConfidence.VERIFIED
            location.accuracy <= 100f -> DataConfidence.UNCERTAIN
            else -> DataConfidence.UNCERTAIN
        }
    }
    
    private fun buildLocationNotes(location: Location): String? {
        val parts = mutableListOf<String>()
        
        if (location.hasAccuracy()) {
            parts.add("Accuracy: ${location.accuracy.toInt()}m")
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (location.hasVerticalAccuracy()) {
                parts.add("Vertical accuracy: ${location.verticalAccuracyMeters.toInt()}m")
            }
        }
        
        location.provider?.let {
            parts.add("Provider: $it")
        }
        
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
    
    fun getLastKnownLocation(): Flow<Location?> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    trySend(location)
                }
                .addOnFailureListener {
                    trySend(null)
                }
        } catch (e: SecurityException) {
            trySend(null)
        }
        
        awaitClose()
    }
}
