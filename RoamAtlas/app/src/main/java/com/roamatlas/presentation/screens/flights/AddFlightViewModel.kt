package com.roamatlas.presentation.screens.flights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TransportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

data class AddFlightUiState(
    val journeyId: Long? = null,
    val transportMode: String = TransportMode.FLIGHT,
    // Departure
    val departureAirportCode: String = "",
    val departureAirportName: String = "",
    val departureCity: String = "",
    val departureCountryCode: String = "",
    val departureAirportSearch: String = "",
    val departureAirportSuggestions: List<Airport> = emptyList(),
    val departureDateMillis: Long? = null,
    val departureHour: Int = 12,
    val departureMinute: Int = 0,
    
    // Arrival
    val arrivalAirportCode: String = "",
    val arrivalAirportName: String = "",
    val arrivalCity: String = "",
    val arrivalCountryCode: String = "",
    val arrivalAirportSearch: String = "",
    val arrivalAirportSuggestions: List<Airport> = emptyList(),
    val arrivalDateMillis: Long? = null,
    val arrivalHour: Int = 14,
    val arrivalMinute: Int = 0,
    
    // Flight details
    val flightNumber: String = "",
    val airline: String = "",
    val aircraftType: String = "",
    val seatNumber: String = "",
    val bookingReference: String = "",
    val notes: String = "",
    
    // Calculated
    val calculatedDistanceKm: Double? = null,
    
    // State
    val isSaving: Boolean = false,
    val flightSaved: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() {
            val effectiveArrival = arrivalAirportCode.ifBlank {
                if (TransportMode.isHotel(transportMode)) departureAirportCode else ""
            }
            val codeOk = when {
                TransportMode.isHotel(transportMode) ->
                    departureAirportCode.length >= 2 && effectiveArrival.length >= 2
                TransportMode.isFlight(transportMode) ->
                    departureAirportCode.length == 3 && effectiveArrival.length == 3
                else ->
                    departureAirportCode.length >= 2 && effectiveArrival.length >= 2
            }
            return codeOk && departureDateMillis != null && arrivalDateMillis != null
        }
}

