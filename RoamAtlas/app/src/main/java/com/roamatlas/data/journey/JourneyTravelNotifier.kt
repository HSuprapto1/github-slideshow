package com.roamatlas.data.journey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.roamatlas.MainActivity
import com.roamatlas.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JourneyTravelNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notifyOnce(key: String, title: String, text: String, journeyId: Long?) {
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (journeyId != null) {
                putExtra(EXTRA_JOURNEY_ID, journeyId)
            }
        }
        val pending = PendingIntent.getActivity(
            context,
            (journeyId ?: 0L).toInt() + 4100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((key.hashCode() and 0x7fffffff) % 100_000 + 4100, notification)
    }

    fun clearKey(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun setSuggestedArrivalPlace(journeyId: Long, placeName: String, placeId: Long) {
        prefs.edit()
            .putString(suggestedPlaceKey(journeyId), placeName)
            .putLong(suggestedPlaceIdKey(journeyId), placeId)
            .apply()
    }

    fun getSuggestedArrivalPlace(journeyId: Long): String? =
        prefs.getString(suggestedPlaceKey(journeyId), null)?.takeIf { it.isNotBlank() }

    fun getSuggestedArrivalPlaceId(journeyId: Long): Long =
        prefs.getLong(suggestedPlaceIdKey(journeyId), -1L)

    fun clearSuggestedArrivalPlace(journeyId: Long) {
        prefs.edit()
            .remove(suggestedPlaceKey(journeyId))
            .remove(suggestedPlaceIdKey(journeyId))
            .apply()
    }

    private fun suggestedPlaceKey(journeyId: Long) = "suggested_place_$journeyId"
    private fun suggestedPlaceIdKey(journeyId: Long) = "suggested_place_id_$journeyId"

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Journey travel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Airport arrival/departure and journey recovery alerts"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "journey_travel"
        const val PREFS = "journey_travel_notify"
        const val EXTRA_JOURNEY_ID = "open_journey_id"
    }
}
