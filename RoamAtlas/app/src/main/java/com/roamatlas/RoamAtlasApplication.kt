package com.roamatlas

import android.app.Application
import android.util.Log
import com.roamatlas.data.flight.FlightReminderManager
import com.roamatlas.data.journey.JourneyTravelDetector
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.photo.MissingPhotoCleaner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoamAtlasApplication : Application() {
    @Inject
    lateinit var flightReminderManager: FlightReminderManager

    @Inject
    lateinit var missingPhotoCleaner: MissingPhotoCleaner

    @Inject
    lateinit var locationDao: LocationDao

    @Inject
    lateinit var journeyTravelDetector: JourneyTravelDetector

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            flightReminderManager.startServiceIfEnabled()
            runCatching { locationDao.markLikelyAirborneFromSensors() }
                .onFailure { Log.w(TAG, "Airborne backfill failed", it) }
            // Airplane-mode recovery: last known point after GPS returns / app reopen.
            runCatching {
                locationDao.getLatestLocation()?.let { latest ->
                    journeyTravelDetector.recheckWithLastKnown(
                        latitude = latest.latitude,
                        longitude = latest.longitude,
                        timestampMillis = latest.timestampMillis,
                        isAirborne = latest.isAirborne
                    )
                }
            }.onFailure { Log.w(TAG, "Journey travel recheck failed", it) }
            runCatching { missingPhotoCleaner.cleanup() }
                .onSuccess { result ->
                    if (!result.isEmpty) {
                        Log.i(TAG, "Cleared missing photo links: ${result.summary}")
                    }
                }
                .onFailure { Log.w(TAG, "Missing photo cleanup failed", it) }
        }
    }

    companion object {
        private const val TAG = "RoamAtlasApp"
    }
}
