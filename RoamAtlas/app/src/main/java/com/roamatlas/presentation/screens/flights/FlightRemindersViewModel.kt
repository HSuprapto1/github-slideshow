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
    private val flightDao: FlightDao
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

    fun updateAirportBoardDetails(flightId: Long, terminal: String, gate: String) {
        viewModelScope.launch {
            val flight = flightDao.getFlightById(flightId) ?: return@launch
            val hasManualSource = noteValue(flight.notes, "Airport details source")
                ?.equals("Manually entered from airport board", ignoreCase = true) == true
            val automaticTerminal = noteValue(flight.notes, "Automatic departure terminal")
                ?: noteValue(flight.notes, "Departure terminal")
                    .takeIf { flight.source.contains("EMAIL") && !hasManualSource }
            val automaticGate = noteValue(flight.notes, "Automatic departure gate")
                ?: noteValue(flight.notes, "Departure gate")
                    .takeIf { flight.source.contains("EMAIL") && !hasManualSource }
            val updatedNotes = replaceAirportDetail(
                notes = flight.notes,
                terminal = terminal.trim().uppercase(),
                gate = gate.trim().uppercase(),
                automaticTerminal = automaticTerminal,
                automaticGate = automaticGate
            )
            flightDao.update(
                flight.copy(
                    notes = updatedNotes,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun replaceAirportDetail(
        notes: String?,
        terminal: String,
        gate: String,
        automaticTerminal: String?,
        automaticGate: String?
    ): String {
        val retained = notes.orEmpty().lineSequence()
            .filter { line ->
                !line.startsWith("Departure terminal:", ignoreCase = true) &&
                    !line.startsWith("Departure gate:", ignoreCase = true) &&
                    !line.startsWith("Automatic departure terminal:", ignoreCase = true) &&
                    !line.startsWith("Automatic departure gate:", ignoreCase = true) &&
                    !line.startsWith("Airport details source:", ignoreCase = true)
            }
            .filter { it.isNotBlank() }
            .toMutableList()
        if (terminal.isNotBlank() || gate.isNotBlank()) {
            if (terminal.isNotBlank()) retained.add("Departure terminal: $terminal")
            if (gate.isNotBlank()) retained.add("Departure gate: $gate")
            automaticTerminal?.let { retained.add("Automatic departure terminal: $it") }
            automaticGate?.let { retained.add("Automatic departure gate: $it") }
            retained.add("Airport details source: Manually entered from airport board")
        } else {
            automaticTerminal?.let { retained.add("Departure terminal: $it") }
            automaticGate?.let { retained.add("Departure gate: $it") }
        }
        return retained.joinToString("\n")
    }

    private fun noteValue(notes: String?, label: String): String? =
        notes?.lineSequence()
            ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