@HiltViewModel
class AddFlightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flightDao: FlightDao,
    private val airportRepository: AirportRepository,
    private val journeyRepository: JourneyRepository
) : ViewModel() {

    private val initialJourneyId =
        savedStateHandle.get<String>("journeyId")?.toLongOrNull()
    private val initialMode = savedStateHandle.get<String>("mode")
        ?.takeIf { it.isNotBlank() }
        ?: TransportMode.FLIGHT

    private val _uiState = MutableStateFlow(
        AddFlightUiState(
            journeyId = initialJourneyId,
            transportMode = when (initialMode.uppercase()) {
                TransportMode.TRAIN -> TransportMode.TRAIN
                TransportMode.BUS -> TransportMode.BUS
                TransportMode.HOTEL -> TransportMode.HOTEL
                else -> TransportMode.FLIGHT
            }
        )
    )
    val uiState: StateFlow<AddFlightUiState> = _uiState.asStateFlow()
    
    private var selectedDepartureAirport: Airport? = null
    private var selectedArrivalAirport: Airport? = null

    fun setTransportMode(mode: String) {
        _uiState.update { current ->
            val next = current.copy(transportMode = mode)
            if (TransportMode.isHotel(mode) &&
                current.arrivalAirportCode.isBlank() &&
                current.departureAirportCode.isNotBlank()
            ) {
                next.copy(
                    arrivalAirportCode = current.departureAirportCode,
                    arrivalAirportName = current.departureAirportName,
                    arrivalCity = current.departureCity,
                    arrivalCountryCode = current.departureCountryCode,
                    arrivalAirportSearch = current.departureAirportSearch
                )
            } else {
                next
            }
        }
    }

    fun searchDepartureAirport(query: String) {
        _uiState.update { it.copy(departureAirportSearch = query) }
        viewModelScope.launch {
            val suggestions = if (query.length >= 2) {
                airportRepository.search(query, 8)
            } else {
                emptyList()
            }
            _uiState.update {
                if (it.departureAirportSearch == query) {
                    it.copy(departureAirportSuggestions = suggestions)
                } else it
            }
        }
    }

    fun searchArrivalAirport(query: String) {
        _uiState.update { it.copy(arrivalAirportSearch = query) }
        viewModelScope.launch {
            val suggestions = if (query.length >= 2) {
                airportRepository.search(query, 8)
            } else {
                emptyList()
            }
            _uiState.update {
                if (it.arrivalAirportSearch == query) {
                    it.copy(arrivalAirportSuggestions = suggestions)
                } else it
            }
        }
    }

    fun selectDepartureAirport(airport: Airport) {
        selectedDepartureAirport = airport
        _uiState.update {
            it.copy(
                departureAirportCode = airport.iataCode,
                departureAirportName = airport.name,
                departureCity = airport.city,
                departureCountryCode = airport.countryCode,
                departureAirportSearch = airport.displayName,
                departureAirportSuggestions = emptyList()
            )
        }
        calculateDistance()
    }

    fun selectArrivalAirport(airport: Airport) {
        selectedArrivalAirport = airport
        _uiState.update {
            it.copy(
                arrivalAirportCode = airport.iataCode,
                arrivalAirportName = airport.name,
                arrivalCity = airport.city,
                arrivalCountryCode = airport.countryCode,
                arrivalAirportSearch = airport.displayName,
                arrivalAirportSuggestions = emptyList()
            )
        }
        calculateDistance()
    }
    
    fun updateDepartureAirportCode(code: String) {
        _uiState.update { it.copy(departureAirportCode = code) }
        selectedDepartureAirport = null
        viewModelScope.launch {
            airportRepository.findByCode(code)?.let(::selectDepartureAirport)
        }
    }
    
    fun updateDepartureAirportName(name: String) {
        _uiState.update { it.copy(departureAirportName = name) }
    }
    
    fun updateDepartureCity(city: String) {
        _uiState.update { it.copy(departureCity = city) }
    }
    
    fun updateDepartureCountryCode(code: String) {
        _uiState.update { it.copy(departureCountryCode = code) }
    }
    
    fun updateDepartureDate(millis: Long) {
        _uiState.update { it.copy(departureDateMillis = millis) }
    }
    
    fun updateDepartureTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(departureHour = hour, departureMinute = minute) }
    }
    
    fun updateArrivalAirportCode(code: String) {
        _uiState.update { it.copy(arrivalAirportCode = code) }
        selectedArrivalAirport = null
        viewModelScope.launch {
            airportRepository.findByCode(code)?.let(::selectArrivalAirport)
        }
    }
    
    fun updateArrivalAirportName(name: String) {
        _uiState.update { it.copy(arrivalAirportName = name) }
    }
    
    fun updateArrivalCity(city: String) {
        _uiState.update { it.copy(arrivalCity = city) }
    }
    
    fun updateArrivalCountryCode(code: String) {
        _uiState.update { it.copy(arrivalCountryCode = code) }
    }
    
    fun updateArrivalDate(millis: Long) {
        _uiState.update { it.copy(arrivalDateMillis = millis) }
    }
    
    fun updateArrivalTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(arrivalHour = hour, arrivalMinute = minute) }
    }
    
    fun updateFlightNumber(number: String) {
        val normalized = number.uppercase().replace(" ", "")
        val prefix = Regex("^([A-Z0-9]{2,3}?)(?=\\d)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
        _uiState.update {
            it.copy(
                flightNumber = normalized,
                airline = if (it.airline.isBlank()) {
                    AIRLINE_NAMES[prefix].orEmpty()
                } else {
                    it.airline
                }
            )
        }
    }
    
    fun updateAirline(airline: String) {
        _uiState.update { it.copy(airline = airline) }
    }
    
    fun updateAircraftType(type: String) {
        _uiState.update { it.copy(aircraftType = type) }
    }
    
    fun updateSeatNumber(seat: String) {
        _uiState.update { it.copy(seatNumber = seat) }
    }
    
    fun updateBookingReference(ref: String) {
        _uiState.update { it.copy(bookingReference = ref) }
    }
    
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun calculateDistance() {
        val depCoords = selectedDepartureAirport
        val arrCoords = selectedArrivalAirport
        
        if (depCoords != null && arrCoords != null) {
            val distance = haversineDistance(
                depCoords.latitude, depCoords.longitude,
                arrCoords.latitude, arrCoords.longitude
            )
            _uiState.update { it.copy(calculatedDistanceKm = distance) }
        } else {
            _uiState.update { it.copy(calculatedDistanceKm = null) }
        }
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * acos(kotlin.math.sqrt(1 - a).coerceIn(0.0, 1.0))
        return earthRadiusKm * c
    }
    
    fun saveFlight() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update { it.copy(error = "Please fill in all required fields") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            try {
                val departureTime = com.roamatlas.data.flight.FlightScheduleTimes.combineLocalDateAndTimeMillis(
                    state.departureDateMillis!!,
                    state.departureHour,
                    state.departureMinute,
                    selectedDepartureAirport?.timeZoneId
                )
                val arrivalTime = com.roamatlas.data.flight.FlightScheduleTimes.combineLocalDateAndTimeMillis(
                    state.arrivalDateMillis!!,
                    state.arrivalHour,
                    state.arrivalMinute,
                    selectedArrivalAirport?.timeZoneId
                )
                
                val durationMinutes = com.roamatlas.data.flight.FlightScheduleTimes.durationMinutes(
                    departureMillis = departureTime,
                    arrivalMillis = arrivalTime,
                    departureTimeZoneId = selectedDepartureAirport?.timeZoneId,
                    arrivalTimeZoneId = selectedArrivalAirport?.timeZoneId,
                    distanceKm = state.calculatedDistanceKm
                ).toInt()
                
                val isHotel = TransportMode.isHotel(state.transportMode)
                val arrivalCode = state.arrivalAirportCode.ifBlank {
                    if (isHotel) state.departureAirportCode else ""
                }
                if (arrivalCode.isBlank()) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "Please fill in all required fields")
                    }
                    return@launch
                }
                val flight = FlightEntity(
                    id = 0, // Auto-generated
                    departureAirportCode = state.departureAirportCode.uppercase(),
                    departureAirportName = state.departureAirportName.ifBlank { null },
                    departureCity = state.departureCity.ifBlank { null },
                    departureCountryCode = state.departureCountryCode.ifBlank { null },
                    arrivalAirportCode = arrivalCode.uppercase(),
                    arrivalAirportName = state.arrivalAirportName.ifBlank {
                        if (isHotel) state.departureAirportName.ifBlank { null } else null
                    },
                    arrivalCity = state.arrivalCity.ifBlank {
                        if (isHotel) state.departureCity.ifBlank { null } else null
                    },
                    arrivalCountryCode = state.arrivalCountryCode.ifBlank {
                        if (isHotel) state.departureCountryCode.ifBlank { null } else null
                    },
                    departureTimeMillis = departureTime,
                    arrivalTimeMillis = arrivalTime,
                    flightNumber = state.flightNumber.ifBlank { null },
                    airline = state.airline.ifBlank { null },
                    aircraftType = if (isHotel) null else state.aircraftType.ifBlank { null },
                    distanceKm = if (isHotel) null else state.calculatedDistanceKm,
                    durationMinutes = if (durationMinutes > 0) durationMinutes else null,
                    seatNumber = if (isHotel) null else state.seatNumber.ifBlank { null },
                    bookingReference = state.bookingReference.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    photoUri = null,
                    source = "MANUAL",
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis(),
                    transportMode = state.transportMode
                )

                val insertedId = flightDao.insert(flight)
                state.journeyId?.let { journeyId ->
                    journeyRepository.attachFlightToJourney(journeyId, insertedId)
                }
                _uiState.update { it.copy(isSaving = false, flightSaved = true) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false, 
                        error = "Failed to save flight: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    private fun combineDateAndTime(
        dateMillis: Long,
        hour: Int,
        minute: Int,
        timeZoneId: String?
    ): Long {
        if (!timeZoneId.isNullOrBlank()) {
            val date = java.time.Instant.ofEpochMilli(dateMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
            return date.atTime(hour, minute)
                .atZone(runCatching { java.time.ZoneId.of(timeZoneId) }
                    .getOrDefault(java.time.ZoneId.systemDefault()))
                .toInstant()
                .toEpochMilli()
        }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    // For pre-filling from parsed email
    fun prefillFromParsedFlight(
        departureCode: String,
        arrivalCode: String,
        departureTime: Long,
        arrivalTime: Long,
        flightNumber: String?,
        airline: String?,
        bookingRef: String?
    ) {
        val depCal = Calendar.getInstance().apply { timeInMillis = departureTime }
        val arrCal = Calendar.getInstance().apply { timeInMillis = arrivalTime }
        
        _uiState.update {
            it.copy(
                departureAirportCode = departureCode,
                arrivalAirportCode = arrivalCode,
                departureDateMillis = departureTime,
                departureHour = depCal.get(Calendar.HOUR_OF_DAY),
                departureMinute = depCal.get(Calendar.MINUTE),
                arrivalDateMillis = arrivalTime,
                arrivalHour = arrCal.get(Calendar.HOUR_OF_DAY),
                arrivalMinute = arrCal.get(Calendar.MINUTE),
                flightNumber = flightNumber ?: "",
                airline = airline ?: "",
                bookingReference = bookingRef ?: ""
            )
        }
        viewModelScope.launch {
            airportRepository.findByCode(departureCode)?.let(::selectDepartureAirport)
            airportRepository.findByCode(arrivalCode)?.let(::selectArrivalAirport)
        }
    }

    companion object {
        private val AIRLINE_NAMES = mapOf(
            "SQ" to "Singapore Airlines",
            "GA" to "Garuda Indonesia",
            "QF" to "Qantas",
            "EK" to "Emirates",
            "QR" to "Qatar Airways",
            "CX" to "Cathay Pacific",
            "JL" to "Japan Airlines",
            "NH" to "ANA",
            "KE" to "Korean Air",
            "TG" to "Thai Airways",
            "MH" to "Malaysia Airlines",
            "AK" to "AirAsia",
            "TR" to "Scoot",
            "ID" to "Batik Air",
            "JT" to "Lion Air",
            "QG" to "Citilink",
            "BA" to "British Airways",
            "LH" to "Lufthansa",
            "TK" to "Turkish Airlines",
            "EY" to "Etihad Airways"
        )
    }
}
