package com.roamatlas.presentation.screens.journey

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JourneyDetailUiState(
    val journey: JourneyWithFlights? = null,
    val airports: Map<String, Airport> = emptyMap(),
    val nowMillis: Long = System.currentTimeMillis(),
    val isLoading: Boolean = true,
    val wasDeleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class JourneyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journeyRepository: JourneyRepository,
    private val airportRepository: AirportRepository
) : ViewModel() {
    private val journeyId = savedStateHandle.get<String>("journeyId")?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(JourneyDetailUiState())
    val uiState: StateFlow<JourneyDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            journeyRepository.getJourney(journeyId).collect { journey ->
                val codes = journey?.flights.orEmpty().flatMap {
                    listOf(it.departureAirportCode, it.arrivalAirportCode)
                }
                _uiState.value = _uiState.value.copy(
                    journey = journey,
                    airports = airportRepository.getByIataCodes(codes),
                    isLoading = false,
                    errorMessage = if (journey == null) "Journey not found." else null
                )
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                _uiState.value = _uiState.value.copy(nowMillis = System.currentTimeMillis())
            }
        }
    }

    fun updateJourney(name: String, notes: String) {
        viewModelScope.launch {
            journeyRepository.updateJourney(journeyId, name, notes)
        }
    }

    fun updateFlight(
        flight: FlightEntity,
        departureDelayMinutes: Int?,
        arrivalDelayMinutes: Int?,
        status: String,
        minimumConnectionMinutes: Int?,
        separateTicketFromPrevious: Boolean?,
        aircraftType: String,
        seatNumber: String,
        terminal: String,
        gate: String
    ) {
        viewModelScope.launch {
            journeyRepository.updateFlightOperationalDetails(
                flight = flight,
                estimatedDepartureTimeMillis = departureDelayMinutes?.let {
                    flight.departureTimeMillis + it * 60_000L
                },
                estimatedArrivalTimeMillis = arrivalDelayMinutes?.let {
                    flight.arrivalTimeMillis + it * 60_000L
                },
                status = status,
                minimumConnectionMinutes = minimumConnectionMinutes,
                separateTicketFromPrevious = separateTicketFromPrevious,
                aircraftType = aircraftType,
                seatNumber = seatNumber,
                terminal = terminal,
                gate = gate
            )
        }
    }

    fun deleteJourney() {
        viewModelScope.launch {
            journeyRepository.deleteJourney(journeyId)
            _uiState.value = _uiState.value.copy(wasDeleted = true)
        }
    }
}
