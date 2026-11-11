package com.roamatlas.data.flight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "flight_reminders")

data class FlightReminderSettings(
    val isEnabled: Boolean = false,
    val hoursBeforeDeparture: Int = 2,      // Hours to be at airport before flight
    val advanceWarningHours: Int = 6,        // When to start checking
    val urgentWarningHours: Int = 3,         // When to send urgent warnings
    val notifyWhenAtAirport: Boolean = true, // Notify when user arrives at airport
    val notifyPossibleMissed: Boolean = true // Notify if flight might be missed
)

@Singleton
class FlightReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val HOURS_BEFORE_DEPARTURE = intPreferencesKey("hours_before_departure")
        val ADVANCE_WARNING_HOURS = intPreferencesKey("advance_warning_hours")
        val URGENT_WARNING_HOURS = intPreferencesKey("urgent_warning_hours")
        val NOTIFY_AT_AIRPORT = booleanPreferencesKey("notify_at_airport")
        val NOTIFY_POSSIBLE_MISSED = booleanPreferencesKey("notify_possible_missed")
    }
    
    val settings: Flow<FlightReminderSettings> = context.reminderDataStore.data.map { prefs ->
        FlightReminderSettings(
            isEnabled = prefs[PreferencesKeys.REMINDERS_ENABLED] ?: false,
            hoursBeforeDeparture = prefs[PreferencesKeys.HOURS_BEFORE_DEPARTURE] ?: 2,
            advanceWarningHours = prefs[PreferencesKeys.ADVANCE_WARNING_HOURS] ?: 6,
            urgentWarningHours = prefs[PreferencesKeys.URGENT_WARNING_HOURS] ?: 3,
            notifyWhenAtAirport = prefs[PreferencesKeys.NOTIFY_AT_AIRPORT] ?: true,
            notifyPossibleMissed = prefs[PreferencesKeys.NOTIFY_POSSIBLE_MISSED] ?: true
        )
    }
    
    suspend fun setEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { prefs ->
            prefs[PreferencesKeys.REMINDERS_ENABLED] = enabled
        }
        
        if (enabled) {
            FlightReminderService.start(context)
        } else {
            FlightReminderService.stop(context)
        }
    }
    
    suspend fun updateSettings(settings: FlightReminderSettings) {
        context.reminderDataStore.edit { prefs ->
            prefs[PreferencesKeys.REMINDERS_ENABLED] = settings.isEnabled
            prefs[PreferencesKeys.HOURS_BEFORE_DEPARTURE] = settings.hoursBeforeDeparture
            prefs[PreferencesKeys.ADVANCE_WARNING_HOURS] = settings.advanceWarningHours
            prefs[PreferencesKeys.URGENT_WARNING_HOURS] = settings.urgentWarningHours
            prefs[PreferencesKeys.NOTIFY_AT_AIRPORT] = settings.notifyWhenAtAirport
            prefs[PreferencesKeys.NOTIFY_POSSIBLE_MISSED] = settings.notifyPossibleMissed
        }
        
        if (settings.isEnabled) {
            FlightReminderService.start(context)
        } else {
            FlightReminderService.stop(context)
        }
    }
    
    suspend fun startServiceIfEnabled() {
        if (settings.first().isEnabled) {
            FlightReminderService.start(context)
        }
    }
}
