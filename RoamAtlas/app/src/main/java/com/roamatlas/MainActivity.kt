package com.roamatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.roamatlas.presentation.RoamAtlasApp
import com.roamatlas.presentation.theme.RoamAtlasTheme
import com.roamatlas.data.location.LocationTrackingService
import com.roamatlas.data.location.TrackingPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            RoamAtlasTheme {
                RoamAtlasApp(windowSizeClass = windowSizeClass)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (trackingPreferences.enabled) {
            try {
                LocationTrackingService.startTracking(this, trackingPreferences.mode)
            } catch (_: Exception) {
                // The tracking screen explains any missing runtime permission.
            }
        }
    }
}
