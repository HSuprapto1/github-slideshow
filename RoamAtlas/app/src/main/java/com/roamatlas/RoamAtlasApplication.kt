package com.roamatlas

import android.app.Application
import com.roamatlas.data.flight.FlightReminderManager
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

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            flightReminderManager.startServiceIfEnabled()
        }
    }
}
