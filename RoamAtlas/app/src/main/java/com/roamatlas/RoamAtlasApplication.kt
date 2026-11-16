package com.roamatlas

import android.app.Application
import android.util.Log
import com.roamatlas.data.flight.FlightReminderManager
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

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            flightReminderManager.startServiceIfEnabled()
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
