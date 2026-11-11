package com.roamatlas.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.roamatlas.MainActivity
import com.roamatlas.R
import com.roamatlas.data.geo.OfflineGeoResolver
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {
    
    @Inject
    lateinit var locationTracker: LocationTracker
    
    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    @Inject
    lateinit var geoResolver: OfflineGeoResolver
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var isTracking = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent)
            ACTION_STOP -> stopTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            null -> if (trackingPreferences.enabled) startTracking(null)
        }
        return START_STICKY
    }
    
    private fun startTracking(intent: Intent?) {
        if (isTracking) return
        
        val modeOrdinal = intent?.getIntExtra(EXTRA_TRACKING_MODE, trackingPreferences.mode.ordinal)
            ?: trackingPreferences.mode.ordinal
        val mode = TrackingMode.entries[modeOrdinal]
        trackingPreferences.mode = mode
        
        val configuration = when (mode) {
            TrackingMode.HIGH_ACCURACY -> TrackingConfiguration.HIGH_ACCURACY
            TrackingMode.SMART -> TrackingConfiguration.SMART
            TrackingMode.BATTERY_SAVER -> TrackingConfiguration.BATTERY_SAVER
            TrackingMode.PASSIVE -> TrackingConfiguration.PASSIVE
        }
        
        val notification = createNotification(
            title = "Tracking Active",
            text = "Recording your location (${mode.displayName})"
        )
        
        startForeground(NOTIFICATION_ID, notification)
        
        val success = locationTracker.startTracking(configuration) { location ->
            saveLocation(location)
        }
        
        if (success) {
            isTracking = true
            trackingPreferences.enabled = true
            updateNotification("Tracking Active", "Recording your location")
        } else {
            trackingPreferences.enabled = false
            stopSelf()
        }
    }
    
    private fun stopTracking() {
        locationTracker.stopTracking()
        trackingPreferences.enabled = false
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun pauseTracking() {
        locationTracker.pauseTracking()
        updateNotification("Tracking Paused", "Tap to resume")
    }
    
    private fun resumeTracking() {
        locationTracker.resumeTracking()
        updateNotification("Tracking Active", "Recording your location")
    }
    
    private fun saveLocation(location: LocationRecord) {
        serviceScope.launch {
            try {
                val place = geoResolver.resolve(location.latitude, location.longitude)
                val enriched = location.copy(
                    countryCode = place.countryCode,
                    countryName = place.countryName,
                    city = place.city
                )
                val isDuplicate = enriched.originalSourceId?.let {
                    locationRepository.isDuplicate(it)
                } ?: false
                
                if (!isDuplicate) {
                    locationRepository.insertLocation(enriched)
                    
                    val count = locationTracker.trackingState.value.locationCount
                    updateNotification(
                        "Tracking Active",
                        "$count locations recorded"
                    )
                }
            } catch (e: Exception) {
                // Log error but don't crash the service
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when RoamAtlas is recording your location"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val pauseResumeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, LocationTrackingService::class.java).apply {
                action = if (locationTracker.trackingState.value.isPaused) {
                    ACTION_RESUME
                } else {
                    ACTION_PAUSE
                }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val isPaused = locationTracker.trackingState.value.isPaused
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                if (isPaused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(0, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationTracker.stopTracking()
        serviceScope.cancel()
    }
    
    companion object {
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.roamatlas.action.START_TRACKING"
        const val ACTION_STOP = "com.roamatlas.action.STOP_TRACKING"
        const val ACTION_PAUSE = "com.roamatlas.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.roamatlas.action.RESUME_TRACKING"
        
        const val EXTRA_TRACKING_MODE = "tracking_mode"
        
        fun startTracking(context: Context, mode: TrackingMode = TrackingMode.SMART) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRACKING_MODE, mode.ordinal)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        
        fun pauseTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }
        
        fun resumeTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }
}
