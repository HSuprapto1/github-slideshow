package com.roamatlas.presentation.screens.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.flight.FlightReminderManager
import com.roamatlas.data.flight.FlightReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlightRemindersViewModel @Inject constructor(
    private val reminderManager: FlightReminderManager
) : ViewModel() {
    
    val settings: StateFlow<FlightReminderSettings> = reminderManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlightReminderSettings()
        )
    
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reminderManager.setEnabled(enabled)
        }
    }
    
    fun updateHoursBeforeDeparture(hours: Int) {
        viewModelScope.launch {
            val current = settings.value
            reminderManager.updateSettings(current.copy(hoursBeforeDeparture = hours))
        }
    }
    
    fun updateAdvanceWarningHours(hours: Int) {
        viewModelScope.launch {
            val current = settings.value
            reminderManager.updateSettings(current.copy(advanceWarningHours = hours))
        }
    }
    
    fun updateUrgentWarningHours(hours: Int) {
        viewModelScope.launch {
            val current = settings.value
            reminderManager.updateSettings(current.copy(urgentWarningHours = hours))
        }
    }
    
    fun updateNotifyAtAirport(notify: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            reminderManager.updateSettings(current.copy(notifyWhenAtAirport = notify))
        }
    }
    
    fun updateNotifyPossibleMissed(notify: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            reminderManager.updateSettings(current.copy(notifyPossibleMissed = notify))
        }
    }
}
