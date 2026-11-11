package com.roamatlas.data.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class TrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val preferences = context.getSharedPreferences("gps_tracking", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("enabled", false)) return
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackground = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine || !hasBackground) return
        val ordinal = preferences.getInt("mode", TrackingMode.SMART.ordinal)
        val mode = TrackingMode.entries.getOrElse(ordinal) { TrackingMode.SMART }
        try {
            LocationTrackingService.startTracking(context, mode)
        } catch (_: Exception) {
            // Android may defer location foreground services after boot. MainActivity retries.
        }
    }
}
