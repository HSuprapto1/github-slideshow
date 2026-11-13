package com.roamatlas.data.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("gps_tracking", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var mode: TrackingMode
        get() {
            val ordinal = preferences.getInt(KEY_MODE, TrackingMode.SMART.ordinal)
            return TrackingMode.entries.getOrElse(ordinal) { TrackingMode.SMART }
        }
        set(value) = preferences.edit().putInt(KEY_MODE, value.ordinal).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
    }
}
