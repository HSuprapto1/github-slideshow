package com.roamatlas.data.location

import com.google.android.gms.location.Priority

/**
 * Configuration for GPS tracking behavior.
 * Designed to balance accuracy with battery efficiency.
 */
data class TrackingConfiguration(
    val mode: TrackingMode = TrackingMode.SMART,
    val intervalMillis: Long = 5 * 60 * 1000L,
    val fastestIntervalMillis: Long = 2 * 60 * 1000L,
    val minDisplacementMeters: Float = 50f,
    val maxWaitTimeMillis: Long = 10 * 60 * 1000L
) {
    val priority: Int
        get() = when (mode) {
            TrackingMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
            TrackingMode.SMART -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            TrackingMode.BATTERY_SAVER -> Priority.PRIORITY_LOW_POWER
            TrackingMode.PASSIVE -> Priority.PRIORITY_PASSIVE
        }
    
    companion object {
        val HIGH_ACCURACY = TrackingConfiguration(
            mode = TrackingMode.HIGH_ACCURACY,
            intervalMillis = 60 * 1000L,
            fastestIntervalMillis = 30 * 1000L,
            minDisplacementMeters = 10f,
            maxWaitTimeMillis = 2 * 60 * 1000L
        )
        
        val SMART = TrackingConfiguration(
            mode = TrackingMode.SMART,
            intervalMillis = 5 * 60 * 1000L,
            fastestIntervalMillis = 2 * 60 * 1000L,
            minDisplacementMeters = 50f,
            maxWaitTimeMillis = 10 * 60 * 1000L
        )
        
        val BATTERY_SAVER = TrackingConfiguration(
            mode = TrackingMode.BATTERY_SAVER,
            intervalMillis = 15 * 60 * 1000L,
            fastestIntervalMillis = 10 * 60 * 1000L,
            minDisplacementMeters = 100f,
            maxWaitTimeMillis = 30 * 60 * 1000L
        )
        
        val PASSIVE = TrackingConfiguration(
            mode = TrackingMode.PASSIVE,
            intervalMillis = 30 * 60 * 1000L,
            fastestIntervalMillis = 15 * 60 * 1000L,
            minDisplacementMeters = 200f,
            maxWaitTimeMillis = 60 * 60 * 1000L
        )
    }
}

enum class TrackingMode {
    HIGH_ACCURACY,
    SMART,
    BATTERY_SAVER,
    PASSIVE;
    
    val displayName: String
        get() = when (this) {
            HIGH_ACCURACY -> "High Accuracy"
            SMART -> "Smart (Recommended)"
            BATTERY_SAVER -> "Battery Saver"
            PASSIVE -> "Passive Only"
        }
    
    val description: String
        get() = when (this) {
            HIGH_ACCURACY -> "Updates every 1 min, 10m movement. Uses more battery."
            SMART -> "Updates every 5 min, 50m movement. Balanced accuracy and battery."
            BATTERY_SAVER -> "Updates every 15 min, 100m movement. Minimal battery impact."
            PASSIVE -> "Only receives locations from other apps. No battery impact."
        }
}
