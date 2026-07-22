package com.hardian.phonetracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hardian.phonetracker.data.LiveLocation

class LocationTracker(
    context: Context,
    private val onLocation: (LiveLocation) -> Unit,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
        .setMinUpdateIntervalMillis(1_000L)
        .setMinUpdateDistanceMeters(1f)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onLocation(
                LiveLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    updatedAtMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }
}
