package com.roamatlas.presentation.screens.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.flight.FlightReminderManager
import com.roamatlas.data.flight.FlightReminderSettings
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpcomingFlightState(
    val flight: FlightEntity? = null,
    val minutesUntilDeparture: Long = 0,
    val isTransit: Boolean = false,
    val transitMinutesRemaining: Long? = null
)

@HiltViewModel
class FlightRemindersViewModel @Inject constructor(
    private val reminderManager: FlightReminderManager,
    flightDao: FlightDao
) : ViewModel() {

    private val clock = MutableStateFlow(System.currentTimeMillis())
    
    val settings: StateFlow<FlightReminderSettings> = reminderManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlightReminderSettings()
        )

    val upcomingFlight: StateFlow<UpcomingFlightState> = combine(
        flightDao.getAllFlights(),
        clock
    ) { flights, now ->
        val next = flights.filter { it.departureTimeMillis > now }
            .minByOrNull { it.departureTimeMillis }
        val previous = flights.filter { it.arrivalTimeMillis <= now }
            .maxByOrNull { it.arrivalTimeMillis }
        val isTransit = next != null && previous != null &&
            previous.arrivalAirportCode.equals(next.departureAirportCode, ignoreCase = true) &&
            next.departureTimeMillis - previous.arrivalTimeMillis in 0..(24 * 60 * 60 * 1000L)
        UpcomingFlightState(
            flight = next,
            minutesUntilDeparture = next?.let {
                (it.departureTimeMillis - now).coerceAtLeast(0) / 60_000
            } ?: 0,
            isTransit = isTransit,
            transitMinutesRemaining = if (isTransit) {
                next?.let { (it.departureTimeMillis - now).coerceAtLeast(0) / 60_000 }
            } else null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UpcomingFlightState()
    )

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }
    
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
